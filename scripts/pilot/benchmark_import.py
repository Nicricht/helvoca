#!/usr/bin/env python3
"""Deterministic, offline truth-set scorer for RecepVoz document extraction.

A proposal can be syntactically valid but commercially unsafe. Explicitly
penalize invented promo times, requirements, prices, availability and photos.
Never needs network, API credentials or business database.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import unicodedata

SECTIONS = ("products", "extras", "promotions")
HIGH_RISK = {"price", "previousPrice", "availability", "hasVisiblePhoto",
             "startTime", "endTime", "conditions", "days", "discountPercent",
             "eligibleProducts"}
MAX_ITEMS = 100


def clean(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).strip().casefold().split())


def compare(expected: dict, actual: dict) -> dict:
    if not isinstance(expected, dict) or not isinstance(actual, dict):
        raise ValueError("Expected and actual must be JSON objects")
    errors = []
    passed = 0
    checked = 0

    def check(location: str, want, got, critical: bool = False):
        nonlocal passed, checked
        checked += 1
        if isinstance(want, str) and isinstance(got, str):
            ok = clean(want) == clean(got)
        elif isinstance(want, list) and isinstance(got, list):
            ok = [clean(x) if isinstance(x, str) else x for x in want] == [
                clean(x) if isinstance(x, str) else x for x in got]
        else:
            ok = type(want) is type(got) and want == got
            # Numeric JSON prices 2500.0 and 2500 are semantically identical.
            if (type(want) in (int, float) and type(got) in (int, float)
                    and type(want) is not bool and type(got) is not bool):
                ok = want == got
        if ok:
            passed += 1
        else:
            errors.append({"field": location, "expected": want, "actual": got,
                           "severity": "critical" if critical else "error"})

    for field, want in expected.get("business", {}).items():
        check("business." + field, want, actual.get("business", {}).get(field),
              field in ("phone", "currencyCode"))
    for section in SECTIONS:
        want_items = expected.get(section, [])
        got_items = actual.get(section, [])
        if not isinstance(got_items, list) or len(got_items) > MAX_ITEMS:
            raise ValueError(f"{section} missing or exceeds safe capacity")
        want_index = {clean(i["name"]): i for i in want_items}
        got_index = {}
        for item in got_items:
            if not isinstance(item, dict) or not isinstance(item.get("name"), str):
                raise ValueError(f"Malformed {section} proposal")
            key = clean(item["name"])
            if key in got_index:
                errors.append({"field": section + "." + item["name"],
                               "expected": "unique", "actual": "duplicate",
                               "severity": "critical"})
            got_index[key] = item
        for key, target in want_index.items():
            source = got_index.get(key)
            check(f"{section}.{target['name']}.present", True, source is not None,
                  critical=True)
            if source is None:
                continue
            for field, want in target.items():
                if field != "name":
                    check(f"{section}.{target['name']}.{field}", want,
                          source.get(field), critical=field in HIGH_RISK)
        for key, item in got_index.items():
            if key not in want_index:
                errors.append({"field": section + "." + item["name"],
                               "expected": "absent", "actual": "invented or unknown",
                               "severity": "critical"})
    if "businessHours" in expected:
        def hours_index(items):
            if not isinstance(items, list):
                raise ValueError("businessHours missing")
            return {tuple(sorted(clean(d) for d in i["days"])): i
                    for i in items if isinstance(i, dict) and isinstance(i.get("days"), list)}
        target = hours_index(expected["businessHours"])
        found = hours_index(actual.get("businessHours"))
        check("businessHours.groupCount", len(target), len(found), critical=True)
        for days, entry in target.items():
            check("businessHours." + str(days) + ".present", True, days in found, critical=True)
            if days in found:
                for field in ("open", "close"):
                    check("businessHours." + str(days) + "." + field,
                          entry.get(field), found[days].get(field), critical=True)
    for target in expected.get("faqContains", []):
        joined = "\n".join(str(i.get("answer", "")) for i in actual.get("faq", [])
                           if isinstance(i, dict))
        check("faqContains." + target, True, clean(target) in clean(joined))
    for target in expected.get("generalNoticesContain", []):
        joined = "\n".join(str(i) for i in actual.get("generalNotices", []))
        check("generalNoticesContain." + target, True, clean(target) in clean(joined))
    critical = [x for x in errors if x["severity"] == "critical"]
    return {"passed": not errors, "fieldsChecked": checked, "fieldsCorrect": passed,
            "scorePercent": round(passed * 100 / checked, 2) if checked else 0,
            "criticalErrors": len(critical), "errors": errors}


def main(argv=None):
    parser = argparse.ArgumentParser(description="Score AI import proposals against reviewed image facts")
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    parser.add_argument("--output", type=Path, help="Local private JSON report")
    args = parser.parse_args(argv)
    expected = json.loads(args.expected.read_text(encoding="utf-8"))
    actual = json.loads(args.actual.read_text(encoding="utf-8"))
    result = compare(expected, actual)
    if args.output:
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
