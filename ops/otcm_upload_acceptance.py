"""Two synthetic production uploads with exact fixture cleanup, never real patients."""
import base64
import datetime
import hashlib
import hmac
import json
import os
import pathlib
import re
import secrets
import shutil
import stat
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import zlib

import otcm_backend_release as release
import otcm_promotion as promotion
import otcm_session_config as session

REQUEST = {}
ROOT = pathlib.Path('/www/backup/otcm-upload-qa')
BASE = 'https://www.otcm.app'


class ApiResponseError(release.Guard):
    def __init__(self, metadata):
        super().__init__('Expected JSON API response')
        self.metadata = metadata


def require(value, message):
    release.require(value, message)


def fixture(qa_id):
    require(re.fullmatch(r'[0-9a-f]{32}', qa_id or ''), 'Invalid QA identity')
    return {'qaId': qa_id, 'username': 'qa_upload_' + qa_id[:20], 'patientId': 'qa-patient-' + qa_id,
            'consultationId': 'qa-consult-' + qa_id, 'name': 'OTCM_QA_' + qa_id}


def literal(value):
    return "CONVERT(X'" + str(value).encode().hex() + "' USING utf8mb4)"


def mutate(state, sql):
    database, env, options = promotion.mysql(state)
    return promotion.run(['mysql'] + options + ['--batch', '--skip-column-names', '--default-character-set=utf8mb4', database],
                         input=('START TRANSACTION; ' + sql + '; COMMIT;').encode(), env=env)


def private_write(path, data):
    session.atomic_write(path, json.dumps(data).encode())


def api(path, token=None, method='GET', body=None, content_type='application/json', binary=False):
    require(path.startswith('/') and not path.startswith('//'), 'Unexpected API path')
    headers = {'User-Agent': 'OTCM-Synthetic-Upload-Acceptance', 'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if isinstance(body, dict) else body
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    with opener.open(request, timeout=120) as response:
        require(response.status == 200, 'Unexpected API status')
        raw_type = response.headers.get_content_type()
        metadata = {'status': response.status, 'contentType': raw_type if raw_type in
                    ('application/json', 'text/html', 'image/png', 'application/octet-stream') else 'other'}
        value = response.read(8 * 1024 * 1024 + 1)
    require(len(value) <= 8 * 1024 * 1024, 'API response exceeded bound')
    if binary:
        return value
    try:
        return json.loads(value)
    except (ValueError, UnicodeError):
        raise ApiResponseError({**metadata, 'responseBytes': len(value)}) from None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise release.Guard('Redirect is forbidden during upload acceptance')


def png(width, height):
    def chunk(kind, body):
        return struct.pack('!I', len(body)) + kind + body + struct.pack('!I', zlib.crc32(kind + body) & 0xffffffff)
    raw = b''.join(b'\0' + secrets.token_bytes(width * 3) for _ in range(height))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 1)) + chunk(b'IEND', b'')


def multipart(meta, filename, content):
    boundary = 'otcmqa' + secrets.token_hex(16)
    parts = []
    for name in ('patientId', 'consultationId'):
        parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + name + '"\r\n\r\n' + meta[name] + '\r\n').encode())
    parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="fileType"\r\n\r\ndocument\r\n').encode())
    parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + filename + '"\r\nContent-Type: image/png\r\n\r\n').encode())
    return b''.join(parts) + content + ('\r\n--' + boundary + '--\r\n').encode(), 'multipart/form-data; boundary=' + boundary


def resource_pattern(meta):
    return re.escape('hospital-private/' + meta['name'] + '/') + r'Consultation/[0-9]{4}/document_[0-9a-f]{32}\.png'


def validate_resource(meta, resource):
    require(isinstance(resource, str) and re.fullmatch(resource_pattern(meta), resource), 'Unexpected QA file resource')
    return resource


def signed_url_metadata(url):
    parsed = urllib.parse.urlsplit(str(url or ''))
    try:
        port = parsed.port
    except ValueError:
        port = 'invalid'
    host = parsed.hostname or ''
    return {'scheme': parsed.scheme if parsed.scheme in ('', 'http', 'https') else 'other',
            'host': host if host in ('', 'www.otcm.app', 'otcm.app', 'localhost', '127.0.0.1', '43.166.154.98') else 'other',
            'port': port, 'path': parsed.path if parsed.path == '/api/public/files/access' else 'other'}


