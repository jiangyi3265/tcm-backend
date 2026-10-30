"""Synthetic local files and mocked services only; no network or production data."""
import copy
import json
import os
import pathlib
import shutil
import sys
import tempfile
import types
import unittest
import zipfile
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
    def test_r2_flags_match_application_without_returning_values(self):
        props = {'tcm.r2.enabled': 'true', 'tcm.r2.endpoint': 'https://private.invalid', 'tcm.r2.bucket': 'private',
                 'tcm.r2.access-key-id': 'private', 'tcm.r2.secret-access-key': 'private'}
        self.assertEqual(release.r2_configuration(props), {'enabled': True, 'configured': True, 'backupOnWrite': True, 'backupEffective': True})
        self.assertNotIn('private', json.dumps(release.r2_configuration(props)))
        for key in ('endpoint', 'bucket', 'access-key-id', 'secret-access-key'):
            self.assertFalse(release.r2_configuration({**props, 'tcm.r2.' + key: None})['configured'])
        self.assertFalse(release.r2_configuration({**props, 'tcm.r2.backup-on-write': 'false'})['backupEffective'])
        self.assertFalse(release.r2_configuration({})['enabled'])

    def test_upload_journal_excludes_other_failures_and_all_raw_content(self):
        records = [
            {'MESSAGE': 'WARN Cloudflare R2 backup failed for private-patient: Read timed out'},
            {'MESSAGE': 'ERROR /api/files/upload IOException: private-file permission denied'},
            {'MESSAGE': 'ERROR /api/appointments SocketTimeoutException unrelated-private'},
            {'MESSAGE': 'ERROR CloudflareR2StorageService SocketTimeoutException private-url'},
            {'MESSAGE': 'WARN Cloudflare R2 restore failed for private-name: denied'},
            {'MESSAGE': 123},
        ]
        result = release.upload_journal_summary([json.dumps(item) for item in records] + ['bad-json'])
        self.assertEqual(result['matchingEntries'], 4)
        self.assertEqual(result['categories']['r2BackupFailure'], 1)
        self.assertEqual(result['categories']['timeout'], 2)
        self.assertEqual(result['exceptionClasses'], {'IOException': 1, 'SocketTimeoutException': 1})
        self.assertEqual(result['unparsedEntries'], 1)
        self.assertNotIn('private', json.dumps(result))

    def test_request_timings_use_declared_columns_and_upload_requests_only(self):
        fmt = '$remote_addr "$request" $status $request_time "$upstream_response_time"'
        text = ('private "POST /api/files/upload HTTP/1.1" 499 15.123 "15.100"\n'
                'private "POST /api/files/upload?token=private HTTP/1.1" 200 1.001 "0.500, 0.400"\n'
                'private "POST /other HTTP/1.1" 500 999.0 "998.0"\n'
                'private "POST /api/files/upload HTTP/1.1" 413 0.001 "-"\n')
        result = release.request_timing_summary(text, fmt)
        self.assertEqual(result['request_time']['samples'], 3)
        self.assertEqual(result['request_time']['maxSeconds'], 15.123)
        self.assertEqual(result['upstream_response_time']['samples'], 2)
        self.assertEqual(result['upstream_response_time']['minSeconds'], 0.9)
        self.assertNotIn('private', json.dumps(result))

    def test_request_timings_not_guessed_without_log_format(self):
        self.assertEqual(release.request_timing_summary('private 50.0', ''), {'available': False})
        self.assertEqual(release.request_timing_summary('private 50.0', '$remote_addr $request'), {'available': False})

    def test_recent_journal_scoped_and_bounded(self):
        with patch.object(release.promotion, 'run', side_effect=['a' * 32, json.dumps({'MESSAGE': 'unrelated'})]) as run:
            report = release.recent_upload_journal()
        self.assertTrue(report['available'])
        args = run.call_args_list[1].args[0]
        self.assertIn('--since=48 hours ago', args)
        self.assertIn('--lines=20001', args)
        self.assertIn('_SYSTEMD_INVOCATION_ID=' + 'a' * 32, args)

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
        with patch.object(release.promotion, 'query', side_effect=['0\tid\n1\temail', '2\t6', '3', '8\t4']) as query, \
                patch.object(release, 'patient_name_detail', return_value={'available': False}) as detail:
            report = release.patient_diagnostics({})
        self.assertEqual(report['indexes'], [{'unique': True, 'columns': ['id']}, {'unique': False, 'columns': ['email']}])
        self.assertEqual(report['patientsInSharedEmailGroups'], 6)
        self.assertEqual(report['patientsWithMultiplePublicBookingNames'], 3)
        for call in query.call_args_list:
            self.assertTrue(call.args[1].startswith('SELECT '))
        detail.assert_called_once_with({}, 4, 8)


