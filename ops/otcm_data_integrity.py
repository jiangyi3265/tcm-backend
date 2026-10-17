#!/usr/bin/env python3
"""Read-only, server-local comparison with the fixed OTCM cutover backup.

No database is restored or updated. SQL literals and file names stay in memory
or in a 0700 temporary directory; the report contains table-level aggregates.
"""
import base64
from collections import Counter
import datetime
from decimal import Decimal, InvalidOperation
import gzip
import hashlib
import hmac
import io
import json
import os
import pathlib
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

import otcm_promotion as promotion

REQUEST = {}
BACKUP = pathlib.Path('/www/backup/otcm-releases/20261017T161852183005Z-39048276224')
EXPECTED = {
    'database.sql.gz': '7258f5e0ae5bd53e03750ffb1843eef72f0e7c99819ad0294738d8bdd3cfc2d2',
    'files.tar': '9858a8e5780f7cd89da18a9b22109944c0c9d1db842e4b98d5786a3e89a5fecb',
    'previous.jar': '1a13fc82a6dbb9ee45a515d3284227db94e63c75b939a6a6efe465377332a0df',
}
CURRENT_JAR = '4d30e97954c0c5883da5630bb439a7a52ce9ac97f913a2098c98c60602638e79'
FILE_ROOTS = (promotion.APP / 'runtime/uploadPath', promotion.APP / 'runtime/hospital-private')
ACCOUNT_COLUMNS = frozenset({'user_id', 'user_name', 'password', 'user_type', 'dept_id', 'status',
                             'del_flag', 'email', 'phonenumber', 'nick_name', 'sex', 'avatar', 'remark'})
PDF_KEYS = frozenset({'invoicePdfPath', 'invoicePdfUrl', 'invoicePdfGeneratedAt', 'reportPdfPath',
                     'reportPdfUrl', 'reportPdfGeneratedAt', 'consultationPdfPath', 'consultationPdfUrl',
                     'consentPdfPath', 'consentPdfUrl', 'consentPdfGeneratedAt'})
CLINICAL_KEYS = frozenset({'chiefComplaint', 'history', 'historyAndMedication', 'historyAndMedicationSnapshot',
                         'medicalHistory', 'assessment', 'diagnosis', 'notes', 'comments', 'diff', 'differentiation',
                         'tongue', 'tongueImage', 'pulse', 'treatment', 'treatmentPlan', 'acupoints', 'advice',
                         'prescriptions', 'deletedPrescriptions', 'intakeFormData', 'feedback', 'prognosis',
                         'previousPrognosisReview', 'allergies', 'currentMedications'})
PRICING_KEYS = frozenset({'invoice', 'invoiceSubtotal', 'invoiceTax', 'invoiceTotal', 'subtotal', 'tax', 'total',
                        'taxRate', 'consultationFee', 'services', 'discount', 'discountType', 'discountValue',
                        'paymentRecords', 'payments', 'paymentStatus', 'paidAmount', 'balance', 'amountDue',
                        'invoiceNumber', 'invoiceDate', 'includePrescription', 'includeRx', 'pricing'})
OTHER_SAFE_PAYLOAD_KEYS = frozenset({'modifications', 'version', 'status', 'lockedAt', 'createdAt', 'updatedAt',
                                    'deletedAt', 'practitionerId', 'branchId', 'patientId', 'consultDate'})
SAFE_PAYLOAD_KEYS = PDF_KEYS | CLINICAL_KEYS | PRICING_KEYS | OTHER_SAFE_PAYLOAD_KEYS
EMAIL_TEMPLATES = frozenset({'appointmentConfirmation', 'appointmentCancellation', 'reminder', 'consent', 'intake',
                            'consultationRecord', 'invoice', 'appointmentChange', 'aftercare', 'followUp',
                            'internalBooking', 'internalAppointmentChange', 'internalAppointmentCancellation'})
EMAIL_TYPES = frozenset({'appointment_confirmation', 'appointment_change', 'appointment_change_internal',
                        'appointment_internal_new', 'appointment_cancel', 'appointment_cancel_internal',
                        'appointment_aftercare', 'appointment_reminder', 'appointment_follow_up',
                        'consent', 'intake', 'invoice', 'consultation', 'consultation_record', 'manual', 'general'})
AUDIT_ACTIONS = frozenset({'LOGIN', 'PASSWORD_CHANGE', 'PASSWORD_RESET', 'CREATE', 'UPDATE', 'DELETE',
                          'SOFT_DELETE', 'RESTORE', 'IMPORT', 'BATCH_IMPORT', 'ADJUST_STOCK', 'STATUS_CHANGE',
                          'GENERATE_INTAKE_LINK', 'CONSENT', 'SEND_CONSENT', 'SEND_INTAKE', 'UPLOAD_FILE',
                          'DELETE_FILE', 'DOWNLOAD_ALL_FILES', 'TOGGLE', 'DISABLE', 'UPLOAD', 'COMPLETE',
                          'DISPENSE', 'PAID', 'Consultation completed', 'Consultation reactivated',
                          'Prescription synced', 'Prescription completed', 'Prescription dispensed',
                          'Prescription reopened', 'Prescription deleted', 'Prescription restored',
                          'Prescription permanently deleted', 'Payment recorded', 'Stripe payment recorded'})
AUDIT_TYPES = frozenset({'user', 'patient', 'appointment', 'consultation', 'inventory', 'supplier', 'settings',
                        'branch', 'template', 'file', 'complete', 'reactivate', 'prescription', 'dispense', 'payment'})
