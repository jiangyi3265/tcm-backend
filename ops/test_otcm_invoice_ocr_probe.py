"""Synthetic local files and mocked commands only; never call OCR, SSH or production."""
import base64
import contextlib
import io
import json
import pathlib
import shutil
import subprocess
import tempfile
import types
import unittest
import zipfile
from unittest.mock import patch

import otcm_invoice_ocr_probe as probe


def archive_bytes(names=None):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        for name in (probe.CLASSES if names is None else names):
            archive.writestr(name, b'\xca\xfe\xba\xbe' + b'synthetic')
    return stream.getvalue()


def request():
    return {'target': '43.166.154.98', 'expectedBackendSha256': 'a' * 64, 'expectedFrontendRevision': 'b' * 40,
            'probeJarBase64': base64.b64encode(archive_bytes()).decode()}


def result():
    return {'success': True, 'code': 'OCR_VERIFIED', 'providerRequests': 1, 'providerStatus': 200,
            'memoryPreviewWrites': 1, 'rejectedAccess': False, 'supplierMatched': True, 'itemMatched': True,
            'quantityMatched': True, 'priceMatched': True, 'currencyMatched': True}


class GuardTests(unittest.TestCase):
    def test_request_pins_host_and_artifacts(self):
        self.assertEqual(probe.validate_request(request()), archive_bytes())
        for key, value in [('target', '152.136.191.51'), ('expectedBackendSha256', 'master'),
                           ('expectedFrontendRevision', 'b' * 40 + ';'), ('mode', 'publish')]:
            with self.subTest(key=key), self.assertRaises(probe.Guard):
                probe.validate_request({**request(), key: value})

    def test_only_two_reviewed_probe_classes_may_be_packaged(self):
        for names in [['../escape.class'], list(probe.CLASSES) + ['application.yml'],
                      list(probe.CLASSES) + ['OtcmInvoiceOcrProbeTest.class']]:
            with self.subTest(names=names), self.assertRaises(probe.Guard):
                probe.validate_request({**request(), 'probeJarBase64': base64.b64encode(archive_bytes(names)).decode()})

    def test_settings_allow_only_config_keys_and_preserve_null_fallback(self):
        key = 'deepseekApiKey'.encode().hex()
        model = 'deepseekModel'.encode().hex()
        self.assertEqual(probe.parse_settings(key + ':' + 'synthetic-key'.encode().hex()), {'deepseekApiKey': 'synthetic-key'})
        self.assertEqual(probe.parse_settings(key + ':NULL\n' + model + ':'), {'deepseekModel': ''})
        for raw in [key + ':00', key + ':AA\n' + key + ':AA', 'smtpSettings'.encode().hex() + ':',
                    key + ':' + ('${SECRET}').encode().hex(), '\n'.join([key + ':NULL'] * 3)]:
            with self.subTest(raw=raw), self.assertRaises((probe.Guard, UnicodeError)):
                probe.parse_settings(raw)

    def test_public_configured_https_proxy_allowed_private_dns_rejected(self):
        public = [(2, 1, 6, '', ('8.8.8.8', 443))]
        private = [(2, 1, 6, '', ('127.0.0.1', 443))]
        with patch.object(probe.socket, 'getaddrinfo', return_value=public):
            self.assertEqual(probe.validate_endpoint('https://proxy.example.com/v1/chat/completions'),
                             'https://proxy.example.com/v1/chat/completions')
            for value in ['', ' https://proxy.example.com/chat/completions', 'http://proxy.example.com/chat/completions', 'https://user:secret@proxy.example.com/chat/completions',
                          'https://proxy.example.com/chat/completions?secret=x', 'https://host.local/chat/completions']:
                with self.subTest(value=value), self.assertRaises(probe.Guard):
                    probe.validate_endpoint(value)
        for answers in [private, public + private, []]:
            with patch.object(probe.socket, 'getaddrinfo', return_value=answers), self.assertRaises(probe.Guard):
                probe.validate_endpoint('https://proxy.example.com/chat/completions')

    def test_result_schema_rejects_private_values_and_inconsistent_success(self):
        self.assertTrue(probe.validate_result(json.dumps(result()).encode())['success'])
        for key, value in [('providerResponse', 'sensitive'), ('code', 'key=secret'), ('providerRequests', 2),
                           ('memoryPreviewWrites', 2), ('supplierMatched', False), ('rejectedAccess', True),
                           ('providerStatus', 401), ('success', 1)]:
            with self.subTest(key=key), self.assertRaises(probe.Guard):
                probe.validate_result(json.dumps({**result(), key: value}).encode())
        for code in probe.CODES - {'OCR_VERIFIED'}:
            self.assertFalse(probe.validate_result(json.dumps({**result(), 'success': False, 'code': code}).encode())['success'])


