from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT / "skill" / "first-pass-engineering" / "SKILL.md"


class FirstPassSkillContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SKILL.read_text(encoding="utf-8")

    def test_frontmatter_and_trigger(self):
        self.assertRegex(self.text, r"(?m)^name:\s*first-pass-engineering\s*$")
        match = re.search(r"(?m)^description:\s*(.+)$", self.text)
        self.assertIsNotNone(match)
        self.assertTrue(match.group(1).strip().startswith("Use when"))

    def test_contains_mandatory_qa_ownership_contract(self):
        lowered = self.text.lower()
        for phrase in (
            "user is not responsible for selecting",
            "test level",
            "lowest test level",
            "higher-level verification",
            "regression test",
            "production verification",
        ):
            self.assertIn(phrase, lowered)

    def test_contains_core_first_pass_controls(self):
        lowered = self.text.lower()
        for phrase in (
            "risk",
            "impact map",
            "invariant",
            "test-driven-development",
            "systematic-debugging",
            "verification-before-completion",
            "adversarial",
            "final commit",
        ):
            self.assertIn(phrase, lowered)

    def test_is_universal_not_helvoca_specific(self):
        lowered = self.text.lower()
        self.assertNotIn("helvoca", lowered)
        self.assertNotIn("recepvoz", lowered)
        self.assertNotIn("jacoco", lowered)

    def test_not_behavior_certified_marker_exists(self):
        self.assertIn("NOT YET BEHAVIOR-CERTIFIED", self.text)


if __name__ == "__main__":
    unittest.main()
