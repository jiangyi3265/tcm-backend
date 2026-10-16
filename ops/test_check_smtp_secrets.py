import unittest
from check_smtp_secrets import findings


class SmtpSecretScanTest(unittest.TestCase):
    def test_detects_credential_without_requiring_its_value_in_the_report(self):
        self.assertEqual(findings(b'first line\npassword: ' + b're_' + b'A' * 32), [2])

    def test_ignores_env_references_and_ordinary_identifier_substrings(self):
        self.assertEqual(findings(b'${SMTP_PASSWORD:}\nbefore_long_identifier_not_a_credential'), [])