def validate_signed_url(url):
    parsed = urllib.parse.urlsplit(url)
    require(parsed.scheme in ('', 'https') and parsed.hostname in (None, 'www.otcm.app', 'otcm.app')
            and parsed.port in (None, 443) and not parsed.username and not parsed.password
            and parsed.path == '/api/public/files/access', 'Unexpected signed file host')
    return parsed


def r2_prefix(state, meta):
    prefix = str(state['properties'].get('tcm.r2.object-prefix', 'clinic-files/')).replace('\\', '/').strip()
    if prefix.startswith('/'):
        prefix = prefix[1:]
    if prefix and not prefix.endswith('/'):
        prefix += '/'
    require(not prefix.startswith('/') and '..' not in prefix.split('/'), 'Unexpected R2 prefix')
    return prefix, prefix + 'hospital-private/' + meta['name'] + '/'


def r2_call(state, method, key='', params=None):
    props = state['properties']
    endpoint = urllib.parse.urlsplit(props['tcm.r2.endpoint'])
    require(endpoint.scheme == 'https' and endpoint.hostname and endpoint.hostname.endswith('.r2.cloudflarestorage.com')
            and endpoint.port in (None, 443) and not endpoint.username and not endpoint.password
            and endpoint.path in ('', '/') and not endpoint.query, 'Unexpected R2 endpoint')
    path = '/' + urllib.parse.quote(props['tcm.r2.bucket'], safe='') + ('/' + urllib.parse.quote(key, safe='/') if key else '')
    query = urllib.parse.urlencode(sorted((params or {}).items()), quote_via=urllib.parse.quote)
    now = datetime.datetime.now(datetime.timezone.utc)
    day, stamp = now.strftime('%Y%m%d'), now.strftime('%Y%m%dT%H%M%SZ')
    empty = hashlib.sha256(b'').hexdigest()
    scope = day + '/auto/s3/aws4_request'
    canonical = method + '\n' + path + '\n' + query + '\nhost:' + endpoint.netloc + '\nx-amz-content-sha256:' + empty + '\nx-amz-date:' + stamp + '\n\nhost;x-amz-content-sha256;x-amz-date\n' + empty
    to_sign = 'AWS4-HMAC-SHA256\n' + stamp + '\n' + scope + '\n' + hashlib.sha256(canonical.encode()).hexdigest()
    secret = ('AWS4' + props['tcm.r2.secret-access-key']).encode()
    for part in (day, 'auto', 's3', 'aws4_request'):
        secret = hmac.new(secret, part.encode(), hashlib.sha256).digest()
    signature = hmac.new(secret, to_sign.encode(), hashlib.sha256).hexdigest()
    headers = {'x-amz-date': stamp, 'x-amz-content-sha256': empty,
               'Authorization': 'AWS4-HMAC-SHA256 Credential=' + props['tcm.r2.access-key-id'] + '/' + scope + ', SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=' + signature}
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    with opener.open(urllib.request.Request(endpoint.scheme + '://' + endpoint.netloc + path + ('?' + query if query else ''),
                                           method=method, headers=headers), timeout=30) as response:
        return response.read(64 * 1024)


def cloud_keys(state, meta):
    prefix, scoped = r2_prefix(state, meta)
    root = ET.fromstring(r2_call(state, 'GET', params={'list-type': '2', 'prefix': scoped, 'max-keys': '3'}))
    require(root.findtext('{*}IsTruncated', 'false') == 'false', 'Too many QA cloud objects')
    keys = [node.text for node in root.findall('.//{*}Contents/{*}Key')]
    require(len(keys) <= 2, 'Too many QA cloud objects')
    for key in keys:
        require(key.startswith(prefix), 'Unexpected cloud key prefix')
        validate_resource(meta, key[len(prefix):])
    return keys


def local_files(state, meta):
    profile = pathlib.Path(state['properties']['ruoyi.profile'])
    require(profile.is_absolute(), 'Unexpected storage root')
    root = profile.parent / 'hospital-private'
    folder = root / meta['name']
    require(folder.resolve() == folder and folder.parent == root, 'Unsafe QA file folder')
    files = []
    if folder.exists():
        for path in folder.rglob('*'):
            require(not path.is_symlink() and (path.is_file() or path.is_dir()), 'Unsafe QA file entry')
            if path.is_file():
                validate_resource(meta, 'hospital-private/' + path.relative_to(root).as_posix())
                files.append(path)
        require(len(files) <= 2, 'Too many QA local files')
    return folder, files


