#!/usr/bin/env python3
"""Fixed OTCM backend-only release; no schema, patient, frontend or config edits."""
import datetime
import gzip
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.parse
import zipfile

import otcm_promotion as promotion
import otcm_session_config as session
import otcm_frontend_release as frontend

REQUEST = {}
APP = promotion.APP
WEB = promotion.WEB
BACKUPS = pathlib.Path('/www/backup/otcm-backend-releases')
LOCK = pathlib.Path('/www/backup/otcm-releases/release.lock')


class Guard(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise Guard(message)


def validate_request(request):
    require(request.get('target') == '43.166.154.98', 'Wrong production target')
    require(request.get('mode') in ('inspect', 'publish'), 'Invalid release mode')
    for name, length in [('expectedBackendSha256', 64), ('expectedFrontendRevision', 40)]:
        require(re.fullmatch('[0-9a-f]{%d}' % length, request.get(name, '')), 'Invalid expected release identity')
    if request['mode'] == 'publish':
        require(re.fullmatch(r'[0-9a-f]{40}', request.get('revision', '')), 'Invalid backend revision')
        require(re.fullmatch(r'[0-9a-f]{64}', request.get('sha256', '')), 'Invalid backend digest')
        require(request['sha256'] != request['expectedBackendSha256'], 'Release must change the backend')
        url = request.get('artifactUrl', '')
        parsed = urllib.parse.urlsplit(url)
        require(len(url) <= 8192 and parsed.scheme == 'https' and not parsed.username and not parsed.password
                and parsed.port in (None, 443) and parsed.hostname
                and parsed.hostname.endswith(('.blob.core.windows.net', '.actions.githubusercontent.com')),
                'Untrusted artifact URL')


def validate_current(request):
    state = promotion.runtime()
    require(state['cwd'] == APP and state['jar'] == APP / 'yy.jar', 'Unexpected service paths')
    require(promotion.digest(state['jar']) == request['expectedBackendSha256'], 'Current backend differs from reviewed release')
    require(json.loads((WEB / 'version.json').read_text()) == {'version': request['expectedFrontendRevision']},
            'Current frontend differs from reviewed release')
    require(session.PRESERVED.resolve() == session.PRESERVED and session.PRESERVED.is_file(), 'Unexpected preserved config path')
    require(stat.S_IMODE(session.PRESERVED.stat().st_mode) == 0o600, 'Unexpected preserved config permissions')
    require(str(session.PRESERVED) in state['environment'].get('SPRING_CONFIG_ADDITIONAL_LOCATION', ''),
            'Preserved config is not explicitly loaded')
    require(session.health(), 'Existing service is not healthy')
    return state


def patient_diagnostics(state):
    indexes = promotion.query(state, "SELECT NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                              "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                              "AND TABLE_NAME='tcm_patient' GROUP BY INDEX_NAME,NON_UNIQUE")
    result = []
    for line in indexes.splitlines():
        non_unique, columns = line.split('\t')
        require(non_unique in ('0', '1') and re.fullmatch(r'[a-z_,]+', columns), 'Unexpected patient index shape')
        result.append({'unique': non_unique == '0', 'columns': columns.split(',')})
    groups = promotion.query(state, "SELECT COUNT(*),COALESCE(SUM(n),0) FROM (SELECT COUNT(*) n FROM tcm_patient "
                             "WHERE deleted_at IS NULL AND merged_into IS NULL AND TRIM(COALESCE(email,''))<>'' "
                             "GROUP BY LOWER(TRIM(email)) HAVING COUNT(*)>1) shared")
    numbers = groups.split('\t')
    require(len(numbers) == 2 and all(x.isdigit() for x in numbers), 'Unexpected family aggregate shape')
    source = "(SELECT patient_id,CASE WHEN JSON_VALID(payload) THEN payload ELSE '{}' END AS j FROM tcm_appointment) snapshots"
    predicate = "LOWER(JSON_UNQUOTE(JSON_EXTRACT(j,'$.bookingSource')))='public' "
    name = "LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j,'$.patientName')),'')))"
    snapshots = promotion.query(state, "SELECT COUNT(*) FROM (SELECT patient_id FROM " + source + " WHERE "
        + predicate + "AND " + name + "<>'' GROUP BY patient_id HAVING COUNT(DISTINCT " + name + ")>1) mixed")
    require(snapshots.isdigit(), 'Unexpected booking snapshot aggregate')
    mismatch = promotion.query(state, "SELECT COUNT(*),COUNT(DISTINCT snapshots.patient_id) FROM " + source
        + " JOIN tcm_patient p ON p.id=snapshots.patient_id WHERE " + predicate + "AND " + name
        + "<>'' AND " + name + "<>LOWER(TRIM(COALESCE(p.name,'')))")
    mismatches = mismatch.split('\t')
    require(len(mismatches) == 2 and all(x.isdigit() for x in mismatches), 'Unexpected booking name aggregate')
    detail = patient_name_detail(state, int(mismatches[1]), int(mismatches[0]))
    return {'indexes': result, 'sharedEmailGroups': int(numbers[0]), 'patientsInSharedEmailGroups': int(numbers[1]),
            'patientsWithMultiplePublicBookingNames': int(snapshots),
            'publicBookingSnapshotsWithDifferentCurrentName': int(mismatches[0]),
            'patientsWithPublicBookingNameMismatch': int(mismatches[1]),
            'nameDifferenceBreakdown': detail,
            'scope': 'Aggregate only; no patient names, addresses or identifiers returned.'}


NAME_DIFFERENCE_CATEGORIES = ('caseOrWhitespaceOnly', 'displayOrderCompatible', 'structuredNameMatches',
                              'structuredNameConflict', 'legacyOrderAmbiguous', 'insufficientNameData',
                              'unexplainedNameDifference')
MAX_NAME_REVIEW_PROFILES = 6
MAX_NAME_REVIEW_SNAPSHOTS = 5000


def identity_text(value):
    # Deliberately do not strip accents, punctuation or reorder name tokens.
    # Unicode whitespace + lower-case match the new booking normalisation.
    return ' '.join(value.split()).lower() if isinstance(value, str) else ''


def classify_name_difference(row):
    current = identity_text(row.get('currentName'))
    submitted = identity_text(row.get('submittedName'))
    current_parts = tuple(identity_text(row.get(key)) for key in ('currentFirst', 'currentLast'))
    submitted_parts = tuple(identity_text(row.get(key)) for key in ('submittedFirst', 'submittedLast'))
    if all(current_parts) and all(submitted_parts):
        if current_parts != submitted_parts:
            return 'structuredNameConflict'
        if current and current == submitted:
            return 'caseOrWhitespaceOnly'
        forms = {' '.join(current_parts), ' '.join(reversed(current_parts))}
        return 'displayOrderCompatible' if current in forms and submitted in forms else 'structuredNameMatches'
    # A differing known first/last part cannot be explained away by a display name.
    if any(left and right and left != right for left, right in zip(current_parts, submitted_parts)):
        return 'structuredNameConflict'
    if current and current == submitted:
        return 'caseOrWhitespaceOnly'
    complete = current_parts if all(current_parts) else submitted_parts if all(submitted_parts) else None
    if complete:
        forms = {' '.join(complete), ' '.join(reversed(complete))}
        if current in forms and submitted in forms:
            return 'displayOrderCompatible'
    if not current or not submitted:
        return 'insufficientNameData'
    if not complete and len(current.split()) == 2 and current.split() == list(reversed(submitted.split())):
        return 'legacyOrderAmbiguous'
    return 'unexplainedNameDifference'


def summarize_name_rows(rows, expected_profiles, expected_mismatches):
    require(len(rows) <= MAX_NAME_REVIEW_SNAPSHOTS, 'Historical name review exceeded snapshot bound')
    counts = {key: 0 for key in NAME_DIFFERENCE_CATEGORIES}
    profiles = {}
    for row in rows:
        require(isinstance(row, dict) and isinstance(row.get('profile'), str) and row['profile'], 'Invalid name review row')
        require(type(row.get('baselineMismatch')) in (int, bool) and row['baselineMismatch'] in (0, 1), 'Invalid mismatch marker')
        record = profiles.setdefault(row['profile'], {'categories': set(), 'namesByEmail': {}, 'structuredNames': set()})
        if row['baselineMismatch']:
            category = classify_name_difference(row)
            counts[category] += 1
            record['categories'].add(category)
        pair = tuple(identity_text(row.get(key)) for key in ('submittedFirst', 'submittedLast'))
        if all(pair):
            record['structuredNames'].add(pair)
            email = row['submittedEmail'].strip().lower() if isinstance(row.get('submittedEmail'), str) else ''
            if email:
                record['namesByEmail'].setdefault(email, set()).add(pair)
    require(len(profiles) == expected_profiles and sum(counts.values()) == expected_mismatches,
            'Historical candidate set changed during read-only inspection')
    require(len(profiles) <= MAX_NAME_REVIEW_PROFILES, 'Historical name review exceeded profile bound')
    # Matching split fields do not prove that an arbitrary conflicting display
    # name is harmless: the display field may have been overwritten separately.
    explained = {'caseOrWhitespaceOnly', 'displayOrderCompatible'}
    multiple_names = sum(len(record['structuredNames']) > 1 for record in profiles.values())
    shared_email_names = sum(any(len(names) > 1 for names in record['namesByEmail'].values()) for record in profiles.values())
    explained_profiles = sum(bool(record['categories']) and record['categories'] <= explained
                             and len(record['structuredNames']) <= 1 for record in profiles.values())
    return {'available': True, 'readOnly': True, 'databaseWrites': 0, 'profilesReviewed': len(profiles),
            'relatedPublicSnapshotsReviewed': len(rows), 'baselineMismatchedSnapshotsReviewed': sum(counts.values()),
            'snapshotCategories': counts, 'profilesExplainedByFormattingOrStructuredName': explained_profiles,
            'profilesRequiringRecordReview': len(profiles) - explained_profiles,
            'profilesWithMultipleStructuredSubmittedNames': multiple_names,
            'profilesWithMultipleStructuredNamesAtSameEmail': shared_email_names,
            'scope': 'Original display-name mismatch candidates only; names, emails and IDs stay in server process memory.',
            'interpretation': 'Compatibility is not proof of identity. Different submitted names may reflect a correction or shared-email misassociation; no clinical records are changed.'}


def patient_name_detail(state, expected_profiles, expected_mismatches):
    if expected_profiles > MAX_NAME_REVIEW_PROFILES:
        return {'available': False, 'reason': 'candidate-profile-bound-exceeded', 'readOnly': True, 'databaseWrites': 0}
    if not expected_profiles:
        return summarize_name_rows([], 0, expected_mismatches)
    source = "(SELECT patient_id,CASE WHEN JSON_VALID(payload) THEN payload ELSE '{}' END AS j FROM tcm_appointment)"
    public = lambda alias: "LOWER(JSON_UNQUOTE(JSON_EXTRACT(" + alias + ".j,'$.bookingSource')))='public'"
    display = lambda alias: "LOWER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(" + alias + ".j,'$.patientName')),'')))"
    mismatch = lambda alias, patient: display(alias) + "<>'' AND " + display(alias) + "<>LOWER(TRIM(COALESCE(" + patient + ".name,'')))"
    candidates = "SELECT DISTINCT candidate.patient_id FROM " + source + " candidate JOIN tcm_patient cp ON cp.id=candidate.patient_id WHERE " + public('candidate') + ' AND ' + mismatch('candidate', 'cp')
    def text_field(key):
        field = "JSON_EXTRACT(snapshots.j,'$." + key + "')"
        return "CASE WHEN JSON_TYPE(" + field + ")='STRING' THEN JSON_UNQUOTE(" + field + ") ELSE NULL END"
    fields = ["'profile',p.id", "'currentName',p.name", "'currentFirst',p.first_name", "'currentLast',p.last_name"]
    for output, key in [('submittedName', 'patientName'), ('submittedFirst', 'firstName'), ('submittedLast', 'lastName'), ('submittedEmail', 'email')]:
        fields.append("'" + output + "'," + text_field(key))
    fields.append("'baselineMismatch',(" + mismatch('snapshots', 'p') + ')')
    # Recheck the candidate count inside the same read view before projecting
    # identities. If it changed, return no identities instead of widening scope.
    same_scope = '(SELECT COUNT(*) FROM (' + candidates + ') candidate_count)=' + str(expected_profiles)
    sql = "SELECT JSON_OBJECT(" + ','.join(fields) + ') FROM ' + source + ' snapshots JOIN tcm_patient p ON p.id=snapshots.patient_id JOIN (' + candidates + ') candidates ON candidates.patient_id=p.id WHERE ' + public('snapshots') + ' AND ' + same_scope + ' LIMIT ' + str(MAX_NAME_REVIEW_SNAPSHOTS + 1)
    try:
        database, env, options = promotion.mysql(state)
        # --raw preserves JSON escaping rather than applying mysql's second
        # layer of backslash escaping. Raw identity data is never printed/saved.
        output = promotion.run(['mysql'] + options + ['--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4', database],
                               input=('START TRANSACTION READ ONLY; ' + sql + '; ROLLBACK;').encode(), env=env)
        require(len(output.encode()) <= 8 * 1024 * 1024, 'Historical name review response exceeded bound')
        rows = [json.loads(line) for line in output.splitlines()]
        return summarize_name_rows(rows, expected_profiles, expected_mismatches)
    except Exception as error:
        # No exception message or raw row can leave this process, including JSON errors.
        return {'available': False, 'reason': 'bounded-name-review-unavailable', 'errorType': type(error).__name__,
                'readOnly': True, 'databaseWrites': 0}


def upload_log_summary(text):
    counts = {}
    for match in re.finditer(r'"(?:POST|PUT) /api/files/upload(?:\?[^\s"]*)? HTTP/[^\s"]+"\s+(\d{3})\b', text):
        code = match[1]
        counts[code] = counts.get(code, 0) + 1
    errors = {name: sum('/api/files/upload' in line and phrase in line.lower() for line in text.splitlines())
              for name, phrase in [('bodyTooLarge', 'too large body'), ('permissionDenied', 'permission denied'),
                                   ('upstreamTimeout', 'upstream timed out'), ('connectionRefused', 'connection refused')]}
    return {'httpStatusCounts': counts, 'errorCounts': errors}


def r2_configuration(properties):
    configured = all(str(properties.get('tcm.r2.' + key) or '').strip()
                     for key in ('endpoint', 'bucket', 'access-key-id', 'secret-access-key'))
    enabled = str(properties.get('tcm.r2.enabled', 'false')).lower() == 'true'
    on_write = str(properties.get('tcm.r2.backup-on-write', 'true')).lower() == 'true'
    return {'enabled': enabled, 'configured': bool(configured), 'backupOnWrite': on_write,
            'backupEffective': enabled and bool(configured) and on_write}


def upload_journal_summary(entries):
    counts = {'r2BackupFailure': 0, 'r2RestoreFailure': 0, 'uploadFailure': 0, 'timeout': 0, 'permissionDenied': 0}
    exceptions = {}
    related = malformed = 0
    allowed = promotion.SAFE_EXCEPTION_CLASSES | {'IOException', 'MultipartException', 'MaxUploadSizeExceededException', 'ClientAbortException'}
    for line in entries:
        try:
            item = json.loads(line)
        except (TypeError, ValueError):
            malformed += 1
            continue
        message = item.get('MESSAGE') if isinstance(item, dict) else None
        if not isinstance(message, str) or not re.search(
                r'Cloudflare R2 (?:backup|restore) failed|file upload failed|TcmFileController|/api/files/upload|HospitalFileStorage|CloudflareR2StorageService', message):
            continue
        related += 1
        lower = message.lower()
        for key, phrase in [('r2BackupFailure', 'cloudflare r2 backup failed'), ('r2RestoreFailure', 'cloudflare r2 restore failed'),
                            ('uploadFailure', 'file upload failed'), ('permissionDenied', 'permission denied')]:
            counts[key] += int(phrase in lower)
        counts['timeout'] += int(any(value in lower for value in ('timed out', 'timeoutexception', 'timeout')))
        for name in set(re.findall(r'\b[A-Za-z][A-Za-z0-9]*(?:Exception|Error)\b', message)) & allowed:
            exceptions[name] = exceptions.get(name, 0) + 1
    return {'matchingEntries': related, 'categories': counts, 'exceptionClasses': exceptions, 'unparsedEntries': malformed}


def recent_upload_journal():
    try:
        invocation = promotion.run(['systemctl', 'show', promotion.SERVICE, '--property=InvocationID', '--value'])
        require(re.fullmatch(r'[0-9a-f]{32}', invocation), 'Service invocation unavailable')
        limit = 20000
        raw = promotion.run(['journalctl', '--unit=' + promotion.SERVICE, '--boot', '--since=48 hours ago',
                             '--output=json', '--no-pager', '--lines=' + str(limit + 1), '_SYSTEMD_INVOCATION_ID=' + invocation])
        entries = raw.splitlines()
        return {'available': True, **upload_journal_summary(entries[-limit:]), 'truncated': len(entries) > limit,
                'scope': 'Current invocation, last 48 hours. Only entries explicitly identifying upload or R2; no journal text returned.'}
    except Exception as error:
        return {'available': False, 'errorType': type(error).__name__}


def request_timing_summary(text, log_format):
    """Extract durations using the actual declared nginx format, never guessed columns."""
    tokens = re.split(r'(\$[a-zA-Z_][a-zA-Z0-9_]*)', log_format)
    pattern, fields = '', set()
    for token in tokens:
        if token.startswith('$'):
            name = token[1:]
            if name in ('request', 'request_time', 'upstream_response_time') and name not in fields:
                pattern += '(?P<' + name + '>.*?)'
                fields.add(name)
            else:
                pattern += '.*?'
        else:
            pattern += re.escape(token)
    if 'request' not in fields or not fields.intersection({'request_time', 'upstream_response_time'}):
        return {'available': False}
    values = {'request_time': [], 'upstream_response_time': []}
    for line in text.splitlines():
        match = re.fullmatch(pattern, line)
        if not match or not re.match(r'(?:POST|PUT) /api/files/upload(?:[?\s])', match.group('request')):
            continue
        for name in values:
            value = match.groupdict().get(name, '')
            # Multiple upstream attempts are aggregated as their total duration.
            if re.fullmatch(r'[0-9]+(?:\.[0-9]+)?(?:\s*[, :]\s*[0-9]+(?:\.[0-9]+)?)*', value):
                values[name].append(sum(float(number) for number in re.findall(r'[0-9]+(?:\.[0-9]+)?', value)))
    result = {'available': True}
    for name, samples in values.items():
        result[name] = {'samples': len(samples), 'minSeconds': round(min(samples), 3) if samples else None,
                        'maxSeconds': round(max(samples), 3) if samples else None,
                        'meanSeconds': round(sum(samples) / len(samples), 3) if samples else None}
    return result


def upload_diagnostics(state):
    configs = [pathlib.Path('/www/server/nginx/conf/nginx.conf')]
    vhosts = pathlib.Path('/www/server/panel/vhost/nginx')
    if vhosts.is_dir():
        configs.extend(path for path in vhosts.glob('*.conf') if 'otcm.app' in path.name)
    limits, logs, formats = [], {}, {}
    for path in configs:
        if not path.is_file():
            continue
        content = path.read_text(errors='replace')
        for match in re.finditer(r'(?ms)^\s*log_format\s+(\w+)\s+((?:[^;\'\"]|\'[^\']*\'|\"[^\"]*\")*);', content):
            quoted = re.findall(r"'([^']*)'|\"([^\"]*)\"", match[2])
            if quoted:
                formats[match[1]] = ''.join(left or right for left, right in quoted)
        values = re.findall(r'(?m)^\s*client_max_body_size\s+([0-9]+[kKmMgG]?)\s*;', content)
        limits.append({'scope': 'global' if path.name == 'nginx.conf' else 'OTCM vhost', 'declaredBodyLimits': values})
        if path.name != 'nginx.conf':
            for match in re.finditer(r'(?m)^\s*(access_log|error_log)\s+([^;\s]+)(?:\s+([\w]+))?', content):
                if match[2].startswith('/www/wwwlogs/') and 'otcm.app' in match[2]:
                    logs[pathlib.Path(match[2])] = (match[3] or 'combined') if match[1] == 'access_log' else None
    text = ''
    timings = []
    for path, format_name in sorted(logs.items()):
        if path.is_file():
            with path.open('rb') as source:
                source.seek(max(0, path.stat().st_size - 2 * 1024 * 1024))
                log_text = source.read().decode('utf-8', errors='replace')
                text += log_text + '\n'
                if format_name:
                    timings.append(request_timing_summary(log_text, formats.get(format_name, '')))
    storage = []
    for key in ('ruoyi.profile', 'tcm.file.storage-root'):
        value = state['properties'].get(key)
        if not value or not pathlib.Path(value).is_absolute():
            continue
        path = pathlib.Path(value)
        storage.append({'setting': key, 'exists': path.exists(), 'isDirectory': path.is_dir(),
                        'mode': oct(stat.S_IMODE(path.stat().st_mode)) if path.exists() else None})
    return {'nginx': limits, 'logsExamined': len(logs), 'recentLogTail': upload_log_summary(text),
            'storage': storage, 'r2': r2_configuration(state['properties']), 'recentBackend': recent_upload_journal(),
            'requestTimings': timings,
            'scope': 'Declared limits only; includes may override. Bounded log tails, no log text returned.'}


EMAIL_TYPES = frozenset({'appointment_confirmation', 'appointment_change', 'appointment_change_internal',
    'appointment_internal_new', 'appointment_cancel', 'appointment_cancel_internal', 'appointment_aftercare',
    'appointment_reminder', 'appointment_follow_up', 'consent', 'intake', 'invoice', 'consultation',
    'consultation_record', 'manual', 'general'})
EMAIL_TEMPLATES = frozenset({'appointmentConfirmation', 'appointmentCancellation', 'reminder', 'consent', 'intake',
    'consultationRecord', 'invoice', 'appointmentChange', 'aftercare', 'followUp', 'internalBooking',
    'internalAppointmentChange', 'internalAppointmentCancellation'})
EMAIL_ERROR_CATEGORIES = ('auth', 'quota', 'dailyLimit', 'rateLimit', 'recipientRejected', 'senderDomain',
                          'connectionTimeout', 'TLS', 'unknown')
SMTP_REPLY_CODES = frozenset({'421', '450', '451', '452', '454', '500', '501', '502', '503', '504',
                              '530', '534', '535', '538', '550', '551', '552', '553', '554'})
SMTP_ERROR_CODES = frozenset({'SMTP_AUTH_FAILED', 'SMTP_CONNECTION_FAILED', 'SMTP_NEW_PASSWORD_REQUIRED',
    'SMTP_TLS_REQUIRED', 'SMTP_SETTINGS_INVALID', 'SMTP_PASSWORD_INVALID', 'SMTP_SECRET_UNAVAILABLE_REENTER_KEY',
    'SMTP_HOST_INVALID', 'SMTP_PORT_INVALID', 'SMTP_USERNAME_INVALID', 'SMTP_FROM_INVALID'})
MAX_EMAIL_DIAGNOSTIC_ROWS = 20000
RECIPIENT_FLAGS = ('empty', 'reservedTestDomain', 'hasWhitespace', 'hasComma', 'multipleAt',
                   'missingAt', 'invalidSimpleAddressShape')


def classify_email_failure(error, supplied_code):
    """Only fixed categories/codes leave this function, never provider text."""
    error = error if isinstance(error, str) else ''
    supplied_code = str(supplied_code) if isinstance(supplied_code, (str, int)) else ''
    code = supplied_code if supplied_code in SMTP_REPLY_CODES | SMTP_ERROR_CODES else 'unavailable'
    if code == 'unavailable':
        matches = set(re.findall(r'\bSMTP_[A-Z_]+\b', error)) & SMTP_ERROR_CODES
        if len(matches) == 1:
            code = next(iter(matches))
    if code == 'unavailable':
        replies = set(re.findall(r'(?:^|[\s;:])([45][0-9]{2})(?=[\s-]|$)', error)) & SMTP_REPLY_CODES
        if len(replies) == 1:
            code = next(iter(replies))
    text = (error + ' ' + code).lower()
    patterns = (
        ('dailyLimit', r'daily.{0,40}(?:limit|quota)|(?:limit|quota).{0,40}(?:per day|daily)'),
        ('rateLimit', r'rate.?limit|too many requests|throttl'),
        ('quota', r'quota|credit.{0,20}(?:exhaust|limit)|monthly.{0,20}limit'),
        ('auth', r'authenticationfailed|mailauthentication|authentication (?:failed|required)|invalid credentials|smtp_auth_failed|smtp_new_password_required|smtp_password_invalid'),
        ('TLS', r'sslhandshake|certificate|starttls|tls (?:required|failed|error)|smtp_tls_required'),
        ('connectionTimeout', r'(?:connect|read|write|socket|connection).{0,24}(?:timed? out|timeout)|sockettimeoutexception'),
        ('senderDomain', r'(?:sender|from address|sending domain|domain).{0,45}(?:not verified|unverified|not authorized|not allowed|verification|required)'),
        ('recipientRejected', r'recipient.{0,30}(?:reject|invalid|unknown)|unknown user|user unknown|mailbox (?:unavailable|not found)|no such user'),
    )
    category = next((name for name, pattern in patterns if re.search(pattern, text)), 'unknown')
    if category == 'unknown' and code in ('530', '534', '535'):
        category = 'auth'
    return category, code


def summarize_email_rows(rows, service_uptime):
    require(len(rows) <= MAX_EMAIL_DIAGNOSTIC_ROWS, 'Email diagnostic row bound exceeded')
    windows = {'last1Hour': 3600, 'last24Hours': 86400, 'last7Days': 604800,
               'currentBackendInvocation': service_uptime, 'allRecordedHistory': None}
    result = {key: {'sent': 0, 'failed': 0, 'unknown': 0, 'failureCategories': {name: 0 for name in EMAIL_ERROR_CATEGORIES},
                    'smtpCodes': {}, 'byTemplateAndType': {}} for key in windows}
    latest = {'sent': None, 'failed': None}
    future = missing_time = generic_failures = 0
    for row in rows:
        require(isinstance(row, dict) and set(row) in ({'ageSeconds', 'delivery', 'emailType', 'template', 'error', 'smtpCode'},
                {'ageSeconds', 'delivery', 'emailType', 'template', 'error', 'smtpCode', 'failureDetail'}),
                'Unexpected email diagnostic shape')
        age, delivery = row['ageSeconds'], row['delivery']
        require(age is None or type(age) is int, 'Unexpected email age')
        require(delivery in ('sent', 'failed', 'unknown'), 'Unexpected email delivery state')
        email_type = row['emailType'] if row['emailType'] in EMAIL_TYPES else 'other'
        template = row['template'] if row['template'] in EMAIL_TEMPLATES else 'other'
        category, code = classify_email_failure(row['error'], row['smtpCode']) if delivery == 'failed' else ('unknown', 'unavailable')
        generic_failures += int(delivery == 'failed' and row['error'] == 'SMTP delivery failed. Check the SMTP settings and connection test.')
        future += int(age is not None and age < 0)
        missing_time += int(age is None)
        if delivery in latest and age is not None and age >= 0:
            latest[delivery] = age if latest[delivery] is None else min(latest[delivery], age)
        for name, maximum in windows.items():
            if maximum is not None and (age is None or age < 0 or age > maximum):
                continue
            window = result[name]
            window[delivery] += 1
            key = template + '/' + email_type
            group = window['byTemplateAndType'].setdefault(key, {'template': template, 'emailType': email_type,
                                                               'sent': 0, 'failed': 0, 'unknown': 0})
            group[delivery] += 1
            if delivery == 'failed':
                window['failureCategories'][category] += 1
                window['smtpCodes'][code] = window['smtpCodes'].get(code, 0) + 1
    for window in result.values():
        window['byTemplateAndType'] = [value for _, value in sorted(window['byTemplateAndType'].items())]
    return {'windows': result, 'latestSuccessAgeSeconds': latest['sent'], 'latestFailureAgeSeconds': latest['failed'],
            'futureDatedRows': future, 'missingCreateTimeRows': missing_time, 'genericFailureMessageRows': generic_failures,
            'rowsExamined': len(rows)}


def recipient_flags(address):
    """Conservative simple-mailbox screen, never a deliverability assertion."""
    require(isinstance(address, str) and len(address) <= 1024, 'Invalid recipient diagnostic field')
    trimmed = address.strip()
    domain = trimmed.rsplit('@', 1)[1].lower() if trimmed.count('@') == 1 else ''
    reserved = any(domain == value or domain.endswith('.' + value) for value in
                   ('example.com', 'example.net', 'example.org', 'invalid', 'test'))
    local_chars = r"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
    label = r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?'
    simple = bool(re.fullmatch(local_chars + r'(?:\.' + local_chars + ')*@' + label + r'(?:\.' + label + ')+', address))
    simple = simple and len(address) <= 254 and len(address.split('@', 1)[0]) <= 64
    return {'empty': not trimmed, 'reservedTestDomain': reserved, 'hasWhitespace': any(char.isspace() for char in address),
            'hasComma': ',' in address, 'multipleAt': address.count('@') > 1, 'missingAt': '@' not in address,
            'invalidSimpleAddressShape': not simple}


def summarize_failed_recipients(rows):
    groups = {}
    required = {'recipient', 'patientId', 'appointmentId', 'appointmentStartTime', 'appointmentEndTime'}
    for row in rows:
        age = row.get('ageSeconds')
        if row.get('delivery') != 'failed' or type(age) is not int or not 0 <= age <= 86400:
            continue
        detail = row.get('failureDetail')
        require(isinstance(detail, dict) and set(detail) == required
                and all(isinstance(value, str) and len(value) <= 1024 for value in detail.values()),
                'Missing bounded failed-recipient diagnostic')
        email_type = row['emailType'] if row['emailType'] in EMAIL_TYPES else 'other'
        template = row['template'] if row['template'] in EMAIL_TEMPLATES else 'other'
        for key in ('allFailures', template + '/' + email_type):
            group = groups.setdefault(key, {'rows': 0, 'recipients': set(), 'normalizedRecipients': set(),
                'patients': set(), 'appointments': set(), 'scheduleSignatures': set(), 'attemptsByRecipient': {},
                'patientIdPresentRows': 0, 'appointmentIdPresentRows': 0, 'appointmentSchedulePresentRows': 0,
                'recipientFlags': {name: 0 for name in RECIPIENT_FLAGS}, 'ages': []})
            group['rows'] += 1
            group['ages'].append(age)
            recipient = detail['recipient']
            normalized = recipient.strip().lower()
            if recipient:
                group['recipients'].add(recipient)
            if normalized:
                group['normalizedRecipients'].add(normalized)
                group['attemptsByRecipient'][normalized] = group['attemptsByRecipient'].get(normalized, 0) + 1
            for field, output in [('patientId', 'patients'), ('appointmentId', 'appointments')]:
                if detail[field].strip():
                    group[output].add(detail[field].strip())
                    group[field + 'PresentRows'] += 1
            if detail['patientId'].strip() and detail['appointmentStartTime'].strip():
                group['scheduleSignatures'].add(tuple(detail[name].strip() for name in
                    ('patientId', 'appointmentStartTime', 'appointmentEndTime')))
                group['appointmentSchedulePresentRows'] += 1
            for name, value in recipient_flags(recipient).items():
                group['recipientFlags'][name] += int(value)
    output = {}
    for key, group in groups.items():
        gaps = [right - left for left, right in zip(sorted(group['ages']), sorted(group['ages'])[1:])]
        output[key] = {'failedAttempts': group['rows'], 'distinctRawRecipients': len(group['recipients']),
            'distinctNormalizedRecipients': len(group['normalizedRecipients']), 'distinctPatientIds': len(group['patients']),
            'distinctExplicitAppointmentIds': len(group['appointments']),
            'distinctPatientAndAppointmentTimeSignatures': len(group['scheduleSignatures']),
            'patientIdPresentRows': group['patientIdPresentRows'], 'appointmentIdPresentRows': group['appointmentIdPresentRows'],
            'appointmentSchedulePresentRows': group['appointmentSchedulePresentRows'],
            'maximumAttemptsPerNormalizedRecipient': max(group['attemptsByRecipient'].values(), default=0),
            'recipientFlags': group['recipientFlags'], 'consecutiveAttemptGaps270to330Seconds': sum(270 <= gap <= 330 for gap in gaps),
            'consecutiveAttemptGapSamples': len(gaps)}
    return {'window': 'last24Hours', 'groups': output,
            'scope': 'Counts only. Recipient, patient and schedule strings stay in server memory; none returned.',
            'limitations': 'Appointment notifications currently persist variables.patientId and appointmentStartTime/EndTime, not appointmentId. Schedule signatures are not verified appointment identities. Simple-address screening is a heuristic, not RFC completeness or deliverability.'}


def email_service_clock():
    raw = promotion.run(['systemctl', 'show', promotion.SERVICE, '--property=InvocationID',
                         '--property=ActiveEnterTimestampMonotonic'])
    values = dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)
    invocation, started = values.get('InvocationID', ''), values.get('ActiveEnterTimestampMonotonic', '')
    require(re.fullmatch(r'[0-9a-f]{32}', invocation) and started.isdigit() and int(started) > 0,
            'Email diagnostic service clock unavailable')
    uptime = int(time.monotonic() - int(started) / 1000000)
    require(0 <= uptime <= 366 * 86400, 'Unexpected service uptime')
    return invocation, uptime