SMTP_CODES = frozenset({'SMTP_SETTINGS_INVALID', 'SMTP_PORT_INVALID', 'SMTP_PASSWORD_INVALID', 'SMTP_NEW_PASSWORD_REQUIRED',
                       'SMTP_SAVE_FAILED', 'SMTP_HOST_INVALID', 'SMTP_TLS_REQUIRED', 'SMTP_USERNAME_INVALID',
                       'SMTP_FROM_INVALID', 'SMTP_CONNECTION_FAILED', 'SMTP_AUTH_FAILED', 'SMTP_CONNECTED'})
SMTP_RUNTIME_KEYS = frozenset({'spring.mail.host', 'spring.mail.port', 'spring.mail.username', 'spring.mail.password',
                               'spring.mail.from', 'spring.mail.properties.mail.smtp.ssl.enable'})
SMTP_PROBE_CODES = SMTP_CODES | frozenset({'SMTP_SECRET_UNAVAILABLE_REENTER_KEY', 'PROBE_INPUT_INVALID',
    'PROBE_RUNTIME_UNAVAILABLE', 'PROBE_UNSAFE_LOGGING_CLASSPATH', 'PROBE_MAPPER_ACCESS_REJECTED', 'PROBE_RESULT_INVALID'})


def ensure(condition, message):
    if not condition:
        raise ValueError(message)


def stream_digest(source):
    digest = hashlib.sha256()
    for block in iter(lambda: source.read(1024 * 1024), b''):
        digest.update(block)
    return digest.hexdigest()


def canonical_literal(token):
    token = token.strip()
    if token == 'NULL':
        return ['null']
    if re.fullmatch(r'0x[0-9A-Fa-f]*', token):
        return ['binary', token[2:].lower()]
    if token.startswith("'") and token.endswith("'"):
        body, value, index = token[1:-1], [], 0
        escapes = {'0': '\0', 'b': '\b', 'n': '\n', 'r': '\r', 't': '\t', 'Z': '\x1a',
                   "'": "'", '"': '"', '\\': '\\'}
        while index < len(body):
            character = body[index]
            if character == '\\':
                index += 1
                ensure(index < len(body), 'Incomplete SQL literal escape')
                value.append(escapes.get(body[index], body[index]))
            elif character == "'" and index + 1 < len(body) and body[index + 1] == "'":
                value.append("'")
                index += 1
            else:
                value.append(character)
            index += 1
        return ['string', ''.join(value)]
    if re.fullmatch(r"[bB]'[01]+'", token):
        return ['bits', token[2:-1]]
    ensure(re.fullmatch(r'[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?', token), 'Unrecognized SQL literal')
    # Same MySQL representation can vary with complete/extended INSERT options.
    try:
        numeric = Decimal(token)
        rendered = format(numeric, 'f')
        if '.' in rendered:
            rendered = rendered.rstrip('0').rstrip('.')
        return ['number', rendered if numeric else '0']
    except InvalidOperation:
        raise ValueError('Invalid numeric SQL literal') from None


def insert_rows(text):
    """Parse mysqldump VALUES tuples; never execute SQL or interpolate literals."""
    position = 0
    while position < len(text):
        while position < len(text) and text[position] in ' ,\r\n\t':
            position += 1
        if position == len(text):
            return
        ensure(text[position] == '(', 'Expected SQL row tuple')
        position += 1
        row = []
        while True:
            start = position
            quoted = False
            while position < len(text):
                character = text[position]
                if quoted and character == '\\':
                    position += 2
                    continue
                if character == "'":
                    if quoted and position + 1 < len(text) and text[position + 1] == "'":
                        position += 2
                        continue
                    quoted = not quoted
                if not quoted and character in ',)':
                    break
                position += 1
            ensure(position < len(text) and not quoted, 'Incomplete SQL row')
            row.append(canonical_literal(text[start:position]))
            separator = text[position]
            position += 1
            if separator == ')':
                yield row
                break


def keyed_digest(salt, value):
    canonical = json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode()
    return hmac.new(salt, canonical, hashlib.sha256).hexdigest()


def scalar(row, name):
    value = row.get(name, ['null'])
    return value[1] if len(value) == 2 and value[0] in ('string', 'number') else None


def object_value(value):
    try:
        parsed = json.loads(value) if isinstance(value, str) else None
        return parsed if isinstance(parsed, dict) else None
    except (ValueError, TypeError):
        return None


def safe_time(value):
    if not isinstance(value, str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?', value):
        return None
    try:
        return datetime.datetime.fromisoformat(value).isoformat(sep=' ')
    except ValueError:
        return None


def allowed(value, choices):
    return value if isinstance(value, str) and value in choices else 'other'


def json_digest(salt, value):
    return keyed_digest(salt, json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(',', ':')))


