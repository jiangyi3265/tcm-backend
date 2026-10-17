#!/usr/bin/env python3
"""Pinned OTCM release, staged separately from the short production cutover.

REQUEST is injected as an in-memory module attribute by the reviewed SSH payload.
No credentials, signed URLs, patient records or command stderr are printed.
"""
import datetime
import fcntl
import gzip
import io
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.parse
import xml.etree.ElementTree as ET
import zipfile

import otcm_promotion as promotion
import production_deploy as deploy

REQUEST = {}
APP = promotion.APP
WEB = promotion.WEB
SERVICE = promotion.SERVICE
ROOT = pathlib.Path('/www/backup/otcm-releases')
STAGING = pathlib.Path('/www/backup/otcm-release-staging')
OLD_BACKEND = '1a13fc82a6dbb9ee45a515d3284227db94e63c75b939a6a6efe465377332a0df'
OLD_FRONTEND = '9e259b16da7208a1c6d6e569e25148eec0e0ec48'
BACKEND_REVISION = 'c0aac159111029eda8c955e230251c974ae4662d'
FRONTEND_REVISION = '4e42f8118a303b09c3f6131144b60216b10217b3'
PUBLIC_BASE = 'https://www.otcm.app'
PRESERVED = APP / 'config/application-otcm-preserved.properties'
DROPIN = pathlib.Path('/etc/systemd/system/yiyuanjava.service.d/99-otcm-release.conf')
FONT = pathlib.Path('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc')


class ReleaseGuard(ValueError):
    """Messages are static operational guards, never runtime property values."""


def require(condition, message):
    if not condition:
        raise ReleaseGuard(message)


def validate_request(request):
    require(request.get('mode') in ('stage', 'publish'), 'Unsupported release mode')
    require(re.fullmatch(r'[0-9]{8,20}', str(request.get('runId', ''))), 'Invalid build run ID')
    for key in ('backendSha256', 'frontendSha256'):
        require(re.fullmatch(r'[0-9a-f]{64}', request.get(key, '')), 'Missing artifact digest')
    require(re.fullmatch(r'[0-9a-f]{40}', request.get('backendRevision', BACKEND_REVISION)), 'Invalid pinned backend revision')
    if 'frontendRevision' in request:
        require(request['frontendRevision'] == FRONTEND_REVISION, 'Unexpected frontend revision')
    return STAGING / str(request['runId'])