def email_journal_summary(raw):
    result = {'matchingFailureEntries': 0, 'exceptionClasses': {}, 'unparsedEntries': 0,
              'errorTypeFieldShape': {'recognized': 0, 'unrecognizedClass': 0, 'missingOrMalformed': 0}}
    allowed = {'MailSendException', 'MailAuthenticationException', 'AuthenticationFailedException',
               'MessagingException', 'SendFailedException', 'SocketTimeoutException', 'SSLHandshakeException',
               'MailConnectException', 'ServiceException', 'IllegalArgumentException', 'AddressException',
               'MailParseException', 'MailPreparationException', 'UnsupportedEncodingException',
               'IllegalStateException', 'NullPointerException', 'SMTPAddressFailedException',
               'SMTPSenderFailedException', 'SMTPSendFailedException', 'IOException', 'ConnectException',
               'UnknownHostException', 'NoSuchProviderException'}
    for line in raw.splitlines():
        try:
            item = json.loads(line)
        except (TypeError, ValueError):
            result['unparsedEntries'] += 1
            continue
        message = item.get('MESSAGE') if isinstance(item, dict) else None
        if not isinstance(message, str) or '\u90ae\u4ef6\u53d1\u9001\u5931\u8d25:' not in message:
            continue
        result['matchingFailureEntries'] += 1
        match = re.search(r'errorType\s*=\s*[\"\']?([A-Za-z][A-Za-z0-9_$.]*)', message)
        found = match[1].rsplit('.', 1)[-1] if match else None
        name = found if found in allowed else 'unknown'
        result['errorTypeFieldShape']['recognized' if name != 'unknown' else 'unrecognizedClass' if match else 'missingOrMalformed'] += 1
        result['exceptionClasses'][name] = result['exceptionClasses'].get(name, 0) + 1
    return result


