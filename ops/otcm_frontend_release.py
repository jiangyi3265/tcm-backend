#!/usr/bin/env python3
"""Fixed-host frontend-only release. No backend, database, or service mutation."""
import base64
import ctypes
import datetime
import hashlib
import io
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

REQUEST = {}
WEB = pathlib.Path('/www/wwwroot/www.otcm.app')
BACKUPS = pathlib.Path('/www/backup/otcm-frontend-releases')
LOCK = pathlib.Path('/www/backup/otcm-releases/release.lock')
APP = pathlib.Path('/www/wwwroot/yiyuanjava')
BASE = 'https://www.otcm.app'
LIMIT = 64 * 1024 * 1024


class Guard(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise Guard(message)


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def tree_files(root):
    result = {}
    require(root.is_dir() and not root.is_symlink() and root.resolve() == root, 'Unexpected web directory')
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink(), 'Web directory contains a symbolic link')
        require(path.is_file() or path.is_dir(), 'Web directory contains a special file')
        if path.is_file():
            result[path.relative_to(root).as_posix()] = digest(path)
    return result


def validate_request(request):
    require(request.get('target') == '43.166.154.98', 'Wrong production host')
    require(request.get('mode') in ('inspect', 'publish'), 'Invalid release mode')
    for name in ('revision', 'expectedRevision'):
        require(re.fullmatch(r'[0-9a-f]{40}', request.get(name, '')), 'Invalid frontend revision')
    require(request['revision'] != request['expectedRevision'], 'Release must have a new revision')
    require(request.get('publicBaseUrl') == BASE, 'Wrong public domain')
    require(re.fullmatch(r'[0-9a-f]{64}', request.get('sha256', '')), 'Invalid artifact digest')
    encoded = request.get('archiveBase64', '')
    require(isinstance(encoded, str) and 0 < len(encoded) <= LIMIT * 2, 'Invalid archive size')
    archive = base64.b64decode(encoded, validate=True)
    require(len(archive) <= LIMIT and hashlib.sha256(archive).hexdigest() == request['sha256'], 'Artifact digest mismatch')
    return archive


def extract(archive, target, revision):
    """Extract static files only, rejecting overwrites of server-owned dotfiles."""
    names = set()
    total = 0
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as source:
        members = source.getmembers()
        require(len(members) <= 5000, 'Too many artifact files')
        for item in members:
            path = pathlib.PurePosixPath(item.name)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in item.name, 'Unsafe artifact path')
            require(all(not part.startswith('.') for part in path.parts), 'Artifact may not replace server dotfiles')
            require(item.isfile() or item.isdir(), 'Artifact links or special files are forbidden')
            normalized = path.as_posix()
            require(normalized not in names, 'Duplicate artifact path')
            names.add(normalized)
            total += item.size
            require(total <= LIMIT, 'Expanded artifact is too large')
        for item in members:
            destination = target / item.name
            if item.isdir():
                destination.mkdir(parents=True, exist_ok=True)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(item) as original, destination.open('xb') as output:
                    shutil.copyfileobj(original, output)
    require((target / 'index.html').is_file() and (target / 'assets').is_dir(), 'Missing frontend entrypoint')
    require(json.loads((target / 'version.json').read_text()) == {'version': revision}, 'Wrong artifact version')
    require(all(name.endswith(('.html', '.json', '.js', '.css', '.svg', '.png', '.jpg', '.jpeg', '.webp', '.ico', '.woff', '.woff2', '.ttf', '.txt'))
                for name in tree_files(target)), 'Unexpected static artifact type')


def backend_fingerprint():
    # Read only. Never start/stop the service or access a database.
    pid = subprocess.run(['systemctl', 'show', 'yiyuanjava', '--property=MainPID', '--value'],
                         check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=10).stdout.decode().strip()
    require(pid.isdigit() and int(pid) > 0, 'Backend is not running')
    protected = [APP / 'yy.jar', APP / 'config/application-otcm-preserved.properties', APP / 'runtime/.otcm/smtp-settings.key']
    return {'pid': pid, 'files': {str(path): digest(path) if path.is_file() else None for path in protected}}


