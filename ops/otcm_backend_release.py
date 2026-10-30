#!/usr/bin/env python3
"""Fixed OTCM backend-only release; no schema, patient, frontend or config edits."""
import datetime
import gzip
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.parse
import zipfile

import otcm_promotion as promotion
import otcm_session_config as session
import otcm_frontend_release as frontend

REQUEST = {}
APP = promotion.APP
WEB = promotion.WEB
BACKUPS = pathlib.Path('/www/backup/otcm-backend-releases')
LOCK = pathlib.Path('/www/backup/otcm-releases/release.lock')


class Guard(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise Guard(message)


def validate_request(request):
    require(request.get('target') == '43.166.154.98', 'Wrong production target')
    require(request.get('mode') in ('inspect', 'publish'), 'Invalid release mode')
    for name, length in [('expectedBackendSha256', 64), ('expectedFrontendRevision', 40)]:
        require(re.fullmatch('[0-9a-f]{%d}' % length, request.get(name, '')), 'Invalid expected release identity')
    if request['mode'] == 'publish':
        require(re.fullmatch(r'[0-9a-f]{40}', request.get('revision', '')), 'Invalid backend revision')
        require(re.fullmatch(r'[0-9a-f]{64}', request.get('sha256', '')), 'Invalid backend digest')
        require(request['sha256'] != request['expectedBackendSha256'], 'Release must change the backend')
        url = request.get('artifactUrl', '')
        parsed = urllib.parse.urlsplit(url)
        require(len(url) <= 8192 and parsed.scheme == 'https' and not parsed.username and not parsed.password
                and parsed.port in (None, 443) and parsed.hostname
                and parsed.hostname.endswith(('.blob.core.windows.net', '.actions.githubusercontent.com')),
                'Untrusted artifact URL')


def validate_current(request):
    state = promotion.runtime()
    require(state['cwd'] == APP and state['jar'] == APP / 'yy.jar', 'Unexpected service paths')
    require(promotion.digest(state['jar']) == request['expectedBackendSha256'], 'Current backend differs from reviewed release')
    require(json.loads((WEB / 'version.json').read_text()) == {'version': request['expectedFrontendRevision']},
            'Current frontend differs from reviewed release')
    require(session.PRESERVED.resolve() == session.PRESERVED and session.PRESERVED.is_file(), 'Unexpected preserved config path')
    require(stat.S_IMODE(session.PRESERVED.stat().st_mode) == 0o600, 'Unexpected preserved config permissions')
    require(str(session.PRESERVED) in state['environment'].get('SPRING_CONFIG_ADDITIONAL_LOCATION', ''),
            'Preserved config is not explicitly loaded')
    require(session.health(), 'Existing service is not healthy')
    return state


def patient_diagnostics(state):
    indexes = promotion.query(state, "SELECT NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                              "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                              "AND TABLE_NAME='tcm_patient' GROUP BY INDEX_NAME,NON_UNIQUE")
    result = []
    for line in indexes.splitlines():
        non_unique, columns = line.split('\t')
        require(non_unique in ('0', '1') and re.fullmatch(r'[a-z_,]+', columns), 'Unexpected patient index shape')
        result.append({'unique': non_unique == '0', 'columns': columns.split(',')})
    groups = promotion.query(state, "SELECT COUNT(*),COALESCE(SUM(n),0) FROM (SELECT COUNT(*) n FROM tcm_patient "
                             "WHERE deleted_at IS NULL AND merged_into IS NULL AND TRIM(COALESCE(email,''))<>'' "
                             "GROUP BY LOWER(TRIM(email)) HAVING COUNT(*)>1) shared")
    numbers = groups.split('\t')
    require(len(numbers) == 2 and all(x.isdigit() for x in numbers), 'Unexpected family aggregate shape')
    source = "(SELECT patient_id,CASE WHEN JSON_VALID(payload) THEN payload ELSE '{}' END AS j FROM tcm_appointment) snapshots"
    predicate = "LOWER(JSON_UNQUOTE(JSON_EXTRACT(j,'$.bookingSource')))='public' "
    name = "LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j,'$.patientName')),'')))"
    snapshots = promotion.query(state, "SELECT COUNT(*) FROM (SELECT patient_id FROM " + source + " WHERE "
        + predicate + "AND " + name + "<>'' GROUP BY patient_id HAVING COUNT(DISTINCT " + name + ")>1) mixed")
    require(snapshots.isdigit(), 'Unexpected booking snapshot aggregate')
    mismatch = promotion.query(state, "SELECT COUNT(*),COUNT(DISTINCT snapshots.patient_id) FROM " + source
        + " JOIN tcm_patient p ON p.id=snapshots.patient_id WHERE " + predicate + "AND " + name
        + "<>'' AND " + name + "<>LOWER(TRIM(COALESCE(p.name,'')))")
    mismatches = mismatch.split('\t')
    require(len(mismatches) == 2 and all(x.isdigit() for x in mismatches), 'Unexpected booking name aggregate')
    return {'indexes': result, 'sharedEmailGroups': int(numbers[0]), 'patientsInSharedEmailGroups': int(numbers[1]),
            'patientsWithMultiplePublicBookingNames': int(snapshots),
            'publicBookingSnapshotsWithDifferentCurrentName': int(mismatches[0]),
            'patientsWithPublicBookingNameMismatch': int(mismatches[1]),
            'scope': 'Aggregate only; no patient names, addresses or identifiers returned.'}


def upload_log_summary(text):
    counts = {}
    for match in re.finditer(r'"(?:POST|PUT) /api/files/upload(?:\?[^\s"]*)? HTTP/[^\s"]+"\s+(\d{3})\b', text):
        code = match[1]
        counts[code] = counts.get(code, 0) + 1
    errors = {name: sum('/api/files/upload' in line and phrase in line.lower() for line in text.splitlines())
              for name, phrase in [('bodyTooLarge', 'too large body'), ('permissionDenied', 'permission denied'),
                                   ('upstreamTimeout', 'upstream timed out'), ('connectionRefused', 'connection refused')]}
    return {'httpStatusCounts': counts, 'errorCounts': errors}


def upload_diagnostics(state):
    configs = [pathlib.Path('/www/server/nginx/conf/nginx.conf')]
    vhosts = pathlib.Path('/www/server/panel/vhost/nginx')
    if vhosts.is_dir():
        configs.extend(path for path in vhosts.glob('*.conf') if 'otcm.app' in path.name)
    limits, logs = [], set()
    for path in configs:
        if not path.is_file():
            continue
        content = path.read_text(errors='replace')
        values = re.findall(r'(?m)^\s*client_max_body_size\s+([0-9]+[kKmMgG]?)\s*;', content)
        limits.append({'scope': 'global' if path.name == 'nginx.conf' else 'OTCM vhost', 'declaredBodyLimits': values})
        if path.name != 'nginx.conf':
            logs.update(pathlib.Path(value) for value in re.findall(r'(?m)^\s*(?:access_log|error_log)\s+([^;\s]+)', content)
                        if value.startswith('/www/wwwlogs/') and 'otcm.app' in value)
    text = ''
    for path in sorted(logs):
        if path.is_file():
            with path.open('rb') as source:
                source.seek(max(0, path.stat().st_size - 2 * 1024 * 1024))
                text += source.read().decode('utf-8', errors='replace') + '\n'
    storage = []
    for key in ('ruoyi.profile', 'tcm.file.storage-root'):
        value = state['properties'].get(key)
        if not value or not pathlib.Path(value).is_absolute():
            continue
        path = pathlib.Path(value)
        storage.append({'setting': key, 'exists': path.exists(), 'isDirectory': path.is_dir(),
                        'mode': oct(stat.S_IMODE(path.stat().st_mode)) if path.exists() else None})
    return {'nginx': limits, 'logsExamined': len(logs), 'recentLogTail': upload_log_summary(text),
            'storage': storage, 'scope': 'Declared limits only; includes may override. Bounded log tails, no log text returned.'}


def stage(request, directory):
    import production_deploy as deploy
    archive_path = directory / 'artifact.zip'
    deploy.download_github_archive(request['artifactUrl'], archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        require(sorted(archive.namelist()) == ['backend.jar', 'manifest.json'], 'Unexpected artifact contents')
        require(sum(item.file_size for item in archive.infolist()) <= deploy.MAX_ARTIFACT_BYTES, 'Expanded artifact too large')
        for name in ('backend.jar', 'manifest.json'):
            with archive.open(name) as source, (directory / name).open('xb') as target:
                shutil.copyfileobj(source, target)
    manifest = json.loads((directory / 'manifest.json').read_text())
    require(manifest == {'revision': request['revision'], 'sha256': request['sha256'], 'publicBaseUrl': 'https://www.otcm.app'},
            'Artifact identity mismatch')
    jar = directory / 'backend.jar'
    require(promotion.digest(jar) == request['sha256'], 'Artifact digest mismatch')
    with zipfile.ZipFile(jar) as archive:
        require('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class' in archive.namelist(), 'Missing backend entrypoint')
        git = promotion.diagnostics.parse_config(archive.read('BOOT-INF/classes/git.properties').decode(), '.properties')
        require(git.get('git.commit.id') == request['revision'], 'Embedded backend revision mismatch')
    return jar


def dump_database(state, destination):
    database, environment, options = promotion.mysql(state)
    command = ['mysqldump'] + options + ['--no-tablespaces', '--single-transaction', '--quick', '--routines', '--triggers',
                                       '--events', '--hex-blob', '--default-character-set=utf8mb4', database]
    with tempfile.TemporaryFile() as errors, destination.open('xb') as raw:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, env=environment)
        try:
            with gzip.GzipFile(fileobj=raw, mode='wb', compresslevel=1) as compressed:
                shutil.copyfileobj(process.stdout, compressed)
            require(process.wait(timeout=120) == 0, 'Database backup failed')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    require(destination.stat().st_size > 100, 'Database backup is empty')
    with gzip.open(destination, 'rb') as verify:
        require(sum(len(block) for block in iter(lambda: verify.read(1024 * 1024), b'')) > 100, 'Invalid database backup')


def fingerprints(metadata):
    return {**session.config_fingerprints(metadata), str(session.PRESERVED): promotion.digest(session.PRESERVED)}


def publish(request, state, jar):
    metadata = session.service_metadata()
    config = fingerprints(metadata)
    web = frontend.tree_files(WEB)
    require(BACKUPS.resolve() == BACKUPS, 'Unexpected backup root')
    BACKUPS.mkdir(parents=True, mode=0o700, exist_ok=True)
    require(BACKUPS.stat().st_uid == os.geteuid() and stat.S_IMODE(BACKUPS.stat().st_mode) == 0o700, 'Backup root must be private')
    require(shutil.disk_usage(BACKUPS).free > 1024 * 1024 * 1024, 'Insufficient backup space')
    backup = BACKUPS / (datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '-' + request['revision'][:12])
    backup.mkdir(mode=0o700)
    session.atomic_copy(state['jar'], backup / 'previous.jar')
    require(promotion.digest(backup / 'previous.jar') == request['expectedBackendSha256'], 'Backend backup mismatch')
    session.atomic_copy(session.PRESERVED, backup / 'application-otcm-preserved.properties')
    session.backup_smtp_key(backup)
    session.atomic_write(backup / 'startup-private.json', json.dumps({'metadata': metadata, 'config': config,
                         'args': state['args'], 'environment': state['environment']}).encode())
    # A consistent read-only snapshot is taken while the current process remains available.
    dump_database(state, backup / 'database.sql.gz')
    report = {'ok': False, 'mode': 'publish', 'revision': request['revision'], 'previousBackendSha256': request['expectedBackendSha256'],
              'backendSha256': request['sha256'], 'frontendRevision': request['expectedFrontendRevision'], 'backup': str(backup),
              'backupSha256': {name: promotion.digest(backup / name) for name in ('previous.jar', 'database.sql.gz')},
              'databaseWrites': 0, 'schemaChanges': 0, 'rollbackPerformed': False, 'serviceHealthy': False}
    jar_metadata = state['jar'].stat()
    touched = False
    try:
        require(promotion.digest(state['jar']) == request['expectedBackendSha256'] and promotion.digest(jar) == request['sha256'],
                'Backend changed during preparation')
        require(fingerprints(metadata) == config and session.service_metadata() == metadata, 'Configuration changed during preparation')
        require(frontend.tree_files(WEB) == web, 'Frontend changed during preparation')
        require(promotion.run(['systemctl', 'show', promotion.SERVICE, '--property=MainPID', '--value']) == str(state['pid']),
                'Backend process changed during preparation')
        touched = True
        promotion.run(['systemctl', 'stop', promotion.SERVICE])
        session.atomic_copy(jar, state['jar'], jar_metadata.st_uid, jar_metadata.st_gid, stat.S_IMODE(jar_metadata.st_mode))
        require(promotion.digest(state['jar']) == request['sha256'], 'Installed artifact mismatch')
        promotion.run(['systemctl', 'start', promotion.SERVICE])
        require(session.health(), 'New backend health check failed')
        after = promotion.runtime()
        require(after['cwd'] == state['cwd'] and after['jar'] == state['jar'], 'Service paths changed')
        require(after['properties'] == state['properties'], 'Effective configuration changed')
        require(promotion.digest(after['jar']) == request['sha256'], 'Running backend digest mismatch')
        require(fingerprints(metadata) == config and session.service_identity(session.service_metadata()) == session.service_identity(metadata),
                'Protected configuration changed')
        require(frontend.tree_files(WEB) == web, 'Frontend changed during backend release')
        report.update(ok=True, serviceHealthy=True, frontendUnchanged=True, configurationAndSecretsUnchanged=True)
    except Exception as error:
        report['errorType'] = type(error).__name__
        if isinstance(error, Guard):
            report['guard'] = str(error)
        if touched:
            try:
                promotion.run(['systemctl', 'stop', promotion.SERVICE])
                session.atomic_copy(backup / 'previous.jar', state['jar'], jar_metadata.st_uid, jar_metadata.st_gid, stat.S_IMODE(jar_metadata.st_mode))
                promotion.run(['systemctl', 'start', promotion.SERVICE])
                report['rollbackPerformed'] = True
                require(session.health(), 'Rollback service health check failed')
                require(promotion.digest(state['jar']) == request['expectedBackendSha256'], 'Rollback artifact mismatch')
                restored = promotion.runtime()
                require(restored['properties'] == state['properties'] and fingerprints(metadata) == config, 'Rollback configuration differs')
                report['serviceHealthy'] = True
            except Exception as rollback_error:
                report['rollbackErrorType'] = type(rollback_error).__name__
    session.atomic_write(backup / 'RESULT.json', json.dumps(report, indent=2).encode())
    return report


def main():
    os.umask(0o077)
    try:
        validate_request(REQUEST)
        require(os.geteuid() == 0, 'Root is required')
        import fcntl
        require(not LOCK.is_symlink() and LOCK.parent.is_dir(), 'Unexpected release lock')
        with LOCK.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            state = validate_current(REQUEST)
            if REQUEST['mode'] == 'inspect':
                result = {'ok': True, 'mode': 'inspect', 'readOnly': True, 'databaseWrites': 0,
                          'backendSha256': REQUEST['expectedBackendSha256'], 'frontendRevision': REQUEST['expectedFrontendRevision'],
                          'patients': patient_diagnostics(state), 'uploads': upload_diagnostics(state)}
            else:
                with tempfile.TemporaryDirectory(prefix='otcm-backend-') as temporary:
                    jar = stage(REQUEST, pathlib.Path(temporary))
                    result = publish(REQUEST, state, jar)
        print(json.dumps(result, indent=2))
        return 0 if result['ok'] else 1
    except Exception as error:
        result = {'ok': False, 'errorType': type(error).__name__}
        if isinstance(error, Guard):
            result['guard'] = str(error)
        print(json.dumps(result))
        return 1


if __name__ == '__main__':
    sys.exit(main())
