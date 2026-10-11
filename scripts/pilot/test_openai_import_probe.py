import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from openai_import_probe import (
    BUDGET_USD, MAX_CALLS, RESERVE_USD_PER_CALL, PilotBlocked,
    append_receipt, existing_receipts, inspect, main, parse_usage,
    prepared_body, parse_proposals, validate_authorization,
)

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16


class PilotSafetyTests(unittest.TestCase):
    def test_dry_run_never_uses_network_or_key(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.png"
            path.write_bytes(PNG)
            with patch("openai_import_probe.send_one") as send:
                self.assertEqual(0, main([str(path)]))
                send.assert_not_called()

    def test_dry_run_blocks_duplicate_files_and_invalid_signature(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.png"
            path.write_bytes(PNG)
            with self.assertRaisesRegex(PilotBlocked, "Duplicate"):
                main([str(path), str(path)])
            path.write_bytes(b"malformed")
            with self.assertRaisesRegex(PilotBlocked, "signature"):
                inspect(path)

    def test_rejects_pdf_without_trustworthy_pdf_parser_or_valid_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.pdf"
            path.write_bytes(b"%PDF-broken")
            with self.assertRaises(PilotBlocked):
                inspect(path)

    def test_rejects_large_and_unknown_files(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "giant.png"
            path.write_bytes(PNG + b"x" * 600000)
            with self.assertRaisesRegex(PilotBlocked, "size"):
                inspect(path)
            unknown = Path(directory) / "giant.txt"
            unknown.write_bytes(b"test")
            with self.assertRaisesRegex(PilotBlocked, "Only JPEG"):
                inspect(unknown)

    def test_send_needs_all_three_attestations_and_a_separate_project_key(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(PilotBlocked, "HELVOCA_PILOT_APPROVED"):
                validate_authorization()
        env = {"HELVOCA_PILOT_APPROVED": "yes",
               "HELVOCA_PILOT_PROJECT_ISOLATED": "yes",
               "HELVOCA_PILOT_HARD_CAP_CONFIRMED": "yes",
               "HELVOCA_PILOT_SPEND_USD": "2.00",
               "OPENAI_PILOT_PROJECT_ID": "proj_testing123456",
               "OPENAI_PILOT_API_KEY": "sk-private-key-example"}
        with patch.dict(os.environ, env, clear=True):
            self.assertEqual("proj_testing123456", validate_authorization()[0])
            with patch.dict(os.environ, {"HELVOCA_PILOT_HARD_CAP_CONFIRMED": ""}):
                with self.assertRaisesRegex(PilotBlocked, "HARD_CAP"):
                    validate_authorization()

    def test_send_requires_private_audit_and_no_repeats(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.png"
            path.write_bytes(PNG)
            with self.assertRaisesRegex(PilotBlocked, "--audit"):
                main([str(path), "--send"])
            audit = Path(directory) / "pilot.jsonl"
            append_receipt(audit, {"number": 1, "phase": "STARTED"})
            self.assertEqual(1, len(existing_receipts(audit)))
            report = Path(directory) / "review.jsonl"
            with patch("openai_import_probe.validate_authorization", return_value=("proj_1", "sk-key")):
                with self.assertRaisesRegex(PilotBlocked, "no hidden retries"):
                    main([str(path), "--send", "--audit", str(audit),
                          "--report", str(report)])

    def test_json_payload_is_low_detail_and_no_side_effects(self):
        image = prepared_body("image/png", PNG)
        self.assertEqual("gpt-4.1-mini", image["model"])
        self.assertEqual(500, image["max_output_tokens"])
        self.assertEqual("low", image["input"][0]["content"][1]["detail"])
        self.assertIn("UNTRUSTED", image["input"][0]["content"][0]["text"])
        pdf = prepared_body("application/pdf", b"%PDF-sample")
        self.assertEqual("input_file", pdf["input"][0]["content"][1]["type"])
        self.assertNotIn("detail", pdf["input"][0]["content"][1])

    def test_model_response_is_parsed_only_for_private_review(self):
        response = {"output": [{"type": "message", "content": [
            {"type": "output_text", "text": '{"products":[{"name":"Café"}],"warnings":[]}'}]}]}
        parsed = parse_proposals(response)
        self.assertEqual("Café", parsed["products"][0]["name"])
        with self.assertRaisesRegex(PilotBlocked, "valid JSON"):
            parse_proposals({"output": [{"content": [{"type": "output_text", "text": "not json"}]}]})
        with self.assertRaisesRegex(PilotBlocked, "Missing model output"):
            parse_proposals({"output": []})
        with self.assertRaisesRegex(PilotBlocked, "review contract"):
            parse_proposals({"output": [{"content": [
                {"type": "output_text", "text": '{"products":[]}'}
            ]}]})

    def test_token_estimate_is_not_billed_usd_and_usage_fail_closed(self):
        self.assertEqual((1000, 300, 0.00088),
                         parse_usage({"usage": {"input_tokens": 1000, "output_tokens": 300}}))
        with self.assertRaisesRegex(PilotBlocked, "Missing"):
            parse_usage({})
        with self.assertRaisesRegex(PilotBlocked, "Invalid"):
            parse_usage({"usage": {"input_tokens": -1, "output_tokens": 0}})
        self.assertLessEqual(MAX_CALLS * RESERVE_USD_PER_CALL, BUDGET_USD)


if __name__ == "__main__":
    unittest.main()
