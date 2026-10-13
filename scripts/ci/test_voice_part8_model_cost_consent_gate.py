"""Adversarial no-go tests: Gemini model identity, consent and price bounds."""
from __future__ import annotations

from copy import deepcopy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "voice_part8_gate", ROOT / "scripts/ci/voice_part8_model_cost_consent_gate.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class VoicePart8OfflinePreflightTests(unittest.TestCase):
    def setUp(self):
        self.valid = gate.fixture(optimistic=True)

    def report(self, doc=None):
        return gate.assess(self.valid if doc is None else doc)

    def blocked(self, expected, doc):
        result = self.report(doc)
        self.assertEqual("STOP", result["state"], result)
        self.assertIn(expected, result["blockers"])
        self.assertFalse(result["may_call"])
        self.assertFalse(result["may_deploy"])
        self.assertFalse(result["commercial_go"])

    def altered(self, section, key, value):
        doc = deepcopy(self.valid)
        doc[section][key] = value
        return doc

    def test_valid_optimistic_rehearsal_never_authorizes_external_activity(self):
        x = self.report()
        self.assertEqual("SYNTHETIC_REVIEW_ONLY", x["state"])
        self.assertEqual([], x["blockers"])
        self.assertFalse(x["source_trusted"])
        self.assertFalse(x["provider_handshake_performed"])
        self.assertFalse(x["carrier_request_performed"])
        self.assertFalse(x["charged_calls_performed"])
        for key in ("may_merge", "may_deploy", "may_migrate", "may_call",
                    "live_call_certified", "commercial_go"):
            self.assertFalse(x[key], key)
        self.assertEqual("1.4000", x["estimated_reserved_usd"])

    def test_default_unknown_real_evidence_stops(self):
        self.assertEqual("STOP", self.report(gate.fixture())["state"])

    def test_missing_input_stops(self):
        self.blocked("INVALID_INPUT", None)
        self.blocked("INVALID_INPUT", [])
        self.blocked("MODEL_NOT_EXACT_LOCAL_ALLOWLIST", {})

    def test_untrusted_real_file_or_fake_source_stops(self):
        for source in (None, "real", "production", "TWILIO", 1, True):
            with self.subTest(source=source):
                doc = deepcopy(self.valid)
                doc["source"] = source
                self.blocked("EXTERNAL_ATTESTATION_NOT_VERIFIED", doc)

    def test_legacy_unknown_or_injected_model_stops(self):
        for value in ("gemini-3.1-flash-live-preview",
                      "gemini-3.8-live-extended-thinking",
                      "models/gemini-3.8-live", "gemini-3.8-flash",
                      " gemini-3.8-live ", None, "evil\nAPI_KEY=secret"):
            with self.subTest(model=value):
                self.blocked(
                    "MODEL_NOT_EXACT_LOCAL_ALLOWLIST",
                    self.altered("model", "id", value))
        self.blocked(
            "MODEL_NOT_EXACT_LOCAL_ALLOWLIST",
            self.altered("model", "tier", "LEGACY_PREVIEW"))

    def test_nonofficial_key_exfiltration_endpoints_stop(self):
        for suffix in ("?key=fake", "#fragment", "/", "&x=a"):
            self.blocked(
                "NON_OFFICIAL_WEBSOCKET_ENDPOINT",
                self.altered("model", "endpoint", gate.OFFICIAL_WS + suffix))
        self.blocked(
            "NON_OFFICIAL_WEBSOCKET_ENDPOINT",
            self.altered("model", "endpoint", "wss://evil.example/"))

    def test_unselected_provider_and_missing_local_readiness(self):
        self.blocked(
            "GEMINI_CONFIG_OR_ROUTER_NOT_PROVEN",
            self.altered("model", "selected_voice_provider", "OTHER"))
        for name in ("api_key_configured", "local_configuration_valid"):
            with self.subTest(name=name):
                self.blocked(
                    "GEMINI_CONFIG_OR_ROUTER_NOT_PROVEN",
                    self.altered("model", name, False))

    def test_claims_do_not_authenticate_google_project_or_carrier(self):
        for key in self.valid["evidence"]:
            with self.subTest(key=key):
                code = ("RECOVERY_OR_TENANT_EVIDENCE_MISSING"
                        if key in ("current_backup_and_restore_attested",
                                   "independent_encrypted_copy_attested",
                                   "flyway_rls_attested")
                        else "REAL_CARRIER_OR_MODEL_ENTITLEMENT_NOT_ATTESTED")
                self.blocked(code, self.altered("evidence", key, False))

    def test_operator_participant_and_recording_consent(self):
        for key in ("operator_consent", "participant_consent",
                    "recording_policy_reviewed"):
            self.blocked(
                "CONSENT_NOT_ATTESTED", self.altered("consent", key, False))

    def test_approvers_must_be_distinct_and_look_structurally_valid(self):
        for value in ([], ["reviewer-a"], ["reviewer-a", "reviewer-a"],
                      ["reviewer-a", "admin"], [True, "reviewer-b"], None):
            with self.subTest(value=value):
                self.blocked(
                    "TWO_INDEPENDENT_APPROVERS_MISSING",
                    self.altered("consent", "two_approvers", value))

    def test_external_effects_fences_always_closed(self):
        for flag in ("real_calls_enabled", "outbound_enabled",
                     "gemini_probe_enabled", "twilio_mutations_enabled"):
            self.blocked("LIVE_EFFECT_FENCE_NOT_CLOSED",
                         self.altered("fences", flag, True))
        for flag in ("pilot_paused", "tenant_isolation"):
            self.blocked("LIVE_EFFECT_FENCE_NOT_CLOSED",
                         self.altered("fences", flag, False))

    def test_bounded_duration_and_single_call(self):
        for value in (0, 181, -1, True, 60.0, "60", None):
            with self.subTest(value=value):
                self.blocked("CALL_DURATION_OR_COUNT_UNBOUNDED",
                             self.altered("budget", "max_call_seconds", value))
        for value in (0, 2, True, "1"):
            self.blocked("CALL_DURATION_OR_COUNT_UNBOUNDED",
                         self.altered("budget", "max_calls", value))

    def test_costs_must_be_positive_finite_decimal_strings(self):
        for value in ("0", "-1", "NaN", "Infinity", "1e+30",
                      "0.0.1", "", "99999999999999999999", 0.1, 0, True, None):
            with self.subTest(value=value):
                self.blocked("UNKNOWN_OR_UNBOUNDED_PROVIDER_PRICES",
                             self.altered("budget", "gemini_usd_per_minute", value))

    def test_cap_must_not_exceed_safety_ceiling(self):
        for value in ("0", "5.01", "999999999999", "NaN", None):
            with self.subTest(value=value):
                self.blocked("UNKNOWN_OR_UNBOUNDED_PROVIDER_PRICES",
                             self.altered("budget", "total_usd_cap", value))

    def test_costs_use_round_up_and_2x_reserve(self):
        d = self.altered("budget", "max_call_seconds", 61)
        r = self.report(d)
        self.assertEqual("STOP", r["state"])
        self.assertEqual("2.8000", r["estimated_reserved_usd"])
        self.assertIn("RESERVED_BUDGET_EXCEEDS_CEILING", r["blockers"])
        d["budget"]["total_usd_cap"] = "2.80"
        self.assertEqual("SYNTHETIC_REVIEW_ONLY", self.report(d)["state"])

    def test_hard_caps_required_even_for_small_amounts(self):
        for flag in ("provider_hard_cap_attested", "pilot_hard_cap_attested"):
            self.blocked("BILLABLE_HARD_CAP_NOT_ATTESTED",
                         self.altered("budget", flag, False))

    def test_redaction_of_tokens_phone_and_arbitrary_source(self):
        doc = deepcopy(self.valid)
        secret = "secret-sent-from-test-do-not-log"
        doc["model"]["api_key"] = secret
        doc["carrier"]["account_sid"] = secret
        doc["carrier"]["phone_number"] = secret
        doc["consent"]["participant_name"] = secret
        self.assertNotIn(secret, str(self.report(doc)))
        doc["model"]["id"] = secret
        self.assertNotIn(secret, str(self.report(doc)))

    def test_source_code_has_no_network_client_or_process_runner(self):
        code = Path(gate.__file__).read_text(encoding="utf-8")
        for forbidden in ("import requests", "import subprocess", "urllib.request",
                          "TWILIO_AUTH_TOKEN", "GEMINI_API_KEY", "PGPASSWORD",
                          "http.client", "os.system(", "eval(", "exec("):
            self.assertNotIn(forbidden, code)


if __name__ == "__main__":
    unittest.main()
