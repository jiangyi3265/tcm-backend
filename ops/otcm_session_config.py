#!/usr/bin/env python3
"""One guarded OTCM session-expiry correction; never touch DB or Redis."""
import argparse
import datetime
import json
import os
import pathlib
import re
import shlex
import shutil
import stat
import sys
import tempfile
import time
import urllib.request

import otcm_promotion as promotion

TARGET = '43.166.154.98'
SERVICE = 'yiyuanjava'
APP = pathlib.Path('/www/wwwroot/yiyuanjava')
PRESERVED = APP / 'config/application-otcm-preserved.properties'
CURRENT_JAR = '945d44b37c03592076c19d76193f6446863aea0ebf4c223aa54315215a1328e2'
BACKUP_ROOT = pathlib.Path('/www/backup/otcm-session-config')
LOCK = pathlib.Path('/www/backup/otcm-releases/release.lock')
SMTP_KEY = APP / 'runtime/.otcm/smtp-settings.key'
STAGING = pathlib.Path('/www/backup/otcm-release-staging')
FRONTEND_REVISION = '4e42f8118a303b09c3f6131144b60216b10217b3'


class SessionGuard(ValueError):
    """Only fixed messages may be surfaced; configuration values stay private."""


def require(condition, message):
    if not condition:
        raise SessionGuard(message)


def canonical(key):
    return re.sub(r'[^a-z0-9]', '', key.lower())


def expiry_key(key):
    return canonical(key) in ('tokenexpiretime', 'ruoyitokenexpireminutes')


def replacement(original):
    matches = list(re.finditer(rb'(?m)^token\.expireTime=(120)(?=\r?$)', original))
    require(len(matches) == 1, 'Expected exactly one canonical expiry line at 120')
    # The preserved file was generated with canonical, unescaped keys. Reject
    # duplicate/escaped spellings instead of relying on a last-value-wins parser.
    expiry_lines = []
    for line in original.decode('ascii').splitlines():
        if not line.strip() or line.lstrip().startswith(('#', '!')):
            continue
        key = re.split(r'[\s=:]', line.lstrip(), maxsplit=1)[0]
        require('\\' not in key, 'Unexpected escaped preserved property key')
        if expiry_key(key):
            expiry_lines.append(line)
    require(expiry_lines == ['token.expireTime=120'], 'Unexpected duplicate or noncanonical expiry line')
    props = promotion.diagnostics.parse_config(original.decode('ascii'), '.properties')
    keys = [key for key in props if expiry_key(key)]
    require(keys == ['token.expireTime'] and str(props[keys[0]]) == '120', 'Unexpected expiry property aliases')
    start, end = matches[0].span(1)
    changed = original[:start] + b'1440' + original[end:]
    require(original[:start] == changed[:start] and original[end:] == changed[start + 4:],
            'Unrelated configuration bytes changed')
    return changed


def no_overrides(state, service_metadata):
    environment = state['environment']
    require(not any(expiry_key(key) for key in environment), 'Session expiry environment override requires review')
    for text in (environment.get('SPRING_APPLICATION_JSON'),):
        if text:
            parsed = promotion.diagnostics.flatten(json.loads(text))
            require(not any(expiry_key(key) for key in parsed), 'JSON session expiry override requires review')
    arguments = list(state['args'])
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        arguments.extend(shlex.split(environment.get(name, '')))
    for argument in arguments:
        if argument.startswith(('--', '-D')):
            require(not expiry_key(argument[2:].split('=', 1)[0]), 'Session expiry startup override requires review')
    # Reject pending systemd/environment-file overrides too, before restarting.
    require(not re.search(r'(?i)(?:token[._-]*expire[._-]*time|ruoyi[._-]*token[._-]*expire[._-]*minutes)\s*[=:]',
                          service_metadata), 'Pending service expiry override requires review')
    for line in service_metadata.splitlines():
        if line.startswith('Environment='):
            line = line[len('Environment='):]
        for assignment in shlex.split(line, comments=True):
            name, separator, value = assignment.partition('=')
            if separator and name == 'SPRING_APPLICATION_JSON':
                pending = promotion.diagnostics.flatten(json.loads(value))
                require(not any(expiry_key(key) for key in pending), 'Pending JSON expiry override requires review')


def service_metadata():
    show = promotion.run(['systemctl', 'show', SERVICE, '--property=FragmentPath', '--property=DropInPaths',
                          '--property=ExecStart', '--property=Environment', '--property=EnvironmentFiles',
                          '--property=User', '--property=Group', '--property=WorkingDirectory'])
    environment_files = promotion.run(['systemctl', 'show', SERVICE, '--property=EnvironmentFiles', '--value'])
    files = {}
    for name in re.findall(r'(/[^\s;()]+)', environment_files):
        path = pathlib.Path(name)
        if path.is_file():
            files[str(path)] = path.read_text()
    # These fields can contain secrets and are backed up only in a root-private file.
    return {'systemd': show, 'environmentFiles': files}


