# Helvoca QA Policy

This policy is mandatory for implementation work. It complements `AGENTS.md` and the First-Pass Engineering skill.

## QA ownership

The implementing agent owns quality assurance for the change. The user must not need to remember, request, or choose the required test categories.

Before implementation, the agent must:
1. classify risk as LOW, MEDIUM, or HIGH;
2. identify the observable properties that can fail;
3. map each property to the lowest test level that can prove it correctly;
4. define important failure and adversarial cases;
5. establish RED evidence first for reproducible bugs and new behavior when TDD is applicable;
6. preserve exact-commit evidence through certification and deployment.

No change is complete merely because it compiles, looks correct, or passes a manual happy-path check.

## Test selection matrix

| Change or risk | Required evidence |
| --- | --- |
| Pure function, mapper, parser, calculation, isolated rule | Focused Unit tests including boundaries and meaningful branches |
| Service/business rule | Unit tests; add Integration when correctness depends on persistence, transactions, framework wiring or another real component |
| Repository, JPA, SQL, transaction semantics | Integration / PostgreSQL using the real database behavior, normally Testcontainers |
| Flyway, schema, FK, CHECK, UNIQUE, RLS | Integration / PostgreSQL plus migration evidence and adversarial cases relevant to the invariant |
| Tenant-owned data/reference | Security / Tenant test proving tenant A cannot read, mutate or reference tenant B when the affected path permits such risk |
| Payment, inventory, booking capacity, counters, replay-sensitive state | Unit + Integration and Concurrency / Idempotency where races or repeated delivery are plausible |
| Webhook or retryable command | Duplicate delivery, stable idempotency, stale state, retry and partial-success tests |
| External API/provider | Contract / Provider failure tests for applicable timeout, error, retry, malformed/partial response and duplicate behavior; never trigger unauthorized real effects for test convenience |
| Stateful React/component behavior | Component / UI test when local behavior is meaningful |
| User-visible multi-step browser flow | E2E with Playwright when the failure can exist only or importantly at browser/system level |
| Release-critical commercial flow | Applicable Golden Journey / release contract |
| Production-bound behavior | Exact-main-SHA Production verification: deployment identity plus relevant migration, startup, health, smoke, logs and terminal-state evidence |

Use the lowest level that proves the property, then add higher levels only for risks that lower tests cannot prove. Do not create E2E tests for trivial getters, DTOs, passive markup, or other behavior already proven more cheaply.

## Unit testing rules

Unit tests should be fast, deterministic and focused on behavior. Cover happy paths, meaningful branches, boundaries, validation and failure behavior. Prefer assertions on outcomes and invariants, not implementation trivia.

Mocks are allowed for boundaries, but a mocked database/provider does not prove the real database/provider contract.

## Integration / PostgreSQL rules

Use real PostgreSQL/Testcontainers when correctness depends on PostgreSQL semantics, Flyway, SQL, JPA mappings, constraints, RLS, transaction isolation or concurrent writes.

For tenant-integrity changes, privileged/owner execution must be tested when appropriate so relational isolation does not accidentally depend only on application filters.

Every changed or affected database invariant must have 100% mapped PostgreSQL evidence. A database-affecting change is incomplete while any applicable persistence, rejection, isolation, transaction, migration, concurrency or reference outcome remains untested.

## Security and adversarial verification

### Adversarial verification

For MEDIUM/HIGH changes, consider applicable:
- null, empty, malformed and boundary values;
- duplicate requests, retries and repeated user input;
- stale state and out-of-order events;
- partial success and interrupted operations;
- tenant A -> tenant B access/reference attempts;
- wrong role, expired session and missing authorization;
- concurrent requests and race conditions;
- provider timeout, failure, duplicate callback and inconsistent response;
- deletion or mutation of related data during an operation.

The point is not to run every case mechanically. The agent must select the cases capable of violating the affected invariant.

## Regression policy

Every reproducible bug that is fixed must gain a regression test at the lowest level that reproduces the real defect. Observe RED for the intended reason before the fix when technically feasible.

A test that was weakened, skipped, broadened into irrelevance, or changed merely to hide a defect does not count as regression protection.

## Evidence contract for every agent and every chat

This document is a **mandatory release and completion contract**, not a suggestion or a report of already achieved coverage. An agent working on any branch must read the current `AGENTS.md` and this policy before implementation. Neither a new conversation nor the absence of an explicit QA request relaxes the requirements.

