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
        "100% quality contract",
        "100% differential line, branch and method coverage",
        "100% mapped interaction/state-transition coverage",
        "100% mapped real-postgresql invariant coverage",
        "mandatory for every chat/agent: qa evidence before done",
        "100% of applicable changed/affected quality dimensions",
        "local_simulation_pass",
        "authentic_provider_pass",
        "never claim 100% coverage",
        # Anti-duplication requirement, triggered by new-chat status and work requests.
        "pre-change fresh-context write gate",
        "fresh_context_evidence_receipt",
        "latest remote head",
        "changed since the last checkpoint",
        "do not edit if the gap is already closed",
        "mandatory anti-redundancy audit before status or next work",
        "evidence-delta",
        "delivered_and_verified",
        "done_offline_not_deployed",
        "external_acceptance_pending",
        "not_verified",
        "do not re-run historical success",
        "do not promote optional improvements to mandatory v1 blockers",
        "piloto_asistido_vendible",
        "únicas brechas reales de aceptación",
    ],
    "docs/engineering/QA_POLICY.md": [
        "QA ownership",
        "Test selection matrix",
        "Coverage policy",
        "Adversarial verification",
        "Definition of done",
        "100% quality contract",
        "differential Lines: **100%**",
        "Branches: **100%**",
        "differential Methods: **100%**",
        "100% behavior coverage",
        "100% of applicable database invariants",
        "evidence contract for every agent and every chat",
        "python, operations scripts and other executable scopes",
        "100% acceptance/failure-path test matrix",
        "signed local fixture cannot certify a real provider event",
        "not certified",
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
        "Backend: differential Lines / Branches / Methods = 100%",
        "Frontend: Statements/Lines / Branches / Functions/Methods = 100%",
        "Database: 100% affected persistence/constraint/migration/transaction/tenant/concurrency invariants",
        "pre-change fresh-context write gate",
        "fresh_context_evidence_receipt",
        "changes since the last checkpoint",
        "mandatory qa evidence matrix (all chats/agents)",
        "exact test/ci link and head",
        "local_simulation_pass",
        "authentic_provider_pass",
        "final head",
    ],
    "docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md": [
        "pre-change fresh-context write gate",
        "fresh_context_evidence_receipt",
        "condición qa de cierre aplicable a todos los agentes",
        "head final",
        "no equivale",
        "100% de cobertura",
        "no al usuario",
    ],
    "README.md": [
        "obligatorio para cualquier chat, asistente o agente que modifique helvoca",
        "docs/engineering/qa_policy.md",
        "issue #823",
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
