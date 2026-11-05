import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from fixture_factory import generate
from openai_import_probe import inspect
from benchmark_batch import run


class SyntheticImageFactoryTests(unittest.TestCase):
    def test_generates_three_unique_real_legal_jpegs_under_size_limit(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            manifest = generate(folder)
            cases = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual(3, len(cases))
            seen = set()
            for entry in cases:
                mime, data, fingerprint = inspect(folder / entry["image"])
                self.assertEqual("image/jpeg", mime)
                self.assertLessEqual(len(data), 512_000)
                self.assertNotIn(fingerprint, seen)
                seen.add(fingerprint)
                self.assertTrue((folder / entry["truth"]).is_file())
            truth = json.loads((folder / "truth.json").read_text(encoding="utf-8"))
            self.assertEqual(26, len(truth["products"]))
            self.assertTrue(all(p["hasVisiblePhoto"] is False for p in truth["products"]))
            self.assertEqual(4,len(truth["promotions"]))

    def test_created_manifest_runs_offline_without_network(self):
        with tempfile.TemporaryDirectory() as folder:
            folder=Path(folder)
            manifest=generate(folder/"cases")
            with patch("benchmark_batch.gemini_main",return_value=0) as send:
                self.assertEqual(0,run([str(manifest),"--out",str(folder/"reports")]))
                self.assertEqual(3,send.call_count)
                self.assertTrue(all("--send" not in call.args[0]
                                    for call in send.call_args_list))


if __name__=="__main__":
    unittest.main()
