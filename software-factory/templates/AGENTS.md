# Project Engineering Guardrails

This repository follows **First-Pass Engineering**.

## Required behavior

- Read this repository's project-specific rules, architecture, tests, and invariants before editing code.
- Classify each change as LOW, MEDIUM, or HIGH risk.
- Use the minimum verification that fully covers the real blast radius.
- New behavior and reproducible bug fixes require test-first evidence when applicable.
- Review affected invariants and failure modes before implementation.
- Do not claim completion from stale evidence. Verification must belong to the exact final commit.
- Preserve stronger existing project checks.
- Do not use the human user as the QA system.

Project-specific commands, safety constraints, deployment rules, and invariants remain authoritative.
