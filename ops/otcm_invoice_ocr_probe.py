#!/usr/bin/env python3
"""One synthetic OCR preview using deployed classes; no business writes or Spring startup."""
import base64
import datetime
import io
import ipaddress
import json
import os
import pathlib
import re
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import urllib.parse
import zipfile

import legacy_integration_diagnostics as diagnostics
import otcm_backend_release as release
import otcm_promotion as promotion

REQUEST = {}
RUNTIME_KEYS = frozenset({'deepseek.api-key', 'deepseek.model', 'deepseek.endpoint'})
SETTING_KEYS = frozenset({'deepseekApiKey', 'deepseekModel'})
CLASSES = frozenset({'OtcmInvoiceOcrProbe.class', 'OtcmInvoiceOcrProbe$SilentLoggingConfiguration.class'})
READ_SETTINGS_SQL = ("SELECT CONCAT(HEX(setting_key),':',COALESCE(HEX(setting_value),'NULL')) "
                     "FROM tcm_clinic_setting WHERE setting_key IN ('deepseekApiKey','deepseekModel') LIMIT 3")
BOOL_KEYS = frozenset({'success', 'rejectedAccess', 'supplierMatched', 'itemMatched', 'quantityMatched',
                       'priceMatched', 'currencyMatched'})
RESULT_KEYS = BOOL_KEYS | {'code', 'providerRequests', 'providerStatus', 'memoryPreviewWrites'}
CODES = frozenset({'OCR_VERIFIED', 'OCR_RESULT_MISMATCH', 'OCR_PROBE_INPUT_INVALID', 'OCR_PROBE_UNSAFE_CLASSPATH',
    'OCR_ENDPOINT_UNSUPPORTED', 'OCR_PROBE_RUNTIME_UNAVAILABLE', 'OCR_NOT_CONFIGURED', 'OCR_RECOGNITION_FAILED',
    'OCR_PROBE_ACCESS_REJECTED', 'OCR_PROVIDER_REJECTED', 'OCR_PROVIDER_TIMEOUT', 'OCR_PROVIDER_TLS_FAILED',
    'OCR_PROVIDER_CONNECT_FAILED', 'OCR_PROVIDER_CONNECTION_CLOSED', 'OCR_PROVIDER_IO_FAILED',
    'OCR_RESPONSE_TOO_LARGE', 'OCR_EMPTY_RESULT', 'OCR_INVALID_RESPONSE'})


class Guard(ValueError):
    pass


class DeadlineExceeded(TimeoutError):
    pass


def require(condition):
    if not condition:
        raise Guard('OCR_PROBE_GUARD')


def validate_request(request):
    require(isinstance(request, dict) and set(request) ==
            {'target', 'expectedBackendSha256', 'expectedFrontendRevision', 'probeJarBase64'})
    require(request['target'] == '43.166.154.98')
    for key, size in (('expectedBackendSha256', 64), ('expectedFrontendRevision', 40)):
        require(isinstance(request[key], str) and re.fullmatch('[0-9a-f]{%d}' % size, request[key]))
    encoded = request['probeJarBase64']
    require(isinstance(encoded, str) and 0 < len(encoded) < 512 * 1024)
    bytecode = base64.b64decode(encoded, validate=True)
    with zipfile.ZipFile(io.BytesIO(bytecode)) as archive:
        require(set(archive.namelist()) == CLASSES and len(archive.namelist()) == len(CLASSES))
        require(sum(info.file_size for info in archive.infolist()) < 256 * 1024)
        require(all(archive.read(name).startswith(b'\xca\xfe\xba\xbe') for name in CLASSES))
    return bytecode


def parse_settings(raw):
    require(isinstance(raw, str) and len(raw) <= 32768)
    rows = raw.splitlines()
    require(len(rows) <= 2)
    settings = {}
    seen = set()
    for row in rows:
        require(re.fullmatch(r'[0-9a-fA-F]+:(?:NULL|[0-9a-fA-F]*)', row))
        key_hex, value_hex = row.split(':')
        key = bytes.fromhex(key_hex).decode('utf-8')
        require(key in SETTING_KEYS and key not in seen)
        seen.add(key)
        if value_hex != 'NULL':
            value = bytes.fromhex(value_hex).decode('utf-8')
            require(len(value) <= 4096 and '\0' not in value and '${' not in value)
            settings[key] = value
    return settings


def validate_endpoint(value):
    """Allow the configured public HTTPS proxy, not user input or private services."""
    require(isinstance(value, str) and value == value.strip() and value)
    endpoint = value
    parsed = urllib.parse.urlsplit(endpoint)
    host = parsed.hostname or ''
    require(parsed.scheme == 'https' and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}', host))
    require(not host.lower().endswith(('.localhost', '.local', '.internal', '.invalid')))
    require(parsed.port in (None, 443) and parsed.username is None and parsed.password is None)
    require(not parsed.query and not parsed.fragment and re.fullmatch(r'/(?:[A-Za-z0-9_-]+/)*chat/completions', parsed.path))
    addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    require(addresses and all(ipaddress.ip_address(address[4][0]).is_global for address in addresses))
    return endpoint