**Every changed or affected functional scope needs a traceable QA evidence matrix** in its PR/checkpoint:

| Evidence item | Required entry |
| --- | --- |
| Scope and risk | Changed paths, affected behaviors/invariants, risk LOW/MEDIUM/HIGH, expected and failure outcomes |
| Test levels | Selected unit, integration, database, security, concurrency/idempotency, provider contract, UI/E2E, Golden Journey and production checks, with each relevant N/A explained |
| RED -> GREEN | Regression that failed for the defect before the fix where feasible; exact failing and passing test run/commit evidence |
| Coverage | Actual measured report and 100% numerator/denominator for applicable changed Java/frontend/Python/script counters; behavior/test inventory mapped to all applicable acceptance and invariants |
| Final certification | Exact final HEAD SHA, CI run URLs with conclusions, Fast/Full Gate state, environment and exact deployed SHA where applicable |
| Provider/business truth | Which boundary was actually exercised: mock/local fixture, public HTTPS, authenticated provider, real database, external checkout/terminal payment, or production |
| Handoff | What is done, what is **not** done, current risks/hold, next exact safe action, and Issue #823 checkpoint for C0–C7 |

An empty cell, assumed PASS, stale CI, skipped required suite, undocumented N/A, or an applicable coverage dimension below 100% means **NOT CERTIFIED**. The owner must not be asked to select test levels, rerun existing tests or compensate for missing agent QA access.

### Python, operations scripts and other executable scopes

For changed executable Python/operational scripts and any language not explicitly covered by the Java/React subsections below, maintain **100% of applicable changed behavior and material failure branches in meaningful tests**. Where reliable coverage instrumentation exists in the repository/job, record 100% changed executable statements/lines, branches (when measurable) and callable/function coverage (when measurable). When instrumentation is not available, do **not** invent a measurement or label a passed test suite "100% coverage"; document a technical N/A and provide a 100% acceptance/failure-path test matrix, including adversarial input and retry/timeout behavior when applicable. The missing metric remains a documented observability gap to address, not automatic certification of global numerical coverage.

For provider handlers, webhooks and diagnostic receivers, require distinct evidence for: invalid/missing signature, valid HMAC, replay/stale timestamp, malformed/conflicting payload, unauthorized production mode, duplicate delivery and idempotency where business effects exist, provider-specific format compatibility, public network transport, and genuine provider delivery. A **signed local fixture cannot certify a real provider event**. Even a provider simulator HTTP 200 cannot certify an approved invoice or activation.

### Enforcement and unverified status

The repository's Fast Gate runs `scripts/ci/check-qa-governance.py` to protect policy invariants from silent removal. This is a **static policy integrity check**, not automatic proof of 100% runtime coverage or correct AI behavior. Exact-HEAD CI and any applicable runtime/provider/database evidence remain separately mandatory. Require code review/release gates before merging; do not merge directly to production-linked `main` to make a policy change appear universal.

States must be accurate: `IN_PROGRESS`, `BLOCKED`, `NO-GO`, `DONE_OFFLINE_ONLY`, `DONE_VERIFIED`, with `DONE_VERIFIED` limited to the acceptance boundary actually proven. Full V1 commercial readiness remains governed by Issue #823 and C0–C7.

## Coverage policy

Coverage is a structural signal, not proof that assertions are useful. Helvoca nevertheless uses a **100% quality contract**: no applicable coverage dimension for changed or affected production behavior may be certified below 100%.

### Backend / Java

For every new or modified executable Java scope:
- differential Lines: **100%**;
- differential Branches: **100%** when branches exist;
- differential Methods: **100%** when methods are affected.

The automated differential gate must reject any value below 100%. A method that is executed without meaningful assertions does not satisfy the behavioral requirement even if JaCoCo marks it covered.

### Frontend / React / browser behavior

For every changed or affected frontend scope:
- executable Statements/Lines: **100%** when instrumented source coverage is available;
- Branches: **100%** when branches exist;
- Functions/Methods: **100%** when functions are affected;
- changed interactive controls and state transitions: **100% behavior coverage**;
- release-critical user journeys: **100% mapped journey coverage**.

