#!/usr/bin/env python3
"""Fail when executable Java lines changed by the PR are insufficiently covered by JaCoCo."""

from __future__ import annotations

import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

BASE = sys.argv[1] if len(sys.argv) > 1 else ""
REPORT = Path(sys.argv[2] if len(sys.argv) > 2 else "target/site/jacoco/jacoco.xml")
LINE_MIN = float(os.getenv("DIFF_LINE_COVERAGE", "80"))
BRANCH_MIN = float(os.getenv("DIFF_BRANCH_COVERAGE", "70"))

if not BASE or BASE == "null" or set(BASE) == {"0"}:
    print("No usable base SHA. Differential coverage skipped.")
    raise SystemExit(0)

if not REPORT.exists():
    print(f"JaCoCo report not found: {REPORT}", file=sys.stderr)
    raise SystemExit(2)

diff = subprocess.run(
    ["git", "diff", "--unified=0", "--no-color", f"{BASE}...HEAD", "--", "src/main/java"],
    check=True,
    text=True,
    stdout=subprocess.PIPE,
).stdout

changed: dict[str, set[int]] = defaultdict(set)
current_file: str | None = None

for raw in diff.splitlines():
    if raw.startswith("+++ b/"):
        current_file = raw[6:]
        continue
    if not raw.startswith("@@") or current_file is None:
        continue
    match = re.search(r"\+(\d+)(?:,(\d+))?", raw)
    if not match:
        continue
    start = int(match.group(1))
    count = int(match.group(2) or "1")
    for line_no in range(start, start + count):
        changed[current_file].add(line_no)

if not changed:
    print("No production Java lines changed. Differential coverage passed.")
    raise SystemExit(0)

tree = ET.parse(REPORT)
root = tree.getroot()

jacoco: dict[str, dict[int, tuple[int, int, int, int]]] = defaultdict(dict)
for package in root.findall("package"):
    package_name = package.get("name", "")
    for source in package.findall("sourcefile"):
        source_name = source.get("name")
        if not source_name:
            continue
        repo_path = f"src/main/java/{package_name}/{source_name}"
        for line in source.findall("line"):
            nr = int(line.get("nr", "0"))
            mi = int(line.get("mi", "0"))
            ci = int(line.get("ci", "0"))
            mb = int(line.get("mb", "0"))
            cb = int(line.get("cb", "0"))
            jacoco[repo_path][nr] = (mi, ci, mb, cb)

line_total = line_covered = 0
branch_total = branch_covered = 0
uncovered: list[str] = []

for path, lines in sorted(changed.items()):
    report_lines = jacoco.get(path, {})
    for nr in sorted(lines):
        counters = report_lines.get(nr)
        if counters is None:
            continue
        mi, ci, mb, cb = counters
        if mi + ci > 0:
            line_total += 1
            if ci > 0:
                line_covered += 1
            else:
                uncovered.append(f"{path}:{nr}")
        if mb + cb > 0:
            branch_total += mb + cb
            branch_covered += cb

if line_total == 0:
    print("Changed Java lines contain no executable JaCoCo lines. Differential coverage passed.")
    raise SystemExit(0)

line_pct = 100.0 * line_covered / line_total
print(f"Changed executable line coverage: {line_covered}/{line_total} = {line_pct:.1f}% (required {LINE_MIN:.1f}%)")

failed = line_pct + 1e-9 < LINE_MIN

if branch_total:
    branch_pct = 100.0 * branch_covered / branch_total
    print(f"Changed branch coverage: {branch_covered}/{branch_total} = {branch_pct:.1f}% (required {BRANCH_MIN:.1f}%)")
    failed = failed or branch_pct + 1e-9 < BRANCH_MIN
else:
    print("No changed branch instructions detected.")

if uncovered:
    print("Uncovered changed executable lines:")
    for entry in uncovered[:50]:
        print(f" - {entry}")
    if len(uncovered) > 50:
        print(f" ... and {len(uncovered) - 50} more")

if failed:
    print("Differential coverage gate FAILED.", file=sys.stderr)
    raise SystemExit(1)

print("Differential coverage gate passed.")
