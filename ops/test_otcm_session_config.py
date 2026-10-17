"""Local synthetic files and mocked services only; never access OTCM or its DB."""
import copy
import json
import os
import pathlib
import shutil
import stat
import tempfile
import unittest
from contextlib import ExitStack
from types import SimpleNamespace
from unittest.mock import patch

import otcm_session_config as session


class ReplacementTests(unittest.TestCase):
    def test_changes_only_numeric_value_preserving_secrets_and_crlf(self):
        original = (b'# protected config\r\ntoken.secret=synthetic-secret\\=\\=\r\n'
                    b'token.expireTime=120\r\nspring.mail.password=synthetic-smtp\r\n'
                    b'stripe.secret-key=synthetic-stripe\r\nlabel=\\u4e2d\r\n')
        changed = session.replacement(original)
        self.assertEqual(changed, original.replace(b'token.expireTime=120', b'token.expireTime=1440'))

    def test_rejects_duplicates_aliases_and_unexpected_values(self):
        for content in (b'token.expireTime=1440\n', b'token.expireTime=120\ntoken.expireTime=120\n',
                        b'token.expireTime=120\ntoken.expireTime=1440\n',
                        b'token.expireTime=120\ntoken.expire-time=120\n',
                        b'token.expireTime=120\nruoyi.token.expire-minutes=120\n',
                        b'token.expireTime=120\ntoken.\\u0065xpireTime=120\n',
                        b'token.expireTime=120\n token.expireTime : 120\n'):
            with self.subTest(content=content), self.assertRaises(session.SessionGuard):
                session.replacement(content)

    def test_accepts_lf_and_no_final_newline(self):
        self.assertEqual(session.replacement(b'x=1\ntoken.expireTime=120'), b'x=1\ntoken.expireTime=1440')


class OverrideTests(unittest.TestCase):
    def state(self, environment=None, args=None):
        return {'environment': environment or {}, 'args': args or ['java', '-jar', 'yy.jar']}

    def test_all_environment_spellings_rejected(self):
        for key in ('TOKEN_EXPIRETIME', 'TOKEN_EXPIRE_TIME', 'RUOYI_TOKEN_EXPIRE_MINUTES'):
            with self.subTest(key=key), self.assertRaises(session.SessionGuard):
                session.no_overrides(self.state({key: '120'}), '')

    def test_json_override_rejected(self):
        with self.assertRaises(session.SessionGuard):
            session.no_overrides(self.state({'SPRING_APPLICATION_JSON': '{"token":{"expireTime":120}}'}), '')

    def test_cli_and_system_property_overrides_rejected(self):
        for argument in ('--token.expireTime=120', '--token.expire-time=120', '-Dtoken.expireTime=120',
                         '--ruoyi.token.expire-minutes=120', '--token.expireTime'):
            with self.subTest(argument=argument), self.assertRaises(session.SessionGuard):
                session.no_overrides(self.state(args=['java', argument]), '')

    def test_java_option_overrides_rejected(self):
        for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
            with self.subTest(key=key), self.assertRaises(session.SessionGuard):
                session.no_overrides(self.state({key: '-Xmx1g -Dtoken.expireTime=120'}), '')

    def test_pending_systemd_or_environment_file_override_rejected(self):
        for text in ('Environment="TOKEN_EXPIRETIME=120"', 'TOKEN_EXPIRE_TIME=120',
                     'ExecStart=/usr/bin/java -Dtoken.expireTime=120 -jar yy.jar'):
            with self.subTest(text=text), self.assertRaises(session.SessionGuard):
                session.no_overrides(self.state(), text)

    def test_pending_json_override_rejected(self):
        text = 'Environment="SPRING_APPLICATION_JSON={\\"token\\":{\\"expireTime\\":120}}"'
        with self.assertRaises(session.SessionGuard):
            session.no_overrides(self.state(), text)

    def test_unrelated_configuration_allowed(self):
        session.no_overrides(self.state({'TOKEN_SECRET': 'synthetic', 'JAVA_TOOL_OPTIONS': '-Xmx1g'}),
                             'Environment="SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/application.properties"')

    def test_startup_identity_ignores_only_runtime_exec_metadata(self):
        before = {'systemd': 'ExecStart={ path=/java ; argv[]=/java -jar yy.jar ; ignore_errors=no'
                            ' ; start_time=[Sat 2026-10-17] ; stop_time=[n/a] ; pid=1 ; code=(null) ; status=0/0 }',
                  'environmentFiles': {}}
        after = copy.deepcopy(before)
        after['systemd'] = after['systemd'].replace('pid=1', 'pid=2').replace('Sat 2026-10-17', 'Sun 2026-10-18')
        self.assertEqual(session.service_identity(before), session.service_identity(after))
        after['systemd'] = after['systemd'].replace('/java', '/another-java')
        self.assertNotEqual(session.service_identity(before), session.service_identity(after))


class ApplyTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.root = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory())).resolve()
        self.app = self.root / 'app'
        self.app.mkdir()
        self.preserved = self.app / 'application-otcm-preserved.properties'
        self.original = b'token.expireTime=120\ntoken.secret=synthetic-secret\nspring.mail.password=synthetic-smtp\n'
        self.preserved.write_bytes(self.original)
        self.jar = self.app / 'yy.jar'
        self.jar.write_bytes(b'old synthetic jar')
        self.old_digest = session.promotion.digest(self.jar)
        self.backup = self.root / 'backup'
        self.backup.mkdir()
        self.unit = {'systemd': 'ExecStart={ path=/java ; pid=1 ; status=0/0 }', 'environmentFiles': {}}
        self.state = {'pid': 321, 'cwd': self.app, 'jar': self.jar, 'args': ['java', '-jar', str(self.jar)],
                      'environment': {}, 'properties': {'token.expireTime': '120', 'token.secret': 'synthetic-secret',
                                                       'spring.mail.password': 'synthetic-smtp'}}
        self.updated = copy.deepcopy(self.state)
        self.updated['properties']['token.expireTime'] = '1440'
        self.metadata = SimpleNamespace(st_uid=0, st_gid=0)
        for key, value in (('APP', self.app), ('PRESERVED', self.preserved), ('CURRENT_JAR', self.old_digest)):
            self.stack.enter_context(patch.object(session, key, value))
        self.stack.enter_context(patch.object(session, 'validate', return_value=(
            self.state, self.metadata, self.original, session.replacement(self.original), self.unit)))
        self.stack.enter_context(patch.object(session, 'new_backup_directory', return_value=self.backup))
        self.stack.enter_context(patch.object(session, 'backup_smtp_key'))
        self.fingerprints = self.stack.enter_context(patch.object(session, 'config_fingerprints', return_value={'config': 'abc'}))
        self.stack.enter_context(patch.object(session, 'service_metadata', return_value=self.unit))
        self.write = self.stack.enter_context(patch.object(session, 'atomic_write', side_effect=self.write_file))
        self.copy = self.stack.enter_context(patch.object(session, 'atomic_copy', side_effect=self.copy_file))
        self.run = self.stack.enter_context(patch.object(session.promotion, 'run', return_value='321'))
        self.runtime = self.stack.enter_context(patch.object(session.promotion, 'runtime', return_value=self.updated))
        self.health = self.stack.enter_context(patch.object(session, 'health', return_value=True))

    @staticmethod
    def write_file(path, content, *metadata):
        path.write_bytes(content)

    @staticmethod
    def copy_file(source, destination, *metadata):
        shutil.copyfile(source, destination)

    def staged(self):
        jar = self.root / 'new.jar'
        jar.write_bytes(b'new synthetic jar')
        return {'path': jar, 'sha256': session.promotion.digest(jar), 'revision': 'a' * 40, 'buildRun': '39012345678'}

    def assert_private(self, report):
        text = json.dumps(report)
        self.assertNotIn('synthetic-secret', text)
        self.assertNotIn('synthetic-smtp', text)
        self.assertTrue((self.backup / 'startup-private.json').is_file())
        self.assertEqual((self.backup / 'application-otcm-preserved.properties').read_bytes(), self.original)
        for call in self.run.call_args_list:
            self.assertEqual(call.args[0][0], 'systemctl')
            self.assertIn(session.SERVICE, call.args[0])

    def test_config_only_success_preserves_other_bytes_and_restarts_only_fixed_service(self):
        report = session.apply_change()
        self.assertTrue(report['ok'])
        self.assertEqual(report['expiryAfter'], 1440)
        self.assertEqual(report['backendBefore'], self.old_digest)
        self.assertEqual(report['backendAfter'], self.old_digest)
        self.assertFalse(report['backendChanged'])
        self.assertTrue(report['otherEffectiveConfigPreserved'])
        self.assertEqual(self.preserved.read_bytes(), session.replacement(self.original))
        self.assertEqual(self.jar.read_bytes(), b'old synthetic jar')
        self.assertEqual([call.args[0] for call in self.run.call_args_list if 'restart' in call.args[0]],
                         [['systemctl', 'restart', session.SERVICE]])
        self.assert_private(report)

    def test_health_failure_restores_original_config(self):
        self.health.side_effect = [False, True]
        self.runtime.return_value = self.state
        report = session.apply_change()
        self.assertFalse(report['ok'])
        self.assertTrue(report['rollbackPerformed'])
        self.assertTrue(report['serviceHealthy'])
        self.assertEqual(report['expiryAfter'], 120)
        self.assertEqual(self.preserved.read_bytes(), self.original)
        self.assert_private(report)

    def test_effective_expiry_mismatch_rolls_back(self):
        self.runtime.side_effect = [self.state, self.state]
        report = session.apply_change()
        self.assertTrue(report['rollbackPerformed'])
        self.assertEqual(self.preserved.read_bytes(), self.original)
        self.assertFalse(report['ok'])

    def test_other_effective_secret_change_rolls_back(self):
        changed = copy.deepcopy(self.updated)
        changed['properties']['token.secret'] = 'unexpected-change'
        self.runtime.side_effect = [changed, self.state]
        report = session.apply_change()
        self.assertTrue(report['rollbackPerformed'])
        self.assertEqual(self.preserved.read_bytes(), self.original)
        self.assertNotIn('unexpected-change', json.dumps(report))

    def test_failure_after_atomic_replace_still_restores_config(self):
        def partial_write(path, content, *metadata):
            path.write_bytes(content)
            if path == self.preserved and content != self.original:
                raise OSError('synthetic fsync failure')
        self.write.side_effect = partial_write
        self.runtime.return_value = self.state
        report = session.apply_change()
        self.assertTrue(report['rollbackPerformed'])
        self.assertEqual(self.preserved.read_bytes(), self.original)

    def test_failed_rollback_does_not_claim_expiry_120(self):
        self.health.return_value = False
        def failed_restore(path, content, *metadata):
            if path == self.preserved and content == self.original:
                raise OSError('synthetic rollback write failure')
            path.write_bytes(content)
        self.write.side_effect = failed_restore
        report = session.apply_change()
        self.assertFalse(report['ok'])
        self.assertIsNone(report['expiryAfter'])
        self.assertIsNone(report['backendAfter'])
        self.assertIsNone(report['backendChanged'])
        self.assertIsNone(report['otherEffectiveConfigPreserved'])
        self.assertEqual(report['rollbackErrorType'], 'OSError')

    def test_preparation_drift_does_not_restart_or_mutate(self):
        self.fingerprints.side_effect = [{'config': 'abc'}, {'config': 'changed'}]
        report = session.apply_change()
        self.assertFalse(report['ok'])
        self.assertEqual(self.preserved.read_bytes(), self.original)
        self.assertFalse(any('restart' in call.args[0] for call in self.run.call_args_list))

    def test_staged_success_backs_up_and_installs_jar_without_frontend_mutation(self):
        staged = self.staged()
        report = session.apply_change(staged)
        self.assertTrue(report['ok'])
        self.assertEqual(report['backendBefore'], self.old_digest)
        self.assertEqual(report['backendAfter'], staged['sha256'])
        self.assertEqual(report['backendRevision'], staged['revision'])
        self.assertEqual(report['buildRun'], staged['buildRun'])
        self.assertTrue(report['backendChanged'])
        self.assertTrue(report['otherEffectiveConfigPreserved'])
        self.assertEqual((self.backup / 'previous.jar').read_bytes(), b'old synthetic jar')
        self.assertEqual(self.jar.read_bytes(), b'new synthetic jar')
        self.assert_private(report)

    def test_staged_health_failure_restores_old_jar_and_config(self):
        self.health.side_effect = [False, True]
        self.runtime.return_value = self.state
        report = session.apply_change(self.staged())
        self.assertFalse(report['ok'])
        self.assertTrue(report['rollbackPerformed'])
        self.assertEqual(report['backendAfter'], self.old_digest)
        self.assertFalse(report['backendChanged'])
        self.assertTrue(report['otherEffectiveConfigPreserved'])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic jar')
        self.assertEqual(self.preserved.read_bytes(), self.original)
        self.assert_private(report)

    def test_failed_stop_attempt_still_recovers_service(self):
        calls = []
        def run(arguments):
            calls.append(arguments)
            if arguments[1] == 'stop' and len([item for item in calls if item[1] == 'stop']) == 1:
                raise OSError('synthetic stop acknowledgement failure')
            return '321'
        self.run.side_effect = run
        self.runtime.return_value = self.state
        report = session.apply_change(self.staged())
        self.assertTrue(report['rollbackPerformed'])
        self.assertTrue(report['serviceHealthy'])
        self.assertEqual(self.jar.read_bytes(), b'old synthetic jar')


class StageTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.root = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory())).resolve()
        self.run_id, self.revision = '39012345678', 'a' * 40
        self.work = self.root / self.run_id
        self.work.mkdir(mode=0o700)
        (self.work / 'backend.jar').write_bytes(b'synthetic reviewed jar')
        self.digest = session.promotion.digest(self.work / 'backend.jar')
        self.manifest = {'backendRevision': self.revision, 'frontendRevision': session.FRONTEND_REVISION,
                         'publicBaseUrl': 'https://www.otcm.app', 'sha256': {'backend.jar': self.digest}}
        self.write_manifest()
        self.stack.enter_context(patch.object(session, 'STAGING', self.root))
        original_stat = pathlib.Path.stat
        def root_stat(path, *args, **kwargs):
            info = list(original_stat(path, *args, **kwargs))
            info[4] = 0  # Root ownership is synthetic; no elevation required.
            if path == self.work:
                info[0] = stat.S_IFDIR | 0o700
            return os.stat_result(info)
        self.stack.enter_context(patch.object(pathlib.Path, 'stat', root_stat))

    def write_manifest(self):
        (self.work / 'manifest.json').write_text(json.dumps(self.manifest))
        (self.work / 'READY.json').write_text(json.dumps({'runId': self.run_id, 'manifest': self.manifest}))

    def test_accepts_only_manifest_and_digest_verified_stage(self):
        self.assertEqual(session.staged_backend(self.run_id, self.revision, self.digest),
                         {'path': self.work / 'backend.jar', 'sha256': self.digest,
                          'revision': self.revision, 'buildRun': self.run_id})

    def test_rejects_manifest_revision_mismatch(self):
        self.manifest['backendRevision'] = 'c' * 40
        self.write_manifest()
        with self.assertRaises(session.SessionGuard):
            session.staged_backend(self.run_id, self.revision, self.digest)

    def test_rejects_changed_jar(self):
        (self.work / 'backend.jar').write_bytes(b'drifted jar')
        with self.assertRaises(session.SessionGuard):
            session.staged_backend(self.run_id, self.revision, self.digest)

    def test_rejects_incomplete_stage(self):
        (self.work / 'READY.json').unlink()
        with self.assertRaises(session.SessionGuard):
            session.staged_backend(self.run_id, self.revision, self.digest)

    def test_rejects_different_frontend_or_domain(self):
        for key, value in (('frontendRevision', 'c' * 40), ('publicBaseUrl', 'https://another.invalid')):
            with self.subTest(key=key):
                previous = self.manifest[key]
                self.manifest[key] = value
                self.write_manifest()
                with self.assertRaises(session.SessionGuard):
                    session.staged_backend(self.run_id, self.revision, self.digest)
                self.manifest[key] = previous

    def test_no_optional_jar(self):
        self.assertIsNone(session.staged_backend(None, None, None))

    def test_partial_or_unsafe_identity_rejected_before_filesystem_access(self):
        for identity in (('39012345678', None, None), ('../outside', 'a' * 40, 'b' * 64),
                         ('39012345678', 'a' * 40, session.CURRENT_JAR)):
            with self.subTest(identity=identity), self.assertRaises(session.SessionGuard):
                session.staged_backend(*identity)


class ValidationTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.app = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory())).resolve()
        self.preserved = self.app / 'config.properties'
        self.preserved.write_bytes(b'token.expireTime=120\ntoken.secret=synthetic-secret\n')
        self.jar = self.app / 'yy.jar'
        self.jar.write_bytes(b'synthetic jar')
        self.state = {'cwd': self.app, 'jar': self.jar, 'properties': {'token.expireTime': '120'},
                      'environment': {'SPRING_CONFIG_ADDITIONAL_LOCATION': 'file:' + str(self.preserved)},
                      'args': ['java', '-jar', str(self.jar)]}
        self.stack.enter_context(patch.object(session, 'APP', self.app))
        self.stack.enter_context(patch.object(session, 'PRESERVED', self.preserved))
        self.stack.enter_context(patch.object(session, 'CURRENT_JAR', session.promotion.digest(self.jar)))
        self.stack.enter_context(patch.object(session.promotion, 'runtime', return_value=self.state))
        self.stack.enter_context(patch.object(session, 'service_metadata',
                                             return_value={'systemd': 'ExecStart=/java', 'environmentFiles': {}}))
        self.file_mode = stat.S_IFREG | 0o600
        original_lstat = pathlib.Path.lstat
        def lstat(path, *args, **kwargs):
            value = original_lstat(path, *args, **kwargs)
            if path == self.preserved:
                items = list(value)
                items[0], items[4] = self.file_mode, 0
                return os.stat_result(items)
            return value
        self.stack.enter_context(patch.object(pathlib.Path, 'lstat', lstat))

    def test_fixed_target_exact_configuration_passes(self):
        before, _, original, changed, _ = session.validate()
        self.assertIs(before, self.state)
        self.assertEqual(changed, session.replacement(original))

    def test_rejects_unexpected_jar(self):
        self.jar.write_bytes(b'different jar')
        with self.assertRaises(session.SessionGuard):
            session.validate()

    def test_rejects_world_readable_config(self):
        self.file_mode = stat.S_IFREG | 0o644
        with self.assertRaises(session.SessionGuard):
            session.validate()

    def test_rejects_different_service_directory(self):
        self.state['cwd'] = self.app / 'unexpected'
        with self.assertRaises(session.SessionGuard):
            session.validate()

    def test_rejects_unloaded_preserved_config(self):
        self.state['environment'] = {}
        with self.assertRaises(session.SessionGuard):
            session.validate()

    def test_rejects_effective_expiry_override(self):
        self.state['properties']['token.expireTime'] = '1440'
        with self.assertRaises(session.SessionGuard):
            session.validate()


@unittest.skipIf(os.name != 'posix', 'POSIX file permissions verified in Linux workflow')
class AtomicFileTests(unittest.TestCase):
    def test_atomic_write_and_copy_modes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            source, target = root / 'source', root / 'target'
            session.atomic_write(source, b'original', os.getuid(), os.getgid())
            self.assertEqual(stat.S_IMODE(source.stat().st_mode), 0o600)
            session.atomic_copy(source, target, os.getuid(), os.getgid(), 0o640)
            self.assertEqual(target.read_bytes(), b'original')
            self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o640)

    def test_failed_writer_keeps_original_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = pathlib.Path(directory) / 'config'
            destination.write_bytes(b'original')
            def fail(output):
                output.write(b'partial')
                raise OSError('synthetic writer failure')
            with self.assertRaises(OSError):
                session.atomic_replace(destination, fail, os.getuid(), os.getgid())
            self.assertEqual(destination.read_bytes(), b'original')
            self.assertEqual(list(destination.parent.iterdir()), [destination])


if __name__ == '__main__':
    unittest.main()