def row_facts(table, row, salt):
    """Retain only hashes, approved labels and timestamps for differential diagnostics."""
    if table not in ('tcm_consultation', 'tcm_clinic_setting', 'tcm_email_log', 'tcm_appointment',
                     'tcm_consultation_mod', 'tcm_patient_file'):
        return None
    result = {'times': {name: value for name in ('create_time', 'update_time', 'sent_at', 'mod_date', 'upload_time', 'start_time', 'end_time')
                        if (value := safe_time(scalar(row, name))) is not None}}
    if table == 'tcm_consultation':
        payload = object_value(scalar(row, 'payload'))
        result['payloadValid'] = payload is not None
        if payload is not None:
            result['payloadKeys'] = {key if key in SAFE_PAYLOAD_KEYS else 'unknown-' + keyed_digest(salt, key): json_digest(salt, value)
                                     for key, value in payload.items()}
            result['nonPdfPayload'] = json_digest(salt, {key: value for key, value in payload.items() if key not in PDF_KEYS})
            result['clinicalPayload'] = json_digest(salt, {key: value for key, value in payload.items() if key in CLINICAL_KEYS})
            result['pricingPayload'] = json_digest(salt, {key: value for key, value in payload.items() if key in PRICING_KEYS})
    elif table == 'tcm_clinic_setting':
        key = scalar(row, 'setting_key')
        result['category'] = 'admin-smtp' if key == 'smtpSettings' else ('mail-templates' if key == 'emailTemplates' else 'other')
        if key == 'smtpSettings':
            config = object_value(scalar(row, 'setting_value'))
            result['configValid'] = config is not None
            result['credentialConfigured'] = bool(config and isinstance(config.get('encryptedPassword'), str) and config['encryptedPassword'].strip())
            result['credentialHash'] = json_digest(salt, config.get('encryptedPassword')) if config else None
            result['transportSecurity'] = allowed(config.get('security'), {'ssl', 'starttls'}) if config else 'other'
    elif table == 'tcm_email_log':
        payload = object_value(scalar(row, 'payload')) or {}
        result.update({'emailType': allowed(scalar(row, 'email_type'), EMAIL_TYPES),
                       'template': allowed(payload.get('templateKey'), EMAIL_TEMPLATES),
                       'delivery': 'sent' if payload.get('sent') is True else ('failed' if payload.get('sent') is False else 'unknown'),
                       'attachmentError': payload.get('attachmentError') is True})
        errors = {'SMTP delivery failed. Check the SMTP settings and connection test.': 'smtp-delivery-failed',
                  'SMTP is not configured.': 'smtp-not-configured',
                  'Email attachment could not be resolved.': 'attachment-unresolved'}
        delivery_error = payload.get('deliveryError')
        result['errorCategory'] = errors.get(delivery_error, 'other' if delivery_error else 'none') if isinstance(delivery_error, str) else ('other' if delivery_error else 'none')
    elif table == 'tcm_appointment':
        result['status'] = allowed(scalar(row, 'status'), {'booked', 'confirmed', 'completed', 'cancelled', 'no_show'})
        result['appointmentKind'] = 'time-block' if scalar(row, 'service_type') == 'time_block' else 'patient-appointment'
    elif table == 'tcm_consultation_mod':
        result['action'] = allowed(scalar(row, 'action'), AUDIT_ACTIONS)
        result['targetType'] = allowed(scalar(row, 'mod_type'), AUDIT_TYPES)
    elif table == 'tcm_patient_file':
        result['fileType'] = allowed(scalar(row, 'file_type'), {'invoice', 'receipt', 'report', 'consultation', 'consultation_report',
                                                              'consent', 'consent_form', 'upload', 'examination', 'examination_report'})
    return result


def parse_dump(source, salt):
    tables = {}
    ddl = []
    creating = None
    delimiter = ';'
    for line in source:
        if line.startswith('DELIMITER '):
            delimiter = line.split(None, 1)[1].strip()
            continue
        # Stored routine/trigger bodies may contain INSERT text; they are not data rows.
        if delimiter != ';':
            continue
        if line.startswith('CREATE TABLE `'):
            match = re.match(r'CREATE TABLE `([A-Za-z0-9_]+)`', line)
            ensure(match is not None and creating is None, 'Unexpected CREATE statement')
            creating = match.group(1)
            ddl = [line]
            continue
        if creating:
            ddl.append(line)
            if line.rstrip().endswith(';'):
                schema = ''.join(ddl)
                columns = re.findall(r'^\s*`([A-Za-z0-9_]+)`\s+', schema, re.M)
                primary = re.search(r'^\s*PRIMARY KEY\s*\(([^\n]+)\)', schema, re.M)
                primary_columns = re.findall(r'`([A-Za-z0-9_]+)`', primary.group(1)) if primary else []
                ensure(columns, 'Table has no columns')
                normalized = re.sub(r'\bAUTO_INCREMENT=\d+\s*', '', schema)
                normalized = re.sub(r'\s+', ' ', normalized).strip()
                tables[creating] = {'columns': columns, 'primaryColumns': primary_columns,
                                    'schemaSha256': hashlib.sha256(normalized.encode()).hexdigest(), 'rows': {}, 'facts': {}}
                creating = None
            continue
        if not line.startswith('INSERT INTO `'):
            continue
        match = re.fullmatch(r'INSERT INTO `([A-Za-z0-9_]+)`(?: \(([^\n]*?)\))? VALUES (.*);\s*', line, re.S)
        ensure(match is not None, 'Unrecognized INSERT shape')
        name, explicit_columns, values = match.groups()
        ensure(name in tables, 'INSERT has no preceding schema')
        table = tables[name]
        order = re.findall(r'`([A-Za-z0-9_]+)`', explicit_columns) if explicit_columns else table['columns']
        ensure(set(order) == set(table['columns']), 'Unexpected INSERT columns')
        for row in insert_rows(values):
            ensure(len(row) == len(order), 'SQL row width mismatch')
            by_column = dict(zip(order, row))
            normalized_row = [by_column[column] for column in table['columns']]
            key = keyed_digest(salt, [by_column[column] for column in table['primaryColumns']]) if table['primaryColumns'] else keyed_digest(salt, normalized_row)
            column_hashes = tuple(keyed_digest(salt, cell) for cell in normalized_row)
            if key in table['rows']:
                ensure(not table['primaryColumns'] and table['rows'][key][0] == column_hashes, 'Duplicate primary key in SQL dump')
                table['rows'][key] = (column_hashes, table['rows'][key][1] + 1)
            else:
                table['rows'][key] = (column_hashes, 1)
            facts = row_facts(name, by_column, salt)
            if facts is not None:
                table['facts'][key] = facts
    ensure(creating is None and 'sys_user' in tables and 'tcm_patient' in tables, 'Incomplete clinic SQL dump')
    return tables