def exchange(left, right):
    """Linux renameat2 exchanges both directories atomically on their common FS."""
    require(left.parent == right.parent and left.stat().st_dev == right.stat().st_dev, 'Cross-device switch refused')
    libc = ctypes.CDLL(None, use_errno=True)
    rename = getattr(libc, 'renameat2', None)
    require(rename is not None, 'Atomic directory exchange unavailable')
    rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    rename.restype = ctypes.c_int
    require(rename(-100, os.fsencode(left), -100, os.fsencode(right), 2) == 0, 'Atomic directory exchange failed')
    descriptor = os.open(str(left.parent), os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def fetch(url):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    request = urllib.request.Request(url, headers={'Cache-Control': 'no-cache'})
    with opener.open(request, timeout=15) as response:
        require(response.status == 200 and response.url.startswith(BASE + '/'), 'Unexpected public response')
        body = response.read(LIMIT + 1)
    require(len(body) <= LIMIT, 'Public response too large')
    return body


def health(revision, files):
    query = '?otcm_release=' + revision
    require(json.loads(fetch(BASE + '/version.json' + query)) == {'version': revision}, 'Public version mismatch')
    require(hashlib.sha256(fetch(BASE + '/booking' + query)).hexdigest() == files['index.html'], 'Public booking HTML mismatch')
    html = (WEB / 'index.html').read_text()
    assets = sorted(set(re.findall(r'(?:src|href)="(/assets/[^"?]+)"', html)))
    require(bool(assets), 'Missing entrypoint assets')
    booking_assets = sorted('/' + name for name in files
                            if re.fullmatch(r'assets/PublicBookingView-[A-Za-z0-9_-]+\.(?:js|css)', name))
    require(any(name.endswith('.js') for name in booking_assets), 'Missing public booking page chunk')
    for name in sorted(set(assets + booking_assets)):
        require(name[1:] in files and hashlib.sha256(fetch(BASE + name + query)).hexdigest() == files[name[1:]],
                'Public entrypoint or booking asset mismatch')
    options = json.loads(fetch(BASE + '/api/public-booking/options'))
    require(isinstance(options.get('practitioners'), list) and isinstance(options.get('serviceTypes'), list), 'Booking API unavailable')
    return {'version': revision, 'entryAssetsVerified': len(assets),
            'bookingPageAssetsVerified': len(booking_assets), 'bookingApiAvailable': True}


def write_private(path, value):
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2)
    os.chmod(path, 0o600)


