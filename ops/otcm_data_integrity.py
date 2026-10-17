#!/usr/bin/env python3
"""Read-only, server-local comparison with the fixed OTCM cutover backup.

No database is restored or updated. SQL literals and file names stay in memory
or in a 0700 temporary directory; the report contains table-level aggregates.
"""
import base64
from collections import Counter
import datetime
from decimal import Decimal, InvalidOperation
import gzip
import hashlib
import hmac
import json
import os
import pathlib
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

import otcm_promotion as promotion

REQUEST = {}
BACKUP = pathlib.Path('/www/backup/otcm-releases/20261017T161852183005Z-39048276224')
EXPECTED = {
    'database.sql.gz': '7258f5e0ae5bd53e03750ffb1843eef72f0e7c99819ad0294738d8bdd3cfc2d2',
    'files.tar': '9858a8e5780f7cd89da18a9b22109944c0c9d1db842e4b98d5786a3e89a5fecb',
    'previous.jar': '1a13fc82a6dbb9ee45a515d3284227db94e63c75b939a6a6efe465377332a0df',
}
CURRENT_JAR = '945d44b37c03592076c19d76193f6446863aea0ebf4c223aa54315215a1328e2'
FILE_ROOTS = (promotion.APP / 'runtime/uploadPath', promotion.APP / 'runtime/hospital-private')
ACCOUNT_COLUMNS = frozenset({'user_id', 'user_name', 'password', 'user_type', 'dept_id', 'status',
                             'del_flag', 'email', 'phonenumber', 'nick_name', 'sex', 'avatar', 'remark'})


def ensure(condition, message):
    if not condition:
        raise ValueError(message)


def stream_digest(source):
    digest = hashlib.sha256()
    for block in iter(lambda: source.read(1024 * 1024), b''):
        digest.update(block)
    return digest.hexdigest()


def canonical_literal(token):
    token = token.strip()
    if token == 'NULL':
        return ['null']
    if re.fullmatch(r'0x[0-9A-Fa-f]*', token):
        return ['binary', token[2:].lower()]
    if token.startswith("'") and token.endswith("'"):
        body, value, index = token[1:-1], [], 0
        escapes = {'0': '\0', 'b': '\b', 'n': '\n', 'r': '\r', 't': '\t', 'Z': '\x1a',
                   "'": "'", '"': '"', '\\': '\\'}
        while index < len(body):
            character = body[index]
            if character == '\\':
                index += 1
                ensure(index < len(body), 'Incomplete SQL literal escape')
                value.append(escapes.get(body[index], body[index]))
            elif character == "'" and index + 1 < len(body) and body[index + 1] == "'":
                value.append("'")
                index += 1
            else:
                value.append(character)
            index += 1
        return ['string', ''.join(value)]
    if re.fullmatch(r"[bB]'[01]+'", token):
        return ['bits', token[2:-1]]
    ensure(re.fullmatch(r'[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?', token), 'Unrecognized SQL literal')
    # Same MySQL representation can vary with complete/extended INSERT options.
    try:
        numeric = Decimal(token)
        rendered = format(numeric, 'f')
        if '.' in rendered:
            rendered = rendered.rstrip('0').rstrip('.')
        return ['number', rendered if numeric else '0']
    except InvalidOperation:
        raise ValueError('Invalid numeric SQL literal') from None


def insert_rows(text):
    """Parse mysqldump VALUES tuples; never execute SQL or interpolate literals."""
    position = 0
    while position < len(text):
        while position < len(text) and text[position] in ' ,\r\n\t':
            position += 1
        if position == len(text):
            return
        ensure(text[position] == '(', 'Expected SQL row tuple')
        position += 1
        row = []
        while True:
            start = position
            quoted = False
            while position < len(text):
                character = text[position]
                if quoted and character == '\\':
                    position += 2
                    continue
                if character == "'":
                    if quoted and position + 1 < len(text) and text[position + 1] == "'":
                        position += 2
                        continue
                    quoted = not quoted
                if not quoted and character in ',)':
                    break
                position += 1
            ensure(position < len(text) and not quoted, 'Incomplete SQL row')
            row.append(canonical_literal(text[start:position]))
            separator = text[position]
            position += 1
            if separator == ')':
                yield row
                break