def table_summary(table):
    if table is None:
        return {'rows': 0, 'contentDigest': None, 'schemaSha256': None}
    rows = table['rows']
    signature = [[key, list(columns), count] for key, (columns, count) in sorted(rows.items())]
    return {'rows': sum(count for _, count in rows.values()),
            'contentDigest': hashlib.sha256(json.dumps(signature, separators=(',', ':')).encode()).hexdigest(),
            'schemaSha256': table['schemaSha256']}


def compare_table(before, after, columns=None):
    before_summary, after_summary = table_summary(before), table_summary(after)
    old, new = (before or {}).get('rows', {}), (after or {}).get('rows', {})
    old_keys, new_keys = set(old), set(new)
    result = {'before': before_summary, 'after': after_summary,
              'schemaEqual': before_summary['schemaSha256'] == after_summary['schemaSha256'],
              'missingRows': sum(old[key][1] for key in old_keys - new_keys),
              'addedRows': sum(new[key][1] for key in new_keys - old_keys), 'changedRows': 0, 'changedColumns': {}}
    old_columns, new_columns = (before or {}).get('columns', []), (after or {}).get('columns', [])
    selected = set(columns) if columns is not None else set(old_columns) | set(new_columns)
    changed = Counter()
    for key in old_keys & new_keys:
        old_values = dict(zip(old_columns, old[key][0]))
        new_values = dict(zip(new_columns, new[key][0]))
        differences = [column for column in selected if old_values.get(column) != new_values.get(column)]
        if differences or old[key][1] != new[key][1]:
            result['changedRows'] += max(old[key][1], new[key][1])
            changed.update(differences)
    result['changedColumns'] = dict(sorted(changed.items()))
    result['contentEqual'] = not any(result[key] for key in ('missingRows', 'addedRows', 'changedRows'))
    if columns is not None:
        # Whole-row digests include login metadata; omit them from this focused comparison.
        result.pop('before')
        result.pop('after')
        result['comparedColumns'] = sorted(selected & (set(old_columns) | set(new_columns)))
    return result


def aggregate_facts(facts, fields):
    result = {'rows': len(facts), 'counts': {}, 'timestampRanges': {}}
    for field in fields:
        result['counts'][field] = dict(sorted(Counter(str(item.get(field, 'other')) for item in facts).items()))
    for field in ('create_time', 'update_time', 'sent_at', 'mod_date', 'upload_time', 'start_time', 'end_time'):
        values = [item['times'][field] for item in facts if item.get('times', {}).get(field)]
        if values:
            result['timestampRanges'][field] = {'first': min(values), 'last': max(values), 'rowsWithTimestamp': len(values)}
    return result


