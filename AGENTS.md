# Helvoca development guardrails

These rules are mandatory for implementation work in this repository.

Helvoca follows **First-Pass Engineering**: understand the complete impact before coding, implement the smallest coherent solution, verify the exact final commit, and preserve durable state so interrupted work can resume safely. The user is not the QA system.

Before implementation, read `software-factory/skill/first-pass-engineering/SKILL.md` when it is present so a fresh conversation does not depend on prior chat memory.

## Goal

Optimize for one short correction loop: design broadly, test cheaply first, run the expensive certification once, and never lose engineering progress because a chat or session ended.

## Risk and impact

Classify each implementation as LOW, MEDIUM, or HIGH risk before coding.

- LOW: copy, isolated styling, and other changes with little or no behavioral blast radius.
- MEDIUM: normal features, APIs, CRUD, business rules, and stateful UI.
- HIGH: authentication, authorization, billing/payments, tenant isolation, inventory consistency, migrations, external messaging/telephony, destructive operations, sensitive data, or production infrastructure.

Use verification proportional to the real risk. Before implementation, review the affected path across UI, API, business logic, data, security, integrations, operations, and continuity for long-running work, plus the project invariants in `docs/engineering/invariants.md`.


## QA ownership and test selection

The implementing agent owns QA for every change. The user is not responsible for selecting or reminding the agent which tests to create or run. Before coding, read `docs/engineering/QA_POLICY.md`, classify the change, identify the properties that can fail, and select the lowest test level that can prove each property correctly.

Do not mechanically run every test category for every edit. Unit tests prove isolated logic; integration/PostgreSQL tests prove persistence, SQL, Flyway, repository and transactional behavior; security/tenant tests prove authorization and isolation boundaries; concurrency/idempotency tests prove race and replay safety; contract/failure tests prove provider boundaries; component tests prove local UI behavior; browser E2E proves user journeys; Golden Journey proves release-critical commercial flows; production verification proves the exact deployed artifact. The selected evidence must collectively cover 100% of the changed or affected behavior and applicable invariants.

Mandatory rules:
- A reproducible bug requires a regression test that is observed RED for the intended reason before the fix when technically feasible.
- New or materially changed business logic requires focused unit tests unless the behavior can only be observed correctly at a higher boundary.
- SQL, JPA, Flyway, constraints, RLS and repository semantics require real PostgreSQL/Testcontainers integration evidence when changed.
- Tenant-owned relationships require an adversarial tenant A -> tenant B attempt when the affected path could permit cross-tenant references or access.
- Payments, inventory, booking capacity, retries, webhooks and other replay/race-sensitive behavior require idempotency and/or concurrency verification when applicable.
- External providers require contract/failure-path evidence for relevant timeout, retry, duplicate, partial-success and provider-error behavior without triggering unauthorized real-world side effects.
- Stateful React behavior requires component/unit coverage when local behavior is meaningful; user-visible journeys require browser E2E when a browser-level failure is plausible.
- Release-critical commercial behavior requires the applicable Golden Journey or release contract.
- Production-bound changes require exact-main-SHA production verification appropriate to the change, including migration/startup/health/smoke/log checks when relevant.
- Do not add assertion-free or behavior-free tests merely to increase a coverage number.
- Any implementation or engineering-contract edit after certification invalidates stale evidence and requires fresh verification.

Coverage is a guardrail, not proof of correctness. Helvoca now uses a **100% quality contract** for every changed or affected production scope. Backend executable Java must reach 100% differential line, branch and method coverage when those counters apply. Frontend executable logic must reach 100% statement/line, branch and function/method coverage when instrumented, and 100% of affected meaningful interactive controls and state transitions must have automated behavioral evidence. Database work must map 100% of affected persistence, constraint, migration, transaction, tenant-isolation and concurrency/idempotency invariants to real PostgreSQL evidence. One layer's 100% never compensates for missing evidence in another layer. Do not claim 100% unless the measured reports and behavior matrix actually prove it.

A repository-wide "100% QA coverage" claim is stronger than a green suite or differential gate. It requires measured 100% backend line/branch/method coverage, measured 100% frontend statement/line/branch/function coverage for executable source, 100% automated meaningful frontend interactions/state transitions, 100% mapped real-PostgreSQL database invariants, and 100% mapped release-critical journeys. Until those reports exist and are green, never describe the whole product as 100% covered.

## Required workflow

1. Never develop directly on `main`. Refresh `main`, then create a dedicated branch.
2. Before coding, define acceptance criteria and failure cases for the complete functional block.
3. Add or update regression tests for every bug fixed. A bug is not closed until a test would fail if it returned.
4. For new behavior and reproducible bugs, use RED -> GREEN -> REFACTOR when applicable: prove the test fails for the intended reason before implementing the fix or behavior.
5. Run the Fast Gate before requesting or waiting for the Full Gate:
   `bash scripts/ci/fast-gate.sh <base-sha>`
