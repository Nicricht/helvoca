import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from benchmark_batch import run
from openai_import_probe import PilotBlocked


class BatchAutomationTests(unittest.TestCase):
    def test_three_cases_run_in_single_offline_command(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            cases=[]
            for i in range(3):
                img=folder/f"{i}.png"
                img.write_bytes(b"\x89PNG\r\n\x1a\n"+b"x"*16)
                truth=folder/f"{i}.json"
                truth.write_text('{"products":[]}')
                cases.append({"image":img.name,"truth":truth.name})
            manifest=folder/"manifest.json"
            manifest.write_text(json.dumps(cases))
            with patch("benchmark_batch.gemini_main",return_value=0) as call:
                self.assertEqual(0,run([str(manifest),"--out",str(folder/"reports")]))
                self.assertEqual(3,call.call_count)
                self.assertFalse(any("--send" in args[0][0] for args in call.call_args_list))

    def test_failed_grade_stops_remaining_requests(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            img=folder/"menu.png"
            img.write_bytes(b"\x89PNG\r\n\x1a\n"+b"x"*16)
            truth=folder/"truth.json"
            truth.write_text('{"products":[]}')
            manifest=folder/"manifest.json"
            manifest.write_text(json.dumps([{"image":"menu.png","truth":"truth.json"}]*2))
            with self.assertRaisesRegex(PilotBlocked,"Duplicate"):
                run([str(manifest),"--out",str(folder/"out")])

    def test_live_mode_requires_provider_preflight(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            img=folder/"menu.png"
            img.write_bytes(b"\x89PNG\r\n\x1a\n"+b"x"*16)
            truth=folder/"truth.json"
            truth.write_text('{"products":[]}')
            manifest=folder/"manifest.json"
            manifest.write_text('[{"image":"menu.png","truth":"truth.json"}]')
            with patch("benchmark_batch.preflight",side_effect=PilotBlocked("No approval")):
                with self.assertRaisesRegex(PilotBlocked,"No approval"):
                    run([str(manifest),"--out",str(folder/"out"),"--send"])


if __name__=="__main__":
    unittest.main()
