#!/usr/bin/env python3
"""Run up to three real document benchmarks from one local command.

Default is offline, never retries a provider request, stops on first failed score.
"""
import argparse
import json
from pathlib import Path
from gemini_import_probe import main as gemini_main, preflight
from openai_import_probe import PilotBlocked, require

MAX_CASES=3


def run(args=None):
    p=argparse.ArgumentParser(description="Safe one-command Gemini document benchmark")
    p.add_argument("manifest",type=Path,help="JSON array of {image,truth} cases")
    p.add_argument("--out",type=Path,required=True,help="New private folder outside repository")
    p.add_argument("--send",action="store_true",help="Explicitly opt into 1-3 Free Tier API calls")
    a=p.parse_args(args)
    cases=json.loads(a.manifest.read_text(encoding="utf-8"))
    require(isinstance(cases,list) and 1<=len(cases)<=MAX_CASES,
            "Manifest must contain between 1 and 3 cases")
    root=Path(__file__).resolve().parents[2]
    dest=a.out.resolve()
    require(not dest.is_relative_to(root),"Private report folder must be outside repository")
    require(not dest.exists() or not any(dest.glob("audit-*.jsonl")),
            "Existing benchmark receipts found; never retry uncertain calls")
    paths=[]
    for case in cases:
        require(isinstance(case,dict) and "image" in case and "truth" in case,
                "Each case needs an image and its reviewed ground truth")
        image=(a.manifest.parent/case["image"]).resolve()
        truth=(a.manifest.parent/case["truth"]).resolve()
        require(image.is_file() and truth.is_file(),"Benchmark fixture is missing")
        paths.append((image,truth))
    require(len({x[0] for x in paths})==len(paths),"Duplicate images not allowed")
    if a.send:
        preflight()
    dest.mkdir(parents=True,exist_ok=True)
    for number,(image,truth) in enumerate(paths,1):
        argv=[str(image),"--truth",str(truth)]
        if a.send:
            argv+=["--send","--audit",str(dest/f"audit-{number}.jsonl"),
                   "--report",str(dest/f"review-{number}.jsonl")]
        rc=gemini_main(argv)
        if rc!=0:
            print(f"NO-GO at case {number}; no additional API calls")
            return rc
    print("Offline validation complete" if not a.send else
          "All graded API cases passed; check Google AI Studio usage")
    return 0


if __name__=="__main__":
    try:
        raise SystemExit(run())
    except PilotBlocked as e:
        print("BLOCKED:",e)
        raise SystemExit(2)
