"""Offline certification evidence contracts: no Twilio/Gemini network or real dial."""
import copy
import json
from pathlib import Path
import sys
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).parent))
from voice_pilot_phase6_live_evidence import assess

EVIDENCE = Path(__file__).resolve().parents[2] / (
    "docs/telephony/evidence/2026-10-12-phase6-no-live-call.json"
)
MAIN_SHA = "a" * 40


def synthetic_complete():
    booking = str(uuid.UUID("e49024bc-0d79-411c-813d-f101f43a02dc"))
    ref = "offline-test-evidence-12345"
    return {
        "deployment": {
            "candidate_sha": MAIN_SHA, "railway_deployed_sha": MAIN_SHA,
            "main_sha": MAIN_SHA, "full_ci_sha": MAIN_SHA,
            "full_ci_success": True,
            "isolated_backup_restore_verified": True,
            "backup_restore_evidence_ref": ref,
            "flyway_migrations_verified": True, "flyway_evidence_ref": ref,
            "carrier_ownership_verified": True, "local_preflight_passed": True,
            "carrier_evidence_ref": ref,
            "pilot_running_for_target_tenant": True,
            "controlled_global_voice_enabled": True,
            "other_tenants_protected": True,
        },
        "authorization": {
            "participant_consented": True, "consent_evidence_ref": ref,
            "controlled_time_window_confirmed": True,
            "caller_e164": "+14355652512",
            "test_recipient_e164": "+12025550123",
            "max_duration_seconds": 60, "carrier_termination_control_verified": True,
            "max_total_cost_usd": "1.00",
            "twilio_tariff_verified": True, "gemini_tariff_verified": True,
        },
        "call": {
            "tenant_id": "044a5d73-2df7-4e77-849f-8639d05ac30b",
            "phone_sid": "PN62601c6420599e74bc940f42de2dd1e4",
            "telephony_provider": "twilio", "ai_provider": "gemini",
            "call_sid": "CA" + "b" * 32, "stream_sid": "MZ" + "c" * 32,
            "carrier_call_evidence_ref": ref,
            "started_at": "2026-10-12T21:30:00-04:00",
            "ended_at": "2026-10-12T21:30:45-04:00",
            "carrier_completed": True, "media_started": True,
            "media_stopped": True, "gemini_connected": True,
            "audio_from_ai_received": True, "no_audio_after_hangup": True,
            "human_heard_ai": True, "human_spoke_to_ai": True,
            "barge_in_successful": True, "human_observation_ref": ref,
            "created_booking_id": booking, "cancelled_booking_id": booking,
            "synthetic_booking_cleanup_verified": True,
            "booking_audit_evidence_ref": ref,
        },
        "cost": {
            "twilio_billed_usd": "0.10", "gemini_billed_usd": "0.20",
            "twilio_billing_ref": ref, "gemini_billing_ref": ref,
        },
    }


