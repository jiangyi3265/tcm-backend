"""Synthetic email/clock fixtures only; no network, SMTP or production data."""
import json
import re
import subprocess
import unittest
from unittest.mock import patch

import otcm_backend_release as release


class EmailSummaryTests(unittest.TestCase):
    def row(self, **changes):
        return {'ageSeconds': 100, 'delivery': 'failed', 'emailType': 'appointment_confirmation',
                'template': 'appointmentConfirmation',
                'error': 'SMTP delivery failed. Check the SMTP settings and connection test.',
                'smtpCode': '', **changes}

    def test_generic_error_remains_unknown_in_every_window(self):
        report = release.summarize_email_rows([self.row()], 200)
        for window in report['windows'].values():
            self.assertEqual(window['failed'], 1)
            self.assertEqual(window['failureCategories']['unknown'], 1)
            self.assertEqual(window['smtpCodes'], {'unavailable': 1})
        self.assertEqual(report['latestFailureAgeSeconds'], 100)
        self.assertEqual(report['genericFailureMessageRows'], 1)
        self.assertIsNone(report['latestSuccessAgeSeconds'])

    def test_structured_failure_types_classify_without_exporting_untrusted_values(self):
        rows = [self.row(deliveryErrorType='MailSendException', deliveryCauseType='SocketTimeoutException'),
                self.row(deliveryErrorType='MailSendException', deliveryCauseType='SMTPAddressFailedException', smtpCode='550'),
                self.row(deliveryErrorType='private@example.invalid', deliveryCauseType='private-token')]
        report = release.summarize_email_rows(rows, 200)
        window = report['windows']['last1Hour']
        self.assertEqual(window['failureCategories']['connectionTimeout'], 1)
        self.assertEqual(window['deliveryErrorTypes'], {'MailSendException': 2, 'unknown': 1})
        self.assertEqual(window['deliveryCauseTypes'], {'SocketTimeoutException': 1, 'SMTPAddressFailedException': 1, 'unknown': 1})
        self.assertEqual(window['smtpCodes'], {'unavailable': 2, '550': 1})
        self.assertNotIn('private', json.dumps(report))

    def test_overlapping_windows_include_boundaries_and_separate_delivery_state(self):
        ages = [0, 3599, 3600, 3601, 86400, 86401, 604800, 604801]
        rows = [self.row(ageSeconds=age) for age in ages]
        rows += [self.row(delivery='sent', ageSeconds=10), self.row(delivery='unknown', ageSeconds=20)]
        result = release.summarize_email_rows(rows, 3600)
        for key, count in [('last1Hour', 3), ('last24Hours', 5), ('last7Days', 7),
                           ('currentBackendInvocation', 3), ('allRecordedHistory', 8)]:
            self.assertEqual(result['windows'][key]['failed'], count)
            self.assertEqual(result['windows'][key]['sent'], 1)
            self.assertEqual(result['windows'][key]['unknown'], 1)
        self.assertEqual(result['latestSuccessAgeSeconds'], 10)

    def test_future_and_missing_timestamps_do_not_appear_recent(self):
        report = release.summarize_email_rows([self.row(ageSeconds=-2), self.row(ageSeconds=None)], 100)
        self.assertEqual(report['futureDatedRows'], 1)
        self.assertEqual(report['missingCreateTimeRows'], 1)
        self.assertEqual(report['windows']['allRecordedHistory']['failed'], 2)
        self.assertEqual(report['windows']['last7Days']['failed'], 0)
        self.assertIsNone(report['latestFailureAgeSeconds'])

    def test_provider_categories_and_reply_codes_are_bounded(self):
        cases = [
            ('535 5.7.8 Authentication failed private@example.invalid', '', 'auth', '535'),
            ('Daily sending quota exceeded private-secret', '', 'dailyLimit', 'unavailable'),
            ('Monthly quota reached private-secret', '', 'quota', 'unavailable'),
            ('Too many requests private-secret', '', 'rateLimit', 'unavailable'),
            ('550 Recipient rejected private@example.invalid', '', 'recipientRejected', '550'),
            ('Sending domain is not verified private.example', '', 'senderDomain', 'unavailable'),
            ('java.net.SocketTimeoutException: Read timed out private-host', '', 'connectionTimeout', 'unavailable'),
            ('SSLHandshakeException certificate failure private-host', '', 'TLS', 'unavailable'),
            ('private-secret', 'SMTP_AUTH_FAILED', 'auth', 'SMTP_AUTH_FAILED'),
            ('INVALID_RECIPIENT', '', 'recipientRejected', 'INVALID_RECIPIENT'),
            ('private-secret', 'INVALID_RECIPIENT', 'recipientRejected', 'INVALID_RECIPIENT'),
            ('private-secret', '999', 'unknown', 'unavailable'),
        ]
        for error, code, category, reply in cases:
            with self.subTest(category=category):
                self.assertEqual(release.classify_email_failure(error, code), (category, reply))

    def test_invalid_recipient_marker_is_exact_and_counted_without_private_text(self):
        rows = [self.row(error='INVALID_RECIPIENT'),
                self.row(error='private-secret', smtpCode='INVALID_RECIPIENT')]
        report = release.summarize_email_rows(rows, 200)
        for window in report['windows'].values():
            self.assertEqual(window['failureCategories']['recipientRejected'], 2)
            self.assertEqual(window['smtpCodes'], {'INVALID_RECIPIENT': 2})
        self.assertNotIn('private', json.dumps(report))
        for value in ('NOT_INVALID_RECIPIENT', 'INVALIDXRECIPIENT', 'INVALID_RECIPIENT private-secret'):
            self.assertEqual(release.classify_email_failure(value, ''), ('unknown', 'unavailable'))

    def test_unknown_template_type_and_error_text_never_returned(self):
        report = release.summarize_email_rows([self.row(emailType='private@example.invalid', template='private-subject',
                    error='private-secret and private-recipient', smtpCode='private-token')], 200)
        encoded = json.dumps(report)
        self.assertNotIn('private', encoded)
        self.assertIn('other', encoded)

    def test_malformed_or_excessive_records_fail_closed(self):
        for row in [self.row(ageSeconds=True), self.row(delivery='private'), {**self.row(), 'to': 'private'}]:
            with self.subTest(row=row), self.assertRaises(release.Guard):
                release.summarize_email_rows([row], 10)
        with patch.object(release, 'MAX_EMAIL_DIAGNOSTIC_ROWS', 1), self.assertRaises(release.Guard):
            release.summarize_email_rows([self.row(), self.row()], 10)

    def test_journal_only_counts_known_failure_marker_and_safe_exception_class(self):
        lines = [json.dumps({'MESSAGE': '\u90ae\u4ef6\u53d1\u9001\u5931\u8d25: to=private@example.invalid, errorType=MailSendException'}),
                 json.dumps({'MESSAGE': '\u90ae\u4ef6\u53d1\u9001\u5931\u8d25: to=private@example.invalid, errorType=PrivateCustomException'}),
                 json.dumps({'MESSAGE': 'unrelated MailSendException private'}), 'invalid']
        report = release.email_journal_summary('\n'.join(lines))
        self.assertEqual(report['matchingFailureEntries'], 2)
        self.assertEqual(report['exceptionClasses'], {'MailSendException': 1, 'unknown': 1})
        self.assertEqual(report['unparsedEntries'], 1)
        self.assertNotIn('private', json.dumps(report).lower())

    def test_journal_accepts_common_address_classes_qualified_or_quoted(self):
        names = ['AddressException', 'MailParseException', 'MailPreparationException', 'UnsupportedEncodingException',
                 'IllegalStateException', 'NullPointerException', 'SMTPAddressFailedException']
        lines = [json.dumps({'MESSAGE': '\u90ae\u4ef6\u53d1\u9001\u5931\u8d25: private, errorType = "jakarta.mail.' + name + '"'}) for name in names]
        report = release.email_journal_summary('\n'.join(lines))
        self.assertEqual(report['exceptionClasses'], {name: 1 for name in names})
        self.assertEqual(report['errorTypeFieldShape']['recognized'], len(names))
        self.assertNotIn('private', json.dumps(report))


