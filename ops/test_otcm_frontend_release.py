import base64
import hashlib
import io
import json
import os
import pathlib
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import otcm_frontend_release as release

OLD = 'a' * 40
NEW = 'b' * 40


def bundle(extra=None, version=NEW):
    data = {'index.html': b'<script src="/assets/new.js"></script>',
            'assets/new.js': b'new app', 'version.json': json.dumps({'version': version}).encode()}
    data.update(extra or {})
    result = io.BytesIO()
    with tarfile.open(fileobj=result, mode='w:gz') as archive:
        for name, value in data.items():
            item = tarfile.TarInfo(name)
            item.size = len(value)
            archive.addfile(item, io.BytesIO(value))
    return result.getvalue()


def request(mode='inspect', archive=None):
    archive = bundle() if archive is None else archive
    return {'target': '43.166.154.98', 'mode': mode, 'revision': NEW, 'expectedRevision': OLD,
            'publicBaseUrl': 'https://www.otcm.app', 'sha256': hashlib.sha256(archive).hexdigest(),
            'archiveBase64': base64.b64encode(archive).decode()}


class ArtifactGuards(unittest.TestCase):
    def test_valid_request(self):
        archive = bundle()
        self.assertEqual(release.validate_request(request(archive=archive)), archive)

    def test_wrong_target(self):
        with self.assertRaises(release.Guard):
            release.validate_request({**request(), 'target': '152.136.191.51'})

    def test_wrong_domain(self):
        with self.assertRaises(release.Guard):
            release.validate_request({**request(), 'publicBaseUrl': 'https://yuanyuanyiyuan.oksja.cn'})

    def test_mutated_artifact(self):
        with self.assertRaises(release.Guard):
            release.validate_request({**request(), 'sha256': '0' * 64})

    def test_bad_revision(self):
        for name in ('revision', 'expectedRevision'):
            with self.subTest(name=name), self.assertRaises(release.Guard):
                release.validate_request({**request(), name: '../master'})

    def test_unchanged_revision(self):
        with self.assertRaises(release.Guard):
            release.validate_request({**request(), 'revision': OLD})

    def test_no_implicit_publish(self):
        with self.assertRaises(release.Guard):
            release.validate_request({**request(), 'mode': 'deploy'})

    def test_extract_valid(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            release.extract(bundle(), root, NEW)
            self.assertEqual(len(release.tree_files(root)), 3)

    def test_traversal_dotfiles_absolute_backslash(self):
        for name in ('../outside', '/outside', 'assets/../../outside', '.user.ini', 'assets/.hidden', 'assets\\outside'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory, self.assertRaises(release.Guard):
                release.extract(bundle({name: b'bad'}), pathlib.Path(directory), NEW)

    def test_wrong_build_version(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(release.Guard):
            release.extract(bundle(version=OLD), pathlib.Path(directory), NEW)

    def test_server_executable_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(release.Guard):
            release.extract(bundle({'danger.php': b'php'}), pathlib.Path(directory), NEW)

    def test_archive_link_rejected(self):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode='w:gz') as archive:
            item = tarfile.TarInfo('index.html')
            item.type = tarfile.SYMTYPE
            item.linkname = '/etc/passwd'
            archive.addfile(item)
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(release.Guard):
            release.extract(data.getvalue(), pathlib.Path(directory), NEW)


class PublicHealth(unittest.TestCase):
    def test_booking_lazy_js_and_css_are_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            web = pathlib.Path(directory)
            contents = {'index.html': b'<script src="/assets/index-abc.js"></script>',
                        'assets/index-abc.js': b'entry',
                        'assets/PublicBookingView-abc.js': b'booking script',
                        'assets/PublicBookingView-def.css': b'booking styles'}
            (web / 'index.html').write_bytes(contents['index.html'])
            files = {name: hashlib.sha256(value).hexdigest() for name, value in contents.items()}
            read = []
            def fake_fetch(url):
                path = url.removeprefix(release.BASE).split('?', 1)[0]
                read.append(path)
                if path == '/version.json':
                    return json.dumps({'version': NEW}).encode()
                if path == '/api/public-booking/options':
                    return b'{"practitioners":[],"serviceTypes":[]}'
                return contents['index.html' if path == '/booking' else path.lstrip('/')]
            with patch.object(release, 'WEB', web), patch.object(release, 'fetch', side_effect=fake_fetch):
                result = release.health(NEW, files)
            self.assertEqual(result['bookingPageAssetsVerified'], 2)
            self.assertIn('/assets/PublicBookingView-abc.js', read)
            self.assertIn('/assets/PublicBookingView-def.css', read)
            files['assets/PublicBookingView-abc.js'] = '0' * 64
            with patch.object(release, 'WEB', web), patch.object(release, 'fetch', side_effect=fake_fetch), self.assertRaises(release.Guard):
                release.health(NEW, files)


@unittest.skipUnless(os.name == 'posix', 'POSIX permissions/renameat2 are verified on the Ubuntu workflow runner')
class AtomicPublication(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary.name)
        self.web = self.root / 'www.otcm.app'
        self.web.mkdir(mode=0o755)
        (self.web / 'assets').mkdir(mode=0o755)
        (self.web / 'assets/old.js').write_bytes(b'old app')
        (self.web / '.user.ini').write_bytes(b'original server settings')
        (self.web / '.well-known').mkdir(mode=0o755)
        (self.web / '.well-known/acme.txt').write_bytes(b'certificate challenge')
        (self.web / 'index.html').write_bytes(b'<script src="/assets/old.js"></script>')
        (self.web / 'version.json').write_text(json.dumps({'version': OLD}))
        self.before = release.tree_files(self.web)
        self.unpacked = self.root / 'unpacked'
        self.unpacked.mkdir()
        release.extract(bundle(), self.unpacked, NEW)
        self.backups = self.root / 'backups'
        self.patches = [patch.object(release, 'WEB', self.web), patch.object(release, 'BACKUPS', self.backups),
                        patch.object(release, 'backend_fingerprint', return_value={'pid': '123', 'files': {'jar': 'same'}}),
                        patch.object(release, 'health', return_value={'version': NEW})]
        for item in self.patches:
            item.start()

    def tearDown(self):
        for item in reversed(self.patches):
            item.stop()
        self.temporary.cleanup()

    def test_inspect_has_no_publication_or_backup(self):
        result = release.publish(request(), self.unpacked)
        self.assertTrue(result['ok'])
        self.assertEqual(self.before, release.tree_files(self.web))
        self.assertFalse(self.backups.exists())

    def test_publish_preserves_old_assets_server_files_and_backend(self):
        result = release.publish(request('publish'), self.unpacked)
        self.assertTrue(result['ok'])
        self.assertTrue(result['backendAndSecretsUnchanged'])
        self.assertFalse(result['backendRestarted'])
        self.assertEqual(result['databaseWrites'], 0)
        self.assertEqual((self.web / 'assets/old.js').read_bytes(), b'old app')
        self.assertEqual((self.web / '.user.ini').read_bytes(), b'original server settings')
        self.assertEqual((self.web / '.well-known/acme.txt').read_bytes(), b'certificate challenge')
        self.assertEqual(json.loads((self.web / 'version.json').read_text())['version'], NEW)
        self.assertEqual(release.tree_files(pathlib.Path(result['retainedTree'])), self.before)
        self.assertEqual(pathlib.Path(result['backup']).stat().st_mode & 0o777, 0o700)
        self.assertEqual(release.digest(pathlib.Path(result['backup']) / 'previous-web.tar.gz'), result['backupSha256'])

    def test_failed_health_rolls_back_every_original_file(self):
        with patch.object(release, 'health', side_effect=release.Guard('Test health failure')):
            result = release.publish(request('publish'), self.unpacked)
        self.assertFalse(result['ok'])
        self.assertTrue(result['rollbackPerformed'])
        self.assertEqual(release.tree_files(self.web), self.before)
        self.assertTrue(result['backendAndSecretsUnchanged'])

    def test_failed_exchange_keeps_original_tree(self):
        with patch.object(release, 'exchange', side_effect=release.Guard('Test unavailable exchange')):
            result = release.publish(request('publish'), self.unpacked)
        self.assertFalse(result['ok'])
        self.assertEqual(release.tree_files(self.web), self.before)

    def test_post_exchange_error_can_still_roll_back(self):
        original = release.exchange
        calls = 0
        def fault(left, right):
            nonlocal calls
            original(left, right)
            calls += 1
            if calls == 1:
                raise OSError('Simulate fsync failure after syscall')
        with patch.object(release, 'exchange', side_effect=fault):
            result = release.publish(request('publish'), self.unpacked)
        self.assertFalse(result['ok'])
        self.assertTrue(result['rollbackPerformed'])
        self.assertEqual(release.tree_files(self.web), self.before)

    def test_wrong_baseline_refused_before_backup(self):
        with self.assertRaises(release.Guard):
            release.publish({**request('publish'), 'expectedRevision': 'c' * 40}, self.unpacked)
        self.assertFalse(self.backups.exists())

    def test_existing_asset_collision_refused(self):
        (self.unpacked / 'assets/old.js').write_bytes(b'changed same name')
        with self.assertRaises(release.Guard):
            release.publish(request('publish'), self.unpacked)
        self.assertFalse(self.backups.exists())

    def test_preparation_backend_restart_refused_before_switch(self):
        with patch.object(release, 'backend_fingerprint', side_effect=[{'pid': '123'}, {'pid': '124'}]):
            result = release.publish(request('publish'), self.unpacked)
        self.assertFalse(result['ok'])
        self.assertEqual(release.tree_files(self.web), self.before)

    def test_symlink_in_webroot_refused(self):
        (self.web / 'unsafe').symlink_to('/etc/passwd')
        with self.assertRaises(release.Guard):
            release.publish(request('publish'), self.unpacked)


if __name__ == '__main__':
    unittest.main()
