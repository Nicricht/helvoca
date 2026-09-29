from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PortableInstallSmokeTest(unittest.TestCase):
    def test_copied_wrapper_executes_configured_low_risk_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp)
            (project / "software-factory" / "scripts").mkdir(parents=True)
            (project / "scripts").mkdir()

            shutil.copy(ROOT / "scripts" / "verify.py", project / "software-factory" / "scripts" / "verify.py")
            shutil.copy(ROOT / "scripts" / "detect_project.py", project / "software-factory" / "scripts" / "detect_project.py")
            shutil.copy(ROOT / "templates" / "verify", project / "scripts" / "verify")

            config = {
                "commands": {
                    "low": [["python3", "-c", "print('LOW_OK')"]],
                    "medium": [["python3", "-c", "print('MEDIUM_OK')"]],
                    "high": []
                },
                "forbidden_real_effect_markers": [],
                "allow_real_external_effects": False
            }
            (project / ".software-factory.json").write_text(json.dumps(config), encoding="utf-8")

            env = os.environ.copy()
            env["FIRST_PASS_RISK"] = "LOW"
            completed = subprocess.run(
                ["bash", "scripts/verify"],
                cwd=project,
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
            )

            self.assertEqual(0, completed.returncode, completed.stdout)
            self.assertIn("LOW_OK", completed.stdout)
            self.assertNotIn("MEDIUM_OK", completed.stdout)


if __name__ == "__main__":
    unittest.main()
