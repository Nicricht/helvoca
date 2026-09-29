from pathlib import Path
import importlib.util
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "detect_project.py"
spec = importlib.util.spec_from_file_location("detect_project", SCRIPT)
detect_project = importlib.util.module_from_spec(spec)
spec.loader.exec_module(detect_project)


class DetectProjectTest(unittest.TestCase):
    def detect_with(self, *files):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in files:
                (root / name).write_text("", encoding="utf-8")
            return detect_project.detect_project(root)

    def test_detects_supported_single_stacks(self):
        cases = {
            "pom.xml": "maven",
            "build.gradle": "gradle",
            "build.gradle.kts": "gradle",
            "package.json": "node",
            "pyproject.toml": "python",
            "go.mod": "go",
            "Cargo.toml": "rust",
        }
        for filename, expected in cases.items():
            with self.subTest(filename=filename):
                result = self.detect_with(filename)
                self.assertEqual((expected,), result.stacks)
                self.assertFalse(result.requires_config)

    def test_ambiguous_stack_requires_local_configuration(self):
        result = self.detect_with("pom.xml", "package.json")
        self.assertEqual(("maven", "node"), result.stacks)
        self.assertTrue(result.requires_config)

    def test_unknown_stack_requires_local_configuration(self):
        result = self.detect_with("README.md")
        self.assertEqual((), result.stacks)
        self.assertTrue(result.requires_config)


if __name__ == "__main__":
    unittest.main()
