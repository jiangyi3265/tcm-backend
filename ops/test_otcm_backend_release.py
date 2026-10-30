"""Synthetic local files and mocked services only; no network or production data."""
import copy
import json
import os
import pathlib
import shutil
import tempfile
import unittest
from contextlib import ExitStack
from unittest.mock import patch

import otcm_backend_release as release


class RequestTests(unittest.TestCase):
    def request(self):
        return {'target': '43.166.154.98', 'mode': 'inspect', 'expectedBackendSha256': 'a' * 64,
                'expectedFrontendRevision': 'b' * 40}

    def test_inspection_requires_no_artifact(self):
        release.validate_request(self.request())

    def test_wrong_targets_and_identities_rejected(self):
        for field, value in [('target', '152.136.191.51'), ('mode', 'apply'), ('expectedBackendSha256', 'abc'),
                             ('expectedFrontendRevision', 'b' * 40 + '; echo x')]:
            with self.subTest(field=field), self.assertRaises(release.Guard):
                release.validate_request({**self.request(), field: value})

    def test_publish_requires_pinned_artifact_identity_and_trusted_url(self):
        request = {**self.request(), 'mode': 'publish', 'revision': 'c' * 40, 'sha256': 'd' * 64,
                   'artifactUrl': 'https://example.blob.core.windows.net/release.zip?signature=synthetic'}
        release.validate_request(request)
        for field, value in [('sha256', 'a' * 64), ('revision', 'master'), ('artifactUrl', 'http://example.blob.core.windows.net/a'),
                             ('artifactUrl', 'https://example.blob.core.windows.net.evil.test/a'),
                             ('artifactUrl', 'https://user:pass@example.blob.core.windows.net/a'),
                             ('artifactUrl', 'https://example.blob.core.windows.net:444/a')]:
            with self.subTest(field=field, value=value), self.assertRaises(release.Guard):
                release.validate_request({**request, field: value})


