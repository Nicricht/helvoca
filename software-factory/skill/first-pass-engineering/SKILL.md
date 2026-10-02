---
name: first-pass-engineering
description: Use when creating, modifying, refactoring, debugging, reviewing, or certifying software intended to be kept.
---

# First-Pass Engineering

## Core principle

Optimize for the fewest total correction loops: understand broadly once, implement coherently once, verify aggressively once, and preserve enough durable state to resume safely.

The user is not the QA system. The chat is not the durable work ledger.

## Engineering perspectives

Evaluate only the perspectives relevant to the task: Product, Architecture, UX/UI, Engineering, Data, Security, QA, Operations, and **Continuity**.

Continuity asks: if this interaction disappears now, can a fresh agent reconstruct exactly where the work stopped without asking the user to rebuild context?

## QA ownership and test selection

The implementing agent owns QA. The user is not responsible for selecting or reminding the agent which tests to create or run.

For every implementation:
1. Identify the properties that can fail.
2. Select the lowest test level that can prove each property correctly.
3. Add higher-level verification only when the failure crosses components, persistence, security boundaries, concurrency, external providers, or real user journeys.
4. Treat coverage as evidence of exercised structure, not proof of correctness.
5. Reject tests that merely execute code without asserting meaningful behavior.
6. For reproducible bugs, create a regression test that fails for the intended reason before the fix when technically feasible.
7. For high-risk behavior, include the applicable adversarial, authorization/isolation, idempotency, concurrency, retry/timeout, partial-success, recovery, and production verification.

A unit test does not replace an integration test when correctness depends on a real database or boundary. An integration test does not replace browser E2E when the user-facing journey can fail independently. An E2E test does not replace focused unit coverage for complex business rules.

## Before coding

1. Read the repository rules, architecture, nearby tests, and existing patterns.
2. Define the requested outcome and acceptance criteria.
3. Classify **risk** as LOW, MEDIUM, or HIGH.
4. Build a short **impact map** across UI, API, business logic, data, security, integrations, operations, and continuity when work is long-running.
5. Identify affected **invariants** and likely failure modes.
6. Select only the verification proportional to the risk.
7. For MEDIUM/HIGH-risk or multi-step work likely to outlive one interaction, establish a durable **resume checkpoint** outside the chat.

## Required sub-skills when available

- **REQUIRED SUB-SKILL:** Use `superpowers:test-driven-development` for new behavior, bug fixes, and refactors.
- **REQUIRED SUB-SKILL:** Use `superpowers:systematic-debugging` whenever a test, build, integration, or runtime behavior fails unexpectedly.
- **REQUIRED SUB-SKILL:** Use `superpowers:verification-before-completion` before any completion or correctness claim.
- Use repository review and branch-finishing skills at release boundaries when available.

## Risk adaptation

**LOW:** syntax/lint/type checks plus targeted behavior/UI tests when affected. A trivial task completed in one short interaction does not need checkpoint ceremony.

**MEDIUM:** acceptance criteria, TDD, unit tests, affected integration boundaries, changed-code coverage, user-flow E2E, project regression gate, and a durable checkpoint when the work spans multiple blocks.

**HIGH:** all MEDIUM evidence plus applicable authorization/isolation, security, idempotency, concurrency, retry/timeout/provider-failure, adversarial, recovery/rollback, full regression checks, and durable recovery state.

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
8. After a meaningful completed block, update the durable resume checkpoint when continuity rules apply.

If implementation changes after certification, previous certification is stale and must be repeated.

For a reproducible bug: reproduce -> regression test RED -> root-cause fix -> GREEN -> regression gate.

## Resume protocol

When continuing interrupted work, **reconstruct** state from durable sources before doing anything:

1. repository;
2. branch;
3. Pull Request or other durable work item;
4. exact HEAD;
5. CI/evidence for that HEAD;
6. completed blocks and rulings;
7. next step.

**Do not repeat** completed work whose evidence remains valid for the same HEAD. Do not duplicate external effects. If code or engineering-contract state changed, invalidate only the evidence that is stale plus any mandatory final gate.

Prefer CI or other durable external execution for long-running builds/tests so work can continue to exist if the conversation disconnects.

## Stop conditions

Do not call work complete when:
- only the happy path was tested;
- a relevant invariant is unverified;
- required CI is red, skipped, stale, or belongs to an earlier commit;
- a test was weakened to hide a production defect;
- real external side effects were triggered merely to satisfy testing;
- the repository has stronger checks that were bypassed or removed;
- long-running work has no durable resume checkpoint and a fresh conversation could not determine the next step.

## Skill certification status

**NOT YET BEHAVIOR-CERTIFIED**

Before deployment as a trusted personal skill, validate it in fresh-context pressure scenarios:
1. time pressure encourages skipping tests;
2. a previously green run becomes stale after a final code edit;
3. a LOW-risk copy change tempts unnecessary HIGH-risk ceremony;
4. a session disconnects after a partial multi-step implementation and a fresh agent must resume without repeating valid work.

Static contract tests are necessary but do not replace behavioral skill testing.