def keyed_digest(salt, value):
    canonical = json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode()
    return hmac.new(salt, canonical, hashlib.sha256).hexdigest()


def parse_dump(source, salt):
    tables = {}
    ddl = []
    creating = None
    delimiter = ';'
    for line in source:
        if line.startswith('DELIMITER '):
            delimiter = line.split(None, 1)[1].strip()
            continue
        # Stored routine/trigger bodies may contain INSERT text; they are not data rows.
        if delimiter != ';':
            continue
        if line.startswith('CREATE TABLE `'):
            match = re.match(r'CREATE TABLE `([A-Za-z0-9_]+)`', line)
            ensure(match is not None and creating is None, 'Unexpected CREATE statement')
            creating = match.group(1)
            ddl = [line]
            continue
        if creating:
            ddl.append(line)
            if line.rstrip().endswith(';'):
                schema = ''.join(ddl)
                columns = re.findall(r'^\s*`([A-Za-z0-9_]+)`\s+', schema, re.M)
                primary = re.search(r'^\s*PRIMARY KEY\s*\(([^\n]+)\)', schema, re.M)
                primary_columns = re.findall(r'`([A-Za-z0-9_]+)`', primary.group(1)) if primary else []
                ensure(columns, 'Table has no columns')
                normalized = re.sub(r'\bAUTO_INCREMENT=\d+\s*', '', schema)
                normalized = re.sub(r'\s+', ' ', normalized).strip()
                tables[creating] = {'columns': columns, 'primaryColumns': primary_columns,
                                    'schemaSha256': hashlib.sha256(normalized.encode()).hexdigest(), 'rows': {}}
                creating = None
            continue
        if not line.startswith('INSERT INTO `'):
            continue
        match = re.fullmatch(r'INSERT INTO `([A-Za-z0-9_]+)`(?: \(([^\n]*?)\))? VALUES (.*);\s*', line, re.S)
        ensure(match is not None, 'Unrecognized INSERT shape')
        name, explicit_columns, values = match.groups()
        ensure(name in tables, 'INSERT has no preceding schema')
        table = tables[name]
        order = re.findall(r'`([A-Za-z0-9_]+)`', explicit_columns) if explicit_columns else table['columns']
        ensure(set(order) == set(table['columns']), 'Unexpected INSERT columns')
        for row in insert_rows(values):
            ensure(len(row) == len(order), 'SQL row width mismatch')
            by_column = dict(zip(order, row))
            normalized_row = [by_column[column] for column in table['columns']]
            key = keyed_digest(salt, [by_column[column] for column in table['primaryColumns']]) if table['primaryColumns'] else keyed_digest(salt, normalized_row)
            column_hashes = tuple(keyed_digest(salt, cell) for cell in normalized_row)
            if key in table['rows']:
                ensure(not table['primaryColumns'] and table['rows'][key][0] == column_hashes, 'Duplicate primary key in SQL dump')
                table['rows'][key] = (column_hashes, table['rows'][key][1] + 1)
            else:
                table['rows'][key] = (column_hashes, 1)
    ensure(creating is None and 'sys_user' in tables and 'tcm_patient' in tables, 'Incomplete clinic SQL dump')
    return tables


def table_summary(table):
    if table is None:
        return {'rows': 0, 'contentDigest': None, 'schemaSha256': None}
    rows = table['rows']
    signature = [[key, list(columns), count] for key, (columns, count) in sorted(rows.items())]
    return {'rows': sum(count for _, count in rows.values()),
            'contentDigest': hashlib.sha256(json.dumps(signature, separators=(',', ':')).encode()).hexdigest(),
            'schemaSha256': table['schemaSha256']}