def differential_activity(before, after, cutoff):
    def added_facts(table):
        old, new = before.get(table, {}), after.get(table, {})
        keys = set(new.get('rows', {})) - set(old.get('rows', {}))
        return [new.get('facts', {}).get(key, {}) for key in keys]

    old, new = before.get('tcm_consultation', {}), after.get('tcm_consultation', {})
    shared = set(old.get('rows', {})) & set(new.get('rows', {}))
    changed = [key for key in shared if old['rows'][key] != new['rows'][key]]
    keys, groups = Counter(), Counter()
    non_pdf_unchanged = clinical_unchanged = pricing_unchanged = semantic_unchanged = 0
    unknown_keys = invalid = 0
    changed_facts = []
    for key in changed:
        previous, current = old['facts'][key], new['facts'][key]
        changed_facts.append(current)
        if not previous.get('payloadValid') or not current.get('payloadValid'):
            invalid += 1
            continue
        changes = [name for name in set(previous['payloadKeys']) | set(current['payloadKeys'])
                   if previous['payloadKeys'].get(name) != current['payloadKeys'].get(name)]
        if not changes:
            semantic_unchanged += 1
        touched = set()
        for name in changes:
            if name in SAFE_PAYLOAD_KEYS:
                keys[name] += 1
            else:
                unknown_keys += 1
            group = 'pdf-metadata' if name in PDF_KEYS else ('clinical' if name in CLINICAL_KEYS else ('pricing' if name in PRICING_KEYS else 'other'))
            touched.add(group)
        groups.update(touched)
        non_pdf_unchanged += previous['nonPdfPayload'] == current['nonPdfPayload']
        clinical_unchanged += previous['clinicalPayload'] == current['clinicalPayload']
        pricing_unchanged += previous['pricingPayload'] == current['pricingPayload']
    consultations = {'changedRows': len(changed), 'changedTopLevelKeys': dict(sorted(keys.items())),
                     'rowsByChangedFieldGroup': dict(sorted(groups.items())), 'unknownChangedKeyOccurrences': unknown_keys,
                     'nonPdfPayloadUnchangedRows': non_pdf_unchanged, 'clinicalPayloadUnchangedRows': clinical_unchanged,
                     'pricingPayloadUnchangedRows': pricing_unchanged, 'semanticallyUnchangedPayloadRows': semantic_unchanged,
                     'invalidPayloadRows': invalid, 'timestamps': aggregate_facts(changed_facts, [])['timestampRanges']}
    settings = aggregate_facts(added_facts('tcm_clinic_setting'), ['category'])
    old_smtp = [item for item in before.get('tcm_clinic_setting', {}).get('facts', {}).values() if item.get('category') == 'admin-smtp']
    new_smtp = [item for item in after.get('tcm_clinic_setting', {}).get('facts', {}).values() if item.get('category') == 'admin-smtp']
    smtp = {'effectiveSource': 'admin' if new_smtp else 'environment', 'adminRecordPresentBefore': bool(old_smtp),
            'adminRecordPresentNow': bool(new_smtp), 'connectionTestPerformed': False}
    if new_smtp:
        value = new_smtp[0]
        smtp.update({'adminRecordValid': value.get('configValid'), 'credentialConfigured': value.get('credentialConfigured'),
                     'credentialAddedOrChangedSinceCutover': not old_smtp or old_smtp[0].get('credentialHash') != value.get('credentialHash'),
                     'transportSecurity': value.get('transportSecurity'), 'updatedAt': value.get('times', {}).get('update_time')})
    appointments = added_facts('tcm_appointment')
    cutoff_time = datetime.datetime.fromisoformat(cutoff) if isinstance(cutoff, str) and safe_time(cutoff) else None
    windows = Counter()
    for item in appointments:
        start = item.get('times', {}).get('start_time')
        if start and cutoff_time:
            delta = datetime.datetime.fromisoformat(start) - cutoff_time
            windows['before-cutover' if delta.total_seconds() < 0 else ('within-7-days-after-cutover' if delta <= datetime.timedelta(days=7) else 'later-than-7-days')] += 1
        else:
            windows['unknown'] += 1
    appointment_summary = aggregate_facts(appointments, ['status', 'appointmentKind'])
    appointment_summary['startTimeWindowsRelativeToTorontoCutover'] = dict(sorted(windows.items()))
    return {'consultationChanges': consultations, 'addedSettings': settings, 'effectiveSmtpStorage': smtp,
            'addedEmails': aggregate_facts(added_facts('tcm_email_log'), ['emailType', 'template', 'delivery', 'errorCategory', 'attachmentError']),
            'addedAppointments': appointment_summary,
            'addedAuditRecords': aggregate_facts(added_facts('tcm_consultation_mod'), ['targetType', 'action']),
            'addedPatientFiles': aggregate_facts(added_facts('tcm_patient_file'), ['fileType']),
            'timestampNote': 'Raw database timestamps only. mysqldump exports TIMESTAMP as UTC; DATETIME retains stored wall time. Appointment start/end use clinic Toronto time. No actor, patient, recipient, row identifier or content is disclosed.'}


def database_comparison(state, temporary):
    salt = secrets.token_bytes(32)
    with gzip.open(BACKUP / 'database.sql.gz', 'rt', encoding='utf-8') as original:
        before = parse_dump(original, salt)
    database, environment, options = promotion.mysql(state)
    current = temporary / 'current.sql'
    command = ['mysqldump'] + options + ['--no-tablespaces', '--single-transaction', '--quick', '--skip-lock-tables',
                                       '--hex-blob', '--default-character-set=utf8mb4', '--skip-comments', database]
    with current.open('xb') as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.PIPE, env=environment, timeout=180)
    ensure(result.returncode == 0, 'Read-only current dump failed')
    with current.open(encoding='utf-8') as source:
        after = parse_dump(source, salt)
    tables = {name: compare_table(before.get(name), after.get(name)) for name in sorted(set(before) | set(after))}
    account = compare_table(before['sys_user'], after['sys_user'], ACCOUNT_COLUMNS)
    return {'method': 'Server-local canonical row comparison; no SQL restored or executed from the backup.',
            'digestScope': 'HMAC-derived, run-specific table digests; row identifiers and values are never returned.',
            'tables': tables, 'accountCredentialsAndProfile': account,
            'differentialActivity': differential_activity(before, after, state['properties'].get('tcm.notifications.not-before', '')),
            'businessTablesEqual': all(value['contentEqual'] and value['schemaEqual'] for key, value in tables.items() if key.startswith('tcm_')),
            'roleTablesEqual': all(value['contentEqual'] and value['schemaEqual'] for key, value in tables.items()
                                   if key in ('sys_role', 'sys_user_role', 'sys_role_menu', 'sys_user_post', 'sys_role_dept')),
            'note': 'Current data can include legitimate activity since cutover. Differences are reported, never modified.'}