def email_diagnostics(state):
    """Bounded server-local processing; raw provider replies never leave this process."""
    try:
        invocation, uptime = email_service_clock()
        source = "(SELECT email_type,to_email,create_time,sent_at,CASE WHEN JSON_VALID(payload) THEN payload ELSE '{}' END AS j FROM tcm_email_log) logs"
        value = lambda key: "JSON_UNQUOTE(JSON_EXTRACT(j,'$." + key + "'))"
        delivery = "CASE WHEN LOWER(" + value('sent') + ")='true' THEN 'sent' WHEN LOWER(" + value('sent') + ")='false' THEN 'failed' WHEN COALESCE(TRIM(sent_at),'')<>'' THEN 'sent' ELSE 'unknown' END"
        clock_sql = "SELECT JSON_OBJECT('databaseNow',DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s'),'databaseUtcNow',DATE_FORMAT(UTC_TIMESTAMP(),'%Y-%m-%d %H:%i:%s'),'databaseUtcOffsetSeconds',TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW()))"
        detail_fields = ["'recipient',LEFT(COALESCE(to_email,''),1024)"]
        for name in ('patientId', 'appointmentId', 'appointmentStartTime', 'appointmentEndTime'):
            field = "JSON_EXTRACT(j,'$.variables." + name + "')"
            detail_fields.append("'" + name + "',CASE WHEN JSON_TYPE(" + field + ")='STRING' THEN LEFT(JSON_UNQUOTE(" + field + "),128) ELSE '' END")
        detail = "CASE WHEN (" + delivery + ")='failed' AND TIMESTAMPDIFF(SECOND,create_time,NOW()) BETWEEN 0 AND 86400 THEN JSON_OBJECT(" + ','.join(detail_fields) + ') ELSE NULL END'
        sql = "SELECT JSON_OBJECT('ageSeconds',TIMESTAMPDIFF(SECOND,create_time,NOW()),'delivery'," + delivery + ", 'emailType',LEFT(COALESCE(email_type,''),128),'template',LEFT(COALESCE(" + value('templateKey') + ",''),128),'error',LEFT(COALESCE(" + value('deliveryError') + ",''),4096),'smtpCode',LEFT(COALESCE(" + value('smtpCode') + ',' + value('errorCode') + ",''),128),'failureDetail'," + detail + ') FROM ' + source + ' LIMIT ' + str(MAX_EMAIL_DIAGNOSTIC_ROWS + 1)
        database, env, options = promotion.mysql(state)
        raw = promotion.run(['mysql'] + options + ['--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4', database],
                            input=('START TRANSACTION READ ONLY; ' + clock_sql + '; ' + sql + '; ROLLBACK;').encode(), env=env)
        require(len(raw.encode()) <= 16 * 1024 * 1024, 'Email diagnostic response bound exceeded')
        lines = raw.splitlines()
        require(bool(lines), 'Email diagnostic clock missing')
        clock = json.loads(lines[0])
        require(isinstance(clock, dict) and set(clock) == {'databaseNow', 'databaseUtcNow', 'databaseUtcOffsetSeconds'}, 'Unexpected email clock shape')
        require(all(isinstance(clock[key], str) and re.fullmatch(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', clock[key])
                    for key in ('databaseNow', 'databaseUtcNow')) and type(clock['databaseUtcOffsetSeconds']) is int
                and abs(clock['databaseUtcOffsetSeconds']) <= 15 * 3600, 'Invalid email database clock')
        rows = [json.loads(line) for line in lines[1:]]
        report = summarize_email_rows(rows, uptime)
        recipients = summarize_failed_recipients(rows)
        limit = 20000
        journal_raw = promotion.run(['journalctl', '--unit=' + promotion.SERVICE, '--boot', '--since=24 hours ago',
            '--output=json', '--no-pager', '--lines=' + str(limit + 1), '_SYSTEMD_INVOCATION_ID=' + invocation])
        entries = journal_raw.splitlines()
        journal = {**email_journal_summary('\n'.join(entries[-limit:])), 'truncated': len(entries) > limit,
                   'scope': 'Current invocation within 24 hours; fixed failure marker and exception-class allowlist only.'}
        require(email_service_clock()[0] == invocation, 'Backend changed during email diagnostics')
        return {'available': True, 'readOnly': True, 'databaseWrites': 0, 'messagesSent': 0,
                **clock, 'backendUptimeSeconds': uptime, **report, 'failedRecipientPatterns': recipients,
                'currentInvocationJournal': journal,
                'ageMethod': 'TIMESTAMPDIFF(SECOND,create_time,NOW()) in the database session; sent_at wall time is not used for age.',
                'limitations': 'The application stores a generic deliveryError on send exceptions, not the provider reply or SMTP code. Such rows remain unknown/unavailable. Counts are log attempts, not distinct messages; resend source IDs are not persisted. SMTP AUTH success is not proof of delivery.'}
    except Exception as error:
        return {'available': False, 'readOnly': True, 'databaseWrites': 0, 'messagesSent': 0,
                'errorType': type(error).__name__, 'reason': 'bounded-email-diagnostics-unavailable'}


def stage(request, directory):
    import production_deploy as deploy
    archive_path = directory / 'artifact.zip'
    deploy.download_github_archive(request['artifactUrl'], archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        require(sorted(archive.namelist()) == ['backend.jar', 'manifest.json'], 'Unexpected artifact contents')
        require(sum(item.file_size for item in archive.infolist()) <= deploy.MAX_ARTIFACT_BYTES, 'Expanded artifact too large')
        for name in ('backend.jar', 'manifest.json'):
            with archive.open(name) as source, (directory / name).open('xb') as target:
                shutil.copyfileobj(source, target)
    manifest = json.loads((directory / 'manifest.json').read_text())
    require(manifest == {'revision': request['revision'], 'sha256': request['sha256'], 'publicBaseUrl': 'https://www.otcm.app'},
            'Artifact identity mismatch')
    jar = directory / 'backend.jar'
    require(promotion.digest(jar) == request['sha256'], 'Artifact digest mismatch')
    with zipfile.ZipFile(jar) as archive:
        require('BOOT-INF/classes/com/ruoyi/RuoYiApplication.class' in archive.namelist(), 'Missing backend entrypoint')
        git = promotion.diagnostics.parse_config(archive.read('BOOT-INF/classes/git.properties').decode(), '.properties')
        require(git.get('git.commit.id') == request['revision'], 'Embedded backend revision mismatch')
    return jar


def dump_database(state, destination):
    database, environment, options = promotion.mysql(state)
    command = ['mysqldump'] + options + ['--no-tablespaces', '--single-transaction', '--quick', '--routines', '--triggers',
                                       '--events', '--hex-blob', '--default-character-set=utf8mb4', database]
    with tempfile.TemporaryFile() as errors, destination.open('xb') as raw:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, env=environment)
        try:
            with gzip.GzipFile(fileobj=raw, mode='wb', compresslevel=1) as compressed:
                shutil.copyfileobj(process.stdout, compressed)
            require(process.wait(timeout=120) == 0, 'Database backup failed')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    require(destination.stat().st_size > 100, 'Database backup is empty')
    with gzip.open(destination, 'rb') as verify:
        require(sum(len(block) for block in iter(lambda: verify.read(1024 * 1024), b'')) > 100, 'Invalid database backup')


def fingerprints(metadata):
    return {**session.config_fingerprints(metadata), str(session.PRESERVED): promotion.digest(session.PRESERVED)}


def publish(request, state, jar):
    metadata = session.service_metadata()
    config = fingerprints(metadata)
    web = frontend.tree_files(WEB)
    require(BACKUPS.resolve() == BACKUPS, 'Unexpected backup root')
    BACKUPS.mkdir(parents=True, mode=0o700, exist_ok=True)
    require(BACKUPS.stat().st_uid == os.geteuid() and stat.S_IMODE(BACKUPS.stat().st_mode) == 0o700, 'Backup root must be private')
    require(shutil.disk_usage(BACKUPS).free > 1024 * 1024 * 1024, 'Insufficient backup space')
    backup = BACKUPS / (datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '-' + request['revision'][:12])
    backup.mkdir(mode=0o700)
    session.atomic_copy(state['jar'], backup / 'previous.jar')
    require(promotion.digest(backup / 'previous.jar') == request['expectedBackendSha256'], 'Backend backup mismatch')
    session.atomic_copy(session.PRESERVED, backup / 'application-otcm-preserved.properties')
    session.backup_smtp_key(backup)
    session.atomic_write(backup / 'startup-private.json', json.dumps({'metadata': metadata, 'config': config,
                         'args': state['args'], 'environment': state['environment']}).encode())
    # A consistent read-only snapshot is taken while the current process remains available.
    dump_database(state, backup / 'database.sql.gz')
    report = {'ok': False, 'mode': 'publish', 'revision': request['revision'], 'previousBackendSha256': request['expectedBackendSha256'],
              'backendSha256': request['sha256'], 'frontendRevision': request['expectedFrontendRevision'], 'backup': str(backup),
              'backupSha256': {name: promotion.digest(backup / name) for name in ('previous.jar', 'database.sql.gz')},
              'databaseWrites': 0, 'schemaChanges': 0, 'rollbackPerformed': False, 'serviceHealthy': False}
    jar_metadata = state['jar'].stat()
    touched = False
    try:
        require(promotion.digest(state['jar']) == request['expectedBackendSha256'] and promotion.digest(jar) == request['sha256'],
                'Backend changed during preparation')
        require(fingerprints(metadata) == config and session.service_metadata() == metadata, 'Configuration changed during preparation')
        require(frontend.tree_files(WEB) == web, 'Frontend changed during preparation')
        require(promotion.run(['systemctl', 'show', promotion.SERVICE, '--property=MainPID', '--value']) == str(state['pid']),
                'Backend process changed during preparation')
        touched = True
        promotion.run(['systemctl', 'stop', promotion.SERVICE])
        session.atomic_copy(jar, state['jar'], jar_metadata.st_uid, jar_metadata.st_gid, stat.S_IMODE(jar_metadata.st_mode))
        require(promotion.digest(state['jar']) == request['sha256'], 'Installed artifact mismatch')
        promotion.run(['systemctl', 'start', promotion.SERVICE])
        require(session.health(), 'New backend health check failed')
        after = promotion.runtime()
        require(after['cwd'] == state['cwd'] and after['jar'] == state['jar'], 'Service paths changed')
        require(after['properties'] == state['properties'], 'Effective configuration changed')
        require(promotion.digest(after['jar']) == request['sha256'], 'Running backend digest mismatch')
        require(fingerprints(metadata) == config and session.service_identity(session.service_metadata()) == session.service_identity(metadata),
                'Protected configuration changed')
        require(frontend.tree_files(WEB) == web, 'Frontend changed during backend release')
        report.update(ok=True, serviceHealthy=True, frontendUnchanged=True, configurationAndSecretsUnchanged=True)
    except Exception as error:
        report['errorType'] = type(error).__name__
        if isinstance(error, Guard):
            report['guard'] = str(error)
        if touched:
            try:
                promotion.run(['systemctl', 'stop', promotion.SERVICE])
                session.atomic_copy(backup / 'previous.jar', state['jar'], jar_metadata.st_uid, jar_metadata.st_gid, stat.S_IMODE(jar_metadata.st_mode))
                promotion.run(['systemctl', 'start', promotion.SERVICE])
                report['rollbackPerformed'] = True
                require(session.health(), 'Rollback service health check failed')
                require(promotion.digest(state['jar']) == request['expectedBackendSha256'], 'Rollback artifact mismatch')
                restored = promotion.runtime()
                require(restored['properties'] == state['properties'] and fingerprints(metadata) == config, 'Rollback configuration differs')
                report['serviceHealthy'] = True
            except Exception as rollback_error:
                report['rollbackErrorType'] = type(rollback_error).__name__
    session.atomic_write(backup / 'RESULT.json', json.dumps(report, indent=2).encode())
    return report


def main():
    os.umask(0o077)
    try:
        validate_request(REQUEST)
        require(os.geteuid() == 0, 'Root is required')
        import fcntl
        require(not LOCK.is_symlink() and LOCK.parent.is_dir(), 'Unexpected release lock')
        with LOCK.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            state = validate_current(REQUEST)
            if REQUEST['mode'] == 'inspect':
                result = {'ok': True, 'mode': 'inspect', 'readOnly': True, 'databaseWrites': 0,
                          'backendSha256': REQUEST['expectedBackendSha256'], 'frontendRevision': REQUEST['expectedFrontendRevision'],
                          'patients': patient_diagnostics(state), 'uploads': upload_diagnostics(state),
                          'emailDelivery': email_diagnostics(state)}
            else:
                with tempfile.TemporaryDirectory(prefix='otcm-backend-') as temporary:
                    jar = stage(REQUEST, pathlib.Path(temporary))
                    result = publish(REQUEST, state, jar)
        print(json.dumps(result, indent=2))
        return 0 if result['ok'] else 1
    except Exception as error:
        result = {'ok': False, 'errorType': type(error).__name__}
        if isinstance(error, Guard):
            result['guard'] = str(error)
        print(json.dumps(result))
        return 1


if __name__ == '__main__':
    sys.exit(main())