def secure_write(path, text, mode=0o600):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(not path.is_symlink(), 'Refusing a symbolic link output')
    temporary = path.with_name('.' + path.name + '.release-tmp')
    descriptor = os.open(str(temporary), os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def backend_library(path):
    with zipfile.ZipFile(path) as jar:
        require('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class' in jar.namelist(), 'Missing backend entrypoint')
        names = [name for name in jar.namelist() if re.fullmatch(r'BOOT-INF/lib/ruoyi-hospital-[^/]+\.jar', name)]
        require(len(names) == 1, 'Unexpected hospital module count')
        return jar.read(names[0])


def validate_staged(work, request):
    manifest = json.loads((work / 'manifest.json').read_text())
    require(manifest.get('backendRevision') == request.get('backendRevision', BACKEND_REVISION), 'Wrong backend revision')
    require(manifest.get('frontendRevision') == FRONTEND_REVISION, 'Wrong frontend revision')
    require(manifest.get('publicBaseUrl') == PUBLIC_BASE, 'Wrong public frontend domain')
    for name, key in [('backend.jar', 'backendSha256'), ('frontend.tar.gz', 'frontendSha256')]:
        require(manifest.get('sha256', {}).get(name) == request[key], 'Manifest digest mismatch')
        require(promotion.digest(work / name) == request[key], 'Staged artifact digest mismatch')
    backend_library(work / 'backend.jar')
    require(json.loads((work / 'frontend/version.json').read_text())['version'] == FRONTEND_REVISION, 'Wrong expanded frontend')
    # Re-extract validation is done at stage; protect every expanded file from drift.
    files = json.loads((work / 'frontend-files.json').read_text())
    actual = sorted(str(path.relative_to(work / 'frontend')) for path in (work / 'frontend').rglob('*') if path.is_file())
    require(actual == sorted(files), 'Expanded frontend files changed')
    for relative, expected in files.items():
        require(promotion.digest(work / 'frontend' / relative) == expected, 'Expanded frontend digest changed')
    return manifest


def stage(request, work):
    if (work / 'READY.json').is_file():
        manifest = validate_staged(work, request)
        return {'ok': True, 'mode': 'stage', 'alreadyStaged': True, 'runId': str(request['runId']), 'manifest': manifest}
    require(not work.exists(), 'Incomplete stage exists; inspect before retrying')
    url = request.get('artifactUrl', '')
    parsed = urllib.parse.urlsplit(url)
    require(len(url) <= 8192 and parsed.scheme == 'https' and not parsed.username and not parsed.password
            and parsed.port in (None, 443) and parsed.hostname
            and parsed.hostname.endswith(('.blob.core.windows.net', '.actions.githubusercontent.com')), 'Untrusted artifact URL')
    work.mkdir(mode=0o700, parents=True)
    archive_path = work / 'release.zip'
    deploy.download_github_archive(url, archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        require(sorted(archive.namelist()) == ['backend.jar', 'frontend.tar.gz', 'manifest.json'], 'Unexpected release archive files')
        require(sum(item.file_size for item in archive.infolist()) <= deploy.MAX_ARTIFACT_BYTES, 'Expanded release too large')
        for name in archive.namelist():
            with archive.open(name) as source, (work / name).open('xb') as target:
                shutil.copyfileobj(source, target)
    unpacked = work / 'frontend'
    unpacked.mkdir()
    deploy.extract_frontend(work / 'frontend.tar.gz', unpacked, FRONTEND_REVISION)
    secure_write(work / 'frontend-files.json', json.dumps({str(path.relative_to(unpacked)): promotion.digest(path)
                 for path in unpacked.rglob('*') if path.is_file()}, sort_keys=True))
    manifest = validate_staged(work, request)
    secure_write(work / 'READY.json', json.dumps({'runId': str(request['runId']), 'manifest': manifest}, indent=2))
    return {'ok': True, 'mode': 'stage', 'runId': str(request['runId']), 'manifest': manifest}


def check_schema(state, jar):
    rows = promotion.query(state, 'SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()')
    actual = {}
    for row in rows.splitlines():
        table, column = row.split('\t')
        actual.setdefault(table, set()).add(column)
    expected = {}
    with zipfile.ZipFile(io.BytesIO(backend_library(jar))) as library:
        for name in library.namelist():
            if not name.startswith('mapper/hospital/') or not name.endswith('.xml'):
                continue
            source = library.read(name).decode('utf-8')
            match = re.search(r'\bfrom\s+(tcm_[a-z_]+)\b', source, re.I)
            require(match is not None, 'Unrecognized hospital mapper')
            table = match.group(1).lower()
            root = ET.fromstring(source)
            columns = {node.attrib['column'] for result in root.findall('resultMap') for node in result
                       if 'column' in node.attrib}
            require(columns, 'Missing mapper result columns')
            expected.setdefault(table, set()).update(columns)
    require(len(expected) >= 15, 'Incomplete schema inspection')
    missing = {table: sorted(columns - actual.get(table, set())) for table, columns in expected.items()
               if columns - actual.get(table, set())}
    require(not missing, 'Required database columns missing: ' + json.dumps(missing, sort_keys=True))
    counts = {table: int(promotion.query(state, 'SELECT COUNT(*) FROM `' + table + '`')) for table in sorted(expected)}
    counts['sys_user'] = int(promotion.query(state, 'SELECT COUNT(*) FROM sys_user'))
    return counts


def configuration(state, request, jar):
    args = state['args']
    require(not any(arg.startswith(('--spring.config.location', '--spring.config.additional-location',
                                    '-Dspring.config.location', '-Dspring.config.additional-location')) for arg in args),
            'Explicit config CLI location needs manual review')
    require(not state['environment'].get('SPRING_APPLICATION_JSON'), 'Existing JSON config needs manual review')
    require(not PRESERVED.exists() and not DROPIN.exists(), 'Release configuration already exists')
    props = state['properties']
    prefixes = ('spring.datasource.', 'spring.redis.', 'spring.mail.', 'stripe.', 'tcm.r2.', 'tcm.backup.',
                'deepseek.', 'file-access.', 'tcm.file.', 'file.access-', 'tcm.smtp.', 'token.')
    fixed = ('ruoyi.profile', 'public.app-base-url', 'tcm.instance-id', 'server.port', 'server.address')
    values = {key: str(value) for key, value in props.items() if key.startswith(prefixes) or key in fixed}
    if not values.get('tcm.smtp.key-file'):
        values.pop('tcm.smtp.key-file', None)
    require(not any('${' in value for value in values.values()), 'Unresolved production property placeholder')
    require(values.get('token.secret') and values['token.secret'] != 'replace-this-token-secret-before-production', 'Missing production token secret')
    upload = pathlib.Path(props.get('ruoyi.profile', ''))
    require(str(upload) not in ('', '.'), 'Missing upload directory')
    upload = (upload if upload.is_absolute() else state['cwd'] / upload).resolve()
    require(upload != pathlib.Path('/') and len(upload.parts) >= 4, 'Unexpected upload path')
    values['ruoyi.profile'] = str(upload)
    values['public.app-base-url'] = PUBLIC_BASE
    values['tcm.instance-id'] = props.get('tcm.instance-id', '')
    notifications = promotion.notification_summary(state)
    totals = notifications['totals']
    require(not any(totals.get(key, 0) for key in ('withScheduledPendingClaims', 'withNotificationOutbox')),
            'Existing notification claims or outbox need separate review')
    cutoff = request.get('notificationNotBefore', '')
    if cutoff:
        require(re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}', cutoff), 'Invalid notification cutoff')
        cutoff_time = datetime.datetime.fromisoformat(cutoff)
        clinic_time = datetime.datetime.fromisoformat(notifications['clinicTime'])
        require(datetime.timedelta(0) <= clinic_time - cutoff_time <= datetime.timedelta(days=1),
                'Notification cutoff must be within the preceding clinic day')
        with zipfile.ZipFile(io.BytesIO(backend_library(jar))) as library:
            require(any(b'tcm.notifications.not-before' in library.read(name) for name in library.namelist()
                        if name.endswith('.class')), 'Backend does not implement notification cutoff')
        values['tcm.notifications.not-before'] = cutoff
    else:
        require(not any(totals.get(key, 0) for key in ('aftercareDueCandidates', 'followUpDueCandidates',
                                                       'withScheduledPendingClaims', 'withNotificationOutbox')),
                'Notification history requires an implemented cutoff before release')
    return values, upload, notifications


def property_escape(value):
    # Java .properties is ISO-8859-1; encode other Unicode as UTF-16 code units.
    output = []
    for character in str(value):
        escaped = {'\\': '\\\\', '\n': '\\n', '\r': '\\r', '\t': '\\t', ' ': '\\ ', '=': '\\=', ':': '\\:', '#': '\\#', '!': '\\!'}.get(character)
        if escaped is not None:
            output.append(escaped)
        elif ord(character) < 32 or ord(character) > 126:
            encoded = character.encode('utf-16-be')
            output.extend('\\u' + encoded[index:index + 2].hex() for index in range(0, len(encoded), 2))
        else:
            output.append(character)
    return ''.join(output)


def snapshot_paths(state, upload):
    paths = [WEB, state['jar'], upload, upload.parent / 'hospital-private', upload.parent / '.otcm',
             APP / 'hospital-private', APP / '.otcm', APP / 'config']
    paths.extend(path for path in APP.iterdir() if path.is_file() and path.suffix in ('.env', '.properties', '.yml', '.yaml'))
    unit = promotion.run(['systemctl', 'show', SERVICE, '--property=FragmentPath', '--value'])
    require(unit and pathlib.Path(unit).is_file(), 'Missing systemd unit')
    paths.append(pathlib.Path(unit))
    paths.append(pathlib.Path('/etc/systemd/system/yiyuanjava.service.d'))
    nginx_root = pathlib.Path('/www/server/panel/vhost/nginx')
    configs = list(nginx_root.glob('*otcm*.conf'))
    require(configs, 'Missing OTCM nginx config')
    paths.extend(configs)
    for config in configs:
        for include in re.findall(r'\binclude\s+([^;\n]+)\s*;', config.read_text()):
            include = include.strip().strip('"\'')
            if include.startswith('/www/server/panel/') and not any(character in include for character in '$*?'):
                candidate = pathlib.Path(include)
                if candidate.exists():
                    paths.append(candidate)
    # Existing explicit config files and environment file paths must be in the backup.
    environment_files = promotion.run(['systemctl', 'show', SERVICE, '--property=EnvironmentFiles', '--value'])
    for name in re.findall(r'(/[^\s;()]+)', environment_files):
        path = pathlib.Path(name)
        if path.is_file():
            paths.append(path)
    unique = []
    for path in sorted(set(paths), key=lambda item: len(item.parts)):
        if path.exists() and not any(parent == path or parent in path.parents for parent in unique):
            require(path.is_absolute() and len(path.parts) >= 4, 'Unsafe backup root')
            unique.append(path)
    return unique


def dump_database(state, destination):
    database, environment, options = promotion.mysql(state)
    command = ['mysqldump'] + options + ['--no-tablespaces', '--single-transaction', '--quick', '--routines', '--triggers',
                                       '--events', '--hex-blob', '--default-character-set=utf8mb4', database]
    with tempfile.TemporaryFile() as errors, destination.open('xb') as raw:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, env=environment)
        try:
            with gzip.GzipFile(fileobj=raw, mode='wb', compresslevel=1) as compressed:
                shutil.copyfileobj(process.stdout, compressed)
            require(process.wait(timeout=120) == 0, 'Database backup command failed')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    require(destination.stat().st_size > 100, 'Database backup is empty')
    with gzip.open(destination, 'rb') as verify:
        total = sum(len(block) for block in iter(lambda: verify.read(1024 * 1024), b''))
    require(total > 100, 'Invalid database backup')


def health():
    for attempt in range(45):
        try:
            result = deploy.fetch_json('http://127.0.0.1:8006/api/public-booking/options')
            if 'practitioners' in result:
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Backend health check failed')


def systemd_argument(value):
    require(not any(character in value for character in '\n\r\0'), 'Unsupported control character in service argument')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('%', '%%').replace('$', '$$') + '"'


def java_command(state, request):
    args = [argument for argument in state['args'] if argument != '']
    require(len(args) > 2 and '-jar' in args, 'Unexpected Java service arguments')
    executable = pathlib.Path(request.get('javaPath') or args[0])
    require(executable.is_absolute() and executable.is_file() and os.access(executable, os.X_OK), 'Java executable unavailable')
    java = subprocess.run([str(executable), '-version'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15)
    require(java.returncode == 0 and re.search(r'version "(?:17|21)(?:\.|\")', java.stderr.decode()),
            'Selected Java must be 17 or 21')
    args[0] = str(executable)
    # Validate the original JVM flags under the new runtime without starting
    # another application instance or connecting it to the live database.
    flag_check = subprocess.run(args[:args.index('-jar')] + ['-version'],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    require(flag_check.returncode == 0, 'Existing JVM flags are incompatible with the selected runtime')
    return args


def publish(request, work):
    manifest = validate_staged(work, request)
    state = promotion.runtime()
    require(promotion.digest(state['jar']) == OLD_BACKEND, 'Running backend changed from reviewed baseline')
    require(json.loads((WEB / 'version.json').read_text())['version'] == OLD_FRONTEND, 'Running frontend changed from reviewed baseline')
    require(FONT.is_file(), 'Required Chinese PDF font is unavailable')
    args = java_command(state, request)
    counts = check_schema(state, work / 'backend.jar')
    values, upload, notifications = configuration(state, request, work / 'backend.jar')
    paths = snapshot_paths(state, upload)
    database, database_env, database_options = promotion.mysql(state)
    dump_probe = subprocess.run(['mysqldump'] + database_options + ['--no-tablespaces', '--no-data', '--routines', '--triggers', '--events', database],
                                stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, env=database_env, timeout=60)
    require(dump_probe.returncode == 0, 'Database backup permissions must be resolved before stopping the service')
    estimated = sum(int(promotion.run(['du', '-sk', str(path)]).split()[0]) * 1024 for path in paths)
    require(shutil.disk_usage(ROOT).free > estimated * 2 + 1024 * 1024 * 1024, 'Insufficient backup space')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = ROOT / (stamp + '-' + str(request['runId']))
    backup.mkdir(mode=0o700)
    shutil.copy2(state['jar'], backup / 'previous.jar')
    for name in ('index.html', 'version.json'):
        shutil.copy2(WEB / name, backup / name)
    secure_write(backup / 'runtime-private.json', json.dumps({'args': state['args'], 'environment': state['environment'],
                 'properties': state['properties']}, ensure_ascii=True))
    report = {'mode': 'publish', 'runId': str(request['runId']), 'backup': str(backup), 'manifest': manifest,
              'rowCountsBefore': counts, 'notificationsBefore': notifications, 'pathsBackedUp': [str(path) for path in paths],
              'databaseRestored': False, 'schemaChangedByHelper': False, 'stoppedService': SERVICE}
    stopped = False
    preserved_created = False
    configured = False
    frontend_changed = False
    try:
        stopped = True
        promotion.run(['systemctl', 'stop', SERVICE])
        require(promotion.run(['systemctl', 'show', SERVICE, '--property=MainPID', '--value']) == '0', 'Service did not stop')
        # Capture fresh counts and a consistent dump only after the sole app writer is stopped.
        counts = check_schema(state, work / 'backend.jar')
        report['rowCountsAtCutover'] = counts
        dump_database(state, backup / 'database.sql.gz')
        with tarfile.open(backup / 'files.tar', 'w') as archive:
            for path in paths:
                archive.add(path, arcname=str(path).lstrip('/'), recursive=True)
        with tarfile.open(backup / 'files.tar') as archive:
            require(bool(archive.getmembers()), 'File backup is empty')
        report['backupSha256'] = {name: promotion.digest(backup / name) for name in ('database.sql.gz', 'files.tar', 'previous.jar')}
        secure_write(backup / 'BACKUP.json', json.dumps(report, indent=2))
        secure_write(PRESERVED, '\n'.join(property_escape(key) + '=' + property_escape(value)
                                         for key, value in sorted(values.items())) + '\n')
        preserved_created = True
        service_user = promotion.run(['systemctl', 'show', SERVICE, '--property=User', '--value']) or 'root'
        if service_user != 'root':
            import pwd
            account = pwd.getpwnam(service_user)
            os.chown(PRESERVED, 0, account.pw_gid)
            os.chmod(PRESERVED, 0o640)
            os.chmod(PRESERVED.parent, 0o750)
            os.chown(PRESERVED.parent, 0, account.pw_gid)
        previous = state['environment'].get('SPRING_CONFIG_ADDITIONAL_LOCATION', '')
        require('\n' not in previous and '\r' not in previous, 'Unsafe additional configuration location')
        location = (previous.rstrip(',') + ',' if previous else '') + 'file:' + str(PRESERVED)
        environment_line = 'SPRING_CONFIG_ADDITIONAL_LOCATION=' + location
        escaped = environment_line.replace('\\', '\\\\').replace('"', '\\"').replace('%', '%%')
        dropin = '[Service]\nEnvironment="' + escaped + '"\n'
        # Only the executable changes; the exact existing argument vector is retained.
        if args[0] != state['args'][0]:
            dropin += 'ExecStart=\nExecStart=' + ' '.join(systemd_argument(argument) for argument in args) + '\n'
        secure_write(DROPIN, dropin)
        configured = True
        promotion.run(['systemctl', 'daemon-reload'])
        deploy.atomic_copy(work / 'backend.jar', state['jar'])
        promotion.run(['systemctl', 'start', SERVICE])
        health()
        require(promotion.digest(state['jar']) == request['backendSha256'], 'Installed backend mismatch')
        frontend_changed = True
        deploy.copy_frontend(work / 'frontend', WEB)
        require(deploy.fetch_json(PUBLIC_BASE + '/version.json?release=' + FRONTEND_REVISION)['version'] == FRONTEND_REVISION,
                'Public frontend verification failed')
        health()
        report.update({'ok': True, 'rollbackPerformed': False, 'frontendRevision': FRONTEND_REVISION,
                       'backendSha256': promotion.digest(state['jar']), 'javaPath': args[0],
                       'rowCountsAfter': check_schema(state, work / 'backend.jar'),
                       'completedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()})
        secure_write(backup / 'RESULT.json', json.dumps(report, indent=2))
        return report
    except Exception as error:
        report.update({'ok': False, 'failureType': type(error).__name__, 'rollbackPerformed': False})
        if isinstance(error, ReleaseGuard):
            report['guard'] = str(error)
        if stopped:
            try:
                promotion.run(['systemctl', 'stop', SERVICE])
                if frontend_changed:
                    for name in ('index.html', 'version.json'):
                        deploy.atomic_copy(backup / name, WEB / name)
                deploy.atomic_copy(backup / 'previous.jar', state['jar'])
                # Only files created by this release are removed. Original configuration is never overwritten.
                if configured and DROPIN.exists():
                    DROPIN.unlink()
                if preserved_created and PRESERVED.exists():
                    PRESERVED.unlink()
                promotion.run(['systemctl', 'daemon-reload'])
                promotion.run(['systemctl', 'start', SERVICE])
                health()
                report['rollbackPerformed'] = True
            except Exception as rollback_error:
                report['rollbackFailureType'] = type(rollback_error).__name__
        secure_write(backup / 'RESULT.json', json.dumps(report, indent=2))
        return report


def main():
    os.umask(0o077)
    try:
        require(os.geteuid() == 0, 'Root is required for this fixed clinic release')
        work = validate_request(REQUEST)
        for path in (ROOT, STAGING):
            require(not path.is_symlink(), 'Unexpected release directory symlink')
            path.mkdir(mode=0o700, parents=True, exist_ok=True)
            os.chmod(path, 0o700)
        with (ROOT / 'release.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = stage(REQUEST, work) if REQUEST['mode'] == 'stage' else publish(REQUEST, work)
        print(json.dumps(result, ensure_ascii=True, indent=2))
        return 0 if result.get('ok') else 1
    except Exception as error:
        # Exception messages can include provider responses; the caller receives type only.
        result = {'ok': False, 'mode': REQUEST.get('mode'), 'errorType': type(error).__name__}
        if isinstance(error, ReleaseGuard):
            result['guard'] = str(error)
        print(json.dumps(result))
        return 1


if __name__ == '__main__':
    sys.exit(main())
