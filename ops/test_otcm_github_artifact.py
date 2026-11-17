"""Synthetic HTTP responses only: no network, token or production access."""
import contextlib
import io
import traceback
import unittest
import urllib.error
from email.message import Message
from unittest.mock import Mock, patch

import otcm_github_artifact as artifact


TOKEN = 'synthetic-secret-token'
SIGNED_URL = 'https://fixture.blob.core.windows.net/release.zip?signature=synthetic-secret-signature'


def response_error(status, headers=None):
    message = Message()
    for key, value in (headers or {}).items():
        message[key] = value
    return urllib.error.HTTPError('https://api.github.com/secret-path', status,
                                  'private-provider-response-' + TOKEN, message, io.BytesIO(b'private-body'))


def redirect():
    return response_error(302, {'Location': SIGNED_URL})


class ArtifactTests(unittest.TestCase):
    def resolve(self, results):
        opener = Mock()
        opener.open.side_effect = results
        output = io.StringIO()
        with patch.object(artifact.urllib.request, 'build_opener', return_value=opener) as build, \
                patch.object(artifact.time, 'sleep') as sleep, contextlib.redirect_stdout(output), \
                contextlib.redirect_stderr(output):
            try:
                result = artifact.get_artifact_download_url('owner/repository', '12345', TOKEN)
                error = None
            except artifact.ArtifactDownloadError as exc:
                result, error = None, exc
                traceback.print_exc()
            finally:
                for response in results:
                    if isinstance(response, urllib.error.HTTPError):
                        response.close()
        self.assertNotIn(TOKEN, output.getvalue())
        self.assertNotIn(SIGNED_URL, output.getvalue())
        self.assertNotIn('private-provider-response', output.getvalue())
        self.assertNotIn('private-body', output.getvalue())
        self.assertIsInstance(build.call_args.args[0], artifact.NoRedirect)
        for call in opener.open.call_args_list:
            request = call.args[0]
            self.assertEqual(request.full_url, 'https://api.github.com/repos/owner/repository/actions/artifacts/12345/zip')
            self.assertEqual(request.get_header('Authorization'), 'Bearer ' + TOKEN)
            self.assertEqual(call.kwargs['timeout'], 20)
        return result, error, opener.open.call_count, [call.args[0] for call in sleep.call_args_list]

    def test_new_artifact_visibility_recovers_without_following_storage_redirect(self):
        result, error, calls, sleeps = self.resolve([response_error(404), response_error(404), redirect()])
        self.assertEqual(result, SIGNED_URL)
        self.assertIsNone(error)
        self.assertEqual((calls, sleeps), (3, [2, 4]))
        self.assertIsNone(artifact.NoRedirect().redirect_request(None, None, 302, '', {}, SIGNED_URL))

    def test_rate_limit_and_server_errors_retry(self):
        for status in (429, 500, 502, 503, 504, 599):
            with self.subTest(status=status):
                result, error, calls, sleeps = self.resolve([response_error(status), redirect()])
                self.assertEqual(result, SIGNED_URL)
                self.assertEqual((calls, sleeps), (2, [2]))
                self.assertIsNone(error)

    def test_transport_errors_retry_without_disclosing_exception_text(self):
        for error in (urllib.error.URLError(TOKEN), TimeoutError(SIGNED_URL), ConnectionResetError(TOKEN)):
            with self.subTest(kind=type(error).__name__):
                result, failure, calls, sleeps = self.resolve([error, redirect()])
                self.assertEqual(result, SIGNED_URL)
                self.assertEqual((calls, sleeps), (2, [2]))
                self.assertIsNone(failure)

    def test_attempt_limit_is_fixed_and_exhaustion_is_sanitized(self):
        for status in (None, 404, 429, 503):
            with self.subTest(status=status):
                results = [urllib.error.URLError(TOKEN) if status is None else response_error(status) for _ in range(6)]
                result, error, calls, sleeps = self.resolve(results)
                self.assertIsNone(result)
                self.assertIn('after 6 attempts', str(error))
                self.assertEqual((calls, sleeps), (6, [2, 4, 8, 16, 30]))

    def test_permission_errors_and_other_unexpected_statuses_fail_immediately(self):
        for status in (400, 401, 403, 410, 422):
            with self.subTest(status=status):
                result, error, calls, sleeps = self.resolve([response_error(status), redirect()])
                self.assertIsNone(result)
                self.assertIn(f'HTTP {status}', str(error))
                self.assertEqual((calls, sleeps), (1, []))

    def test_retry_after_is_bounded(self):
        for value, delay in [('12', 12), ('3600', 30), ('0', 2), ('garbage', 2), ('-1', 2)]:
            with self.subTest(value=value):
                _, error, calls, sleeps = self.resolve([response_error(429, {'Retry-After': value}), redirect()])
                self.assertIsNone(error)
                self.assertEqual((calls, sleeps), (2, [delay]))

    def test_missing_or_invalid_redirect_fails_without_disclosing_the_location(self):
        for location in ('', 'http://fixture.blob.core.windows.net/a', 'https:///missing-host?' + TOKEN,
                         'https://user:password@fixture.blob.core.windows.net/a'):
            with self.subTest(location=location):
                result, error, calls, sleeps = self.resolve([response_error(302, {'Location': location})])
                self.assertIsNone(result)
                self.assertIn('invalid download location', str(error))
                self.assertEqual((calls, sleeps), (1, []))

    def test_invalid_inputs_fail_before_any_network_access(self):
        for repository, identity, token in [('owner/repo/extra', '123', TOKEN), ('owner/repo', 'abc', TOKEN),
                                             ('owner/repo', '123', ''), ('owner/repo', '123', 'a\r\nb')]:
            with self.subTest(repository=repository, identity=identity):
                with patch.object(artifact.urllib.request, 'build_opener') as build:
                    with self.assertRaises(artifact.ArtifactDownloadError):
                        artifact.get_artifact_download_url(repository, identity, token)
                    build.assert_not_called()


if __name__ == '__main__':
    unittest.main()