def redis_target(state):
    props = state['properties']
    host = str(props.get('spring.redis.host', 'localhost'))
    require(host in ('127.0.0.1', 'localhost', '::1'), 'Unexpected Redis host')
    port, database = str(props.get('spring.redis.port', '6379')), str(props.get('spring.redis.database', '0'))
    require(port.isdigit() and database.isdigit(), 'Unexpected Redis target')
    env = dict(os.environ, REDISCLI_AUTH=str(props.get('spring.redis.password') or ''))
    return ['redis-cli', '--raw', '-h', host, '-p', port, '-n', database], env


def redis_session(state, meta, action):
    session_id = meta.get('sessionId')
    require(re.fullmatch(r'[0-9a-f-]{32,36}', session_id or ''), 'Invalid QA session identity')
    command, env = redis_target(state)
    key = 'login_tokens:' + session_id
    existing = promotion.run(command + ['GET', key], env=env)
    if not existing:
        return True
    require(re.search(r'"userName"\s*:\s*"' + re.escape(meta['username']) + r'"', existing), 'Refusing another account session')
    promotion.run(command + (['EXPIRE', key, '300'] if action == 'expire' else ['DEL', key]), env=env)
    return action == 'expire' or promotion.run(command + ['EXISTS', key], env=env) == '0'


def remove_qa_sessions(state, meta):
    # Search and compare inside Redis: other users' cached identity data never
    # leaves Redis. Covers login response loss before the session UUID is saved.
    script = """local cursor='0'; local loops=0; local removed=0
repeat
  local result=redis.call('SCAN',cursor,'MATCH','login_tokens:*','COUNT',100)
  cursor=result[1]; loops=loops+1
  for _,key in ipairs(result[2]) do
    local raw=redis.call('GET',key)
    if raw then
      if raw:match('"userName"%s*:%s*"'..ARGV[1]..'"') then
        removed=removed+redis.call('DEL',key)
      end
    end
  end
until cursor=='0' or loops>=200
return {cursor,removed}"""
    command, env = redis_target(state)
    result = promotion.run(command + ['EVAL', script, '0', meta['username']], env=env).splitlines()
    require(len(result) == 2 and result[0] == '0' and result[1].isdigit(), 'QA session scan incomplete')
    return int(result[1])


def qa_appointment_counts(state):
    audit = "(SELECT user_id,mod_type,action,CASE WHEN JSON_VALID(changes) THEN changes ELSE '{}' END j FROM tcm_consultation_mod) mods"
    sql = "SELECT COUNT(*) FROM tcm_appointment WHERE practitioner_id IN (SELECT DISTINCT user_id FROM " + audit
    sql += " WHERE mod_type='user' AND action='LOGIN' AND JSON_UNQUOTE(JSON_EXTRACT(j,'$.targetName'))='OTCM upload QA')"
    linked = promotion.query(state, sql)
    orphan = promotion.query(state, "SELECT COUNT(*) FROM tcm_appointment a LEFT JOIN sys_user u ON CAST(u.user_id AS CHAR)=a.practitioner_id "
                             "WHERE u.user_id IS NULL AND COALESCE(a.practitioner_id,'')<>'' AND a.create_time>=DATE_SUB(NOW(),INTERVAL 1 DAY)")
    require(linked.isdigit() and orphan.isdigit(), 'Unexpected appointment aggregate')
    return {'appointmentsReferencingQaLoginActors': int(linked), 'appointmentsWithMissingPractitionerCreatedLast24h': int(orphan),
            'appointmentsModified': 0}