class DiagnosticsTests(unittest.TestCase):
    def test_upload_logs_return_only_aggregates(self):
        text = ('private-patient-address "POST /api/files/upload HTTP/1.1" 413 0\n'
                'private-email "POST /api/files/upload?secret=private HTTP/2.0" 200 8\n'
                'private "GET /api/files/upload HTTP/1.1" 401 0\n'
                'private "POST /another/upload HTTP/1.1" 500 0\n'
                'client intended to send too large body, request: "POST /api/files/upload HTTP/1.1"\n')
        report = release.upload_log_summary(text)
        self.assertEqual(report['httpStatusCounts'], {'413': 1, '200': 1})
        self.assertEqual(report['errorCounts']['bodyTooLarge'], 1)
        self.assertNotIn('private', json.dumps(report))

    def test_patient_query_is_read_only_and_returns_no_identifiers(self):
        with patch.object(release.promotion, 'query', side_effect=['0\tid\n1\temail', '2\t6', '3', '8\t4']) as query:
            report = release.patient_diagnostics({})
        self.assertEqual(report['indexes'], [{'unique': True, 'columns': ['id']}, {'unique': False, 'columns': ['email']}])
        self.assertEqual(report['patientsInSharedEmailGroups'], 6)
        self.assertEqual(report['patientsWithMultiplePublicBookingNames'], 3)
        for call in query.call_args_list:
            self.assertTrue(call.args[1].startswith('SELECT '))


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.root = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory())).resolve()
        self.app = self.root / 'app'
        self.app.mkdir()
        self.jar = self.app / 'yy.jar'
        self.jar.write_bytes(b'old synthetic backend')
        self.new = self.root / 'incoming.jar'
        self.new.write_bytes(b'new synthetic backend')
        self.preserved = self.app / 'preserved.properties'
        self.preserved.write_bytes(b'token.secret=synthetic\ntoken.expireTime=1440\n')
        self.backups = self.root / 'backups'
        self.web = self.root / 'web'
        self.web.mkdir()
        (self.web / 'version.json').write_text(json.dumps({'version': 'b' * 40}))
        self.request = {'mode': 'publish', 'revision': 'c' * 40, 'sha256': release.promotion.digest(self.new),
                        'expectedBackendSha256': release.promotion.digest(self.jar), 'expectedFrontendRevision': 'b' * 40}
        self.state = {'jar': self.jar, 'cwd': self.app, 'pid': 123, 'args': ['java', '-jar', 'yy.jar'], 'environment': {},
                      'properties': {'token.expireTime': '1440', 'token.secret': 'synthetic'}}
        for obj, field, value in [(release, 'BACKUPS', self.backups), (release, 'WEB', self.web),
                                  (release.session, 'PRESERVED', self.preserved)]:
            self.stack.enter_context(patch.object(obj, field, value))
        self.stack.enter_context(patch.object(release.os, 'geteuid', return_value=self.root.stat().st_uid, create=True))
        self.stack.enter_context(patch.object(release.stat, 'S_IMODE', wraps=release.stat.S_IMODE))
        # Windows does not implement POSIX mode bits. Deployment guard semantics
        # are exercised separately on Linux CI; all control-flow cases run here.
        if os.name == 'nt':
            original = release.stat.S_IMODE
            self.stack.enter_context(patch.object(release.stat, 'S_IMODE', side_effect=lambda value: 0o700))
        self.stack.enter_context(patch.object(release.session, 'atomic_copy', side_effect=lambda a, b, *args: shutil.copyfile(a, b)))
        self.stack.enter_context(patch.object(release.session, 'atomic_write', side_effect=lambda a, b, *args: a.write_bytes(b)))
        self.stack.enter_context(patch.object(release.session, 'backup_smtp_key'))
        self.stack.enter_context(patch.object(release.session, 'service_metadata', return_value={'systemd': 'same', 'environmentFiles': {}}))
        self.fingerprints = self.stack.enter_context(patch.object(release, 'fingerprints', return_value={'config': 'unchanged'}))
        self.dump = self.stack.enter_context(patch.object(release, 'dump_database', side_effect=lambda state, dest: dest.write_bytes(b'private db snapshot')))
        self.health = self.stack.enter_context(patch.object(release.session, 'health', return_value=True))
        self.runtime = self.stack.enter_context(patch.object(release.promotion, 'runtime', return_value=copy.deepcopy(self.state)))
        self.run = self.stack.enter_context(patch.object(release.promotion, 'run', return_value='123'))

    def commands(self):
        return [call.args[0] for call in self.run.call_args_list]

    def test_success_backs_up_and_preserves_data_frontend_config(self):
        report = release.publish(self.request, self.state, self.new)
        self.assertTrue(report['ok'])
        self.assertTrue(report['configurationAndSecretsUnchanged'])
        self.assertTrue(report['frontendUnchanged'])
        self.assertEqual(report['databaseWrites'], 0)
        self.assertEqual(report['schemaChanges'], 0)
        self.assertEqual(self.jar.read_bytes(), b'new synthetic backend')
        backup = pathlib.Path(report['backup'])
        self.assertEqual((backup / 'previous.jar').read_bytes(), b'old synthetic backend')
        self.assertEqual((backup / 'application-otcm-preserved.properties').read_bytes(), self.preserved.read_bytes())
        self.assertEqual(self.commands()[1:], [['systemctl', 'stop', 'yiyuanjava'], ['systemctl', 'start', 'yiyuanjava']])
        self.assertNotIn('synthetic', json.dumps(report))

    def test_bad_health_rolls_back_previous_backend(self):
        self.health.side_effect = [False, True]
        report = release.publish(self.request, self.state, self.new)
        self.assertFalse(report['ok'])
        self.assertTrue(report['rollbackPerformed'])
        self.assertTrue(report['serviceHealthy'])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic backend')

    def test_configuration_change_after_start_rolls_back(self):
        changed = copy.deepcopy(self.state)
        changed['properties']['token.expireTime'] = '120'
        self.runtime.side_effect = [changed, self.state]
        report = release.publish(self.request, self.state, self.new)
        self.assertFalse(report['ok'])
        self.assertTrue(report['rollbackPerformed'])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic backend')

    def test_failed_backup_does_not_stop_service(self):
        self.dump.side_effect = RuntimeError('synthetic private failure')
        with self.assertRaises(RuntimeError):
            release.publish(self.request, self.state, self.new)
        self.assertEqual(self.commands(), [])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic backend')

    def test_changed_config_before_stop_does_not_touch_service(self):
        self.fingerprints.side_effect = [{'config': 'original'}, {'config': 'drift'}]
        report = release.publish(self.request, self.state, self.new)
        self.assertFalse(report['ok'])
        self.assertFalse(report['rollbackPerformed'])
        self.assertEqual(self.commands(), [])

    def test_failed_stop_still_attempts_rollback_start(self):
        calls = []
        def fail_once(args):
            calls.append(args)
            if len(calls) == 2:
                raise RuntimeError('synthetic stop failure')
            return '123'
        self.run.side_effect = fail_once
        report = release.publish(self.request, self.state, self.new)
        self.assertTrue(report['rollbackPerformed'])
        self.assertTrue(report['serviceHealthy'])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic backend')

    def test_failed_rollback_is_reported(self):
        self.health.side_effect = [False, False]
        report = release.publish(self.request, self.state, self.new)
        self.assertFalse(report['ok'])
        self.assertFalse(report['serviceHealthy'])
        self.assertIn('rollbackErrorType', report)


if __name__ == '__main__':
    unittest.main()
