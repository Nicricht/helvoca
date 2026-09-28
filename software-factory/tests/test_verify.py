from pathlib import Path
import importlib.util
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "verify.py"
spec = importlib.util.spec_from_file_location("verify", SCRIPT)
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class VerifyPlanTest(unittest.TestCase):
    def test_repository_config_overrides_defaults(self):
        config = verify.VerificationConfig(
            commands={
                "low": [["custom-low"]],
                "medium": [["custom-medium"]],
                "high": [["custom-security"], ["custom-adversarial"]],
            },
            forbidden_real_effect_markers=[],
            allow_real_external_effects=False,
            source="repository",
        )
        self.assertEqual([["custom-low"], ["custom-medium"]], verify.verification_commands(config, "MEDIUM"))

    def test_low_risk_omits_high_risk_commands(self):
        config = verify.VerificationConfig(
            commands={"low": [["lint"]], "medium": [["unit"]], "high": [["security"], ["adversarial"]]},
            forbidden_real_effect_markers=[],
            allow_real_external_effects=False,
            source="repository",
        )
        self.assertEqual([["lint"]], verify.verification_commands(config, "LOW"))

    def test_high_risk_includes_security_and_adversarial_commands(self):
        config = verify.VerificationConfig(
            commands={"low": [["lint"]], "medium": [["unit"]], "high": [["security"], ["adversarial"]]},
            forbidden_real_effect_markers=[],
            allow_real_external_effects=False,
            source="repository",
        )
        self.assertEqual(
            [["lint"], ["unit"], ["security"], ["adversarial"]],
            verify.verification_commands(config, "HIGH"),
        )

    def test_unconfigured_ambiguous_project_fails_actionably(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "pom.xml").write_text("", encoding="utf-8")
            (root / "package.json").write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "local verification configuration"):
                verify.load_verification_config(root)

    def test_forbidden_real_effect_marker_is_rejected_without_opt_in(self):
        config = verify.VerificationConfig(
            commands={"low": [["safe"]], "medium": [], "high": [["provider", "--live"]]},
            forbidden_real_effect_markers=["--live"],
            allow_real_external_effects=False,
            source="repository",
        )
        with self.assertRaisesRegex(ValueError, "forbidden real-effect marker"):
            verify.verification_commands(config, "HIGH")


if __name__ == "__main__":
    unittest.main()
