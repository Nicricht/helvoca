# Helvoca development guardrails

These rules are mandatory for implementation work in this repository.

Helvoca follows **First-Pass Engineering**: understand the complete impact before coding, implement the smallest coherent solution, and verify the exact final commit before calling work complete. The user is not the QA system.

## Goal

Optimize for one short correction loop: design broadly, test cheaply first, and run the expensive certification once.

## Risk and impact

Classify each implementation as LOW, MEDIUM, or HIGH risk before coding.

- LOW: copy, isolated styling, and other changes with little or no behavioral blast radius.
- MEDIUM: normal features, APIs, CRUD, business rules, and stateful UI.
- HIGH: authentication, authorization, billing/payments, tenant isolation, inventory consistency, migrations, external messaging/telephony, destructive operations, sensitive data, or production infrastructure.

Use verification proportional to the real risk. Before implementation, review the affected path across UI, API, business logic, data, security, integrations, and operations, plus the project invariants in `docs/engineering/invariants.md`.

## Required workflow

1. Never develop directly on `main`. Refresh `main`, then create a dedicated branch.
2. Before coding, define acceptance criteria and failure cases for the complete functional block.
3. Add or update regression tests for every bug fixed. A bug is not closed until a test would fail if it returned.
4. For new behavior and reproducible bugs, use RED -> GREEN -> REFACTOR when applicable: prove the test fails for the intended reason before implementing the fix or behavior.
5. Run the Fast Gate before requesting or waiting for the Full Gate:
   `bash scripts/ci/fast-gate.sh <base-sha>`
6. Review the change adversarially before Full Gate: nulls, retries, duplicate calls, stale state, partial success, cross-tenant data, concurrency, provider failures and repeated user input.
7. Pull requests must pass the Full Gate. The Full Gate runs all backend tests, JaCoCo differential coverage, JavaScript validation and browser E2E.
8. New or modified executable Java lines must maintain at least 80% differential line coverage and 70% differential branch coverage when branches are present.
9. Never merge a red or incomplete PR. Re-check that the branch is not behind current `main` immediately before merge.
10. Completion evidence must belong to the exact final commit. Any implementation or engineering-contract change after certification invalidates the earlier certification and requires fresh verification.
11. After merge, verify the exact commit deployed to Railway and confirm runtime readiness before calling the work complete.
12. Production calls, payments, messages and destructive operations require the project-specific safety rules and must never be triggered merely to satisfy a test.

## Speed rule

Do not run the complete suite after every small edit. Use targeted tests through Fast Gate while iterating. Run Full Gate once the functional block and its regression tests are complete.

LOW-risk work must not be inflated into HIGH-risk ceremony. HIGH-risk work must not be downgraded to save time.

## Truth rule

A successful tool request, proposal, queued action or accepted provider request is not equivalent to a completed business outcome. Tests and application state must prove the terminal outcome explicitly.

Do not claim code is fixed, passing, safe, or complete without fresh verification evidence from the exact state being presented.