def cleanup(state, meta):
    f = fixture(meta['qaId'])
    require(all(meta.get(key) == value for key, value in f.items()), 'QA cleanup identity mismatch')
    if not meta.get('creationAttempted'):
        return {'completed': True, 'notCreated': True}
    u, p, c, name, marker = [literal(value) for value in (meta['username'], meta['patientId'], meta['consultationId'], meta['name'], meta['qaId'])]
    # Disable the throwaway account first, even if cloud cleanup later needs retry.
    mutate(state, 'UPDATE sys_user SET status=1 WHERE user_name=' + u + ' AND remark=' + marker)
    session_removed = remove_qa_sessions(state, meta)
    patient = promotion.query(state, 'SELECT COUNT(*) FROM tcm_patient WHERE id=' + p + ' AND name<>' + name)
    consultation = promotion.query(state, 'SELECT COUNT(*) FROM tcm_consultation WHERE id=' + c + ' AND patient_id<>' + p)
    require(patient == '0' and consultation == '0', 'QA ownership changed; cleanup stopped')
    rows = promotion.query(state, 'SELECT file_path FROM tcm_patient_file WHERE patient_id=' + p + ' OR consultation_id=' + c).splitlines()
    require(len(rows) <= 2, 'Too many QA database files')
    for resource in rows:
        validate_resource(meta, resource)
    keys = []
    cloud_error = None
    try:
        keys = cloud_keys(state, meta) if release.r2_configuration(state['properties'])['backupEffective'] else []
        for key in keys:
            r2_call(state, 'DELETE', key)
        require(not cloud_keys(state, meta) if release.r2_configuration(state['properties'])['backupEffective'] else True, 'QA cloud cleanup incomplete')
    except Exception as error:
        cloud_error = type(error).__name__
    folder, files = local_files(state, meta)
    for path in files:
        path.unlink()
    if folder.exists():
        for path in sorted(folder.rglob('*'), key=lambda path: len(path.parts), reverse=True):
            path.rmdir()
        folder.rmdir()
    mutate(state, 'DELETE FROM tcm_patient_file WHERE patient_id=' + p + ' AND consultation_id=' + c
           + '; DELETE FROM tcm_consultation WHERE id=' + c + ' AND patient_id=' + p
           + '; DELETE FROM tcm_patient WHERE id=' + p + ' AND name=' + name
           + '; DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id WHERE u.user_name=' + u + ' AND u.remark=' + marker
           + '; DELETE FROM sys_user WHERE user_name=' + u + ' AND remark=' + marker)
    remaining = promotion.query(state, 'SELECT (SELECT COUNT(*) FROM sys_user WHERE user_name=' + u + ')+'
        + '(SELECT COUNT(*) FROM tcm_patient WHERE id=' + p + ')+(SELECT COUNT(*) FROM tcm_consultation WHERE id=' + c
        + ')+(SELECT COUNT(*) FROM tcm_patient_file WHERE patient_id=' + p + ' OR consultation_id=' + c + ')')
    require(remaining == '0', 'QA fixture cleanup incomplete')
    require(remove_qa_sessions(state, meta) == 0, 'QA sessions reappeared during cleanup')
    return {'completed': cloud_error is None, 'remainingFixtures': 0, 'sessionRemoved': True, 'sessionsRemoved': session_removed,
            'remainingQaSessions': 0,
            'cloudObjectsRemoved': len(keys) if cloud_error is None else None, 'cloudCleanupError': cloud_error,
            'localFilesRemoved': len(files), 'auditEventsRetained': True}


def password_hash(state, directory, password):
    bytecode = base64.b64decode(REQUEST['passwordClass'], validate=True)
    require(len(bytecode) < 8192 and bytecode.startswith(b'\xca\xfe\xba\xbe'), 'Invalid QA password helper')
    (directory / 'OtcmQaPassword.class').write_bytes(bytecode)
    with zipfile.ZipFile(state['jar']) as archive:
        names = [name for name in archive.namelist() if re.fullmatch(r'BOOT-INF/lib/spring-security-crypto-[^/]+\.jar', name)]
        require(len(names) == 1, 'Missing deployed password encoder')
        (directory / 'crypto.jar').write_bytes(archive.read(names[0]))
    result = subprocess.run([state['args'][0], '-XX:-UsePerfData', '-cp', str(directory) + os.pathsep + str(directory / 'crypto.jar'), 'OtcmQaPassword'],
                            input=(password + '\n').encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15)
    hashed = result.stdout.decode()
    require(result.returncode == 0 and re.fullmatch(r'\$2[aby]\$10\$[./A-Za-z0-9]{53}', hashed), 'QA password encoder failed')
    return hashed


