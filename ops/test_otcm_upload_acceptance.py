"""Offline synthetic tests; never contact OTCM or mutate real data."""
import base64
import json
import pathlib
import tempfile
import unittest
from contextlib import ExitStack
from unittest.mock import patch

import otcm_upload_acceptance as qa


class GuardTests(unittest.TestCase):
    def test_cloud_backup_waits_for_exact_key_and_size(self):
        meta = qa.fixture('a'*32)
        resource = 'hospital-private/'+meta['name']+'/Consultation/2026/document_'+'b'*32+'.png'
        with patch.object(qa, 'r2_call', return_value={'status':200,'bytes':123}) as call:
            self.assertTrue(qa.backup_ready({'properties':{'tcm.r2.object-prefix':'clinic/'}}, meta, resource, 123))
        self.assertEqual(call.call_args.args[1:3], ('HEAD','clinic/'+resource))
        with patch.object(qa, 'r2_call', return_value={'status':200,'bytes':122}):
            with self.assertRaises(qa.release.Guard):
                qa.backup_ready({'properties':{}}, meta, resource, 123)

    def test_backup_polling_reports_wait_separately_and_is_bounded(self):
        clock = [0]
        def sleep(seconds):
            clock[0] += seconds
        with patch.object(qa.release, 'r2_configuration', return_value={'backupEffective':True}), \
             patch.object(qa.time, 'monotonic', side_effect=lambda:clock[0]), patch.object(qa.time, 'sleep', side_effect=sleep), \
             patch.object(qa, 'backup_ready', side_effect=[False,False,True]):
            report = qa.wait_for_backup({'properties':{}},{},'unused-by-mock',1)
        self.assertEqual(report['backupWaitSeconds'],4)
        self.assertTrue(report['backupCompleted'])
        clock[0] = 0
        with patch.object(qa.release, 'r2_configuration', return_value={'backupEffective':True}), \
             patch.object(qa.time, 'monotonic', side_effect=lambda:clock[0]), patch.object(qa.time, 'sleep', side_effect=sleep), \
             patch.object(qa, 'backup_ready', return_value=False):
            with self.assertRaises(qa.release.Guard):
                qa.wait_for_backup({'properties':{}},{},'unused-by-mock',1)
        self.assertEqual(clock[0],180)

    def test_pending_backup_retains_files_and_rows_but_disables_account_and_session(self):
        meta = {**qa.fixture('a'*32),'creationAttempted':True}
        resource = 'hospital-private/'+meta['name']+'/Consultation/2026/document_'+'b'*32+'.png'
        meta['pendingBackups'] = {resource:100}
        with patch.object(qa, 'mutate') as mutate, patch.object(qa, 'remove_qa_sessions', return_value=1), \
             patch.object(qa.promotion, 'query', side_effect=['0','0',resource]), patch.object(qa, 'backup_ready', return_value=False), \
             patch.object(qa, 'cloud_keys') as cloud, patch.object(qa, 'local_files') as local:
            report = qa.cleanup({'properties':{}},meta)
        self.assertFalse(report['completed'])
        self.assertTrue(report['backupPending'])
        self.assertTrue(report['sessionRemoved'])
        self.assertEqual(mutate.call_count,1)
        self.assertTrue(mutate.call_args.args[1].startswith('UPDATE sys_user SET status=1'))
        cloud.assert_not_called()
        local.assert_not_called()

    def test_signed_url_metadata_excludes_resource_and_signature_and_http_still_rejected(self):
        url = 'http://www.otcm.app:8006/api/public/files/access?resource=private-name&signature=private-signature'
        report = qa.signed_url_metadata(url)
        self.assertEqual(report, {'scheme':'http','host':'www.otcm.app','port':8006,'path':'/api/public/files/access'})
        self.assertNotIn('private', json.dumps(report))
        with self.assertRaises(qa.release.Guard):
            qa.validate_signed_url(url)
        for host in ('www.otcm.app','otcm.app'):
            qa.validate_signed_url('https://'+host+'/api/public/files/access?signature=synthetic')
        for url in ('https://user:pass@www.otcm.app/api/public/files/access', 'https://evil.test/api/public/files/access',
                    'https://www.otcm.app:8006/api/public/files/access'):
            with self.assertRaises(qa.release.Guard):
                qa.validate_signed_url(url)

    def test_fixture_is_bounded_and_sql_is_hex_encoded(self):
        meta = qa.fixture('a' * 32)
        self.assertLessEqual(len(meta['username']), 30)
        self.assertNotIn("'; DROP", qa.literal("'; DROP TABLE users"))
        for value in ('', '../escape', 'A' * 32, 'a' * 33):
            with self.assertRaises(qa.release.Guard):
                qa.fixture(value)

    def test_only_exact_fixture_document_paths_accepted(self):
        meta = qa.fixture('a' * 32)
        good = 'hospital-private/' + meta['name'] + '/Consultation/2026/document_' + 'b' * 32 + '.png'
        self.assertEqual(qa.validate_resource(meta, good), good)
        for bad in (good.replace(meta['name'], 'real-patient'), good.replace('Consultation', '../Consultation'), good + '.txt'):
            with self.assertRaises(qa.release.Guard):
                qa.validate_resource(meta, bad)

    def test_png_is_valid_signature_and_large_payload_above_four_megabytes(self):
        small, large = qa.png(16, 16), qa.png(1200, 1200)
        self.assertTrue(small.startswith(b'\x89PNG\r\n\x1a\n'))
        self.assertGreater(len(large), 4 * 1024 * 1024)
        self.assertLess(len(large), 5 * 1024 * 1024)

    def test_unattempted_creation_never_cleans_preexisting_state(self):
        with patch.object(qa, 'mutate') as mutate, patch.object(qa, 'remove_qa_sessions') as sessions:
            report = qa.cleanup({}, qa.fixture('a' * 32))
        mutate.assert_not_called()
        sessions.assert_not_called()
        self.assertTrue(report['notCreated'])

    def test_session_cleanup_handles_lost_login_response_without_exporting_cache(self):
        meta = qa.fixture('a' * 32)
        with patch.object(qa, 'redis_target', return_value=(['redis-cli'], {})), patch.object(qa.promotion, 'run', return_value='0\n1') as run:
            self.assertEqual(qa.remove_qa_sessions({}, meta), 1)
        args = run.call_args.args[0]
        self.assertEqual(args[-1], meta['username'])
        self.assertIn("raw:match", args[2])
        self.assertNotIn('cjson.decode', args[2])
        self.assertIn("'login_tokens:*'", args[2])
        self.assertNotIn('FLUSH', args[2])

    def test_expiry_accepts_actual_fastjson_classname_wire_format(self):
        meta = {**qa.fixture('a'*32), 'sessionId':'b'*32}
        wire = '{"@type":"LoginUser","permissions":Set["file:upload"],"user":{"userId":456L,"userName":"'+meta['username']+'"}}'
        with patch.object(qa, 'redis_target', return_value=(['redis-cli'], {})), patch.object(qa.promotion, 'run', side_effect=[wire, '1']) as run:
            self.assertTrue(qa.redis_session({}, meta, 'expire'))
        self.assertEqual(run.call_args.args[0][-3:], ['EXPIRE','login_tokens:'+'b'*32,'600'])

    def test_cloud_listing_scoped_and_unexpected_object_rejected(self):
        meta = qa.fixture('a' * 32)
        state = {'properties': {'tcm.r2.object-prefix': 'clinic/'}}
        with patch.object(qa, 'r2_call', return_value=b'<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>clinic/hospital-private/real-patient/file.png</Key></Contents></ListBucketResult>') as call:
            with self.assertRaises(qa.release.Guard):
                qa.cloud_keys(state, meta)
        self.assertEqual(call.call_args.kwargs['params']['prefix'], 'clinic/hospital-private/' + meta['name'] + '/')

    def test_cleanup_continues_local_and_database_when_cloud_fails(self):
        meta = {**qa.fixture('a' * 32), 'creationAttempted': True}
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            root = pathlib.Path(directory)
            file = root / 'qa.png'
            file.write_bytes(b'synthetic')
            folder = root / 'gone'
            stack.enter_context(patch.object(qa, 'mutate'))
            stack.enter_context(patch.object(qa, 'remove_qa_sessions', side_effect=[1, 0]))
            stack.enter_context(patch.object(qa.promotion, 'query', side_effect=['0', '0', '', '0']))
            stack.enter_context(patch.object(qa.release, 'r2_configuration', return_value={'backupEffective': True}))
            stack.enter_context(patch.object(qa, 'cloud_keys', side_effect=RuntimeError('private cloud failure')))
            stack.enter_context(patch.object(qa, 'local_files', return_value=(folder, [file])))
            report = qa.cleanup({'properties': {}}, meta)
            self.assertFalse(report['completed'])
            self.assertEqual(report['remainingFixtures'], 0)
            self.assertTrue(report['sessionRemoved'])
            self.assertFalse(file.exists())
            self.assertNotIn('private', json.dumps(report))


