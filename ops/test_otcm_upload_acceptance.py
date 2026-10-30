"""Offline synthetic tests; never contact OTCM or mutate real data."""
import base64
import json
import pathlib
import tempfile
import unittest
import urllib.error
import os
import time
from contextlib import ExitStack
from unittest.mock import patch

import otcm_upload_acceptance as qa


class GuardTests(unittest.TestCase):
    def test_deleted_download_accepts_only_not_found_and_never_uses_session(self):
        url = 'https://www.otcm.app/api/public/files/access?resource=qa&signature=synthetic'
        with patch.object(qa, 'validate_signed_url', return_value=qa.urllib.parse.urlparse(url)), \
                patch.object(qa, 'api', side_effect=urllib.error.HTTPError(url, 404, 'not found', {}, None)) as request:
            qa.require_deleted_download(url)
        request.assert_called_once_with('/api/public/files/access?resource=qa&signature=synthetic', binary=True)
        for response in (200, 403, 500):
            with self.subTest(response=response), \
                    patch.object(qa, 'validate_signed_url', return_value=qa.urllib.parse.urlparse(url)), \
                    patch.object(qa, 'api', return_value=b'file', side_effect=None if response == 200 else
                                 urllib.error.HTTPError(url, response, 'synthetic', {}, None)):
                with self.assertRaises(qa.release.Guard):
                    qa.require_deleted_download(url)

    def test_terminal_failure_timestamp_must_match_exact_resource_and_follow_write(self):
        resource = 'hospital-private/QA/Consultation/2026/document_example.png'
        now = time.time()
        def entry(path,stamp,message='Unexpected end of file from server'):
            return json.dumps({'MESSAGE':'Cloudflare R2 backup failed for '+path+': '+message,'__REALTIME_TIMESTAMP':str(int(stamp*1000000))})
        lines = [entry(resource,now-40),entry(resource+'-other',now-5),entry(resource,now-5,'read timed out')]
        self.assertIsNone(qa.exact_terminal_failure_time(lines,resource,now-20))
        lines.append(entry(resource,now-10))
        self.assertAlmostEqual(qa.exact_terminal_failure_time(lines,resource,now-20),now-10,places=4)

    def test_terminal_release_rejects_other_fixture_without_reading_backend(self):
        with patch.object(qa.promotion,'digest') as digest:
            report = qa.confirm_pinned_terminal_failure({},qa.fixture('a'*32),'unused',1)
        self.assertFalse(report['confirmed'])
        digest.assert_not_called()

    def test_terminal_release_rejects_different_backend(self):
        with patch.object(qa.promotion,'digest',return_value='different'):
            report = qa.confirm_pinned_terminal_failure({'jar':'unused'},qa.fixture(qa.TERMINAL_QA_ID),'unused',1)
        self.assertFalse(report['confirmed'])

    def test_qa_log_classification_uses_exact_fixture_and_never_returns_messages(self):
        meta = qa.fixture('a'*32)
        prefix = 'hospital-private/'+meta['name']+'/Consultation/2026/'
        records = [
            {'MESSAGE':'Cloudflare R2 backup failed for '+prefix+'private.png: Broken pipe private-secret','__REALTIME_TIMESTAMP':'1793370000000000'},
            {'MESSAGE':'Cloudflare R2 backup failed for '+prefix+'private.png: upload HTTP 403: private-provider-response'},
            {'MESSAGE':'Cloudflare R2 backup failed for hospital-private/other-patient/private.png: Read timed out'},
            {'MESSAGE':'Cloudflare R2 backup failed for '+prefix+'private.png: Connection reset by peer'},
            {'MESSAGE':'Cloudflare R2 backup failed for '+prefix+'private.png: SSLHandshakeException unexpected EOF'},
            {'MESSAGE':'Cloudflare R2 backup failed for '+prefix+'private.png: Read timed out'},
        ]
        result = qa.classify_qa_log([json.dumps(item) for item in records],meta)
        self.assertEqual(result['matchingEntries'],5)
        self.assertEqual(result['categories']['brokenPipe'],1)
        self.assertEqual(result['categories']['readTimeout'],1)
        self.assertEqual(result['httpStatuses'],{'403':1})
        self.assertNotIn('private',json.dumps(result))
        self.assertNotIn(meta['name'],json.dumps(result))

    def test_thread_diagnostics_exclude_other_threads_and_path_details(self):
        dump = '''"private-patient-worker" #1
 java.lang.Thread.State: RUNNABLE
 at private.Secret.method(Secret.java:1)
"r2-file-backup-1" #2
 java.lang.Thread.State: TIMED_WAITING (parking)
 at java.base@17.0.19/jdk.internal.misc.Unsafe.park(Native Method)
 at java.base@17.0.19/java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1050)
 at com.ruoyi.hospital.util.CloudflareR2StorageService.uploadObject(CloudflareR2StorageService.java:100)
 - locked private-object-value
"other-thread" #3
 at private.Secret.method(Secret.java:2)
'''
        result = qa.parse_backup_threads(dump)
        self.assertEqual(len(result),1)
        self.assertEqual(result[0]['name'],'r2-file-backup-1')
        self.assertEqual(result[0]['state'],'TIMED_WAITING')
        self.assertIn('java.util.concurrent.ThreadPoolExecutor.getTask',result[0]['methods'])
        self.assertNotIn('private',json.dumps(result))
        self.assertNotIn('.java:',json.dumps(result))

    def test_session_inspection_lua_has_no_mutation(self):
        meta = qa.fixture('a'*32)
        with patch.object(qa,'redis_target',return_value=(['redis-cli'],{})),patch.object(qa.promotion,'run',return_value='0\n0') as run:
            self.assertEqual(qa.count_qa_sessions({},meta),0)
        script = run.call_args.args[0][2]
        for mutation in ('DEL','EXPIRE','SET','FLUSH'):
            self.assertNotIn(mutation,script)
        self.assertIn("'SCAN'",script)
        self.assertIn("'GET'",script)

    def test_fixture_inspect_is_read_only_and_redacts_missing_object_url(self):
        meta = qa.fixture('a'*32)
        resource = 'hospital-private/'+meta['name']+'/Consultation/2026/document_'+'b'*32+'.png'
        meta['pendingBackups'] = {resource:10}
        with tempfile.TemporaryDirectory() as directory,ExitStack() as stack:
            folder = pathlib.Path(directory)/'hospital-private'/meta['name']
            file = folder/'Consultation'/'2026'/('document_'+'b'*32+'.png')
            file.parent.mkdir(parents=True)
            file.write_bytes(b'0123456789')
            stack.enter_context(patch.object(qa.promotion,'query',side_effect=['1\t1','1\t1\t1']))
            stack.enter_context(patch.object(qa,'local_files',return_value=(folder,[file])))
            stack.enter_context(patch.object(qa,'count_qa_sessions',return_value=0))
            stack.enter_context(patch.object(qa,'qa_logs',return_value={'available':True}))
            stack.enter_context(patch.object(qa,'backup_threads',return_value={'available':False}))
            cloud_error = urllib.error.HTTPError('https://private-url',404,'private-error',{},None)
            self.addCleanup(cloud_error.close)
            cloud = stack.enter_context(patch.object(qa,'r2_call',side_effect=cloud_error))
            mutate = stack.enter_context(patch.object(qa,'mutate'))
            clean = stack.enter_context(patch.object(qa,'cleanup'))
            write = stack.enter_context(patch.object(qa,'private_write'))
            result = qa.inspect_fixture({'properties':{}},meta,pathlib.Path(directory))
            self.assertTrue(file.exists())
        self.assertTrue(result['readOnly'])
        self.assertFalse(result['allPendingBackupsComplete'])
        self.assertEqual(result['pendingObjects'][0]['cloudStatus'],404)
        self.assertEqual(result['account']['sessions'],0)
        self.assertNotIn(meta['name'],json.dumps(result))
        self.assertNotIn('private-url',json.dumps(result))
        self.assertEqual(cloud.call_args.args[1],'HEAD')
        mutate.assert_not_called()
        clean.assert_not_called()
        write.assert_not_called()

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