class RecipientPatternTests(unittest.TestCase):
    def row(self, **changes):
        detail = {'recipient': 'family@example.invalid', 'patientId': 'private-patient-id', 'appointmentId': '',
                  'appointmentStartTime': '2026-10-31 09:00:00', 'appointmentEndTime': '2026-10-31 10:00:00'}
        return {'ageSeconds': 100, 'delivery': 'failed', 'emailType': 'appointment_confirmation',
                'template': 'appointmentConfirmation', 'failureDetail': detail, **changes}

    def test_repeated_attempts_return_counts_and_cadence_not_identity(self):
        rows = [self.row(ageSeconds=age) for age in (100, 400, 700)]
        report = release.summarize_failed_recipients(rows)
        group = report['groups']['allFailures']
        self.assertEqual(group['distinctRawRecipients'], 1)
        self.assertEqual(group['distinctPatientIds'], 1)
        self.assertEqual(group['distinctExplicitAppointmentIds'], 0)
        self.assertEqual(group['appointmentIdPresentRows'], 0)
        self.assertEqual(group['distinctPatientAndAppointmentTimeSignatures'], 1)
        self.assertEqual(group['maximumAttemptsPerNormalizedRecipient'], 3)
        self.assertEqual(group['consecutiveAttemptGaps270to330Seconds'], 2)
        self.assertEqual(group['recipientFlags']['reservedTestDomain'], 3)
        for private in ('family@', 'private-patient-id', '2026-10-31'):
            self.assertNotIn(private, json.dumps(report))

    def test_only_recent_failures_project_recipient_detail(self):
        rows = [self.row(), self.row(ageSeconds=86401, failureDetail=None), self.row(delivery='sent', failureDetail=None),
                self.row(ageSeconds=-1, failureDetail=None)]
        self.assertEqual(release.summarize_failed_recipients(rows)['groups']['allFailures']['failedAttempts'], 1)
        with self.assertRaises(release.Guard):
            release.summarize_failed_recipients([self.row(failureDetail=None)])

    def test_reserved_domain_boundaries_are_exact(self):
        for domain in ('example.com', 'example.net', 'example.org', 'sub.example.com', 'invalid', 'clinic.invalid', 'clinic.test'):
            self.assertTrue(release.recipient_flags('person@' + domain)['reservedTestDomain'], domain)
        for domain in ('notexample.com', 'example.com.evil', 'test.exampleclinic.ca', 'exampleclinic.ca'):
            self.assertFalse(release.recipient_flags('person@' + domain)['reservedTestDomain'], domain)

    def test_bad_shapes_and_unicode_whitespace_are_fixed_flags(self):
        self.assertTrue(release.recipient_flags(' x@clinic.ca')['hasWhitespace'])
        self.assertTrue(release.recipient_flags('x\u00a0@clinic.ca')['hasWhitespace'])
        self.assertTrue(release.recipient_flags('x@clinic.ca,y@clinic.ca')['hasComma'])
        self.assertTrue(release.recipient_flags('x@@clinic.ca')['multipleAt'])
        self.assertTrue(release.recipient_flags('x..y@clinic.ca')['invalidSimpleAddressShape'])
        self.assertTrue(release.recipient_flags('x@-bad.ca')['invalidSimpleAddressShape'])
        self.assertFalse(release.recipient_flags('first.last+family@clinic.ca')['invalidSimpleAddressShape'])
        self.assertTrue(release.recipient_flags('')['empty'])


