"""Synthetic cutover/current patient comparisons; no production, network or DB access."""
import io
import json
import unittest

import otcm_data_integrity as integrity


class PatientPayloadAuditTests(unittest.TestCase):
    def patient(self, payload, consent='1', active='0'):
        return {'payload': ['string', json.dumps(payload)] if payload is not None else ['null'],
                'consent_signed': ['number', consent] if consent is not None else ['null'],
                'is_active': ['number', active] if active is not None else ['null'],
                'update_time': ['string', '2026-10-30 11:00:00']}

    def tables(self, rows):
        return {'tcm_patient': {
            'rows': {key: (tuple(json.dumps(row, sort_keys=True)), 1) for key, row in rows.items()},
            'facts': {key: integrity.row_facts('tcm_patient', row, b'synthetic-salt') for key, row in rows.items()}}}

    def audit(self, previous, current):
        return integrity.patient_payload_loss_candidates(self.tables(previous), self.tables(current))

    def test_missing_and_explicitly_cleared_fields_are_separate(self):
        old = self.patient({'addressStreet': 'private street', 'notes': 'private note', 'tags': ['private tag'],
                            'latestIntakeFormData': {'private condition': 'private value'}, 'emails': ['private@test.invalid']})
        new = self.patient({'notes': '', 'tags': [], 'emails': None}, consent='0', active='1')
        result = self.audit({'private-id': old}, {'private-id': new})
        self.assertTrue(result['available'])
        self.assertEqual(result['nonemptyPayloadKeysNowMissing'], {'addressStreet': 1, 'latestIntakeFormData': 1})
        self.assertEqual(result['nonemptyPayloadKeysNowEmpty'], {'emails': 1, 'notes': 1, 'tags': 1})
        self.assertEqual(result['stateTransitions'], {'consent_signed': {'1_to_0': 1}, 'is_active': {'0_to_1': 1}})
        self.assertEqual(result['counts']['rowsRequiringReview'], 1)
        self.assertEqual(result['counts']['rowsWithPayloadLossAndConsentReset'], 1)
        self.assertEqual(result['counts']['rowsWithPayloadLossAndActiveEnabled'], 1)
        self.assertNotIn('private', json.dumps(result))

    def test_unchanged_added_and_removed_patients_are_not_reviewed(self):
        row = self.patient({'notes': 'private'})
        result = self.audit({'unchanged': row, 'removed': row}, {'unchanged': row, 'added': self.patient({})})
        self.assertEqual(result['changedExistingPatients'], 0)
        self.assertEqual(result['counts']['rowsRequiringReview'], 0)
        self.assertEqual(result['removedPayloadKeys'], {})

    def test_null_and_false_are_not_confused_with_missing_data(self):
        old = self.patient({'notes': None, 'tags': [], 'isStaffProfile': False, 'latestIntakeCompleted': 0}, consent=None)
        new = self.patient({'isStaffProfile': False, 'latestIntakeCompleted': 0}, consent='0')
        result = self.audit({'one': old}, {'one': new})
        self.assertEqual(result['removedPayloadKeys'], {'notes': 1, 'tags': 1})
        self.assertEqual(result['nonemptyPayloadKeysNowMissing'], {})
        self.assertEqual(result['stateTransitions']['consent_signed'], {'null_to_0': 1})
        self.assertEqual(result['counts']['rowsRequiringReview'], 0)
        facts = integrity.row_facts('tcm_patient', old, b'salt')
        self.assertEqual(facts['payloadFields']['isStaffProfile'], 'nonempty')
        self.assertEqual(facts['payloadFields']['latestIntakeCompleted'], 'nonempty')

    def test_unknown_keys_and_contents_never_enter_patient_facts_or_report(self):
        old = self.patient({'private-unknown-field': 'private value', 'intakeToken': 'private-token'})
        new = self.patient({'private-other-field': 'private value'})
        facts = integrity.row_facts('tcm_patient', old, b'salt')
        self.assertEqual(facts['payloadFields'], {'intakeToken': 'nonempty'})
        result = self.audit({'private-id': old}, {'private-id': new})
        self.assertEqual(result['nonemptyPayloadKeysNowMissing'], {'intakeToken': 1})
        self.assertNotIn('private', json.dumps([facts, result]))

    def test_valid_payload_becoming_invalid_is_explicit_review_candidate(self):
        old = self.patient({'notes': 'private'})
        new = self.patient(None)
        result = self.audit({'one': old}, {'one': new})
        self.assertEqual(result['counts']['invalidPayloadPairs'], 1)
        self.assertEqual(result['counts']['rowsWithPreviouslyValidPayloadNowInvalid'], 1)
        self.assertEqual(result['counts']['rowsRequiringReview'], 1)
        self.assertEqual(result['nonemptyPayloadKeysNowMissing'], {})

    def test_unknown_state_values_are_fixed_other_category(self):
        result = self.audit({'one': self.patient({}, consent='private')}, {'one': self.patient({}, consent='0')})
        self.assertEqual(result['stateTransitions']['consent_signed'], {'other_to_0': 1})
        self.assertNotIn('private', json.dumps(result))

    def test_changed_candidate_count_is_bounded_without_partial_findings(self):
        count = integrity.MAX_CHANGED_PATIENTS + 1
        result = self.audit({str(n): self.patient({'notes': 'old'}) for n in range(count)},
                            {str(n): self.patient({}) for n in range(count)})
        self.assertFalse(result['available'])
        self.assertEqual(result['reason'], 'changedPatientBoundExceeded')
        self.assertNotIn('counts', result)

    def test_missing_facts_fail_closed(self):
        before = self.tables({'one': self.patient({'notes': 'old'})})
        after = self.tables({'one': self.patient({})})
        after['tcm_patient']['facts'] = {}
        result = integrity.patient_payload_loss_candidates(before, after)
        self.assertFalse(result['available'])
        self.assertEqual(result['reason'], 'patientFactsUnavailable')

    def test_dump_parser_retains_only_approved_patient_facts(self):
        sql = ('CREATE TABLE `sys_user` (\n `user_id` int,\n PRIMARY KEY (`user_id`)\n);\n'
               'CREATE TABLE `tcm_patient` (\n `id` varchar(64),\n `payload` text,\n `consent_signed` int,\n `is_active` int,\n PRIMARY KEY (`id`)\n);\n'
               'INSERT INTO `tcm_patient` VALUES (\'private-id\',\'{"notes":"private content","private-key":"private value"}\',1,0);\n')
        parsed = integrity.parse_dump(io.StringIO(sql), b'synthetic-salt')
        facts = list(parsed['tcm_patient']['facts'].values())
        self.assertEqual(facts[0]['payloadFields'], {'notes': 'nonempty'})
        self.assertEqual(facts[0]['states'], {'consent_signed': '1', 'is_active': '0'})
        self.assertNotIn('private', json.dumps(parsed))


if __name__ == '__main__':
    unittest.main()
