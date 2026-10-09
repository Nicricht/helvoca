#!/usr/bin/env python3
"""Fail unless changed executable Java reaches 100% JaCoCo line, branch and method coverage."""

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
LINE_MIN = float(os.getenv("DIFF_LINE_COVERAGE", "100"))
BRANCH_MIN = float(os.getenv("DIFF_BRANCH_COVERAGE", "100"))
METHOD_MIN = float(os.getenv("DIFF_METHOD_COVERAGE", "100"))

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
methods_by_path: dict[str, list[tuple[int, str, str, bool]]] = defaultdict(list)

for package in root.findall("package"):
    package_name = package.get("name", "")
    source_paths: dict[str, str] = {}
    for source in package.findall("sourcefile"):
        source_name = source.get("name")
        if not source_name:
            continue
        repo_path = f"src/main/java/{package_name}/{source_name}"
        source_paths[source_name] = repo_path
        for line in source.findall("line"):
            nr = int(line.get("nr", "0"))
            mi = int(line.get("mi", "0"))
            ci = int(line.get("ci", "0"))
            mb = int(line.get("mb", "0"))
            cb = int(line.get("cb", "0"))
            jacoco[repo_path][nr] = (mi, ci, mb, cb)

    for clazz in package.findall("class"):
        source_name = clazz.get("sourcefilename")
        repo_path = source_paths.get(source_name or "")
        if not repo_path:
            continue
        for method in clazz.findall("method"):
            start = int(method.get("line", "0") or "0")
            if start <= 0:
                continue
            counter = method.find("counter[@type='METHOD']")
            covered = counter is not None and int(counter.get("covered", "0")) > 0
            methods_by_path[repo_path].append(
                (start, method.get("name", "<unknown>"), method.get("desc", ""), covered)
            )

line_total = line_covered = 0
branch_total = branch_covered = 0
method_total = method_covered = 0
uncovered: list[str] = []
uncovered_methods: list[str] = []

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

affected_methods: set[tuple[str, int, str, str, bool]] = set()
for path, lines in changed.items():
    methods = sorted(methods_by_path.get(path, []), key=lambda item: item[0])
    for index, (start, name, desc, covered) in enumerate(methods):
        next_start = methods[index + 1][0] if index + 1 < len(methods) else 10**9
        if any(start <= line_no < next_start for line_no in lines):
            affected_methods.add((path, start, name, desc, covered))

for path, start, name, desc, covered in sorted(affected_methods):
    method_total += 1
    if covered:
        method_covered += 1
    else:
        uncovered_methods.append(f"{path}:{start} {name}{desc}")

if line_total == 0 and method_total == 0:
    print("Changed Java lines contain no executable JaCoCo lines or affected methods. Differential coverage passed.")
    raise SystemExit(0)

line_pct = 100.0 * line_covered / line_total if line_total else 100.0
print(f"Changed executable line coverage: {line_covered}/{line_total} = {line_pct:.1f}% (required {LINE_MIN:.1f}%)")

failed = line_pct + 1e-9 < LINE_MIN

if branch_total:
    branch_pct = 100.0 * branch_covered / branch_total
    print(f"Changed branch coverage: {branch_covered}/{branch_total} = {branch_pct:.1f}% (required {BRANCH_MIN:.1f}%)")
    failed = failed or branch_pct + 1e-9 < BRANCH_MIN
else:
    print("No changed branch instructions detected.")

if method_total:
    method_pct = 100.0 * method_covered / method_total
    print(f"Changed method coverage: {method_covered}/{method_total} = {method_pct:.1f}% (required {METHOD_MIN:.1f}%)")
    failed = failed or method_pct + 1e-9 < METHOD_MIN
else:
    print("No changed methods detected.")

if uncovered:
    print("Uncovered changed executable lines:")
    for entry in uncovered[:50]:
        print(f" - {entry}")
    if len(uncovered) > 50:
        print(f" ... and {len(uncovered) - 50} more")

if uncovered_methods:
    print("Uncovered changed methods:")
    for entry in uncovered_methods[:50]:
        print(f" - {entry}")
    if len(uncovered_methods) > 50:
        print(f" ... and {len(uncovered_methods) - 50} more")

if failed:
    print("Differential coverage gate FAILED.", file=sys.stderr)
    raise SystemExit(1)

print("Differential coverage gate passed.")
