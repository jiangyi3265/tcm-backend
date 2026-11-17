"""Resolve a newly uploaded GitHub artifact with bounded, credential-safe retries."""
import re
import time
import urllib.error
import urllib.parse
import urllib.request


RETRY_DELAYS = (2, 4, 8, 16, 30)
REQUEST_TIMEOUT = 20


class ArtifactDownloadError(RuntimeError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Only obtain the signed URL; never forward the GitHub token to storage.
        return None


def _signed_location(headers):
    location = (headers.get('Location') or '').strip()
    try:
        parsed = urllib.parse.urlsplit(location)
        valid = (len(location) <= 8192 and '\r' not in location and '\n' not in location
                 and parsed.scheme == 'https' and parsed.hostname
                 and not parsed.username and not parsed.password)
    except ValueError:
        valid = False
    if not valid:
        raise ArtifactDownloadError('GitHub artifact returned an invalid download location.') from None
    return location


def get_artifact_download_url(repository, artifact_id, token):
    """Return the storage redirect without downloading the archive or logging secrets.

    Six attempts at most: five bounded sleeps plus a 20-second timeout per request.
    Transient artifact visibility, rate limiting and transport errors may recover;
    authorization and all other unexpected responses fail immediately.
    """
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository or ''):
        raise ArtifactDownloadError('Invalid GitHub repository.')
    if not re.fullmatch(r'[0-9]+', artifact_id or ''):
        raise ArtifactDownloadError('Invalid GitHub artifact identity.')
    if not token or '\r' in token or '\n' in token:
        raise ArtifactDownloadError('Missing or invalid GitHub token.')
    request = urllib.request.Request(
        f'https://api.github.com/repos/{repository}/actions/artifacts/{artifact_id}/zip',
        headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
                 'X-GitHub-Api-Version': '2022-11-28'},
    )
    opener = urllib.request.build_opener(NoRedirect())
    for attempt in range(len(RETRY_DELAYS) + 1):
        status, headers = None, {}
        try:
            with opener.open(request, timeout=REQUEST_TIMEOUT) as response:
                status, headers = response.getcode(), response.headers
        except urllib.error.HTTPError as error:
            status, headers = error.code, error.headers or {}
            error.close()
        except (urllib.error.URLError, OSError):
            # Do not include provider text, URLs or headers in the raised error.
            pass
        if status == 302:
            return _signed_location(headers)
        transient = status is None or status in (404, 429) or 500 <= status <= 599
        if not transient:
            raise ArtifactDownloadError(f'GitHub artifact request failed (HTTP {status}).') from None
        if attempt == len(RETRY_DELAYS):
            reason = 'connection failure' if status is None else f'HTTP {status}'
            raise ArtifactDownloadError(f'GitHub artifact unavailable after 6 attempts ({reason}).') from None
        delay = RETRY_DELAYS[attempt]
        # Honor a numeric Retry-After without allowing an unbounded workflow wait.
        retry_after = headers.get('Retry-After', '')
        if retry_after.isascii() and retry_after.isdigit():
            delay = min(30, max(delay, int(retry_after) if len(retry_after) <= 6 else 30))
        time.sleep(delay)