class BlockedEmailAggregateTests(unittest.TestCase):
    def counts(self, **changes):
        return {'appointmentsExamined': 8, 'anyBlockedAppointments': 3, 'outboxAppointments': 2,
                'reminderAppointments': 2, 'aftercareAppointments': 0, 'followUpAppointments': 1, **changes}

    def test_fixed_paths_select_counts_only_and_escape_marker_wildcard(self):
        sql = release.blocked_email_count_sql()
        self.assertTrue(sql.startswith('SELECT JSON_OBJECT('))
        self.assertIn("JSON_SEARCH(j,'one','INVALID!_RECIPIENT','!',", sql)
        self.assertIn("'$.notificationOutbox.*.deliveries[*].blockedReason'", sql)
        for key in ('reminderEmailSentAt', 'aftercareEmailSentAt', 'followUpEmailSentAt'):
            self.assertIn("'$." + key + "BlockedReason'", sql)
        for forbidden in ('to_email', 'patient_id', 'appointment_id', "'$.variables", "'$.to'", "'$.body'",
                          'UPDATE ', 'DELETE ', 'INSERT ', 'COMMIT', 'JSON_TABLE'):
            self.assertNotIn(forbidden, sql)
        self.assertIn('COUNT(*)', sql)
        self.assertIn('CASE WHEN JSON_VALID(payload)', sql)

    def test_valid_overlapping_and_empty_counts_return_only_counts_and_fixed_scope(self):
        for counts in [self.counts(), {key: 0 for key in release.BLOCKED_EMAIL_COUNT_KEYS}]:
            report = release.validate_blocked_email_counts(counts)
            self.assertEqual({key: report[key] for key in counts}, counts)
            self.assertEqual(set(report), release.BLOCKED_EMAIL_COUNT_KEYS | {'scope'})

    def test_untrusted_fields_and_impossible_counts_fail_closed(self):
        invalid = [self.counts(address='private@example.invalid'), self.counts(outboxAppointments=-1),
                   self.counts(anyBlockedAppointments=True), self.counts(anyBlockedAppointments='3'),
                   self.counts(appointmentsExamined=2), self.counts(anyBlockedAppointments=1),
                   self.counts(anyBlockedAppointments=6), {'private': 'secret'}]
        for counts in invalid:
            with self.subTest(counts=counts), self.assertRaises(release.Guard):
                release.validate_blocked_email_counts(counts)