class FlowTests(unittest.TestCase):
    def test_public_practitioner_change_aborts_before_login_and_cleans_fixture(self):
        meta = qa.fixture('a' * 32)
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            query = stack.enter_context(patch.object(qa.promotion, 'query', side_effect=['0', '1']))
            stack.enter_context(patch.object(qa, 'local_files', return_value=(pathlib.Path(directory)/'absent', [])))
            stack.enter_context(patch.object(qa.release, 'r2_configuration', return_value={'backupEffective': False}))
            stack.enter_context(patch.object(qa, 'password_hash', return_value='synthetic-hash'))
            stack.enter_context(patch.object(qa, 'mutate'))
            stack.enter_context(patch.object(qa, 'private_write'))
            stack.enter_context(patch.object(qa.time, 'sleep'))
            clean = stack.enter_context(patch.object(qa, 'cleanup', return_value={'completed': True}))
            api = stack.enter_context(patch.object(qa, 'api', side_effect=[{'practitioners': []}, {'practitioners': [{'id':'unexpected-qa'}]}]))
            report = qa.run_acceptance({'properties': {}}, meta, pathlib.Path(directory))
        self.assertFalse(report['ok'])
        self.assertEqual(report['guard'], 'QA account changed public booking options')
        self.assertIn("role_key='admin'", query.call_args_list[1].args[1])
        self.assertTrue(all(call.args[0]=='/api/public-booking/options' for call in api.call_args_list))
        clean.assert_called_once()

    def test_two_real_request_shapes_and_final_cleanup(self):
        meta = qa.fixture('a' * 32)
        state = {'properties': {}}
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            stack.enter_context(patch.object(qa.promotion, 'query', side_effect=['0', '2']))
            stack.enter_context(patch.object(qa, 'local_files', return_value=(pathlib.Path(directory)/'absent', [])))
            stack.enter_context(patch.object(qa.release, 'r2_configuration', return_value={'backupEffective': True}))
            stack.enter_context(patch.object(qa, 'cloud_keys', return_value=[]))
            stack.enter_context(patch.object(qa, 'password_hash', return_value='synthetic-hash'))
            mutate = stack.enter_context(patch.object(qa, 'mutate'))
            stack.enter_context(patch.object(qa, 'private_write'))
            stack.enter_context(patch.object(qa, 'redis_session'))
            stack.enter_context(patch.object(qa, 'png', side_effect=[b'small', b'large']))
            clean = stack.enter_context(patch.object(qa, 'cleanup', return_value={'completed': True}))
            claims = base64.urlsafe_b64encode(json.dumps({'sub':meta['username'], 'login_user_key':'b'*32}).encode()).decode().rstrip('=')
            calls, files, uploads = [], [], []
            completed_backups = []
            def wait_backup(state, current_meta, resource, size):
                self.assertEqual(current_meta['pendingBackups'][resource],size)
                self.assertEqual(len(files),1)
                completed_backups.append(resource)
                return {'backupCompleted':True,'backupEnabled':True,'backupWaitSeconds':2}
            stack.enter_context(patch.object(qa, 'wait_for_backup', side_effect=wait_backup))
            def api(path, token=None, method='GET', body=None, content_type='application/json', binary=False):
                calls.append((path, method))
                if path == '/api/public-booking/options':
                    return {'practitioners': [{'id': 'existing-public-practitioner'}]}
                if path.endswith('/login'):
                    return {'token':'x.' + claims + '.x'}
                if path.endswith('/upload'):
                    filename = 'qa-small.png' if not uploads else '验收图片.png'
                    uploads.append(filename)
                    file = {'id':len(calls), 'patientId':meta['patientId'], 'consultationId':meta['consultationId'], 'fileName':filename,
                            'resource':'hospital-private/'+meta['name']+'/Consultation/2026/document_'+'c'*32+'.png', 'url':'https://www.otcm.app/api/public/files/access'}
                    files.append(file)
                    return file
                if binary:
                    return b'small' if files[-1]['fileName']=='qa-small.png' else b'large'
                if '/consultation/' in path:
                    return list(files)
                if method == 'DELETE':
                    self.assertFalse(meta['pendingBackups'])
                    self.assertEqual(len(completed_backups),len(uploads))
                    files.clear()
                return {'ok':True}
            stack.enter_context(patch.object(qa, 'api', side_effect=api))
            report = qa.run_acceptance(state, meta, pathlib.Path(directory))
            self.assertTrue(report['ok'])
            self.assertEqual(len(report['tests']), 2)
            self.assertTrue(report['tests'][1]['unicodeFilename'])
            self.assertTrue(all(test['backupCompleted'] for test in report['tests']))
            self.assertTrue(meta['creationAttempted'])
            self.assertTrue(report['publicBookingPractitionersUnchanged'])
            self.assertIn('@qauser', mutate.call_args.args[1])
            self.assertNotIn('/api/patients', [path for path, _ in calls])
            clean.assert_called_once()


if __name__ == '__main__':
    unittest.main()
