from pathlib import Path
import unittest

REPO = Path(__file__).resolve().parents[2]
FACTORY = REPO / "software-factory"


class ResumabilityContractTest(unittest.TestCase):
    def test_continuity_standard_requires_durable_resume_state(self):
        text = (FACTORY / "standards" / "continuity.md").read_text(encoding="utf-8").lower()
        for phrase in (
            "durable checkpoint",
            "chat",
            "branch",
            "pull request",
            "head",
            "ci",
            "next step",
            "idempotent",
        ):
            self.assertIn(phrase, text)

    def test_skill_treats_continuity_as_a_required_engineering_perspective(self):
        text = (FACTORY / "skill" / "first-pass-engineering" / "SKILL.md").read_text(encoding="utf-8").lower()
        self.assertIn("continuity", text)
        self.assertIn("resume checkpoint", text)
        self.assertIn("reconstruct", text)
        self.assertIn("do not repeat", text)

    def test_portable_templates_make_long_work_resumable(self):
        agents = (FACTORY / "templates" / "AGENTS.md").read_text(encoding="utf-8").lower()
        pr = (FACTORY / "templates" / "pull_request_template.md").read_text(encoding="utf-8").lower()
        self.assertIn("resume checkpoint", agents)
        self.assertIn("resume checkpoint", pr)
        self.assertIn("next step", pr)
        self.assertIn("head", pr)

    def test_helvoca_adopter_exposes_same_resume_contract(self):
        agents = (REPO / "AGENTS.md").read_text(encoding="utf-8").lower()
        pr = (REPO / ".github" / "pull_request_template.md").read_text(encoding="utf-8").lower()
        self.assertIn("resume checkpoint", agents)
        self.assertIn("resume checkpoint", pr)


if __name__ == "__main__":
    unittest.main()