def run_acceptance(state, meta, directory):
    u, p, c, name, marker = [literal(value) for value in (meta['username'], meta['patientId'], meta['consultationId'], meta['name'], meta['qaId'])]
    existing = promotion.query(state, 'SELECT (SELECT COUNT(*) FROM sys_user WHERE user_name=' + u + ')+'
        + '(SELECT COUNT(*) FROM tcm_patient WHERE id=' + p + ')+(SELECT COUNT(*) FROM tcm_consultation WHERE id=' + c + ')')
    require(existing == '0' and not local_files(state, meta)[1], 'QA identity already exists')
    require(not cloud_keys(state, meta) if release.r2_configuration(state['properties'])['backupEffective'] else True, 'QA cloud prefix already exists')
    # A practitioner would be advertised by public booking while the fixture
    # exists. An admin-only account is excluded by buildPractitioners(); an
    # apprentice cannot exercise the existing file DELETE endpoint.
    role = promotion.query(state, "SELECT role_id FROM sys_role WHERE role_key='admin' AND status='0' AND del_flag='0'")
    require(role.isdigit(), 'Expected one active admin role')
    public_practitioners = api('/api/public-booking/options')['practitioners']
    password = secrets.token_urlsafe(36)
    hashed = literal(password_hash(state, directory, password))
    payload = literal(json.dumps({'qaUploadFixture': meta['qaId']}))
    token = None
    result = {'ok': False, 'tests': [], 'messagesSent': 0, 'appointmentsCreated': 0, 'realPatientRecordsTouched': 0}
    try:
        result['stage'] = 'create-fixtures'
        meta['creationAttempted'] = True
        private_write(directory / 'fixture.json', meta)
        mutate(state, 'INSERT INTO sys_user(user_name,nick_name,email,password,status,del_flag,remark,create_by,create_time) VALUES ('
            + u + ", 'OTCM upload QA', " + literal(meta['username'] + '@example.invalid') + ',' + hashed + ", '0','0'," + marker + ", 'upload-qa',NOW())"
            + '; SET @qauser=LAST_INSERT_ID(); INSERT INTO sys_user_role(user_id,role_id) VALUES (@qauser,' + role + ')'
            + '; INSERT INTO tcm_patient(id,name,email,is_active,payload,practitioner_id) VALUES (' + p + ',' + name + ",'',1," + payload + ',@qauser)'
            + '; INSERT INTO tcm_consultation(id,consultation_id,patient_id,consult_date,status,payload,practitioner_id) VALUES (' + c + ',' + c + ',' + p + ",'2026-10-30','draft'," + payload + ',@qauser)')
        require(api('/api/public-booking/options')['practitioners'] == public_practitioners, 'QA account changed public booking options')
        result['publicBookingPractitionersUnchanged'] = True
        result['stage'] = 'login'
        token = api('/api/auth/login', method='POST', body={'email': meta['username'], 'password': password})['token']
        result['stage'] = 'decode-login-token'
        claims = json.loads(base64.urlsafe_b64decode(token.split('.')[1] + '==='))
        require(claims.get('sub') == meta['username'], 'Unexpected QA login identity')
        meta['sessionId'] = claims['login_user_key']
        private_write(directory / 'fixture.json', meta)
        result['stage'] = 'expire-qa-session'
        redis_session(state, meta, 'expire')
        for filename, content in [('qa-small.png', png(16, 16)), ('验收图片.png', png(1200, 1200))]:
            result['stage'] = 'upload-small' if filename == 'qa-small.png' else 'upload-large-unicode'
            body, content_type = multipart(meta, filename, content)
            started = time.monotonic()
            uploaded = api('/api/files/upload', token, 'POST', body, content_type)
            duration = time.monotonic() - started
            result.setdefault('uploads', []).append({'bytes': len(content), 'uploadSeconds': round(duration, 3),
                'unicodeFilename': filename != 'qa-small.png', 'signedUrl': signed_url_metadata(uploaded.get('url'))})
            require(uploaded.get('patientId') == meta['patientId'] and uploaded.get('consultationId') == meta['consultationId'], 'Upload linked to wrong fixture')
            resource = validate_resource(meta, uploaded.get('resource'))
            file_id = str(uploaded.get('id'))
            require(file_id.isdigit() and uploaded.get('fileName') == filename, 'Uploaded file metadata mismatch')
            parsed = validate_signed_url(uploaded['url'])
            result['stage'] = 'download-upload'
            downloaded = api(parsed.path + ('?' + parsed.query if parsed.query else ''), token, binary=True)
            require(downloaded == content, 'Downloaded upload bytes differ')
            result['stage'] = 'list-consultation-files'
            listing = api('/api/files/consultation/' + meta['consultationId'], token)
            require(any(str(item.get('id')) == file_id and item.get('fileName') == filename for item in listing), 'Upload absent from consultation list')
            result['stage'] = 'delete-file-api'
            api('/api/files/' + file_id, token, 'DELETE')
            result['tests'].append({'bytes': len(content), 'unicodeFilename': filename != 'qa-small.png', 'uploadSeconds': round(duration, 3),
                                    'upload': True, 'downloadBytesMatch': True, 'consultationListing': True, 'apiDelete': True})
        require(api('/api/files/consultation/' + meta['consultationId'], token) == [], 'Deleted QA files still listed')
        result['ok'] = True
        result['stage'] = 'complete'
    except Exception as error:
        result['errorType'] = type(error).__name__
        if isinstance(error, release.Guard):
            result['guard'] = str(error)
        if isinstance(error, urllib.error.HTTPError):
            result['httpStatus'] = error.code
        if isinstance(error, ApiResponseError):
            result['apiResponse'] = error.metadata
        # The independent 10-minute cleanup remains armed for any late server writes.
        time.sleep(5)
    finally:
        if token:
            try:
                api('/logout', token, 'POST')
            except Exception:
                pass
        try:
            result['cleanup'] = cleanup(state, meta)
            result['ok'] = result['ok'] and result['cleanup']['completed']
        except Exception as error:
            result['ok'] = False
            result['cleanup'] = {'completed': False, 'errorType': type(error).__name__, 'watchdogArmed': True}
    return result


