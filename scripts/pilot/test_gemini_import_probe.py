import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from openai_import_probe import PilotBlocked, existing_receipts
from gemini_import_probe import ENDPOINT, MODEL, PROMPT, preflight, interpret, main, payload, parse_document

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16
RESPONSE = {"candidates": [{"finishReason": "STOP", "content": {"parts": [
    {"text": '{"business":{"name":"Cafetería"},"products":[{"name":"Café","category":"Bebidas","price":1000,"availability":"unknown","hasVisiblePhoto":false,"tags":[]}],"extras":[],"promotions":[],"businessHours":[],"faq":[],"generalNotices":[],"warnings":[]}'}]}}],
    "usageMetadata": {"promptTokenCount": 500, "candidatesTokenCount": 100,
                      "totalTokenCount": 640}, "modelVersion": MODEL}

ENV = {"GEMINI_PILOT_APPROVED": "yes",
       "GEMINI_PILOT_PERSONAL_PROJECT": "yes",
       "GEMINI_PILOT_FREE_TIER_CONFIRMED": "yes",
       "GEMINI_PILOT_NO_BILLING": "yes",
       "GEMINI_PILOT_SYNTHETIC_ONLY": "yes",
       "GEMINI_PILOT_PROJECT_ID": "recepvoz-gemini-pilot-123",
       "GEMINI_PILOT_PROJECT_NAME": "RecepVoz-Gemini-Pilot",
       "GEMINI_PILOT_API_KEY": "x" * 30}


class GeminiPilotTests(unittest.TestCase):
    def test_offline_never_uses_network(self):
        with tempfile.TemporaryDirectory() as folder:
            sample = Path(folder) / "menu.png"
            sample.write_bytes(PNG)
            with patch("gemini_import_probe.send_one") as sender:
                self.assertEqual(0, main([str(sample)]))
                sender.assert_not_called()

    def test_personal_free_unbilled_project_is_mandatory(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(PilotBlocked, "GEMINI_PILOT_APPROVED"):
                preflight()
        with patch.dict(os.environ, ENV, clear=True):
            self.assertEqual("x" * 30, preflight())
            with patch.dict(os.environ, {"GEMINI_PILOT_NO_BILLING": "no"}):
                with self.assertRaisesRegex(PilotBlocked, "GEMINI_PILOT_NO_BILLING"):
                    preflight()
            with patch.dict(os.environ, {"GEMINI_PILOT_PROJECT_NAME": "Default Gemini Project"}):
                with self.assertRaisesRegex(PilotBlocked, "exclusive"):
                    preflight()

    def test_ci_can_never_send(self):
        with patch.dict(os.environ, {**ENV, "CI": "true"}, clear=True):
            with self.assertRaisesRegex(PilotBlocked, "CI"):
                preflight()

    def test_payload_uses_fixed_cheap_model_without_side_effects(self):
        self.assertEqual("gemini-3.5-flash-lite", MODEL)
        self.assertTrue(ENDPOINT.startswith("https://generativelanguage.googleapis.com/"))
        body = payload("image/png", PNG)
        self.assertEqual("image/png", body["contents"][0]["parts"][1]["inlineData"]["mimeType"])
        self.assertEqual(7500, body["generationConfig"]["maxOutputTokens"])
        self.assertIn("UNTRUSTED", PROMPT)

    def test_extended_schema_cannot_drop_promotions_or_return_invalid_types(self):
        with self.assertRaisesRegex(PilotBlocked, "Missing or excessive.*promotions"):
            parse_document('{"business":{},"products":[],"extras":[],"businessHours":[],"faq":[],"generalNotices":[],"warnings":[]}')
        with self.assertRaisesRegex(PilotBlocked, "Invalid availability"):
            parse_document('{"business":{},"products":[{"name":"Café","availability":"maybe","hasVisiblePhoto":false,"tags":[]}],"extras":[],"promotions":[],"businessHours":[],"faq":[],"generalNotices":[],"warnings":[]}')
        self.assertEqual([], parse_document('{"business":{},"products":[],"extras":[],"promotions":[],"businessHours":[],"faq":[],"generalNotices":[],"warnings":[]}')["promotions"])

    def test_result_extracts_review_only_with_real_token_usage(self):
        review, token = interpret(RESPONSE)
        self.assertEqual("Café", review["products"][0]["name"])
        self.assertEqual(640, token["total_tokens"])
        with self.assertRaisesRegex(PilotBlocked, "token usage"):
            interpret({**RESPONSE, "usageMetadata": {}})
        with self.assertRaisesRegex(PilotBlocked, "stopped early"):
            interpret({**RESPONSE, "candidates": [{"finishReason": "MAX_TOKENS"}]})

    def test_mocked_send_writes_private_receipts_without_network(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, ENV, clear=True):
            sample = Path(folder) / "menu.png"
            sample.write_bytes(PNG)
            audit = Path(folder) / "audit.jsonl"
            report = Path(folder) / "report.jsonl"
            with patch("gemini_import_probe.send_one", return_value=RESPONSE) as sender:
                self.assertEqual(0, main([str(sample), "--send", "--audit", str(audit),
                                          "--report", str(report)]))
                sender.assert_called_once()
            self.assertEqual(["STARTED", "RESPONSE"],
                             [x["phase"] for x in existing_receipts(audit)])
            self.assertEqual("Café", existing_receipts(report)[0]["proposals"]["products"][0]["name"])
            with self.assertRaisesRegex(PilotBlocked, "No automatic retries"):
                main([str(sample), "--send", "--audit", str(audit),
                      "--report", str(report)])

    def test_automatic_truth_scoring_stops_after_unsafe_proposal(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, ENV, clear=True):
            sample = Path(folder) / "menu.png"
            sample.write_bytes(PNG)
            truth = Path(folder) / "truth.json"
            truth.write_text('{"business":{},"products":[{"name":"Café","price":999}],"extras":[],"promotions":[]}', encoding="utf-8")
            audit = Path(folder) / "audit.jsonl"
            report = Path(folder) / "report.jsonl"
            with patch("gemini_import_probe.send_one", return_value=RESPONSE) as sender:
                self.assertEqual(4, main([str(sample), "--send", "--audit", str(audit),
                                          "--report", str(report), "--truth", str(truth)]))
                sender.assert_called_once()
            self.assertEqual(["STARTED", "RESPONSE"],
                             [x["phase"] for x in existing_receipts(audit)])
            self.assertEqual(1, existing_receipts(report)[0]["benchmark"]["criticalErrors"])

    def test_uncertain_provider_failure_never_retries(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, ENV, clear=True):
            sample = Path(folder) / "menu.png"
            sample.write_bytes(PNG)
            audit, report = Path(folder) / "audit.jsonl", Path(folder) / "review.jsonl"
            with patch("gemini_import_probe.send_one", side_effect=TimeoutError()) as sender:
                with self.assertRaises(TimeoutError):
                    main([str(sample), "--send", "--audit", str(audit),
                          "--report", str(report)])
                sender.assert_called_once()
            self.assertEqual(["STARTED", "UNCERTAIN"],
                             [x["phase"] for x in existing_receipts(audit)])
            self.assertFalse(report.exists())


if __name__ == "__main__":
    unittest.main()