class Phase6RealCallEvidenceTest(unittest.TestCase):
    def test_real_current_evidence_stays_blocked_without_any_dial(self):
        result = assess(json.loads(EVIDENCE.read_text(encoding="utf-8")))
        self.assertEqual("BLOCKED", result.state)
        self.assertIn("CANDIDATE_NOT_DEPLOYED_AND_CERTIFIED", result.blockers)
        self.assertIn("RECOVERABLE_BACKUP_NOT_VERIFIED", result.blockers)
        self.assertIn("PARTICIPANT_CONSENT_MISSING", result.blockers)
        self.assertIn("CARRIER_CALL_IDENTIFIERS_NOT_VERIFIED", result.blockers)
        self.assertIn("HUMAN_AUDIBLE_CONVERSATION_NOT_CERTIFIED", result.blockers)
        self.assertIn("REAL_PROVIDER_BILLING_NOT_RECONCILED", result.blockers)
        self.assertFalse(result.as_dict()["allowed_to_dial"])
        self.assertFalse(result.as_dict()["live_call_certified"])
        self.assertFalse(result.as_dict()["commercial_go"])

    def test_all_claims_are_still_only_candidate_for_independent_human_review(self):
        result = assess(synthetic_complete())
        self.assertEqual("READY_FOR_INDEPENDENT_LIVE_EVIDENCE_REVIEW", result.state)
        self.assertEqual((), result.blockers)
        self.assertFalse(result.as_dict()["live_call_certified"])
        self.assertFalse(result.as_dict()["commercial_go"])
        self.assertFalse(result.as_dict()["allowed_to_dial"])

    def test_missing_sections_and_non_objects_cannot_pass(self):
        for item in [None, {}, [], "READY", {"call": ["fake"]},
                     {"authorization": {"participant_consented": True}}]:
            with self.subTest(item=item):
                self.assertEqual("BLOCKED", assess(item).state)

    def test_foreign_tenant_phone_sid_or_ai_provider_cannot_certify(self):
        for key, bad in [("tenant_id", str(uuid.uuid4())),
                         ("phone_sid", "PN" + "e" * 32),
                         ("telephony_provider", "other"),
                         ("ai_provider", "openai")]:
            with self.subTest(key=key):
                d = synthetic_complete()
                d["call"][key] = bad
                self.assertIn("CALL_TENANT_OR_PROVIDER_MISMATCH",
                              assess(d).blockers)

    def test_call_never_passes_with_forged_identifiers_or_without_carrier_receipt(self):
        for key, bad in [("call_sid", "not-a-call"), ("stream_sid", "bad"),
                         ("carrier_call_evidence_ref", None)]:
            with self.subTest(key=key):
                d = synthetic_complete()
                d["call"][key] = bad
                self.assertIn("CARRIER_CALL_IDENTIFIERS_NOT_VERIFIED",
                              assess(d).blockers)

    def test_missing_media_or_no_audio_after_hangup_is_a_failure(self):
        for key in ("carrier_completed", "media_started", "media_stopped",
                    "gemini_connected", "audio_from_ai_received",
                    "no_audio_after_hangup"):
            with self.subTest(key=key):
                d = synthetic_complete()
                d["call"][key] = False
                self.assertIn("BIDIRECTIONAL_MEDIA_NOT_CERTIFIED",
                              assess(d).blockers)

    def test_human_observation_is_required_even_with_perfect_media_telemetry(self):
        for key in ("human_heard_ai", "human_spoke_to_ai", "barge_in_successful"):
            with self.subTest(key=key):
                d = synthetic_complete()
                d["call"][key] = False
                self.assertIn("HUMAN_AUDIBLE_CONVERSATION_NOT_CERTIFIED",
                              assess(d).blockers)
        d = synthetic_complete()
        d["call"]["human_observation_ref"] = None
        self.assertIn("HUMAN_AUDIBLE_CONVERSATION_NOT_CERTIFIED",
                      assess(d).blockers)

    def test_a_different_booking_id_is_not_a_valid_cleanup(self):
        d = synthetic_complete()
        d["call"]["cancelled_booking_id"] = str(uuid.uuid4())
        self.assertIn("SAME_BOOKING_CREATION_CANCELLATION_NOT_PROVEN",
                      assess(d).blockers)
        d["call"]["cancelled_booking_id"] = d["call"]["created_booking_id"]
        d["call"]["synthetic_booking_cleanup_verified"] = False
        self.assertIn("SAME_BOOKING_CREATION_CANCELLATION_NOT_PROVEN",
                      assess(d).blockers)

    def test_duration_requires_absolute_timezone_and_hard_termination(self):
        for end in ("2026-10-12T21:32:01-04:00",
                    "2026-10-12T21:30:00-04:00",
                    "2026-10-12T21:30:45"):
            with self.subTest(end=end):
                d = synthetic_complete()
                d["call"]["ended_at"] = end
                self.assertIn("CALL_DURATION_NOT_PROVEN_WITHIN_LIMIT",
                              assess(d).blockers)
        d = synthetic_complete()
        d["authorization"]["carrier_termination_control_verified"] = False
        self.assertIn("HARD_CALL_DURATION_OR_STOP_NOT_VERIFIED",
                      assess(d).blockers)

    def test_cost_exceeding_limit_missing_invoices_and_unknown_rates_block(self):
        for key, val in [("twilio_billed_usd", "1.01"),
                         ("gemini_billed_usd", None),
                         ("gemini_billed_usd", "-0.01"),
                         ("gemini_billed_usd", "NaN"),
                         ("twilio_billing_ref", None),
                         ("gemini_billing_ref", None)]:
            with self.subTest(key=key, value=val):
                d = synthetic_complete()
                d["cost"][key] = val
                self.assertIn("REAL_PROVIDER_BILLING_NOT_RECONCILED",
                              assess(d).blockers)
        d = synthetic_complete()
        d["authorization"]["gemini_tariff_verified"] = False
        self.assertIn("PROVIDER_PRICES_OR_BUDGET_UNKNOWN", assess(d).blockers)

    def test_pilot_deployment_restore_and_auth_are_independent_gatekeepers(self):
        cases = [
            ("deployment", "railway_deployed_sha", "f" * 40,
             "CANDIDATE_NOT_DEPLOYED_AND_CERTIFIED"),
            ("deployment", "isolated_backup_restore_verified", False,
             "RECOVERABLE_BACKUP_NOT_VERIFIED"),
            ("deployment", "local_preflight_passed", False,
             "TENANT_CARRIER_BINDING_NOT_CERTIFIED"),
            ("deployment", "other_tenants_protected", False,
             "CONTROLLED_PILOT_NOT_VERIFIED"),
            ("authorization", "participant_consented", False,
             "PARTICIPANT_CONSENT_MISSING"),
            ("authorization", "test_recipient_e164", None,
             "TEST_NUMBERS_NOT_CONFIRMED"),
            ("authorization", "max_duration_seconds", 1000,
             "HARD_CALL_DURATION_OR_STOP_NOT_VERIFIED"),
        ]
        for section, key, bad, expected in cases:
            with self.subTest(key=key):
                d = synthetic_complete()
                d[section][key] = bad
                self.assertIn(expected, assess(d).blockers)

    def test_untrusted_evidence_does_not_echo_secrets_or_identifying_data(self):
        d = synthetic_complete()
        d["call"]["human_observation_ref"] = "sk-proj-secret-not-for-logs"
        result = assess(d)
        self.assertEqual("BLOCKED", result.state)
        output = json.dumps(result.as_dict())
        self.assertNotIn("sk-proj", output)
        self.assertNotIn("+12025550123", output)
        self.assertNotIn("CA" + "b" * 32, output)


if __name__ == "__main__":
    unittest.main()