def file_comparison():
    result = {str(root): {'backupFiles': 0, 'matchedFiles': 0, 'missingFiles': 0, 'changedFiles': 0,
                         'addedFiles': 0, 'unstableFiles': 0, 'unsupportedEntries': 0, 'verifiedBytes': 0}
              for root in FILE_ROOTS}
    archived_names = set()
    with tarfile.open(BACKUP / 'files.tar', 'r') as archive:
        for member in archive:
            name = pathlib.PurePosixPath(member.name)
            ensure(not name.is_absolute() and '..' not in name.parts, 'Unexpected backup member path')
            path = pathlib.Path('/') / member.name
            root = next((root for root in FILE_ROOTS if path == root or root in path.parents), None)
            if root is None or member.isdir():
                continue
            item = result[str(root)]
            if not member.isfile():
                item['unsupportedEntries'] += 1
                continue
            archived_names.add(path)
            item['backupFiles'] += 1
            if not path.is_file() or path.is_symlink() or path.resolve() != path:
                item['missingFiles'] += 1
                continue
            initial = path.stat()
            with archive.extractfile(member) as old, path.open('rb') as current:
                equal = stream_digest(old) == stream_digest(current)
            final = path.stat()
            if (initial.st_size, initial.st_mtime_ns, initial.st_ino) != (final.st_size, final.st_mtime_ns, final.st_ino):
                item['unstableFiles'] += 1
            elif equal:
                item['matchedFiles'] += 1
                item['verifiedBytes'] += member.size
            else:
                item['changedFiles'] += 1
    for root in FILE_ROOTS:
        result[str(root)]['addedFiles'] = sum(path.is_file() and path not in archived_names for path in root.rglob('*')) if root.exists() else 0
    return {'roots': result, 'allPreexistingFilesEqual': all(not any(item[key] for key in
            ('missingFiles', 'changedFiles', 'unstableFiles', 'unsupportedEntries')) for item in result.values()),
            'pathDisclosure': 'Only fixed storage root names are returned; patient file names never leave the server.'}


def pdf_probe(state, temporary):
    encoded = REQUEST.get('pdfProbeClassBase64', '')
    if not encoded:
        return {'available': False, 'reason': 'probe-class-not-provided'}
    bytecode = base64.b64decode(encoded, validate=True)
    ensure(bytecode.startswith(b'\xca\xfe\xba\xbe') and len(bytecode) < 65536, 'Invalid PDF probe class')
    probe = temporary / 'pdf-probe'
    probe.mkdir(mode=0o700)
    (probe / 'OtcmPdfDependencyProbe.class').write_bytes(bytecode)
    library = probe / 'lib'
    library.mkdir(mode=0o700)
    with zipfile.ZipFile(state['jar']) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'BOOT-INF/lib/[^/]+\.jar', name):
                with archive.open(name) as source, (library / pathlib.PurePosixPath(name).name).open('xb') as target:
                    shutil.copyfileobj(source, target)
    command = [state['args'][0], '-Djava.awt.headless=true', '-Duser.home=' + str(probe),
               '-Djava.io.tmpdir=' + str(probe), '-cp', str(probe) + os.pathsep + str(library / '*'), 'OtcmPdfDependencyProbe']
    result = subprocess.run(command, cwd=probe, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90,
                            env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'HOME': str(probe)})
    lines = [line for line in result.stdout.decode('utf-8', errors='replace').splitlines() if line.startswith('OTCM_PDF_PROBE:')]
    if len(lines) != 1:
        return {'available': True, 'ok': False, 'reason': 'probe-did-not-return-valid-result', 'exitCode': result.returncode}
    data = json.loads(lines[0].split(':', 1)[1])
    allowed = {'ok', 'englishText', 'chineseText', 'rendered', 'pdfBytes', 'imageWidth', 'imageHeight', 'errorClass'}
    ensure(set(data) <= allowed, 'Unexpected PDF probe output')
    return {'available': True, 'exitCode': result.returncode, **data,
            'scope': 'Synthetic PDF in memory using the deployed font loader, iText, PDFBox and Java runtime; no Spring, database, email or patient files.'}