def compare_table(before, after, columns=None):
    before_summary, after_summary = table_summary(before), table_summary(after)
    old, new = (before or {}).get('rows', {}), (after or {}).get('rows', {})
    old_keys, new_keys = set(old), set(new)
    result = {'before': before_summary, 'after': after_summary,
              'schemaEqual': before_summary['schemaSha256'] == after_summary['schemaSha256'],
              'missingRows': sum(old[key][1] for key in old_keys - new_keys),
              'addedRows': sum(new[key][1] for key in new_keys - old_keys), 'changedRows': 0, 'changedColumns': {}}
    old_columns, new_columns = (before or {}).get('columns', []), (after or {}).get('columns', [])
    selected = set(columns) if columns is not None else set(old_columns) | set(new_columns)
    changed = Counter()
    for key in old_keys & new_keys:
        old_values = dict(zip(old_columns, old[key][0]))
        new_values = dict(zip(new_columns, new[key][0]))
        differences = [column for column in selected if old_values.get(column) != new_values.get(column)]
        if differences or old[key][1] != new[key][1]:
            result['changedRows'] += max(old[key][1], new[key][1])
            changed.update(differences)
    result['changedColumns'] = dict(sorted(changed.items()))
    result['contentEqual'] = not any(result[key] for key in ('missingRows', 'addedRows', 'changedRows'))
    if columns is not None:
        # Whole-row digests include login metadata; omit them from this focused comparison.
        result.pop('before')
        result.pop('after')
        result['comparedColumns'] = sorted(selected & (set(old_columns) | set(new_columns)))
    return result


def database_comparison(state, temporary):
    salt = secrets.token_bytes(32)
    with gzip.open(BACKUP / 'database.sql.gz', 'rt', encoding='utf-8') as original:
        before = parse_dump(original, salt)
    database, environment, options = promotion.mysql(state)
    current = temporary / 'current.sql'
    command = ['mysqldump'] + options + ['--no-tablespaces', '--single-transaction', '--quick', '--skip-lock-tables',
                                       '--hex-blob', '--default-character-set=utf8mb4', '--skip-comments', database]
    with current.open('xb') as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.PIPE, env=environment, timeout=180)
    ensure(result.returncode == 0, 'Read-only current dump failed')
    with current.open(encoding='utf-8') as source:
        after = parse_dump(source, salt)
    tables = {name: compare_table(before.get(name), after.get(name)) for name in sorted(set(before) | set(after))}
    account = compare_table(before['sys_user'], after['sys_user'], ACCOUNT_COLUMNS)
    return {'method': 'Server-local canonical row comparison; no SQL restored or executed from the backup.',
            'digestScope': 'HMAC-derived, run-specific table digests; row identifiers and values are never returned.',
            'tables': tables, 'accountCredentialsAndProfile': account,
            'businessTablesEqual': all(value['contentEqual'] and value['schemaEqual'] for key, value in tables.items() if key.startswith('tcm_')),
            'roleTablesEqual': all(value['contentEqual'] and value['schemaEqual'] for key, value in tables.items()
                                   if key in ('sys_role', 'sys_user_role', 'sys_role_menu', 'sys_user_post', 'sys_role_dept')),
            'note': 'Current data can include legitimate activity since cutover. Differences are reported, never modified.'}


def file_comparison():
    result = {str(root): {'backupFiles': 0, 'matchedFiles': 0, 'missingFiles': 0, 'changedFiles': 0,
                         'addedFiles': 0, 'unstableFiles': 0, 'unsupportedEntries': 0, 'verifiedBytes': 0}
              for root in FILE_ROOTS}
    archived_names = set()
    with tarfile.open(BACKUP / 'files.tar', 'r') as archive:
        for member in archive:
            name = pathlib.PurePosixPath(member.name)
            ensure(not name.is_absolute() and '..' not in name.parts, 'Unexpected backup member path')
            path = pathlib.Path('/') / member.name
            root = next((root for root in FILE_ROOTS if path == root or root in path.parents), None)
            if root is None or member.isdir():
                continue
            item = result[str(root)]
            if not member.isfile():
                item['unsupportedEntries'] += 1
                continue
            archived_names.add(path)
            item['backupFiles'] += 1
            if not path.is_file() or path.is_symlink() or path.resolve() != path:
                item['missingFiles'] += 1
                continue
            initial = path.stat()
            with archive.extractfile(member) as old, path.open('rb') as current:
                equal = stream_digest(old) == stream_digest(current)
            final = path.stat()
            if (initial.st_size, initial.st_mtime_ns, initial.st_ino) != (final.st_size, final.st_mtime_ns, final.st_ino):
                item['unstableFiles'] += 1
            elif equal:
                item['matchedFiles'] += 1
                item['verifiedBytes'] += member.size
            else:
                item['changedFiles'] += 1
    for root in FILE_ROOTS:
        result[str(root)]['addedFiles'] = sum(path.is_file() and path not in archived_names for path in root.rglob('*')) if root.exists() else 0
    return {'roots': result, 'allPreexistingFilesEqual': all(not any(item[key] for key in
            ('missingFiles', 'changedFiles', 'unstableFiles', 'unsupportedEntries')) for item in result.values()),
            'pathDisclosure': 'Only fixed storage root names are returned; patient file names never leave the server.'}


