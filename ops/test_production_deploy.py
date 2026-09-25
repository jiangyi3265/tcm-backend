import hashlib
import io
import json
import pathlib
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import production_deploy as deploy


class DeploymentTest(unittest.TestCase):
    def test_rejects_untrusted_artifact_urls(self):
        for url in ['http://example.blob.core.windows.net/file', 'https://localhost/file',
                    'https://example.blob.core.windows.net.evil.test/file',
                    'https://example.blob.core.windows.net:444/file']:
            with self.assertRaises(ValueError):
                deploy.receive_github_artifact(io.BytesIO(url.encode()), pathlib.Path('unused'), '0' * 64)

    def test_github_artifact_checksum_and_archive_validation(self):
        data = b'backend build'
        with tempfile.TemporaryDirectory() as directory:
            for name, expected, valid in [('ruoyi-admin.jar', hashlib.sha256(data).hexdigest(), True),
                                          ('ruoyi-admin.jar', '0' * 64, False), ('../escape', '0' * 64, False)]:
                archive = io.BytesIO()
                with zipfile.ZipFile(archive, 'w') as output:
                    output.writestr(name, data)
                archive.seek(0)
                with patch.object(deploy.urllib.request, 'build_opener') as opener:
                    opener.return_value.open.return_value = archive
                    target = pathlib.Path(directory) / 'artifact'
                    if valid:
                        deploy.receive_github_artifact(io.BytesIO(b'https://test.blob.core.windows.net/file'), target, expected)
                        self.assertEqual(target.read_bytes(), data)
                    else:
                        with self.assertRaises(ValueError):
                            deploy.receive_github_artifact(io.BytesIO(b'https://test.blob.core.windows.net/file'), target, expected)

    def test_rejects_non_deployment_commands(self):
        for command in ['bash', 'deploy ../outside ' + 'a' * 64, 'deploy ' + 'a' * 40 + ' ' + 'b' * 64 + '; id']:
            with self.assertRaises(ValueError):
                deploy.parse_command(command)
        self.assertEqual(deploy.parse_command('deploy ' + 'a' * 40 + ' ' + 'b' * 64), ('a' * 40, 'b' * 64))

    def test_rejects_corrupt_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            target = pathlib.Path(directory) / 'upload'
            with self.assertRaises(ValueError):
                deploy.receive(io.BytesIO(b'incomplete'), target, '0' * 64)
            deploy.receive(io.BytesIO(b'complete'), target, hashlib.sha256(b'complete').hexdigest())
            self.assertEqual(target.read_bytes(), b'complete')

    def test_rejects_archive_escape_and_links(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for name, kind in [('../escape', tarfile.REGTYPE), ('/absolute', tarfile.REGTYPE), ('link', tarfile.SYMTYPE)]:
                with tarfile.open(root / 'input.tar.gz', 'w:gz') as archive:
                    info = tarfile.TarInfo(name)
                    info.type = kind
                    if kind == tarfile.SYMTYPE:
                        info.linkname = '/etc/passwd'
                    archive.addfile(info)
                with self.assertRaises(ValueError):
                    deploy.extract_frontend(root / 'input.tar.gz', root / 'output', 'a' * 40)
            self.assertFalse((root / 'escape').exists())

    def test_validates_frontend_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            with tarfile.open(root / 'input.tar.gz', 'w:gz') as archive:
                for name, body in [('index.html', b'<html>ok</html>'), ('assets/main.js', b'// app'),
                                   ('version.json', json.dumps({'version': 'a' * 40}).encode())]:
                    info = tarfile.TarInfo(name)
                    info.size = len(body)
                    archive.addfile(info, io.BytesIO(body))
            deploy.extract_frontend(root / 'input.tar.gz', root / 'output', 'a' * 40)
            with self.assertRaises(AssertionError):
                deploy.extract_frontend(root / 'input.tar.gz', root / 'wrong', 'b' * 40)

    def test_backend_restores_previous_jar_when_health_check_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'current').mkdir()
            (root / 'backup').mkdir()
            (root / 'current/backend.jar').write_bytes(b'previous release')
            with zipfile.ZipFile(root / 'next.jar', 'w') as archive:
                archive.writestr('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class', b'entry')
                archive.writestr('BOOT-INF/lib/ruoyi-hospital-3.9.1.jar', b'module')
            with patch.object(deploy, 'BASE', root), patch.object(deploy, 'restart_backend', side_effect=[RuntimeError('unhealthy'), None]) as restart:
                with self.assertRaises(RuntimeError):
                    deploy.deploy_backend(root / 'next.jar', root / 'backup')
                self.assertEqual(restart.call_count, 2)
                self.assertEqual((root / 'current/backend.jar').read_bytes(), b'previous release')


if __name__ == '__main__':
    unittest.main()
