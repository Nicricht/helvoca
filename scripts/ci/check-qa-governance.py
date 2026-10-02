#!/usr/bin/env python3
"""Protect the repository QA governance contract from silent regression."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks = {
    "AGENTS.md": [
        "docs/engineering/QA_POLICY.md",
        "user is not responsible for selecting",
        "test level",
        "production verification",
    ],
    "docs/engineering/QA_POLICY.md": [
        "QA ownership",
        "Test selection matrix",
        "Coverage policy",
        "Adversarial verification",
        "Definition of done",
    ],
    ".github/pull_request_template.md": [
        "QA classification",
        "Unit",
        "Integration / PostgreSQL",
        "Security / Tenant",
        "Concurrency / Idempotency",
        "E2E",
        "Golden Journey",
        "Production verification",
    ],
    "software-factory/skill/first-pass-engineering/SKILL.md": [
        "user is not responsible for selecting",
        "test level",
        "lowest test level",
        "higher-level verification",
    ],
}

failures = []
for relative, phrases in checks.items():
    path = ROOT / relative
    if not path.exists():
        failures.append(f"missing required QA governance file: {relative}")
        continue

    text = path.read_text(encoding="utf-8").lower()
    for phrase in phrases:
        if phrase.lower() not in text:
            failures.append(f"{relative}: missing required QA contract phrase: {phrase}")

if failures:
    print("QA governance contract FAILED:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("QA governance contract passed.")