def main():
    os.umask(0o077)
    try:
        require(REQUEST.get('target') == '43.166.154.98' and os.geteuid() == 0, 'Invalid QA target')
        meta = fixture(REQUEST.get('qaId'))
        directory = ROOT / meta['qaId']
        require(ROOT.resolve() == ROOT and directory.resolve() == directory, 'Unsafe QA manifest root')
        state = promotion.runtime()
        require(state['cwd'] == promotion.APP and state['jar'] == promotion.APP / 'yy.jar', 'Unexpected QA service paths')
        if len(sys.argv) == 2 and sys.argv[1] == 'cleanup':
            meta = json.loads((directory / 'fixture.json').read_text())
            cleaned = cleanup(state, meta)
            result = {'ok': cleaned['completed'], 'cleanup': cleaned}
            result['appointments'] = qa_appointment_counts(state)
            private_write(directory / 'watchdog-result.json', result)
        else:
            require(promotion.digest(state['jar']) == REQUEST['expectedBackendSha256'], 'QA backend revision changed')
            require(json.loads((promotion.WEB / 'version.json').read_text()) == {'version': REQUEST['expectedFrontendRevision']}, 'QA frontend revision changed')
            ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
            require(ROOT.stat().st_uid == 0 and stat.S_IMODE(ROOT.stat().st_mode) == 0o700, 'QA manifest root must be root-private')
            directory.mkdir(mode=0o700)
            private_write(directory / 'fixture.json', meta)
            source = base64.b64decode(REQUEST['cleanupSource'], validate=True)
            session.atomic_write(directory / 'cleanup.py', source)
            promotion.run(['systemd-run', '--quiet', '--unit=otcm-upload-qa-' + meta['qaId'], '--on-active=10m',
                           '--property=Restart=on-failure', '--property=RestartSec=60s', '--property=StartLimitIntervalSec=1h', '--property=StartLimitBurst=3',
                           '--timer-property=AccuracySec=1s', '/usr/bin/python3', '-B', str(directory / 'cleanup.py'), 'cleanup'])
            result = run_acceptance(state, meta, directory)
            result['qaId'] = meta['qaId']
            result['cleanupWatchdogArmed'] = True
            private_write(directory / 'result.json', result)
        print(json.dumps(result, indent=2))
        return 0 if result['ok'] else 1
    except Exception as error:
        result = {'ok': False, 'errorType': type(error).__name__}
        if isinstance(error, release.Guard):
            result['guard'] = str(error)
        print(json.dumps(result))
        return 1


if __name__ == '__main__':
    sys.exit(main())