class HistoricalNameTests(unittest.TestCase):
    def row(self, **updates):
        return {'profile': 'private-fixture-id', 'currentName': 'Lee Alice', 'currentFirst': 'Alice', 'currentLast': 'Lee',
                'submittedName': 'Lee Alice', 'submittedFirst': 'Alice', 'submittedLast': 'Lee',
                'submittedEmail': 'private-family@example.invalid', 'baselineMismatch': 1, **updates}

    def test_unicode_whitespace_and_case_are_formatting_only(self):
        row = self.row(submittedName=' LEE\u00a0\t Alice ', submittedFirst=' ALICE ', submittedLast='lee')
        self.assertEqual(release.classify_name_difference(row), 'caseOrWhitespaceOnly')

    def test_complete_structured_name_can_explain_display_order(self):
        self.assertEqual(release.classify_name_difference(self.row(submittedName='Alice Lee')), 'displayOrderCompatible')
        self.assertEqual(release.classify_name_difference(self.row(currentFirst=None, currentLast=None,
                        currentName='Alice Lee')), 'displayOrderCompatible')

    def test_matching_structured_parts_do_not_require_identical_display_label(self):
        self.assertEqual(release.classify_name_difference(self.row(currentName='A. Lee')), 'structuredNameMatches')
        self.assertEqual(release.classify_name_difference(self.row(currentName='张三', currentFirst='三', currentLast='张',
                        submittedName='张 三', submittedFirst='三', submittedLast='张')), 'structuredNameMatches')

    def test_matching_split_fields_do_not_clear_an_overwritten_display_name(self):
        report = release.summarize_name_rows([self.row(currentName='Unrelated Person')], 1, 1)
        self.assertEqual(report['snapshotCategories']['structuredNameMatches'], 1)
        self.assertEqual(report['profilesExplainedByFormattingOrStructuredName'], 0)
        self.assertEqual(report['profilesRequiringRecordReview'], 1)

    def test_repartitioned_or_changed_name_parts_are_not_hidden_by_same_display(self):
        row = self.row(currentName='Lee Mary Jane', currentFirst='Mary Jane', currentLast='Lee',
                       submittedName='Lee Mary Jane', submittedFirst='Jane', submittedLast='Lee Mary')
        self.assertEqual(release.classify_name_difference(row), 'structuredNameConflict')
        self.assertEqual(release.classify_name_difference(self.row(submittedFirst='Bob', submittedLast=None)), 'structuredNameConflict')

    def test_two_legacy_names_in_reverse_order_remain_ambiguous(self):
        row = self.row(currentFirst=None, currentLast=None, submittedFirst=None, submittedLast=None, submittedName='Alice Lee')
        self.assertEqual(release.classify_name_difference(row), 'legacyOrderAmbiguous')

    def test_accents_and_punctuation_are_not_removed_and_missing_data_is_not_guessed(self):
        self.assertEqual(release.classify_name_difference(self.row(submittedFirst='Álice')), 'structuredNameConflict')
        self.assertEqual(release.classify_name_difference(self.row(submittedFirst="Ali'ce")), 'structuredNameConflict')
        self.assertEqual(release.classify_name_difference(self.row(currentFirst=None, currentLast=None, submittedFirst=None,
                        submittedLast=None, submittedName=None)), 'insufficientNameData')
        self.assertEqual(release.classify_name_difference(self.row(currentFirst=None, currentLast=None, submittedFirst=None,
                        submittedLast=None, submittedName='Another Name')), 'unexplainedNameDifference')

    def test_multiple_names_at_shared_email_are_review_candidates_not_cleared(self):
        rows = [self.row(currentFirst=None, currentLast=None, submittedName='Alice Lee'),
                self.row(currentFirst=None, currentLast=None, submittedName='Lee Alice', submittedFirst='Lee',
                         submittedLast='Alice', submittedEmail='PRIVATE-FAMILY@example.invalid', baselineMismatch=0)]
        report = release.summarize_name_rows(rows, 1, 1)
        self.assertEqual(report['snapshotCategories']['displayOrderCompatible'], 1)
        self.assertEqual(report['profilesWithMultipleStructuredNamesAtSameEmail'], 1)
        self.assertEqual(report['profilesRequiringRecordReview'], 1)
        self.assertEqual(report['profilesExplainedByFormattingOrStructuredName'], 0)
        self.assertNotIn('private-', json.dumps(report))
        self.assertNotIn('Alice', json.dumps(report))

    def test_repeated_same_person_snapshots_do_not_inflate_profile_count(self):
        rows = [self.row(submittedName='Alice Lee'), self.row(submittedName=' lee  alice ')]
        report = release.summarize_name_rows(rows, 1, 2)
        self.assertEqual(report['profilesExplainedByFormattingOrStructuredName'], 1)
        self.assertEqual(report['profilesRequiringRecordReview'], 0)
        self.assertEqual(sum(report['snapshotCategories'].values()), 2)

    def test_changed_baseline_or_excess_scope_rejected(self):
        with self.assertRaises(release.Guard):
            release.summarize_name_rows([self.row()], 2, 1)
        with self.assertRaises(release.Guard):
            release.summarize_name_rows([self.row()], 1, 2)
        with patch.object(release.promotion, 'mysql') as mysql:
            report = release.patient_name_detail({}, 7, 8)
        self.assertFalse(report['available'])
        mysql.assert_not_called()

    def test_projection_is_read_only_bounded_and_does_not_export_raw_rows(self):
        row = self.row(submittedName='Alice Lee')
        with patch.object(release.promotion, 'mysql', return_value=('fixture_db', {}, [])), \
                patch.object(release.promotion, 'run', return_value=json.dumps(row)) as run:
            report = release.patient_name_detail({}, 1, 1)
        args = run.call_args.args[0]
        query = run.call_args.kwargs['input'].decode()
        self.assertIn('--raw', args)
        self.assertTrue(query.startswith('START TRANSACTION READ ONLY; SELECT JSON_OBJECT('))
        self.assertTrue(query.endswith('LIMIT 5001; ROLLBACK;'))
        self.assertIn('SELECT DISTINCT candidate.patient_id', query)
        self.assertIn('candidate_count)=1', query)
        self.assertNotIn('SELECT payload', query)
        self.assertNotIn('private-', json.dumps(report))
        self.assertNotIn('Alice', json.dumps(report))
        self.assertTrue(report['available'])

    def test_parse_failures_do_not_leak_identity_strings(self):
        with patch.object(release.promotion, 'mysql', return_value=('fixture_db', {}, [])), \
                patch.object(release.promotion, 'run', return_value='private-person-not-json'):
            report = release.patient_name_detail({}, 1, 1)
        self.assertFalse(report['available'])
        self.assertEqual(report['errorType'], 'JSONDecodeError')
        self.assertNotIn('private-person', json.dumps(report))


class StageTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.root = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory())).resolve()
        self.jar = self.root / 'source.jar'
        with zipfile.ZipFile(self.jar, 'w') as jar:
            jar.writestr('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class', b'synthetic')
            jar.writestr('BOOT-INF/classes/git.properties', 'git.commit.id=' + 'c' * 40)
        self.request = {'revision': 'c' * 40, 'sha256': release.promotion.digest(self.jar), 'artifactUrl': 'unused-by-mock'}
        self.manifest = {'revision': self.request['revision'], 'sha256': self.request['sha256'], 'publicBaseUrl': 'https://www.otcm.app'}
        self.archive = self.root / 'source.zip'
        self.directory = self.root / 'stage'
        self.directory.mkdir()
        fake = types.SimpleNamespace(MAX_ARTIFACT_BYTES=160*1024*1024,
                 download_github_archive=lambda url, path: shutil.copyfile(self.archive, path))
        self.stack.enter_context(patch.dict(sys.modules, {'production_deploy': fake}))

    def bundle(self, extra=None):
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.write(self.jar, 'backend.jar')
            archive.writestr('manifest.json', json.dumps(self.manifest))
            if extra:
                archive.writestr(extra, 'forbidden')

    def test_verified_bundle_stages_without_execution(self):
        self.bundle()
        staged = release.stage(self.request, self.directory)
        self.assertEqual(release.promotion.digest(staged), self.request['sha256'])

    def test_archive_with_traversal_rejected(self):
        self.bundle('../escape')
        with self.assertRaises(release.Guard):
            release.stage(self.request, self.directory)
        self.assertFalse((self.root / 'escape').exists())

    def test_manifest_identity_mismatch_rejected(self):
        self.manifest['revision'] = 'd' * 40
        self.bundle()
        with self.assertRaises(release.Guard):
            release.stage(self.request, self.directory)

    def test_jar_digest_mismatch_rejected(self):
        self.request['sha256'] = self.manifest['sha256'] = 'a' * 64
        self.bundle()
        with self.assertRaises(release.Guard):
            release.stage(self.request, self.directory)

    def test_jar_embedded_revision_mismatch_rejected(self):
        self.request['revision'] = self.manifest['revision'] = 'd' * 40
        self.bundle()
        with self.assertRaises(release.Guard):
            release.stage(self.request, self.directory)


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
