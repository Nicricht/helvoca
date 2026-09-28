---
name: first-pass-engineering
description: Use when creating, modifying, refactoring, debugging, reviewing, or certifying software intended to be kept.
---

# First-Pass Engineering

## Core principle

Optimize for the fewest total correction loops: understand broadly once, implement coherently once, verify aggressively once.

The user is not the QA system.

## Before coding

1. Read the repository rules, architecture, nearby tests, and existing patterns.
2. Define the requested outcome and acceptance criteria.
3. Classify **risk** as LOW, MEDIUM, or HIGH.
4. Build a short **impact map** across UI, API, business logic, data, security, integrations, and operations.
5. Identify affected **invariants** and likely failure modes.
6. Select only the verification proportional to the risk.

## Required sub-skills when available

- **REQUIRED SUB-SKILL:** Use `superpowers:test-driven-development` for new behavior, bug fixes, and refactors.
- **REQUIRED SUB-SKILL:** Use `superpowers:systematic-debugging` whenever a test, build, integration, or runtime behavior fails unexpectedly.
- **REQUIRED SUB-SKILL:** Use `superpowers:verification-before-completion` before any completion or correctness claim.
- Use repository review and branch-finishing skills at release boundaries when available.

## Risk adaptation

**LOW:** syntax/lint/type checks plus targeted behavior/UI tests when affected.

**MEDIUM:** acceptance criteria, TDD, unit tests, affected integration boundaries, changed-code coverage, user-flow E2E, and project regression gate.

**HIGH:** all MEDIUM evidence plus applicable authorization/isolation, security, idempotency, concurrency, retry/timeout/provider-failure, adversarial, recovery/rollback, and full regression checks.

Never inflate a LOW-risk change into maximum ceremony. Never downgrade a HIGH-risk change to save time.

## Implementation loop

For each behavior:

1. Write the failing test first when TDD applies.
2. Verify the failure is for the intended reason.
3. Implement the smallest complete solution.
4. Verify GREEN.
5. Review the resulting diff **adversarially** against the impact map and invariants.
6. Run the selected project gates.
7. Verify evidence belongs to the exact **final commit**.

If implementation changes after certification, previous certification is stale and must be repeated.

For a reproducible bug: reproduce -> regression test RED -> root-cause fix -> GREEN -> regression gate.

## Stop conditions

Do not call work complete when:
- only the happy path was tested;
- a relevant invariant is unverified;
- required CI is red, skipped, stale, or belongs to an earlier commit;
- a test was weakened to hide a production defect;
- real external side effects were triggered merely to satisfy testing;
- the repository has stronger checks that were bypassed or removed.

## Skill certification status

**NOT YET BEHAVIOR-CERTIFIED**

Before deployment as a trusted personal skill, validate it in fresh-context pressure scenarios:
1. time pressure encourages skipping tests;
2. a previously green run becomes stale after a final code edit;
3. a LOW-risk copy change tempts unnecessary HIGH-risk ceremony.

Static contract tests are necessary but do not replace behavioral skill testing.
