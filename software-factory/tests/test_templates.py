from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = ROOT / "templates"


class RepositoryTemplateContractTest(unittest.TestCase):
    def read(self, name: str) -> str:
        return (TEMPLATES / name).read_text(encoding="utf-8")

    def test_agents_template_points_to_first_pass_and_local_rules(self):
        text = self.read("AGENTS.md").lower()
        self.assertIn("first-pass engineering", text)
        self.assertIn("project-specific", text)
        self.assertIn("risk", text)
        self.assertIn("final commit", text)
        self.assertIn("software-factory/skill/first-pass-engineering/skill.md", text)

    def test_pr_template_captures_evidence_and_risk(self):
        text = self.read("pull_request_template.md").lower()
        for phrase in (
            "risk level",
            "acceptance criteria",
            "affected invariants",
            "test evidence",
            "final commit",
            "rollback",
        ):
            self.assertIn(phrase, text)

    def test_invariants_template_explains_never_break_truths(self):
        text = self.read("invariants.md").lower()
        self.assertIn("must never", text)
        self.assertIn("testable", text)

    def test_verify_wrapper_is_portable_and_points_to_factory_runner(self):
        text = self.read("verify")
        self.assertIn("software-factory/scripts/verify.py", text)
        self.assertIn("python3", text)

    def test_workflow_calls_repository_verifier_without_stack_assumptions(self):
        text = self.read("software-factory.yml")
        self.assertIn("bash scripts/verify", text)
        self.assertNotIn("mvn ", text)
        self.assertNotIn("npm test", text)
        self.assertNotIn("pytest", text)


if __name__ == "__main__":
    unittest.main()