6. Review the change adversarially before Full Gate: nulls, retries, duplicate calls, stale state, partial success, cross-tenant data, concurrency, provider failures and repeated user input.
7. Pull requests must pass the Full Gate. The Full Gate runs all backend tests, JaCoCo differential coverage, JavaScript validation and browser E2E.
8. New or modified executable Java must maintain 100% differential line, branch and method coverage when those counters apply. Changed or affected frontend behavior must have 100% mapped interaction/state-transition coverage, and changed or affected database behavior must have 100% mapped real-PostgreSQL invariant coverage.
9. Never merge a red or incomplete PR. Re-check that the branch is not behind current `main` immediately before merge.
10. Completion evidence must belong to the exact final commit. Any implementation or engineering-contract change after certification invalidates the earlier certification and requires fresh verification.
11. For MEDIUM/HIGH or multi-step work likely to outlive one interaction, maintain a **resume checkpoint** in the Draft PR. It must identify branch, PR, exact HEAD, completed blocks, CI/evidence valid for that HEAD, blockers/rulings, and next step.
12. On resume, reconstruct repository -> branch -> PR -> HEAD -> CI -> checkpoint -> next step. Do not repeat completed work whose evidence remains valid, and never duplicate external side effects.
13. Before a long external wait such as Full CI, update the resume checkpoint so a disconnected session can be recovered without user reconstruction.
14. A complete, exact-HEAD-green PR should be merged promptly instead of being left indefinitely in Draft, unless an explicit task-specific review/freeze or safety hold applies.
15. After merge, require green CI on the exact `main` merge SHA. For production-bound work, normal Railway deployment is allowed once the release gates for that change are satisfied; verify Railway deploys that exact SHA and confirm runtime health before calling the work complete.
16. Delete or otherwise retire merged source branches once their merge and deployment evidence is durable. Keep a branch open only when it still contains unmerged work, a documented blocker, or deliberately deferred future scope.
17. Do not impose a blanket `NO MERGE` or `NO DEPLOY` rule on normal engineering work. Use an explicit hold only when the task specifically requires a review/freeze, when release evidence is incomplete, or when deployment would trigger an unsafe/unapproved external effect.
18. Production calls, payments, messages and other destructive or customer-visible external effects still require their project-specific safety/authorization rules and must never be triggered merely to satisfy a test.

## Frontend frame contract

For any frontend, UI, layout, visual, responsive, styling, navigation, or component task, read `docs/frontend/FRAME_CONTRACT.md` before implementation.

The default for ordinary screen work is `FRAME CHANGE: NO`.

When `FRAME CHANGE: NO`:
- identify the affected screen and editable slot(s);
- keep protected `--rv-frame-*` tokens unchanged;
- do not redefine reserved `.rv-frame-*` or `.rv-page-*` primitives outside `frontend-foundation.css`;
- do not alter global shell, topbar, sidebar, page gutters, canonical breakpoints, or shared visual primitives to solve a local screen problem;
- do not modify unrelated principal screens.

Use `FRAME CHANGE: YES` only when the task intentionally changes the shared product frame. A frame change requires cross-screen evidence at the canonical viewports defined in `docs/frontend/FRAME_CONTRACT.json`.

If a supposedly local change cannot be completed without touching protected frame territory, promote it explicitly to `FRAME CHANGE: YES` instead of making an implicit global redesign.

## Speed rule

Do not run the complete suite after every small edit. Use targeted tests through Fast Gate while iterating. Run Full Gate once the functional block and its regression tests are complete.

LOW-risk work must not be inflated into HIGH-risk ceremony. HIGH-risk work must not be downgraded to save time. Resume must be idempotent: reuse still-valid evidence instead of repeating expensive work without cause.

## Truth rule

A successful tool request, proposal, queued action or accepted provider request is not equivalent to a completed business outcome. Tests and application state must prove the terminal outcome explicitly.

Do not claim code is fixed, passing, safe, or complete without fresh verification evidence from the exact state being presented. Chat memory never overrides Git, PR, commit, or CI state.

## Delivery and branch hygiene

The default lifecycle is:

`branch -> Draft PR -> RED/GREEN implementation -> Fast Gate -> Full Gate on exact HEAD -> merge -> main CI -> Railway exact-SHA deployment when production-bound -> runtime verification -> source-branch cleanup`.

Draft is a working state, not a permanent parking state.

Prefer integrating completed stacked work into the designated integration branch as soon as its exact HEAD is certified. Once the integration branch itself is certified and releaseable, merge it through the normal protected-branch workflow instead of accumulating another generation of completed feature branches.

A temporary hold is valid only when it has a concrete reason and next step. Record that reason in the PR checkpoint. “Do not merge/deploy” must never be copied forward mechanically from an older task.
