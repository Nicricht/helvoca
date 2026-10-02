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

## Coverage policy

Coverage is a structural signal, not proof that assertions are useful.

Current CI floor for changed executable Java:
- differential Lines: at least 80%;
- differential Branches: at least 70% when branches exist.

Engineering target for HIGH-risk new or materially modified business logic:
- meaningful Lines: 100%;
- meaningful Branches: 100%;
- meaningful Methods: 100%.

This target must not be met with assertion-free, duplicate, unreachable-only, or behavior-free tests. Any meaningful exception must be documented in the PR with a technical reason.

Historical code follows a ratchet rule: touching an under-tested critical area should improve its protection rather than preserve known weakness. Do not claim global or module-level 100% unless the measured report proves it.

Mutation testing should be introduced for critical business logic as an additional strength signal. Surviving meaningful mutations must be investigated; mutation score is not a substitute for integration, security or E2E evidence.

## E2E and Golden Journey

Browser E2E proves user-visible system behavior, not every internal branch. Keep E2E focused on important journeys and regressions that can fail across UI/API/system boundaries.

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
- coverage evidence meets the applicable floor/target or a justified exception is recorded;
- adversarial review found no unresolved invariant violation;
- required Fast Gate and Full Gate are green;
- certification belongs to the exact final HEAD;
- branch is synchronized with current `main` before merge;
- exact-main CI is green after merge;
- production verification is complete when production behavior changed;
- durable PR checkpoint records enough state for another conversation to resume without asking the user to reconstruct QA context.

No test strategy can prove software will never fail. The objective is to make regressions difficult to introduce, likely to be detected before release, bounded in impact, observable in production, and easy to diagnose or roll back.
