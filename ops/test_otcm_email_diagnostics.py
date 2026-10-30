"""Synthetic email/clock fixtures only; no network, SMTP or production data."""
import json
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
            ('private-secret', '999', 'unknown', 'unavailable'),
        ]
        for error, code, category, reply in cases:
            with self.subTest(category=category):
                self.assertEqual(release.classify_email_failure(error, code), (category, reply))

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


class EmailReadOnlyProbeTests(unittest.TestCase):
    def setUp(self):
        self.clock = {'databaseNow': '2026-10-30 17:20:00', 'databaseUtcNow': '2026-10-30 09:20:00',
                      'databaseUtcOffsetSeconds': 28800}
        self.row = {'ageSeconds': 3, 'delivery': 'failed', 'emailType': 'invoice', 'template': 'invoice',
                    'error': 'private-secret', 'smtpCode': ''}
        self.raw = json.dumps(self.clock) + '\n' + json.dumps(self.row)

    def test_read_only_transaction_db_relative_age_and_no_identity_projection(self):
        with patch.object(release, 'email_service_clock', return_value=('a' * 32, 100)), \
                patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                patch.object(release.promotion, 'run', side_effect=[self.raw, '']) as run:
            report = release.email_diagnostics({})
        self.assertTrue(report['available'])
        self.assertEqual(report['databaseUtcOffsetSeconds'], 28800)
        self.assertEqual(report['latestFailureAgeSeconds'], 3)
        sql = run.call_args_list[0].kwargs['input'].decode()
        self.assertTrue(sql.startswith('START TRANSACTION READ ONLY; SELECT '))
        self.assertTrue(sql.endswith('; ROLLBACK;'))
        self.assertIn('TIMESTAMPDIFF(SECOND,create_time,NOW())', sql)
        for forbidden in ('to_email', 'subject', 'body', 'UPDATE ', 'DELETE ', 'INSERT ', 'COMMIT'):
            self.assertNotIn(forbidden, sql)
        self.assertIn('_SYSTEMD_INVOCATION_ID=' + 'a' * 32, run.call_args_list[1].args[0])
        self.assertEqual(report['messagesSent'], 0)
        self.assertEqual(report['databaseWrites'], 0)
        self.assertNotIn('private', json.dumps(report))

    def test_restart_during_probe_discards_mixed_invocation_report(self):
        with patch.object(release, 'email_service_clock', side_effect=[('a' * 32, 100), ('b' * 32, 1)]), \
                patch.object(release.promotion, 'mysql', return_value=('test', {}, [])), \
                patch.object(release.promotion, 'run', side_effect=[self.raw, '']):
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