class TerminalProofTests(unittest.TestCase):
    def setUp(self):
        self.stack=ExitStack()
        self.addCleanup(self.stack.close)
        self.directory=pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        self.meta=qa.fixture(qa.TERMINAL_QA_ID)
        self.folder=self.directory/'hospital-private'/self.meta['name']
        self.file=self.folder/'Consultation'/'2026'/('document_'+'b'*32+'.png')
        self.file.parent.mkdir(parents=True)
        self.file.write_bytes(b'0123456789')
        self.modified=time.time()-300
        os.utime(self.file,(self.modified,self.modified))
        self.resource='hospital-private/'+self.file.relative_to(self.folder.parent).as_posix()
        self.state={'jar':self.directory/'app.jar','pid':123,'properties':{},'args':['java'],'environment':{}}
        self.stack.enter_context(patch.object(qa.promotion,'digest',return_value=qa.TERMINAL_BACKEND_SHA256))
        self.stack.enter_context(patch.object(qa,'local_files',return_value=(self.folder,[self.file])))
        self.log=json.dumps({'MESSAGE':'Cloudflare R2 backup failed for '+self.resource+': Unexpected end of file from server',
                            '__REALTIME_TIMESTAMP':str(int((self.modified+100)*1000000))})
        self.run=self.stack.enter_context(patch.object(qa.promotion,'run',side_effect=['a'*32,self.log,'123']))
        self.dump=self.stack.enter_context(patch.object(qa,'read_thread_dump',return_value='Full thread dump\n"idle-scheduler" #1\n at java.util.concurrent.ThreadPoolExecutor.getTask(Native Method)\nJNI global refs: 1'))
        self.stack.enter_context(patch.object(qa,'backup_ready',return_value=False))
        fake_datetime=self.stack.enter_context(patch.object(qa.datetime,'datetime'))
        fake_datetime.now.return_value.hour=15
        fake_datetime.fromtimestamp.return_value.isoformat.return_value='2026-10-30T07:41:56+00:00'

    def test_pinned_quiescent_failed_put_is_confirmed(self):
        report=qa.confirm_pinned_terminal_failure(self.state,self.meta,self.resource,10)
        self.assertTrue(report['confirmed'])
        self.assertTrue(report['allThreadStacksChecked'])
        self.assertTrue(report['dailyScanInactive'])
        self.assertNotIn(self.resource,json.dumps(report))

    def test_active_backup_any_thread_or_daily_scan_blocks_release(self):
        for frame in ('at com.ruoyi.hospital.util.CloudflareR2StorageService.uploadObject(File.java:1)',
                      'at com.ruoyi.hospital.service.impl.TcmBackupScheduler.runDailyBackup(File.java:1)',
                      '"r2-file-backup-1" #2'):
            with self.subTest(frame=frame):
                self.run.side_effect=['a'*32,self.log,'123']
                self.dump.return_value=frame
                report=qa.confirm_pinned_terminal_failure(self.state,self.meta,self.resource,10)
                self.assertFalse(report['confirmed'])

    def test_missing_failure_evidence_does_not_release(self):
        self.run.side_effect=['a'*32,'']
        report=qa.confirm_pinned_terminal_failure(self.state,self.meta,self.resource,10)
        self.assertFalse(report['confirmed'])
        self.dump.assert_not_called()

    def test_daily_cron_override_blocks_release(self):
        self.state['properties']['tcm.backup.cron']='*/5 * * * * ?'
        report=qa.confirm_pinned_terminal_failure(self.state,self.meta,self.resource,10)
        self.assertFalse(report['confirmed'])
        self.run.assert_not_called()

    def test_confirmed_terminal_pending_is_persisted_then_exact_cleanup_completes(self):
        meta={**self.meta,'creationAttempted':True,'pendingBackups':{self.resource:10}}
        proof={'confirmed':True,'terminalFailure':'EOF','allThreadStacksChecked':True}
        with patch.object(qa,'confirm_pinned_terminal_failure',return_value=proof), \
             patch.object(qa.promotion,'query',side_effect=['0','0',self.resource,'0']), \
             patch.object(qa,'remove_qa_sessions',side_effect=[0,0]),patch.object(qa,'mutate') as mutate, \
             patch.object(qa,'private_write') as write,patch.object(qa.release,'r2_configuration',return_value={'backupEffective':True}), \
             patch.object(qa,'cloud_keys',return_value=[]):
            report=qa.cleanup(self.state,meta)
        self.assertTrue(report['completed'])
        self.assertFalse(self.file.exists())
        self.assertEqual(meta['pendingBackups'],{})
        self.assertEqual(report['pinnedTerminalFailuresResolved'],[proof])
        self.assertNotIn(self.resource,json.dumps(report))
        self.assertEqual(write.call_count,1)
        self.assertEqual(write.call_args.args[0],qa.ROOT/meta['qaId']/'fixture.json')
        self.assertEqual(mutate.call_count,2)


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
                    if not files:
                        raise urllib.error.HTTPError(path, 404, 'not found', {}, None)
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
            self.assertTrue(all(test['deletedSignedUrlRejected'] and test['repeatDelete'] for test in report['tests']))
            self.assertEqual(sum(method == 'DELETE' for _, method in calls), 4)
            self.assertTrue(meta['creationAttempted'])
            self.assertTrue(report['publicBookingPractitionersUnchanged'])
            self.assertIn('@qauser', mutate.call_args.args[1])
            self.assertNotIn('/api/patients', [path for path, _ in calls])
            clean.assert_called_once()


if __name__ == '__main__':
    unittest.main()
