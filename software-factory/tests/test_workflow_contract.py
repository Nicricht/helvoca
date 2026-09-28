from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "reusable-quality-gate.yml"


class WorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    def test_is_reusable_and_parameterized_by_risk(self):
        self.assertIn("workflow_call:", self.text)
        self.assertIn("risk:", self.text)
        self.assertIn("verify_command:", self.text)

    def test_checks_out_and_runs_repository_verifier(self):
        self.assertIn("actions/checkout@", self.text)
        self.assertIn("inputs.verify_command", self.text)
        self.assertIn("FIRST_PASS_RISK", self.text)

    def test_contains_no_deployment_or_embedded_secret_values(self):
        lowered = self.text.lower()
        self.assertNotIn("deploy", lowered)
        self.assertNotIn("password:", lowered)
        self.assertNotIn("api_key:", lowered)


if __name__ == "__main__":
    unittest.main()
