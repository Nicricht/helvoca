from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"


class ReadmeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = README.read_text(encoding="utf-8").lower()

    def test_explains_reuse_and_separation_of_responsibilities(self):
        for phrase in (
            "universal",
            "project-specific",
            "low",
            "medium",
            "high",
            "scripts/verify",
            "first-pass-engineering",
            "standalone",
        ):
            self.assertIn(phrase, self.text)

    def test_does_not_overclaim_skill_certification(self):
        self.assertIn("not yet behavior-certified", self.text)


if __name__ == "__main__":
    unittest.main()