def service_identity(metadata):
    # systemctl's ExecStart property also contains the last process PID/times.
    # They necessarily change at restart and are not configuration changes.
    stable = re.sub(r' ; (?:start_time|stop_time|pid|code|status)=[^;}\n]*', '', metadata['systemd'])
    return {'systemd': stable, 'environmentFiles': metadata['environmentFiles']}


def config_fingerprints(unit_metadata):
    paths = {path for path in APP.iterdir() if path.is_file()
             and (path.suffix in ('.env', '.properties', '.yml', '.yaml', '.conf')
                  or path.name == '.env' or path.name.startswith('.env.'))}
    config = APP / 'config'
    if config.exists():
        paths.update(path for path in config.rglob('*') if path.is_file())
    paths.update(pathlib.Path(path) for path in unit_metadata['environmentFiles'])
    for line in unit_metadata['systemd'].splitlines():
        if line.startswith(('FragmentPath=', 'DropInPaths=')):
            paths.update(pathlib.Path(value) for value in shlex.split(line.split('=', 1)[1]))
    if SMTP_KEY.exists():
        paths.add(SMTP_KEY)
    return {str(path): promotion.digest(path) for path in sorted(paths) if path != PRESERVED}


def new_backup_directory():
    require(BACKUP_ROOT.resolve() == BACKUP_ROOT, 'Unexpected backup directory link')
    BACKUP_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(BACKUP_ROOT.stat().st_uid == 0 and stat.S_IMODE(BACKUP_ROOT.stat().st_mode) == 0o700,
            'Backup directory must be root-private')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = BACKUP_ROOT / stamp
    backup.mkdir(mode=0o700)
    return backup


def backup_smtp_key(backup):
    if not SMTP_KEY.exists():
        require(not SMTP_KEY.is_symlink(), 'Unexpected SMTP key link')
        return
    metadata = SMTP_KEY.lstat()
    require(stat.S_ISREG(metadata.st_mode) and not SMTP_KEY.is_symlink(), 'Unexpected SMTP key file')
    target = backup / 'smtp-settings.key'
    # Never decode the key or include its content/digest in the public report.
    shutil.copy2(SMTP_KEY, target)
    os.chown(target, metadata.st_uid, metadata.st_gid)
    require(promotion.digest(target) == promotion.digest(SMTP_KEY)
            and stat.S_IMODE(target.stat().st_mode) == stat.S_IMODE(metadata.st_mode), 'SMTP key backup verification failed')


def staged_backend(run_id, revision, digest):
    if not any((run_id, revision, digest)):
        return None
    require(re.fullmatch(r'[0-9]{8,20}', run_id or ''), 'Invalid staged build run')
    require(re.fullmatch(r'[0-9a-f]{40}', revision or ''), 'Invalid staged backend revision')
    require(re.fullmatch(r'[0-9a-f]{64}', digest or '') and digest != CURRENT_JAR, 'Invalid staged backend digest')
    work = STAGING / run_id
    require(work.resolve() == work, 'Unexpected staged directory link')
    require(work.stat().st_uid == 0 and not stat.S_IMODE(work.stat().st_mode) & 0o022,
            'Staged directory must be root-owned and not group/world writable')
    for name in ('backend.jar', 'manifest.json', 'READY.json'):
        path = work / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_uid == 0,
                'Missing or unsafe staged release file')
    manifest = json.loads((work / 'manifest.json').read_text())
    ready = json.loads((work / 'READY.json').read_text())
    require(ready.get('runId') == run_id and ready.get('manifest') == manifest, 'Staged release readiness mismatch')
    require(manifest.get('backendRevision') == revision and manifest.get('frontendRevision') == FRONTEND_REVISION
            and manifest.get('publicBaseUrl') == 'https://www.otcm.app', 'Staged release identity mismatch')
    require(manifest.get('sha256', {}).get('backend.jar') == digest
            and promotion.digest(work / 'backend.jar') == digest, 'Staged backend digest mismatch')
    return {'path': work / 'backend.jar', 'sha256': digest, 'revision': revision, 'buildRun': run_id}


def validate():
    state = promotion.runtime()
    require(state['cwd'] == APP and state['jar'] == APP / 'yy.jar', 'Unexpected service deployment paths')
    require(promotion.digest(state['jar']) == CURRENT_JAR, 'Running JAR differs from reviewed release')
    metadata = PRESERVED.lstat()
    require(stat.S_ISREG(metadata.st_mode) and stat.S_IMODE(metadata.st_mode) == 0o600
            and metadata.st_uid == 0 and PRESERVED.resolve() == PRESERVED, 'Preserved config must be root-owned regular 0600')
    original = PRESERVED.read_bytes()
    changed = replacement(original)
    require(str(state['properties'].get('token.expireTime')) == '120', 'Effective session expiry is not 120')
    require(str(PRESERVED) in state['environment'].get('SPRING_CONFIG_ADDITIONAL_LOCATION', ''),
            'Preserved configuration is not explicitly loaded')
    metadata_text = service_metadata()
    no_overrides(state, metadata_text['systemd'] + '\n' + '\n'.join(metadata_text['environmentFiles'].values()))
    return state, metadata, original, changed, metadata_text