def pdf_probe(state, temporary):
    encoded = REQUEST.get('pdfProbeClassBase64', '')
    if not encoded:
        return {'available': False, 'reason': 'probe-class-not-provided'}
    bytecode = base64.b64decode(encoded, validate=True)
    ensure(bytecode.startswith(b'\xca\xfe\xba\xbe') and len(bytecode) < 65536, 'Invalid PDF probe class')
    probe = temporary / 'pdf-probe'
    probe.mkdir(mode=0o700)
    (probe / 'OtcmPdfDependencyProbe.class').write_bytes(bytecode)
    library = probe / 'lib'
    library.mkdir(mode=0o700)
    with zipfile.ZipFile(state['jar']) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'BOOT-INF/lib/[^/]+\.jar', name):
                with archive.open(name) as source, (library / pathlib.PurePosixPath(name).name).open('xb') as target:
                    shutil.copyfileobj(source, target)
    command = [state['args'][0], '-Djava.awt.headless=true', '-Duser.home=' + str(probe),
               '-Djava.io.tmpdir=' + str(probe), '-cp', str(probe) + os.pathsep + str(library / '*'), 'OtcmPdfDependencyProbe']
    result = subprocess.run(command, cwd=probe, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90,
                            env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'HOME': str(probe)})
    lines = [line for line in result.stdout.decode('utf-8', errors='replace').splitlines() if line.startswith('OTCM_PDF_PROBE:')]
    if len(lines) != 1:
        return {'available': True, 'ok': False, 'reason': 'probe-did-not-return-valid-result', 'exitCode': result.returncode}
    data = json.loads(lines[0].split(':', 1)[1])
    allowed = {'ok', 'englishText', 'chineseText', 'rendered', 'pdfBytes', 'imageWidth', 'imageHeight', 'errorClass'}
    ensure(set(data) <= allowed, 'Unexpected PDF probe output')
    return {'available': True, 'exitCode': result.returncode, **data,
            'scope': 'Synthetic PDF in memory using the deployed font loader, iText, PDFBox and Java runtime; no Spring, database, email or patient files.'}


def inspect_integrity():
    ensure(promotion.TARGET == '43.166.154.98', 'Wrong fixed host')
    ensure(BACKUP.is_dir() and not BACKUP.is_symlink(), 'Cutover backup missing')
    backup_checks = {name: promotion.digest(BACKUP / name) == expected for name, expected in EXPECTED.items()}
    ensure(all(backup_checks.values()), 'Cutover backup integrity mismatch')
    state = promotion.runtime()
    ensure(promotion.digest(state['jar']) == CURRENT_JAR, 'Unexpected production backend version')
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='otcm-readonly-integrity-') as directory:
        temporary = pathlib.Path(directory)
        os.chmod(temporary, 0o700)
        files = file_comparison()
        database = database_comparison(state, temporary)
        pdf = pdf_probe(state, temporary)
    props = state['properties']
    expiry = str(props.get('token.expireTime') or props.get('token.expire-time') or '')
    non_secret = {'tokenExpireMinutes': int(expiry) if re.fullmatch(r'[0-9]{1,8}', expiry) else None,
                  'notificationTimezone': 'America/Toronto',
                  'smtpHostConfigured': bool(props.get('spring.mail.host')),
                  'smtpCredentialConfigured': bool(props.get('spring.mail.password'))}
    return {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'target': promotion.TARGET,
            'readOnly': True, 'productionWrites': 0, 'backup': str(BACKUP), 'backupHashesMatch': backup_checks,
            'files': files, 'database': database, 'pdfDependencies': pdf, 'nonSecretConfiguration': non_secret,
            'currentInspection': promotion.inspect()}


def main():
    try:
        print(json.dumps(inspect_integrity(), ensure_ascii=True, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({'ok': False, 'readOnly': True, 'errorType': type(error).__name__}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
