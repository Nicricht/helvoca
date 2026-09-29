from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
STANDARDS = ROOT / "standards"


class EngineeringContractTest(unittest.TestCase):
    def read(self, name: str) -> str:
        return (STANDARDS / name).read_text(encoding="utf-8")

    def test_risk_model_defines_three_levels_and_proportionality(self):
        text = self.read("risk-model.md")
        for marker in ("LOW", "MEDIUM", "HIGH"):
            self.assertIn(marker, text)
        self.assertIn("proportional", text.lower())

    def test_definition_of_done_rejects_stale_evidence_and_unverified_bug_fixes(self):
        text = self.read("definition-of-done.md")
        self.assertIn("final commit", text.lower())
        self.assertIn("invalid", text.lower())
        self.assertIn("regression test", text.lower())
        self.assertIn("reproducible", text.lower())

    def test_repository_contract_requires_invariants_and_preserves_stronger_checks(self):
        text = self.read("repository-contract.md")
        self.assertIn("invariant", text.lower())
        self.assertIn("preserve stronger", text.lower())
        self.assertIn("external side effect", text.lower())


if __name__ == "__main__":
    unittest.main()