def smtp_probe(state, temporary):
    """Use the deployed service's active SMTP settings; AUTH only, never send or save."""
    encoded = REQUEST.get('smtpProbeJarBase64', '')
    if not encoded:
        return {'available': False, 'code': 'PROBE_NOT_PROVIDED', 'messagesSent': 0}
    source_name = None
    try:
        # Only reviewed Java bytecode enters the temporary runtime classpath.
        ensure(isinstance(encoded, str) and len(encoded) < 512 * 1024, 'Invalid SMTP probe archive')
        bytecode = base64.b64decode(encoded, validate=True)
        classes = {'OtcmSmtpConnectionProbe.class', 'OtcmSmtpConnectionProbe$SilentLoggingConfiguration.class'}
        with zipfile.ZipFile(io.BytesIO(bytecode)) as archive:
            ensure(set(archive.namelist()) == classes and len(archive.namelist()) == len(classes), 'Unexpected SMTP probe classes')
            ensure(sum(item.file_size for item in archive.infolist()) < 256 * 1024, 'SMTP probe archive too large')
            ensure(all(archive.read(name).startswith(b'\xca\xfe\xba\xbe') for name in classes), 'Invalid SMTP probe class')

        # mysql batch output is hex encoded to preserve raw JSON without escaping or logging it.
        raw = promotion.query(state, "SELECT CONCAT('ROW:',COALESCE(HEX(setting_value),'NULL')) "
                                    "FROM tcm_clinic_setting WHERE setting_key='smtpSettings' LIMIT 2")
        ensure(len(raw) <= 2 * 1024 * 1024, 'SMTP setting exceeds probe input bound')
        rows = raw.splitlines()
        ensure(len(rows) <= 1, 'Ambiguous SMTP settings')
        stored = None
        if rows:
            ensure(re.fullmatch(r'ROW:(?:NULL|[0-9A-Fa-f]*)', rows[0]), 'Invalid SMTP setting read')
            value = rows[0][4:]
            # A present SQL NULL record remains an invalid admin record, never an environment fallback.
            stored = 'null' if value == 'NULL' else bytes.fromhex(value).decode('utf-8')
        source_name = 'admin' if stored is not None else 'environment'
        props = state['properties']
        runtime_properties = {key: props[key] for key in SMTP_RUNTIME_KEYS if props.get(key) not in (None, '')}
        key_file = str(props.get('tcm.smtp.key-file') or (str(props.get('ruoyi.profile', '')) + '/../.otcm/smtp-settings.key'))
        ensure('${' not in key_file and '\0' not in key_file, 'Unresolved SMTP key path')
        key_path = pathlib.Path(key_file)
        if not key_path.is_absolute():
            key_path = state['cwd'] / key_path
        # Match Path.toAbsolutePath().normalize(), without following or replacing a key symlink.
        key_file = os.path.abspath(key_path)

        probe = temporary / 'smtp-probe'
        probe.mkdir(mode=0o700)
        probe_jar = probe / 'smtp-probe.jar'
        probe_jar.write_bytes(bytecode)
        library = probe / 'lib'
        library.mkdir(mode=0o700)
        with zipfile.ZipFile(state['jar']) as archive:
            for name in archive.namelist():
                if not re.fullmatch(r'BOOT-INF/lib/[^/]+\.jar', name):
                    continue
                filename = pathlib.PurePosixPath(name).name
                if filename.startswith(('logback-', 'log4j-')) or (filename.startswith('slf4j-') and not filename.startswith('slf4j-api-')):
                    continue
                with archive.open(name) as original, (library / filename).open('xb') as target:
                    shutil.copyfileobj(original, target)
        # No application resources or Spring bootstrap. Secrets exist only in subprocess stdin/memory.
        command = [state['args'][0], '-XX:-UsePerfData', '-XX:+DisableAttachMechanism',
                   '-Duser.home=' + str(probe), '-Djava.io.tmpdir=' + str(probe),
                   '-cp', str(probe_jar) + os.pathsep + str(library / '*'), 'OtcmSmtpConnectionProbe']
        payload = json.dumps({'runtimeProperties': runtime_properties, 'storedSettings': stored, 'keyFile': key_file}).encode()
        ensure(len(payload) <= 1024 * 1024, 'SMTP probe input exceeds limit')
        result = subprocess.run(command, input=payload, cwd=probe, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=25, env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'HOME': str(probe)})
        ensure(result.returncode == 0 and len(result.stdout) <= 2048, 'SMTP probe did not return a valid result')
        data = json.loads(result.stdout)
        ensure(isinstance(data, dict) and set(data) == {'success', 'code', 'source'}, 'Unexpected SMTP probe output')
        ensure(type(data['success']) is bool and data['code'] in SMTP_PROBE_CODES and data['source'] == source_name,
               'Invalid SMTP probe result')
        ensure(data['success'] == (data['code'] == 'SMTP_CONNECTED'), 'Inconsistent SMTP probe result')
        return {'available': True, **data, 'messagesSent': 0, 'settingsWrites': 0, 'keyFilesCreated': 0,
                'scope': 'Deployed TcmSmtpSettingsService.testConnection with the active database setting and existing encryption key; TLS/AUTH only. No Spring startup, message send, credential save, or key generation.'}
    except subprocess.TimeoutExpired:
        return {'available': True, 'success': False, 'code': 'PROBE_TIMEOUT',
                **({'source': source_name} if source_name else {}), 'messagesSent': 0}
    except Exception:
        # Never include provider responses, SQL values, credentials, paths or subprocess stderr.
        return {'available': True, 'success': False, 'code': 'PROBE_SETUP_FAILED',
                **({'source': source_name} if source_name else {}), 'messagesSent': 0}


def classify_error_message(message):
    """Return only fixed source labels/codes; unknown messages are hashed by caller."""
    source_patterns = (
        ('request-access-denied', ('权限校验失败',)),
        ('request-method-not-supported', ('不支持', '请求')),
        ('request-missing-path-variable', ('请求路径中缺少必需的路径变量',)),
        ('request-argument-type-mismatch', ('请求参数类型不匹配',)),
        ('unhandled-runtime-exception', ('发生未知异常',)),
        ('unhandled-system-exception', ('发生系统异常',)),
        ('token-user-information-error', ('获取用户信息异常',)),
        ('audit-exception', ('异常信息:',)),
        ('request-argument-assembly-error', ('请求参数拼装异常',)),
    )
    categories = {name for name, fragments in source_patterns if all(fragment in message for fragment in fragments)}
    codes = set(re.findall(r'\bSMTP_[A-Z_]+\b', message)) & SMTP_CODES
    known_messages = {
        '邮箱或密码错误': 'AUTH_INVALID_CREDENTIALS', '邮箱和密码不能为空': 'AUTH_CREDENTIALS_REQUIRED',
        '旧密码错误': 'AUTH_OLD_PASSWORD_INVALID', 'access denied': 'ACCESS_DENIED',
        'patient not found': 'PATIENT_NOT_FOUND', 'consultation not found': 'CONSULTATION_NOT_FOUND',
        'appointment not found': 'APPOINTMENT_NOT_FOUND', 'file not found': 'FILE_NOT_FOUND',
        'email log not found': 'EMAIL_LOG_NOT_FOUND', 'failed to load PDF font': 'PDF_FONT_UNAVAILABLE',
        'PDF generation failed:': 'PDF_GENERATION_FAILED',
        'Consultation changed while saving PDF; please retry': 'PDF_CONCURRENT_CHANGE',
        'The original attachment is unavailable. Attach the file before resending.': 'EMAIL_ORIGINAL_ATTACHMENT_UNAVAILABLE',
        'Invoice PDF generation returned no attachment. Please retry.': 'EMAIL_INVOICE_ATTACHMENT_UNAVAILABLE',
        '访问过于频繁，请稍候再试': 'RATE_LIMITED',
    }
    codes.update(code for literal, code in known_messages.items() if literal in message)
    if codes:
        categories.add('known-business-error')
    classes = set(re.findall(r'\b[A-Za-z][A-Za-z0-9]*(?:Exception|Error)\b', message))
    classes &= promotion.SAFE_EXCEPTION_CLASSES | {'ServiceException', 'AccessDeniedException',
                                                   'HttpRequestMethodNotSupportedException', 'MethodArgumentTypeMismatchException'}
    return categories, codes, classes