class FilteredJournalTests(unittest.TestCase):
    def completed(self, stdout=b'', stderr=b'', returncode=0):
        return subprocess.CompletedProcess(['journalctl'], returncode, stdout, stderr)

    def test_server_filters_before_limit_so_old_failure_survives_busy_journal(self):
        # Simulate journalctl's documented grep/reverse/lines semantics over a
        # journal in which the old unfiltered 20,000-line tail missed the error.
        failure = {'MESSAGE': release.EMAIL_FAILURE_MARKER + ' private@example.invalid errorType=MailSendException'}
        public_file = {'MESSAGE': 'ERROR /api/public/files/private ClientAbortException'}
        archive = [failure, public_file] + [{'MESSAGE': 'INFO unrelated private'}] * 20005
        self.assertNotIn(failure, archive[-20000:])

        def query(args, **kwargs):
            pattern = next(value.removeprefix('--grep=') for value in args if value.startswith('--grep='))
            maximum = int(next(value.removeprefix('--lines=') for value in args if value.startswith('--lines=')))
            self.assertIn('--reverse', args)
            self.assertIn('--case-sensitive=yes', args)
            self.assertIn('_SYSTEMD_INVOCATION_ID=' + 'a' * 32, args)
            self.assertEqual(kwargs['timeout'], 60)
            self.assertEqual(kwargs['stdout'], subprocess.PIPE)
            self.assertEqual(kwargs['stderr'], subprocess.PIPE)
            matches = [entry for entry in reversed(archive) if re.search(pattern, entry['MESSAGE'])][:maximum]
            return self.completed('\n'.join(json.dumps(entry) for entry in matches).encode())

        with patch.object(release.subprocess, 'run', side_effect=query):
            entries, truncated = release.filtered_journal_entries('a' * 32, 'email')
            report = release.email_journal_summary('\n'.join(entries))
            self.assertFalse(truncated)
            self.assertEqual(report['exceptionClasses'], {'MailSendException': 1})
            self.assertNotIn('private', json.dumps(report))
            entries, truncated = release.filtered_journal_entries('a' * 32, 'uploads')
            report = release.upload_journal_summary(entries)
            self.assertFalse(truncated)
            self.assertEqual(report['exceptionClasses'], {'ClientAbortException': 1})
            self.assertNotIn('private', json.dumps(report))

    def test_empty_grep_result_is_normal_but_command_errors_are_not_hidden(self):
        for code in (0, 1):
            for body in (b'', b'-- No entries --\n'):
                with self.subTest(code=code, body=body), \
                        patch.object(release.subprocess, 'run', return_value=self.completed(body, returncode=code)):
                    self.assertEqual(release.filtered_journal_entries('a' * 32, 'email'), ([], False))
        for result in (self.completed(stderr=b'private unsupported flag', returncode=1),
                       self.completed(b'private raw journal', returncode=1),
                       self.completed(returncode=2)):
            with patch.object(release.subprocess, 'run', return_value=result), self.assertRaises(release.Guard) as error:
                release.filtered_journal_entries('a' * 32, 'email')
            self.assertNotIn('private', str(error.exception))

    def test_bound_keeps_latest_matches_and_rejects_untrusted_queries(self):
        recent = json.dumps({'MESSAGE': release.EMAIL_FAILURE_MARKER + ' errorType=MailSendException'})
        oldest = json.dumps({'MESSAGE': release.EMAIL_FAILURE_MARKER + ' errorType=MailAuthenticationException'})
        with patch.object(release, 'FILTERED_JOURNAL_LIMIT', 2), \
                patch.object(release.subprocess, 'run', return_value=self.completed((recent + '\n' + recent + '\n' + oldest).encode())):
            entries, truncated = release.filtered_journal_entries('a' * 32, 'email')
        self.assertTrue(truncated)
        self.assertEqual(release.email_journal_summary('\n'.join(entries))['exceptionClasses'], {'MailSendException': 2})
        with patch.object(release.subprocess, 'run') as run:
            for invocation, query in [('private', 'email'), ('a' * 32, 'private')]:
                with self.assertRaises(release.Guard):
                    release.filtered_journal_entries(invocation, query)
            run.assert_not_called()