def publish(request, unpacked):
    before = tree_files(WEB)
    require(json.loads((WEB / 'version.json').read_text()) == {'version': request['expectedRevision']}, 'Current frontend differs from reviewed version')
    backend = backend_fingerprint()
    incoming = tree_files(unpacked)
    # Hashed assets already in use by open pages must never be replaced.
    require(all(before[name] == value for name, value in incoming.items() if name.startswith('assets/') and name in before),
            'An existing hashed asset would change')
    if request['mode'] == 'inspect':
        return {'ok': True, 'mode': 'inspect', 'previousRevision': request['expectedRevision'], 'revision': request['revision'],
                'currentFileCount': len(before), 'incomingFileCount': len(incoming), 'readOnly': True}
    require(BACKUPS.resolve() == BACKUPS and not BACKUPS.is_symlink(), 'Unsafe backup root')
    BACKUPS.mkdir(parents=True, mode=0o700, exist_ok=True)
    # main() requires root; keeping this relative to the effective user also
    # permits the isolated permission/rollback tests on an unprivileged runner.
    require(BACKUPS.stat().st_uid == os.geteuid() and stat.S_IMODE(BACKUPS.stat().st_mode) == 0o700, 'Backup root must be root-private')
    size = sum((WEB / name).stat().st_size for name in before)
    require(shutil.disk_usage(WEB.parent).free > 4 * size + LIMIT, 'Insufficient release space')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = BACKUPS / (stamp + '-' + request['revision'][:12])
    backup.mkdir(mode=0o700)
    with tarfile.open(backup / 'previous-web.tar.gz', 'w:gz') as archive:
        archive.add(WEB, arcname='frontend', recursive=True)
    os.chmod(backup / 'previous-web.tar.gz', 0o600)
    with tarfile.open(backup / 'previous-web.tar.gz') as archive:
        actual = {pathlib.PurePosixPath(item.name).relative_to('frontend').as_posix(): hashlib.sha256(archive.extractfile(item).read()).hexdigest()
                  for item in archive.getmembers() if item.isfile()}
    require(actual == before, 'Frontend backup verification failed')
    write_private(backup / 'BACKUP.json', {'oldFiles': before, 'incomingFiles': incoming, 'backendFingerprint': backend,
                  'previousRevision': request['expectedRevision'], 'revision': request['revision'],
                  'backupSha256': digest(backup / 'previous-web.tar.gz')})
    staged = pathlib.Path(tempfile.mkdtemp(prefix='.otcm-frontend-', dir=WEB.parent))
    switched = False
    report = {'ok': False, 'mode': 'publish', 'revision': request['revision'], 'previousRevision': request['expectedRevision'],
              'backup': str(backup), 'backupSha256': digest(backup / 'previous-web.tar.gz'), 'rollbackPerformed': False,
              'databaseWrites': 0, 'backendRestarted': False, 'backendAndSecretsUnchanged': False}
    try:
        shutil.copytree(WEB, staged, dirs_exist_ok=True, copy_function=shutil.copy2)
        require(tree_files(staged) == before, 'Staged copy differs from original frontend')
        for name in incoming:
            destination = staged / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(unpacked / name, destination)
            os.chmod(destination, 0o644)
        for path in [staged] + [path for path in staged.rglob('*') if path.is_dir()]:
            # Preserve original server-owned directory permissions. New asset folders need nginx read access.
            relative = path.relative_to(staged)
            original = WEB / relative
            metadata = original.stat() if original.exists() else WEB.stat()
            os.chown(path, metadata.st_uid, metadata.st_gid)
            os.chmod(path, stat.S_IMODE(metadata.st_mode))
        expected = {**before, **incoming}
        require(tree_files(staged) == expected, 'Staged release file mismatch')
        require(tree_files(WEB) == before and backend_fingerprint() == backend, 'Production changed during release preparation')
        # Set before exchange: failure after rename but before directory fsync still requires rollback detection.
        switched = True
        exchange(WEB, staged)
        require(tree_files(WEB) == expected, 'Published file mismatch')
        report['health'] = health(request['revision'], incoming)
        require(backend_fingerprint() == backend, 'Backend changed during frontend release')
        report.update(ok=True, backendAndSecretsUnchanged=True, preservedOldAssetCount=sum(name.startswith('assets/') for name in before))
    except Exception as error:
        report['errorType'] = type(error).__name__
        if isinstance(error, Guard):
            report['guard'] = str(error)
        if switched:
            try:
                # An exchange syscall failure can leave the original tree in place.
                if tree_files(WEB) != before:
                    require(tree_files(staged) == before, 'Original rollback tree changed')
                    exchange(WEB, staged)
                require(tree_files(WEB) == before, 'Frontend rollback mismatch')
                report['rollbackPerformed'] = True
                report['backendAndSecretsUnchanged'] = backend_fingerprint() == backend
            except Exception as rollback_error:
                report['rollbackErrorType'] = type(rollback_error).__name__
    # Retain the displaced tree for recovery; do not recursively delete any live/saved directory.
    report['retainedTree'] = str(staged)
    os.chmod(staged, 0o700)
    write_private(backup / 'RESULT.json', report)
    return report


def main():
    os.umask(0o077)
    try:
        require(os.geteuid() == 0, 'Root is required for fixed frontend ownership')
        archive = validate_request(REQUEST)
        require(not LOCK.is_symlink() and LOCK.parent.is_dir(), 'Unexpected release lock path')
        import fcntl
        with LOCK.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with tempfile.TemporaryDirectory(prefix='otcm-frontend-') as directory:
                unpacked = pathlib.Path(directory) / 'frontend'
                unpacked.mkdir()
                extract(archive, unpacked, REQUEST['revision'])
                result = publish(REQUEST, unpacked)
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
