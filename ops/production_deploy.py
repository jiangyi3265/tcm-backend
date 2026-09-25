#!/usr/bin/env python3
"""Forced SSH command for this clinic's two production deployment keys only."""
import datetime
import concurrent.futures
import fcntl
import hashlib
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
import urllib.request
import zipfile

BASE = pathlib.Path('/opt/yuanyuan-tcm')
BACKUPS = pathlib.Path('/www/backup/yuanyuan-tcm/releases')
DOMAINS = ['yuanyuanyiyuan.oksja.cn', 'yuanyuanyiyuanht.oksja.cn']
MAX_ARTIFACT_BYTES = 160 * 1024 * 1024


def parse_command(command):
    parts = command.split()
    if (len(parts) not in [3, 4] or parts[0] != 'deploy'
            or (len(parts) == 4 and parts[3] != 'github-artifact')
            or not re.fullmatch(r'[0-9a-f]{40}', parts[1])
            or not re.fullmatch(r'[0-9a-f]{64}', parts[2])):
        raise ValueError('Only deploy <commit SHA> <artifact SHA256> is allowed')
    return parts[1], parts[2]


def receive_github_artifact(stream, target, expected):
    url = stream.read(8193).decode('ascii').strip()
    parsed = urllib.parse.urlsplit(url)
    if (len(url) > 8192 or parsed.scheme != 'https' or parsed.username or parsed.password
            or parsed.port not in [None, 443] or not parsed.hostname
            or not parsed.hostname.endswith(('.blob.core.windows.net', '.actions.githubusercontent.com'))):
        raise ValueError('Only a signed GitHub artifact URL is allowed')

    archive_path = target.with_suffix('.zip')
    download_github_archive(url, archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        if archive.namelist() != ['ruoyi-admin.jar']:
            raise ValueError('Unexpected GitHub artifact contents')
        with archive.open('ruoyi-admin.jar') as source:
            receive(source, target, expected)


def download_github_archive(url, archive_path):
    # Several bounded range requests avoid slow single-connection overseas transfers.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise ValueError('Artifact redirects are forbidden')

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(urllib.request.Request(url, method='HEAD'), timeout=45) as response:
            size = int(response.headers['Content-Length'])
        if not 0 < size <= MAX_ARTIFACT_BYTES:
            raise ValueError('Invalid artifact size')
        with archive_path.open('wb') as output:
            output.truncate(size)

        def download_range(bounds):
            start, end = bounds
            request = urllib.request.Request(url, headers={'Range': 'bytes=%d-%d' % (start, end)})
            with opener.open(request, timeout=45) as response, archive_path.open('r+b') as output:
                if response.status != 206 or response.headers.get('Content-Range') != 'bytes %d-%d/%d' % (start, end, size):
                    raise ValueError('Invalid artifact range response')
                output.seek(start)
                remaining = end - start + 1
                while remaining:
                    block = response.read(min(1024 * 1024, remaining))
                    if not block:
                        raise ValueError('Incomplete artifact range')
                    output.write(block)
                    remaining -= len(block)

        chunk = 8 * 1024 * 1024
        ranges = [(start, min(start + chunk, size) - 1) for start in range(0, size, chunk)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as workers:
            list(workers.map(download_range, ranges))
    except Exception:
        raise RuntimeError('GitHub artifact download failed') from None


def receive(stream, target, expected):
    size = 0
    digest = hashlib.sha256()
    with target.open('wb') as output:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            size += len(block)
            if size > MAX_ARTIFACT_BYTES:
                raise ValueError('Artifact is too large')
            digest.update(block)
            output.write(block)
    if not size or digest.hexdigest() != expected:
        raise ValueError('Artifact checksum mismatch')


def extract_frontend(archive, target, revision):
    total = 0
    with tarfile.open(archive, 'r:gz') as tar:
        members = tar.getmembers()
        if len(members) > 5000:
            raise ValueError('Too many archive entries')
        for item in members:
            path = pathlib.PurePosixPath(item.name)
            if path.is_absolute() or '..' in path.parts or '\\' in item.name:
                raise ValueError('Unsafe archive path')
            if not item.isdir() and not item.isfile():
                raise ValueError('Archive links and special files are forbidden')
            total += item.size
            if total > MAX_ARTIFACT_BYTES:
                raise ValueError('Expanded archive is too large')
        for item in members:
            destination = target / item.name
            if item.isdir():
                destination.mkdir(parents=True, exist_ok=True)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                with tar.extractfile(item) as source, destination.open('wb') as output:
                    shutil.copyfileobj(source, output)
    assert (target / 'index.html').is_file(), 'Missing index.html'
    assert (target / 'assets').is_dir(), 'Missing assets'
    assert json.loads((target / 'version.json').read_text())['version'] == revision, 'Wrong build revision'


def atomic_copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name('.' + target.name + '.ci-upload')
    shutil.copyfile(source, temporary)
    os.chmod(temporary, 0o644)
    os.replace(temporary, target)


def fetch_json(url):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    request = urllib.request.Request(url, headers={'Cache-Control': 'no-cache'})
    with opener.open(request, timeout=8) as response:
        return json.load(response)


def restart_backend():
    subprocess.run(['systemctl', 'restart', 'yuanyuan-tcm'], check=True, timeout=90)
    for attempt in range(45):
        try:
            if 'practitioners' in fetch_json('http://127.0.0.1:8006/api/public-booking/options'):
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Production backend health check failed')


def deploy_backend(artifact, backup):
    with zipfile.ZipFile(artifact) as jar:
        names = jar.namelist()
        assert 'BOOT-INF/classes/com/ruoyi/RuoYiApplication.class' in names, 'Not a clinic backend'
        assert any(n.startswith('BOOT-INF/lib/ruoyi-hospital-') for n in names), 'Missing hospital module'
    target = BASE / 'current/backend.jar'
    shutil.copy2(target, backup / 'backend.jar')
    try:
        atomic_copy(artifact, target)
        restart_backend()
    except Exception:
        atomic_copy(backup / 'backend.jar', target)
        restart_backend()
        raise


def copy_frontend(source, destination):
    # Keep old hashed assets so already-open browser pages continue to work.
    for path in source.rglob('*'):
        if path.is_dir():
            folder = destination / path.relative_to(source)
            folder.mkdir(parents=True, exist_ok=True)
            os.chmod(folder, 0o755)
        elif path.name not in ['index.html', 'version.json']:
            atomic_copy(path, destination / path.relative_to(source))
    for name in ['index.html', 'version.json']:
        atomic_copy(source / name, destination / name)


def deploy_frontend(artifact, work, backup, revision):
    unpacked = work / 'frontend'
    unpacked.mkdir()
    extract_frontend(artifact, unpacked, revision)
    destinations = [pathlib.Path('/www/wwwroot') / name for name in DOMAINS] + [BASE / 'current/frontend']
    for number, destination in enumerate(destinations):
        saved = backup / str(number)
        saved.mkdir()
        for name in ['index.html', 'version.json']:
            shutil.copy2(destination / name, saved / name)
    try:
        for destination in destinations:
            copy_frontend(unpacked, destination)
        for domain in DOMAINS:
            assert fetch_json('https://' + domain + '/version.json?release=' + revision)['version'] == revision
    except Exception:
        for number, destination in enumerate(destinations):
            for name in ['index.html', 'version.json']:
                atomic_copy(backup / str(number) / name, destination / name)
        raise


def main():
    os.umask(0o077)
    component = sys.argv[1] if len(sys.argv) == 2 else ''
    if component not in ['backend', 'frontend']:
        raise ValueError('Invalid component')
    command = os.environ.get('SSH_ORIGINAL_COMMAND', '')
    revision, digest = parse_command(command)
    pull_artifact = len(command.split()) == 4
    if pull_artifact and component != 'backend':
        raise ValueError('Only backend artifacts can use GitHub download')
    stage = BASE / 'ci-staging'
    stage.mkdir(mode=0o700, exist_ok=True)
    with (stage / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        with tempfile.TemporaryDirectory(prefix=component + '-', dir=stage) as temporary:
            work = pathlib.Path(temporary)
            artifact = work / 'artifact'
            if pull_artifact:
                receive_github_artifact(sys.stdin.buffer, artifact, digest)
            else:
                receive(sys.stdin.buffer, artifact, digest)
            stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
            backup = BACKUPS / (component + '-' + stamp + '-' + revision[:12])
            backup.mkdir(mode=0o700, parents=True)
            if component == 'backend':
                deploy_backend(artifact, backup)
            else:
                deploy_frontend(artifact, work, backup, revision)
            record = BASE / 'DEPLOYED'
            metadata = json.loads(record.read_text()) if record.exists() else {}
            metadata[component] = revision
            metadata[component + '_deployed_at'] = stamp
            metadata[component + '_artifact_sha256'] = digest
            metadata[component + '_rollback'] = str(backup)
            temporary_record = BASE / '.DEPLOYED.ci'
            temporary_record.write_text(json.dumps(metadata, indent=2))
            os.replace(temporary_record, record)
            print(json.dumps({'component': component, 'revision': revision, 'health': 'passed', 'rollback': str(backup)}))


if __name__ == '__main__':
    main()