class EmailReadOnlyProbeTests(unittest.TestCase):
    def setUp(self):
        self.clock = {'databaseNow': '2026-10-30 17:20:00', 'databaseUtcNow': '2026-10-30 09:20:00',
                      'databaseUtcOffsetSeconds': 28800}
        self.row = {'ageSeconds': 3, 'delivery': 'failed', 'emailType': 'invoice', 'template': 'invoice',
                    'error': 'private-secret', 'smtpCode': '', 'failureDetail': {
                        'recipient': 'private@example.invalid', 'patientId': 'private-patient', 'appointmentId': '',
                        'appointmentStartTime': '', 'appointmentEndTime': ''}}
        self.blocked = {'appointmentsExamined': 10, 'anyBlockedAppointments': 1, 'outboxAppointments': 1,
                        'reminderAppointments': 0, 'aftercareAppointments': 0, 'followUpAppointments': 0}
        self.raw = '\n'.join(json.dumps(value) for value in (self.clock, self.blocked, self.row))

    def test_read_only_transaction_db_relative_age_and_no_identity_projection(self):
        with patch.object(release, 'email_service_clock', return_value=('a' * 32, 100)), \
                patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                patch.object(release.promotion, 'run', return_value=self.raw) as run, \
                patch.object(release, 'filtered_journal_entries', return_value=([], False)) as journal:
            report = release.email_diagnostics({})
        self.assertTrue(report['available'])
        self.assertEqual(report['databaseUtcOffsetSeconds'], 28800)
        self.assertEqual(report['latestFailureAgeSeconds'], 3)
        self.assertEqual(report['blockedInvalidRecipients']['outboxAppointments'], 1)
        self.assertEqual(report['blockedInvalidRecipients']['anyBlockedAppointments'], 1)
        sql = run.call_args_list[0].kwargs['input'].decode()
        self.assertTrue(sql.startswith('START TRANSACTION READ ONLY; SELECT '))
        self.assertTrue(sql.endswith('; ROLLBACK;'))
        self.assertIn('TIMESTAMPDIFF(SECOND,create_time,NOW())', sql)
        for forbidden in ('subject', 'body', 'UPDATE ', 'DELETE ', 'INSERT ', 'COMMIT'):
            self.assertNotIn(forbidden, sql)
        self.assertIn("='failed' AND TIMESTAMPDIFF(SECOND,create_time,NOW()) BETWEEN 0 AND 86400 THEN JSON_OBJECT", sql)
        self.assertIn("'$.variables.patientId'", sql)
        journal.assert_called_once_with('a' * 32, 'email')
        self.assertTrue(report['currentInvocationJournal']['filteredBeforeLimit'])
        self.assertEqual(report['messagesSent'], 0)
        self.assertEqual(report['databaseWrites'], 0)
        self.assertNotIn('private', json.dumps(report))

    def test_natural_scheduler_comparison_retains_count_as_latest_failure_ages(self):
        reports = []
        for age in (3, 303):
            raw = '\n'.join(json.dumps(value) for value in (self.clock, self.blocked,
                {**self.row, 'ageSeconds': age, 'error': 'INVALID_RECIPIENT'}))
            with patch.object(release, 'email_service_clock', return_value=('a' * 32, 400)), \
                    patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                    patch.object(release.promotion, 'run', return_value=raw) as run, \
                    patch.object(release, 'filtered_journal_entries', return_value=([], False)) as journal:
                reports.append(release.email_diagnostics({}))
            self.assertEqual(run.call_count, 1)
            self.assertEqual(run.call_args_list[0].args[0][0], 'mysql')
            journal.assert_called_once_with('a' * 32, 'email')
        self.assertTrue(all(report['available'] for report in reports))
        self.assertEqual(reports[0]['blockedInvalidRecipients'], reports[1]['blockedInvalidRecipients'])
        self.assertEqual([report['latestFailureAgeSeconds'] for report in reports], [3, 303])
        self.assertEqual([report['windows']['currentBackendInvocation']['failed'] for report in reports], [1, 1])

    def test_missing_or_private_blocked_aggregate_discards_report(self):
        for middle in ({'to': 'private@example.invalid'}, {**self.blocked, 'outboxAppointments': -1}):
            raw = '\n'.join(json.dumps(value) for value in (self.clock, middle, self.row))
            with patch.object(release, 'email_service_clock', return_value=('a' * 32, 100)), \
                    patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                    patch.object(release.promotion, 'run', return_value=raw):
                report = release.email_diagnostics({})
            self.assertFalse(report['available'])
            self.assertNotIn('windows', report)
            self.assertNotIn('private', json.dumps(report))

    def test_restart_during_probe_discards_mixed_invocation_report(self):
        with patch.object(release, 'email_service_clock', side_effect=[('a' * 32, 100), ('b' * 32, 1)]), \
                patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                patch.object(release.promotion, 'run', return_value=self.raw), \
                patch.object(release, 'filtered_journal_entries', return_value=([], False)):
            report = release.email_diagnostics({})
        self.assertFalse(report['available'])
        self.assertNotIn('windows', report)

    def test_untrusted_clock_and_exception_text_never_returned(self):
        for raw in ['private-secret', json.dumps({**self.clock, 'databaseNow': 'private-secret'})]:
            with patch.object(release, 'email_service_clock', return_value=('a' * 32, 100)), \
                    patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                    patch.object(release.promotion, 'run', return_value=raw):
                report = release.email_diagnostics({})
            self.assertFalse(report['available'])
            self.assertNotIn('private', json.dumps(report))

    def test_service_age_uses_monotonic_clock(self):
        raw = 'InvocationID=' + 'a' * 32 + '\nActiveEnterTimestampMonotonic=100000000'
        with patch.object(release.promotion, 'run', return_value=raw), patch.object(release.time, 'monotonic', return_value=250.8):
            self.assertEqual(release.email_service_clock(), ('a' * 32, 150))


if __name__ == '__main__':
    unittest.main()