def validate_result(raw):
    require(isinstance(raw, bytes) and len(raw) <= 4096)
    result = json.loads(raw)
    require(isinstance(result, dict) and set(result) == RESULT_KEYS)
    require(all(type(result[key]) is bool for key in BOOL_KEYS))
    require(result['code'] in CODES and result['success'] == (result['code'] == 'OCR_VERIFIED'))
    for key, maximum in (('providerRequests', 1), ('providerStatus', 599), ('memoryPreviewWrites', 1)):
        require(type(result[key]) is int and 0 <= result[key] <= maximum)
    require(result['providerStatus'] == 0 or (result['providerRequests'] == 1 and result['providerStatus'] >= 100))
    if result['success']:
        require(result['providerRequests'] == result['memoryPreviewWrites'] == 1 and result['providerStatus'] == 200)
        require(not result['rejectedAccess'] and all(result[key] for key in BOOL_KEYS - {'rejectedAccess'}))
    return result


def run_probe(state, bytecode, runtime, stored, temporary, timeout):
    probe = temporary / 'ocr-probe'
    probe.mkdir(mode=0o700)
    jar = probe / 'ocr-probe.jar'
    jar.write_bytes(bytecode)
    library = probe / 'lib'
    library.mkdir(mode=0o700)
    with zipfile.ZipFile(state['jar']) as archive:
        for info in archive.infolist():
            if not re.fullmatch(r'BOOT-INF/lib/[^/]+\.jar', info.filename):
                continue
            filename = pathlib.PurePosixPath(info.filename).name
            if filename.startswith(('logback-', 'log4j-')) or (filename.startswith('slf4j-') and not filename.startswith('slf4j-api-')):
                continue
            require(info.file_size < 150 * 1024 * 1024)
            with archive.open(info) as source, (library / filename).open('xb') as destination:
                shutil.copyfileobj(source, destination)
    command = [state['args'][0], '-XX:-UsePerfData', '-XX:+DisableAttachMechanism', '-Djava.awt.headless=true',
        '-Duser.home=' + str(probe), '-Djava.io.tmpdir=' + str(probe), '-cp',
        str(jar) + os.pathsep + str(library / '*'), 'OtcmInvoiceOcrProbe']
    payload = json.dumps({'runtimeProperties': runtime, 'storedSettings': stored}).encode('utf-8')
    require(len(payload) <= 65536 and 0 < timeout <= 180)
    # No credentials in argv, environment, files, output, or exception strings. No retries.
    result = subprocess.run(command, input=payload, cwd=probe, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'HOME': str(probe)})
    require(result.returncode == 0)
    return validate_result(result.stdout)


def _deadline(signum, frame):
    raise DeadlineExceeded('OCR_PROBE_TIMEOUT')


def main():
    started = time.monotonic()
    report = {'success': False, 'code': 'OCR_PROBE_SETUP_FAILED', 'target': '43.166.154.98',
              'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'databaseWrites': 0, 'inventoryWrites': 0, 'messagesSent': 0, 'accountsCreated': 0,
              'requestLimitSeconds': 90, 'totalLimitSeconds': 180,
              'scope': 'One synthetic PNG through deployed recognition and effective AI configuration; configuration SELECT only; '
                       'preview insert intercepted in memory; all other mapper/service accesses rejected; no Spring, schedules or email.'}
    handler = signal.signal(signal.SIGALRM, _deadline)
    signal.setitimer(signal.ITIMER_REAL, 180)
    try:
        bytecode = validate_request(REQUEST)
        report['backendSha256'] = REQUEST['expectedBackendSha256']
        report['frontendRevision'] = REQUEST['expectedFrontendRevision']
        diagnostics.CONFIG_KEYS.update(RUNTIME_KEYS)
        state = release.validate_current(REQUEST)
        report['releaseMatched'] = True
        runtime = {key: state['properties'][key] for key in RUNTIME_KEYS if state['properties'].get(key) is not None}
        require(all(isinstance(value, str) and len(value) <= 4096 and '\0' not in value and '${' not in value
                    for value in runtime.values()))
        stored = parse_settings(promotion.query(state, READ_SETTINGS_SQL))
        try:
            validate_endpoint(runtime.get('deepseek.endpoint', 'https://api.deepseek.com/chat/completions'))
        except (Guard, ValueError):
            report['code'] = 'OCR_ENDPOINT_UNSUPPORTED'
            return 1
        except socket.gaierror:
            report['code'] = 'OCR_PROVIDER_CONNECT_FAILED'
            return 1
        with tempfile.TemporaryDirectory(prefix='otcm-ocr-probe-') as directory:
            result = run_probe(state, bytecode, runtime, stored, pathlib.Path(directory),
                               max(0.001, 180 - (time.monotonic() - started)))
        report.update(result)
        # Recheck the pinned release without starting/stopping the service or touching its data.
        after = release.validate_current(REQUEST)
        require(after['pid'] == state['pid'])
        report['releaseUnchanged'] = True
        return 0 if report['success'] else 1
    except (subprocess.TimeoutExpired, DeadlineExceeded):
        report['success'] = False
        report['code'] = 'OCR_PROBE_TIMEOUT'
        return 1
    except Exception:
        report['success'] = False
        report['code'] = 'OCR_PROBE_SETUP_FAILED'
        return 1
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, handler)
        report['elapsedSeconds'] = round(time.monotonic() - started, 3)
        print(json.dumps(report, sort_keys=True))


if __name__ == '__main__':
    raise SystemExit(main())