class ExecutionTests(unittest.TestCase):
    def test_credentials_stdin_only_and_no_application_resources_or_logging_bindings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            app = root / 'app.jar'
            with zipfile.ZipFile(app, 'w') as archive:
                for name in ['ruoyi-hospital.jar', 'spring-web.jar', 'slf4j-api-1.jar', 'slf4j-simple-1.jar', 'logback-classic-1.jar', 'log4j-core-1.jar']:
                    archive.writestr('BOOT-INF/lib/' + name, b'synthetic-library')
                archive.writestr('BOOT-INF/classes/logback.xml', 'unsafe logging')
                archive.writestr('BOOT-INF/classes/application.yml', 'secret')
            def command(argv, **kwargs):
                self.assertEqual(argv[-1], 'OtcmInvoiceOcrProbe')
                self.assertNotIn('synthetic-secret', ' '.join(argv))
                self.assertNotIn('synthetic-secret', json.dumps(kwargs['env']))
                self.assertEqual(json.loads(kwargs['input'])['storedSettings'], {'deepseekApiKey': 'synthetic-secret'})
                self.assertEqual(kwargs['timeout'], 90)
                files = {item.name for item in (root / 'ocr-probe' / 'lib').iterdir()}
                self.assertEqual(files, {'ruoyi-hospital.jar', 'spring-web.jar', 'slf4j-api-1.jar'})
                self.assertEqual({item.name for item in (root / 'ocr-probe').iterdir()}, {'lib', 'ocr-probe.jar'})
                return types.SimpleNamespace(returncode=0, stdout=json.dumps(result()).encode(), stderr=b'sensitive stderr never returned')
            with patch.object(probe.subprocess, 'run', side_effect=command) as run:
                data = probe.run_probe({'jar': app, 'args': ['/usr/bin/java']}, archive_bytes(), {},
                                       {'deepseekApiKey': 'synthetic-secret'}, root, 90)
            self.assertTrue(data['success'])
            self.assertEqual(run.call_count, 1)

    def test_timeout_and_private_error_output_are_not_exposed(self):
        # main is Linux-only; signal callbacks are mocked to test it safely on Windows too.
        for failure, code in [(subprocess.TimeoutExpired(['sensitive'], 90), 'OCR_PROBE_TIMEOUT'),
                              (RuntimeError('private-key'), 'OCR_PROBE_SETUP_FAILED')]:
            output = io.StringIO()
            with contextlib.ExitStack() as stack:
                stack.enter_context(patch.object(probe, 'REQUEST', request()))
                stack.enter_context(patch.object(probe.signal, 'SIGALRM', 14, create=True))
                stack.enter_context(patch.object(probe.signal, 'ITIMER_REAL', 0, create=True))
                stack.enter_context(patch.object(probe.signal, 'signal', return_value=None))
                stack.enter_context(patch.object(probe.signal, 'setitimer', create=True))
                stack.enter_context(patch.object(probe.release, 'validate_current', side_effect=failure))
                stack.enter_context(contextlib.redirect_stdout(output))
                self.assertEqual(probe.main(), 1)
            report = json.loads(output.getvalue())
            self.assertEqual(report['code'], code)
            self.assertEqual(report['databaseWrites'], 0)
            self.assertNotIn('sensitive', output.getvalue())
            self.assertNotIn('private-key', output.getvalue())

    def test_fixed_sql_is_read_only_and_contains_only_two_ai_keys(self):
        self.assertTrue(probe.READ_SETTINGS_SQL.startswith('SELECT '))
        self.assertIn("IN ('deepseekApiKey','deepseekModel')", probe.READ_SETTINGS_SQL)
        self.assertNotIn('UPDATE', probe.READ_SETTINGS_SQL)
        self.assertNotIn('INSERT', probe.READ_SETTINGS_SQL)


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK 17 required')
class JavaGuardTests(unittest.TestCase):
    def test_compiled_mapper_guards_fixture_and_error_classification(self):
        ops = pathlib.Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as directory:
            compiled = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', directory,
                str(ops / 'OtcmInvoiceOcrProbe.java'), str(ops / 'OtcmInvoiceOcrProbeTest.java')], capture_output=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr.decode(errors='replace'))
            executed = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', directory, 'OtcmInvoiceOcrProbeTest'],
                                      capture_output=True, timeout=30)
            self.assertEqual(executed.returncode, 0, executed.stderr.decode(errors='replace'))
            self.assertEqual(executed.stdout.decode().strip(), 'OCR_PROBE_OFFLINE_GUARDS_PASSED')

    def test_packaged_recognition_and_probe_observer_with_in_memory_http(self):
        ops = pathlib.Path(__file__).resolve().parent
        application = ops.parent / 'ruoyi-admin' / 'target' / 'ruoyi-admin.jar'
        if not application.is_file():
            self.skipTest('Optional local packaged application unavailable; production is never downloaded for this test')
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            library = root / 'lib'
            library.mkdir()
            with zipfile.ZipFile(application) as archive:
                for name in archive.namelist():
                    if not name.startswith('BOOT-INF/lib/') or not name.endswith('.jar'):
                        continue
                    filename = pathlib.PurePosixPath(name).name
                    if filename.startswith(('logback-', 'log4j-')) or (filename.startswith('slf4j-') and not filename.startswith('slf4j-api-')):
                        continue
                    (library / filename).write_bytes(archive.read(name))
            compiled = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', directory,
                str(ops / 'OtcmInvoiceOcrProbe.java'), str(ops / 'OtcmInvoiceOcrRuntimeTest.java')], capture_output=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr.decode(errors='replace'))
            import os
            executed = subprocess.run(['java', '-Djava.awt.headless=true', '-Djava.io.tmpdir=' + directory,
                '-Duser.home=' + directory, '-cp', directory + os.pathsep + str(library / '*'), 'OtcmInvoiceOcrRuntimeTest'],
                cwd=root, capture_output=True, timeout=30)
            self.assertEqual(executed.returncode, 0, executed.stderr.decode(errors='replace'))
            self.assertEqual(executed.stdout.decode().strip(), 'OCR_PROBE_PACKAGED_RUNTIME_MOCK_HTTP_PASSED')


if __name__ == '__main__':
    unittest.main()