def journal_error_diagnostics(inspection):
    started = inspection.get('serviceHealth', {}).get('ActiveEnterTimestamp')
    if not started:
        return {'available': False, 'reason': 'service-start-time-unavailable'}
    try:
        invocation = promotion.run(['systemctl', 'show', promotion.SERVICE, '--property=InvocationID', '--value'])
        ensure(re.fullmatch(r'[0-9a-f]{32}', invocation), 'Service invocation unavailable')
        limit = 20000
        command = ['journalctl', '--unit=' + promotion.SERVICE, '--boot', '--since=' + started,
                   '--output=json', '--no-pager', '--lines=' + str(limit + 1), '_SYSTEMD_INVOCATION_ID=' + invocation]
        entries = promotion.run(command).splitlines()
        categories, codes, exceptions, unknown = Counter(), Counter(), Counter(), Counter()
        errors = malformed = 0
        times = []
        for line in entries[-limit:]:
            try:
                record = json.loads(line)
            except (TypeError, ValueError):
                malformed += 1
                continue
            if not isinstance(record, dict) or not isinstance(record.get('MESSAGE'), str):
                continue
            message = record['MESSAGE']
            if str(record.get('PRIORITY')) not in ('0', '1', '2', '3') and not re.search(r'\bERROR\b', message):
                continue
            errors += 1
            safe_categories, safe_codes, safe_classes = classify_error_message(message)
            categories.update(safe_categories)
            codes.update(safe_codes)
            exceptions.update(safe_classes)
            if not safe_categories and not safe_codes:
                unknown[hashlib.sha256(message.encode()).hexdigest()] += 1
            timestamp = record.get('__REALTIME_TIMESTAMP')
            if isinstance(timestamp, str) and re.fullmatch(r'\d{1,18}', timestamp):
                try:
                    times.append(datetime.datetime.fromtimestamp(int(timestamp) / 1000000, datetime.timezone.utc).isoformat())
                except (ValueError, OverflowError, OSError):
                    pass
        return {'available': True, 'since': started, 'errorEntries': errors,
                'sourceCategories': dict(sorted(categories.items())), 'knownBusinessCodes': dict(sorted(codes.items())),
                'knownExceptionClasses': dict(sorted(exceptions.items())), 'unknownMessageSha256Counts': dict(sorted(unknown.items())),
                'errorTimestampRangeUtc': {'first': min(times), 'last': max(times)} if times else None,
                'truncated': len(entries) > limit, 'unparsedEntries': malformed,
                'scope': 'Current service invocation only. Fixed source categories/codes or opaque hashes; no raw messages, URLs, identities or credentials.'}
    except Exception as error:
        return {'available': False, 'errorType': type(error).__name__}


def inspect_integrity():
    ensure(promotion.TARGET == '43.166.154.98', 'Wrong fixed host')
    ensure(BACKUP.is_dir() and not BACKUP.is_symlink(), 'Cutover backup missing')
    backup_checks = {name: promotion.digest(BACKUP / name) == expected for name, expected in EXPECTED.items()}
    ensure(all(backup_checks.values()), 'Cutover backup integrity mismatch')
    promotion.diagnostics.CONFIG_KEYS.update(SMTP_RUNTIME_KEYS)
    state = promotion.runtime()
    ensure(promotion.digest(state['jar']) == CURRENT_JAR, 'Unexpected production backend version')
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='otcm-readonly-integrity-') as directory:
        temporary = pathlib.Path(directory)
        os.chmod(temporary, 0o700)
        files = file_comparison()
        database = database_comparison(state, temporary)
        pdf = pdf_probe(state, temporary)
        smtp = smtp_probe(state, temporary)
    props = state['properties']
    expiry = str(props.get('token.expireTime') or props.get('token.expire-time') or '')
    non_secret = {'tokenExpireMinutes': int(expiry) if re.fullmatch(r'[0-9]{1,8}', expiry) else None,
                  'notificationTimezone': 'America/Toronto',
                  'environmentSmtpHostConfigured': bool(props.get('spring.mail.host')),
                  'environmentSmtpCredentialConfigured': bool(props.get('spring.mail.password')),
                  'smtpSourceNote': 'Environment presence is not the active delivery configuration when an admin SMTP record exists; see database.differentialActivity.effectiveSmtpStorage.'}
    inspection = promotion.inspect()
    return {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'target': promotion.TARGET,
            'readOnly': True, 'productionWrites': 0, 'backup': str(BACKUP), 'backupHashesMatch': backup_checks,
            'files': files, 'database': database, 'pdfDependencies': pdf, 'smtpConnection': smtp, 'nonSecretConfiguration': non_secret,
            'currentInspection': inspection, 'journalErrorDiagnostics': journal_error_diagnostics(inspection)}


def main():
    try:
        print(json.dumps(inspect_integrity(), ensure_ascii=True, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({'ok': False, 'readOnly': True, 'errorType': type(error).__name__}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