Every affected button, link, tab, menu action, form submission, keyboard action, drag/drop interaction or other meaningful control must have automated evidence for its intended observable result at the lowest correct level. Rendering an element is not proof that the interaction works. Playwright pass count is not a substitute for proving that the interaction inventory is complete.

### Database / PostgreSQL

Database completeness is measured by behavior and invariants, not by pretending SQL has a JaCoCo-style line metric. For every changed or affected data scope, **100% of applicable database invariants and outcomes must be mapped to real PostgreSQL evidence**, including:
- successful persistence and reload;
- relevant validation/constraint rejection;
- FK, CHECK, UNIQUE and RLS behavior;
- Flyway forward migration behavior;
- transaction commit/rollback semantics;
- tenant isolation;
- concurrency/idempotency behavior when applicable;
- delete/update/reference behavior when applicable.

Mocks never count toward PostgreSQL completeness.

### Cross-layer system behavior

When a feature crosses layers, **100% of its acceptance criteria and material failure outcomes must be mapped to automated evidence across the required layers**. Frontend coverage cannot compensate for missing backend or PostgreSQL evidence, and backend coverage cannot compensate for an untested browser interaction.

No assertion-free, duplicate, unreachable-only or behavior-free test may be added merely to reach 100%. No threshold below 100% may be introduced as a convenience exception. If a metric is technically inapplicable, the PR must state why and provide the behavior-level evidence that replaces it; "too hard to test" is not sufficient.

Historical code does not authorize new uncovered behavior. Any touched production scope must satisfy this 100% contract before completion.

### Repository-wide 100% certification

Helvoca may be described as having **100% QA coverage** only when repository-wide evidence proves all of the following for the production scope:
- backend global Lines / Branches / Methods: **100%**;
- frontend global Statements/Lines / Branches / Functions/Methods: **100%** for executable source included by the coverage instrument;
- frontend meaningful interaction/state-transition inventory: **100% automated**;
- database invariant catalog for the production schema and persistence paths: **100% mapped to real PostgreSQL tests**;
- release-critical acceptance criteria and journeys: **100% mapped to automated evidence**.

A green test suite means 100% of existing tests passed; it does not by itself mean 100% of the product is covered. Until the repository-wide measurements above are green, do not claim global 100% coverage.

Mutation testing should be introduced for critical business logic as an additional strength signal. Surviving meaningful mutations must be investigated; mutation score is not a substitute for integration, security, frontend interaction, PostgreSQL or E2E evidence.

## E2E and Golden Journey

Browser E2E proves user-visible system behavior, not every internal branch. For every changed or affected frontend surface, maintain a complete interaction inventory and automate 100% of meaningful controls/state transitions at the lowest correct test level. Keep E2E focused on journeys and regressions that can fail across UI/API/system boundaries, while component/unit tests cover local branches more cheaply.

Golden Journey or equivalent release contracts protect a smaller set of business-critical flows. They complement rather than replace unit and integration tests.

## Production verification

QA does not end at merge.

For production-bound changes, verify the exact merged `main` SHA. Depending on impact, evidence may include:
- CI success on the exact merge SHA;
- Railway deployment commit identity;
- Flyway validation/migration result;
- application startup;
- configured health endpoint;
- public smoke checks;
- critical logs without migration/startup/security failures;
- terminal business outcome when safe and authorized to verify.

A queued deploy, accepted provider request or green PR is not itself proof of a successful production outcome.

## Definition of done

A change is done only when all applicable statements are true:
- acceptance criteria and important failure cases are defined;
- the selected test levels match the actual risks;
- reproducible bugs have regression protection;
- RED -> GREEN evidence exists when required;
- unit/integration/security/concurrency/contract/component/E2E evidence is green where applicable;
- every applicable changed/affected coverage dimension is proven at 100%; technically inapplicable metrics have an explicit N/A plus equivalent behavior evidence;
- adversarial review found no unresolved invariant violation;
- required Fast Gate and Full Gate are green;
- certification belongs to the exact final HEAD;
- branch is synchronized with current `main` before merge;
- exact-main CI is green after merge;
- production verification is complete when production behavior changed;
- durable PR checkpoint records enough state for another conversation to resume without asking the user to reconstruct QA context.

No test strategy can prove software will never fail. The objective is to make regressions difficult to introduce, likely to be detected before release, bounded in impact, observable in production, and easy to diagnose or roll back.