def atomic_replace(path, writer, uid=0, gid=0, mode=0o600):
    require(not path.is_symlink(), 'Refusing symbolic link destination')
    descriptor, temporary_name = tempfile.mkstemp(prefix='.' + path.name + '-', dir=str(path.parent))
    temporary = pathlib.Path(temporary_name)
    try:
        os.fchmod(descriptor, mode)
        os.fchown(descriptor, uid, gid)
        with os.fdopen(descriptor, 'wb') as output:
            descriptor = None
            writer(output)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        directory = os.open(str(path.parent), os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if descriptor is not None:
            os.close(descriptor)
        if temporary.exists():
            temporary.unlink()


def atomic_write(path, content, uid=0, gid=0):
    atomic_replace(path, lambda output: output.write(content), uid, gid)


def atomic_copy(source, destination, uid=0, gid=0, mode=0o600):
    def copy(output):
        with source.open('rb') as input_file:
            shutil.copyfileobj(input_file, output, 1024 * 1024)
    atomic_replace(destination, copy, uid, gid, mode)


def health():
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(45):
        try:
            active = promotion.run(['systemctl', 'is-active', SERVICE]) == 'active'
            with opener.open('http://127.0.0.1:8006/api/public-booking/options', timeout=3) as response:
                body = json.loads(response.read(1024 * 1024))
            if active and isinstance(body.get('practitioners'), list):
                return True
        except Exception:
            pass
        time.sleep(2)
    return False


def apply_change(staged=None):
    state, metadata, original, changed, unit_metadata = validate()
    backend_before = promotion.digest(state['jar'])
    original_revision = state.get('summary', {}).get('version', {}).get('git.commit.id')
    fingerprints = config_fingerprints(unit_metadata)
    backup = new_backup_directory()
    atomic_write(backup / 'application-otcm-preserved.properties', original)
    private_metadata = {'args': state['args'], 'environment': state['environment'], 'serviceMetadata': unit_metadata,
                        'configUid': metadata.st_uid, 'configGid': metadata.st_gid, 'configMode': '0600',
                        'otherConfigSha256': fingerprints}
    atomic_write(backup / 'startup-private.json', json.dumps(private_metadata, ensure_ascii=True).encode())
    require((backup / 'application-otcm-preserved.properties').read_bytes() == original, 'Config backup verification failed')
    backup_smtp_key(backup)
    jar_metadata = state['jar'].stat()
    if staged:
        require(promotion.digest(staged['path']) == staged['sha256'], 'Staged backend changed before backup')
        atomic_copy(state['jar'], backup / 'previous.jar')
        require(promotion.digest(backup / 'previous.jar') == CURRENT_JAR, 'Backend backup verification failed')
    report = {'ok': False, 'target': TARGET, 'service': SERVICE, 'expiryBefore': 120, 'expiryAfter': 120,
              'otherConfigBytesPreserved': True, 'backup': str(backup), 'serviceHealthy': False,
              'rollbackPerformed': False, 'databaseTouched': False, 'redisSessionsTouched': False,
              'backendBefore': backend_before, 'backendAfter': backend_before, 'backendChanged': False,
              'backendRevision': original_revision, 'buildRun': staged['buildRun'] if staged else None,
              'otherEffectiveConfigPreserved': None}
    changed_on_disk = False
    jar_changed = False
    try:
        require(PRESERVED.read_bytes() == original, 'Config changed during preparation')
        require(service_metadata() == unit_metadata, 'Service metadata changed during preparation')
        require(promotion.run(['systemctl', 'show', SERVICE, '--property=MainPID', '--value']) == str(state['pid']),
                'Running service changed during preparation')
        report['otherConfigBytesPreserved'] = config_fingerprints(unit_metadata) == fingerprints
        require(report['otherConfigBytesPreserved'], 'Other config changed during preparation')
        require(promotion.digest(state['jar']) == backend_before, 'Running backend changed during preparation')
        if staged:
            require(promotion.digest(staged['path']) == staged['sha256'], 'Staged backend changed before install')
            # Never overwrite a JAR still in use by the old process.
            jar_changed = True
            promotion.run(['systemctl', 'stop', SERVICE])
            atomic_copy(staged['path'], state['jar'], jar_metadata.st_uid, jar_metadata.st_gid,
                        stat.S_IMODE(jar_metadata.st_mode))
            require(promotion.digest(state['jar']) == staged['sha256'], 'Installed backend digest mismatch')
        # Set before atomic write: a failure after os.replace still requires rollback.
        changed_on_disk = True
        atomic_write(PRESERVED, changed, metadata.st_uid, metadata.st_gid)
        require(PRESERVED.read_bytes() == changed, 'Written config differs from exact replacement')
        promotion.run(['systemctl', 'restart', SERVICE])
        require(health(), 'Service did not recover after config change')
        updated = promotion.runtime()
        require(updated['cwd'] == APP and updated['jar'] == state['jar'], 'Application paths changed during restart')
        backend_after = promotion.digest(updated['jar'])
        require(backend_after == (staged['sha256'] if staged else CURRENT_JAR),
                'Application changed during restart')
        require(str(updated['properties'].get('token.expireTime')) == '1440', 'Effective expiry is not 1440 after restart')
        before_other = {key: value for key, value in state['properties'].items() if key != 'token.expireTime'}
        after_other = {key: value for key, value in updated['properties'].items() if key != 'token.expireTime'}
        require(before_other == after_other, 'Other effective configuration changed')
        require(PRESERVED.read_bytes() == changed, 'Config changed during service restart')
        require(config_fingerprints(unit_metadata) == fingerprints, 'Other config bytes changed during restart')
        require(service_identity(service_metadata()) == service_identity(unit_metadata),
                'Service metadata changed during restart')
        report.update({'ok': True, 'expiryAfter': 1440, 'serviceHealthy': True, 'backendAfter': backend_after,
                       'backendChanged': backend_after != backend_before,
                       'backendRevision': staged['revision'] if staged else original_revision,
                       'otherEffectiveConfigPreserved': True})
    except Exception as error:
        report['errorType'] = type(error).__name__
        if isinstance(error, SessionGuard):
            report['guard'] = str(error)
        if changed_on_disk or jar_changed:
            report['expiryAfter'] = None
            report['backendAfter'] = None
            report['backendChanged'] = None
            report['backendRevision'] = None
            try:
                if jar_changed:
                    promotion.run(['systemctl', 'stop', SERVICE])
                    atomic_copy(backup / 'previous.jar', state['jar'], jar_metadata.st_uid, jar_metadata.st_gid,
                                stat.S_IMODE(jar_metadata.st_mode))
                atomic_write(PRESERVED, original, metadata.st_uid, metadata.st_gid)
                promotion.run(['systemctl', 'restart', SERVICE])
                report['rollbackPerformed'] = True
                report['serviceHealthy'] = health()
                restored = promotion.runtime()
                require(PRESERVED.read_bytes() == original
                        and str(restored['properties'].get('token.expireTime')) == '120', 'Rollback verification failed')
                restored_digest = promotion.digest(restored['jar'])
                require(restored_digest == CURRENT_JAR, 'Rollback backend verification failed')
                require(restored['properties'] == state['properties'], 'Rollback effective configuration differs')
                report['expiryAfter'] = 120
                report['otherConfigBytesPreserved'] = config_fingerprints(unit_metadata) == fingerprints
                report.update({'backendAfter': restored_digest, 'backendChanged': False,
                               'backendRevision': original_revision, 'otherEffectiveConfigPreserved': True})
            except Exception as rollback_error:
                report['rollbackErrorType'] = type(rollback_error).__name__
    atomic_write(backup / 'RESULT.json', json.dumps(report, indent=2).encode())
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', choices=[TARGET], required=True)
    parser.add_argument('--mode', choices=['inspect', 'apply'], default='inspect')
    parser.add_argument('--build-run')
    parser.add_argument('--backend-revision')
    parser.add_argument('--backend-sha256')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        require(os.geteuid() == 0, 'Root is required for this fixed service configuration')
        staged = staged_backend(args.build_run, args.backend_revision, args.backend_sha256)
        if args.mode == 'inspect':
            validate()
            result = {'ok': True, 'target': TARGET, 'service': SERVICE, 'expiryBefore': 120, 'expiryAfter': 120,
                      'proposedExpiry': 1440, 'otherConfigBytesPreserved': True, 'serviceHealthy': health(), 'readOnly': True}
        else:
            import fcntl
            require(not LOCK.is_symlink() and LOCK.parent.is_dir(), 'Unexpected release lock path')
            with LOCK.open('a') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                result = apply_change(staged)
        print(json.dumps(result, ensure_ascii=True, indent=2))
        return 0 if result['ok'] and result['serviceHealthy'] else 1
    except Exception as error:
        result = {'ok': False, 'errorType': type(error).__name__}
        if isinstance(error, SessionGuard):
            result['guard'] = str(error)
        print(json.dumps(result))
        return 1


if __name__ == '__main__':
    sys.exit(main())
