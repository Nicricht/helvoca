# RecepVoz Engineering Operating System

> Mandatory operating instructions for any human, ChatGPT conversation, coding agent, or automation that creates, changes, fixes, reviews, or certifies code in the RecepVoz / Helvoca repository.

## 1. Mission

Build RecepVoz quickly **without trading speed for hidden defects**.

The agent working on this repository is never "just a programmer". For every implementation task, the same agent must temporarily assume the professional responsibilities needed to design, implement, challenge, verify, and release the change safely.

The goal is not to promise that software can never contain a bug. That promise would be false. The goal is to make every change carry reproducible evidence proportional to its risk so regressions are caught as early as possible and previously solved failures do not silently return.

**Core rule:**

> Code is not complete because it was written. Code is complete only when the final commit has fresh evidence that the intended behavior works, relevant failure modes are covered, existing behavior still passes, and repository quality gates are green.

## 2. Mandatory first actions

Before changing anything:

1. Read the current `AGENTS.md`.
2. Read this file completely.
3. Inspect the **current repository state**, relevant code, tests, documentation, open PR/base branch, and recent changes. Do not rely on an old chat summary when GitHub can answer the current state.
4. Never develop directly on main.
5. Refresh or compare against current `main` before beginning substantial work.
6. Create or use a dedicated branch appropriate to the task.
7. Define:
   - the user/business outcome;
   - acceptance criteria;
   - relevant failure cases;
   - affected surfaces;
   - risk level;
   - verification required before completion.
8. Prefer existing architecture and flows over inventing parallel systems.
9. Do not ask the user to make routine technical decisions that can be resolved from the repository and engineering judgment.
10. Preserve the user's explicit safety boundaries and project rules.

## 3. The virtual professional team

For each task, determine which of the roles below are relevant. High-risk changes often require all of them.

### Product / Functional Analysis

Responsibilities:

- understand what problem is being solved and for whom;
- translate the request into observable acceptance criteria;
- identify what should **not** change;
- distinguish required behavior from optional ideas;
- avoid building unnecessary functionality;
- verify the final behavior against the original request, not only against implementation details.

Questions this role must answer:

- What outcome should the user or business observe?
- What is the happy path?
- What failures must be understandable and recoverable?
- What existing behavior must remain unchanged?

### Architecture / Backend / Data

Responsibilities:

- place the change in the correct existing module;
- preserve boundaries and avoid duplicated business logic;
- design API/service/repository interactions coherently;
- protect data integrity, transactions, idempotency, concurrency, and state transitions;
- design schema/migration changes safely when required;
- prefer explicit domain invariants over accidental behavior;
- avoid coupling unrelated modules.

Questions this role must answer:

- Where is the source of truth?
- What state changes?
- Can calls be duplicated or reordered?
- Can stale state be used?
- What happens on partial failure?
- What happens concurrently?

### UX / UI / Accessibility

Responsibilities:

- make the feature understandable to a first-time user;
- design clear hierarchy, wording, states, feedback, loading, empty, success and failure behavior;
- preserve responsive behavior;
- support keyboard interaction and accessible semantics;
- avoid exposing backend concepts, UUIDs, tool names, internal status codes or implementation jargon to normal users;
- verify visually/behaviorally with browser tests when UI changes.

Questions this role must answer:

- Does the user know what to do next?
- Does the interface explain what happened?
- What does the user see on error, empty state and partial success?
- Does it work on narrow/mobile layouts?
- Can it be operated without a mouse where appropriate?

### AI / Voice / Telephony

Required whenever the change affects prompts, conversational behavior, tool calling, simulator, speech, calls, WhatsApp orchestration or AI-generated decisions.

Responsibilities:

- ground answers in authoritative business data;
- never invent stock, price, booking state, payment result, compatibility, delivery result or provider completion;
- distinguish tool request/acceptance from terminal business success;
- handle corrections, interruptions and repeated user input;
- avoid duplicate tool actions;
- use explicit confirmation for irreversible or commercial state changes when required;
- provide safe fallback behavior when AI/provider responses are incomplete;
- keep retries bounded;
- test long and adversarial conversations where appropriate;
- isolate simulators from real side effects.

Questions this role must answer:

- What can the model hallucinate here?
- What information must come from a tool/source of truth?
- What happens when the provider returns malformed, missing or stale data?
- What happens when the customer changes their mind?
- What happens if the same confirmation arrives twice?

### Security / Privacy / Multi-tenant

Responsibilities:

- preserve authentication and authorization;
- maintain strict tenant isolation;
- validate untrusted input;
- avoid secret exposure;
- avoid unsafe logging of sensitive data;
- test negative authorization paths;
- protect webhook/provider boundaries;
- ensure simulators, fixtures and tests cannot trigger real external side effects;
- consider abuse, replay and privilege escalation.

Questions this role must answer:

- Can tenant A observe or mutate tenant B?
- Can an unauthenticated/unauthorized caller reach this path?
- Can the operation be replayed?
- Can user-controlled input alter authority or query scope?
- Could this test accidentally call, message, charge, deploy or mutate production?

### QA / SDET / Adversarial Testing

This role is mandatory for **every code change**.

The implementer is also the QA owner. Do not hand correctness to a future person.

Responsibilities:

- define verification before implementation;
- use TDD for new behavior and bug fixes when behavior is testable;
- add regression tests for every reproducible bug;
- test happy path, boundary cases, negative cases and failure recovery;
- run targeted tests while iterating;
- run repository gates before declaring completion;
- read test output and count failures/errors/skips rather than assuming green;
- challenge the implementation adversarially.

Adversarial checklist as applicable:

- null / missing / blank values;
- malformed input;
- retry and timeout;
- duplicate request;
- idempotent replay;
- stale token/state;
- last-unit / race conditions;
- concurrency;
- partial success;
- provider failure;
- unknown response shape;
- repeated user input;
- correction after prior intent;
- multi-tenant leakage;
- unauthorized access;
- browser back/refresh/double-click;
- long conversation;
- unavailable inventory;
- zero/negative/extreme quantities;
- incompatible or ambiguous units.

### DevOps / SRE / Release

Responsibilities:

- preserve CI/CD behavior;
- manage environment/configuration changes safely;
- ensure migrations and deploy ordering are safe;
- verify health/readiness and observable failures;
- define rollback for risky changes;
- distinguish branch/CI success from deployed runtime success;
- after an authorized deployment, verify the **exact deployed commit** and runtime behavior before calling production complete.

Questions this role must answer:

- What exact commit is being certified?
- What exact commit is deployed?
- What happens if deployment fails halfway?
- Can the change be rolled back safely?
- What logs/metrics make failure diagnosable?

## 4. Mandatory development lifecycle

### 4.1 Understand before editing

For the complete functional block:

1. inspect current code and tests;
2. identify existing patterns and source-of-truth services;
3. write acceptance criteria;
4. enumerate relevant failure cases;
5. choose the smallest coherent implementation;
6. determine test layers required.

Do not redesign unrelated parts of the system.

### 4.2 Test-driven behavior

For new behavior, refactors that alter behavior, and reproducible bug fixes, use:

```text
RED -> GREEN -> REFACTOR
```

**RED**

- Write the smallest meaningful test that demonstrates the desired behavior or reproduces the bug.
- Run it.
- Confirm it fails for the expected reason.
- A test that passes before the change does not prove the new behavior.

**GREEN**

- Implement the smallest coherent fix/change.
- Run the test again.
- Fix production code, not the assertion, when the test accurately represents the requirement.

**REFACTOR**

- Clean structure only after green.
- Re-run relevant tests after refactoring.

For a reproducible bug:

```text
reproduce -> failing regression test -> verify RED -> fix root cause -> verify GREEN -> regression suite
```

A bug is not considered closed without regression protection unless automated reproduction is genuinely impossible. When impossible, document why and use the strongest alternative verification available.

## 5. Test layers

Use the layers relevant to the changed surface. Passing only one layer is not enough when multiple layers apply.

### Unit tests

Use for:

- pure business rules;
- validation;
- calculations;
- parsing;
- state transitions;
- mapping;
- error semantics.

### Integration tests

Use for:

- repositories/database behavior;
- transactions;
- API/service boundaries;
- migrations;
- inventory/order consistency;
- multi-component behavior;
- tenant scoping.

### Security and tenant tests

Required when touching auth, tenant-scoped data, webhooks, provider boundaries or privileged actions.

Test both allowed and denied behavior.

### Concurrency / idempotency tests

Required when state can be modified more than once or by multiple actors:

- inventory reservation;
- order confirmation;
- payment/provider callbacks;
- webhook retries;
- duplicate user confirmation;
- last-unit races.

### Conversational / AI tests

Required when changing prompts, tool availability, simulator, fallback or voice behavior.

Include:

- corrections;
- ambiguity;
- unknown information;
- provider/tool failures;
- duplicate confirmations;
- unsafe advice boundaries;
- long conversations;
- authoritative-data grounding.

### Frontend / browser tests

For behavior visible to users, use Playwright or the repository's corresponding browser test.

Test:

- action;
- visible result;
- error state;
- persistence/save semantics;
- navigation;
- important responsive behavior.

Syntax checking alone is not a UI test.

### Performance tests

Use when a change can materially affect latency, throughput, database load, provider volume or memory. Do not add synthetic performance work to unrelated changes.

## 6. Repository quality gates

### Fast Gate

Use the repository Fast Gate during iteration:

```bash
bash scripts/ci/fast-gate.sh <base-sha>
```

The Fast Gate is the cheap early detector. A red Fast Gate means the work is not ready for the expensive suite.

### JaCoCo differential coverage

Modified/new executable Java code must satisfy the repository differential coverage gate:

- at least **80%** changed executable line coverage;
- at least **70%** changed branch coverage when branches exist.

Coverage is not correctness. It is a floor that complements meaningful assertions.

Never:

- lower the threshold to make a PR pass;
- exclude legitimate production code merely to satisfy coverage;
- add meaningless tests whose only purpose is touching lines.

### Full backend regression

Before completion, run or obtain fresh CI evidence for the complete backend suite.

Read the result:

- test count;
- failures;
- errors;
- skips;
- build exit status.

### JavaScript validation

When JavaScript changes, run the repository syntax checks and relevant browser tests.

### Playwright

Run applicable targeted E2E during development and the repository Full Gate before completion.

A frontend change is not certified merely because HTML renders or JavaScript parses.

### Golden Journey

Changes that can affect commercial/customer journeys must preserve the repository Golden Journey contract.

A set of green unit tests cannot substitute for a broken end-to-end business journey.

### Specialized gates

Run specialized gates when touching their domain, including simulator, billing, onboarding, inventory, orders, voice, WhatsApp, release, adversarial fixtures or other repository-defined certification surfaces.

## 7. Risk-based verification matrix

### Low risk

Examples:

- documentation;
- comments;
- non-behavioral copy;
- isolated style adjustment.

Minimum:

- relevant static validation;
- affected UI test when behavior/layout can regress;
- Fast Gate / CI as required by repository.

### Medium risk

Examples:

- normal CRUD;
- settings;
- catalog;
- UI interactions;
- business validation.

Minimum:

- targeted automated tests;
- negative/edge cases;
- Fast Gate;
- JaCoCo when Java changes;
- Playwright when user-visible;
- Full Gate.

### High risk

Examples:

- authentication/authorization;
- multi-tenant;
- inventory;
- orders;
- billing;
- payments;
- telephony;
- WhatsApp;
- webhooks;
- production data;
- migrations;
- AI tool actions.

Required:

- TDD/regression protection;
- unit + integration where applicable;
- negative security tests;
- idempotency/concurrency when applicable;
- adversarial/failure tests;
- Fast Gate;
- JaCoCo;
- relevant specialized gate;
- Golden Journey when commercial;
- Full backend;
- applicable Playwright;
- fresh final CI evidence.

## 8. External side-effect safety

Unless explicitly authorized for a controlled real-world verification, tests and development must not perform:

- real phone calls;
- real WhatsApp/messages;
- real charges/payments;
- real physical delivery;
- live provider activation;
- destructive production mutation;
- manual production SQL used as a shortcut.

Use mocks, simulators, fixtures, test credentials or provider sandboxes.

A provider accepting a request does **not** prove the real-world outcome completed.

## 9. Multi-tenant invariant

Every tenant-scoped read or write must be evaluated for isolation.

The default adversarial question is:

> Can a valid user from business A cause any read, write, inference, cache hit, lookup, export, webhook effect, conversation state, inventory mutation or order mutation belonging to business B?

If the affected surface is tenant-scoped, include evidence that the answer remains no.

## 10. Source-of-truth invariant

Never duplicate authoritative business state into prompt text or UI state when a repository service/database/provider is the source of truth.

Examples:

- stock comes from inventory;
- order state comes from order workflow;
- subscription entitlement comes from billing/subscription state;
- booking availability comes from scheduling;
- payment terminal state comes from authoritative payment processing;
- tenant identity comes from authenticated tenant context.

The AI may explain authoritative data. It must not replace it.

## 11. Failure semantics

Every meaningful failure path must answer:

1. Is the operation safe to retry?
2. Is the prior operation already committed?
3. Can duplicate execution occur?
4. What should the user be told?
5. What should be logged?
6. What state allows support/recovery?
7. Could partial success be mistaken for complete success?

Never label queued, attempted, accepted or pending work as completed.

## 12. UX definition of done

For user-visible changes, the feature is not done until the relevant UX states are coherent:

- default;
- loading;
- empty;
- success;
- validation failure;
- backend/provider failure;
- retry/recovery;
- disabled/not-entitled where relevant;
- mobile/narrow viewport where relevant.

Avoid exposing implementation vocabulary to end users.

## 13. Review as a different engineer

Before completion, review the diff as if you did **not** write it.

Look for:

- duplicated logic;
- hidden side effects;
- permissive defaults;
- missing tenant filters;
- stale-state bugs;
- race conditions;
- unbounded retries;
- swallowed errors;
- accidental real provider calls;
- fragile mocks;
- tests that assert implementation rather than behavior;
- dead code;
- unnecessary complexity;
- unrelated refactors;
- frontend states not covered;
- misleading success language.

## 14. Fresh-evidence rule

**No completion claim without fresh evidence.**

Before saying any variation of:

- done;
- fixed;
- working;
- certified;
- safe;
- green;
- ready;

identify what evidence proves that exact claim and obtain it on the **final commit**.

Old evidence is invalidated when relevant code changes afterward.

The final report should state concrete evidence such as:

- final commit SHA;
- targeted test result;
- Fast Gate result;
- backend test count and failures/errors;
- JaCoCo differential line/branch percentages;
- Playwright result;
- Golden Journey result;
- specialized gate result;
- PR state;
- whether merge/deploy occurred.

Do not infer success from "it should work".

## 15. Branch and integration rules

- **Never develop directly on main.**
- Use a dedicated branch.
- Keep PRs DRAFT while implementation/certification is still active.
- Do not force-push.
- Check whether the branch is behind current `main` immediately before integration.
- If main moved, synchronize safely and re-run the required certification on the resulting commit.
- Never merge a red or incomplete PR.
- **No merge or deploy without explicit authorization** when project/user rules require that authorization.

## 16. Definition of done

A code task is complete only when all applicable items are true:

- acceptance criteria are satisfied;
- implementation follows existing architecture;
- every reproducible bug fixed has regression protection;
- relevant unit tests pass;
- relevant integration tests pass;
- security/tenant tests pass when applicable;
- concurrency/idempotency tests pass when applicable;
- AI/conversation tests pass when applicable;
- frontend Playwright tests pass when applicable;
- Fast Gate passes;
- JaCoCo differential coverage passes for Java changes;
- Golden Journey passes when applicable;
- specialized gates pass when applicable;
- Full Gate is green on the final commit;
- branch freshness relative to main was checked;
- no unauthorized real side effects occurred;
- the diff received adversarial self-review;
- documentation/runbooks changed when operational behavior changed;
- the final report distinguishes code/CI readiness from actual production deployment.

If any applicable item is missing, report the task as incomplete and state the exact blocker.

## 17. How to behave in a new ChatGPT conversation

When a new conversation receives a request such as:

> "Hazlo", "corrige esto", "agrega esta función", "termina esta pantalla", "arregla inventario"

the agent must not jump directly into editing.

It should internally execute this sequence:

```text
CURRENT REPO STATE
        ↓
PRODUCT / ACCEPTANCE CRITERIA
        ↓
ARCHITECTURE + DATA FLOW
        ↓
UX / AI / SECURITY REVIEW AS APPLICABLE
        ↓
TEST DESIGN
        ↓
RED -> GREEN -> REFACTOR
        ↓
TARGETED REGRESSION
        ↓
FAST GATE
        ↓
JACOCO / SECURITY / SPECIALIZED GATES
        ↓
FULL BACKEND + PLAYWRIGHT
        ↓
GOLDEN JOURNEY WHEN APPLICABLE
        ↓
ADVERSARIAL DIFF REVIEW
        ↓
FRESH CI ON FINAL COMMIT
        ↓
ONLY THEN REPORT COMPLETION
```

The user should not need to remind the agent to "also test it".

Testing and QA are part of implementation, not a separate optional request.

## 18. Skills and agent workflows

When the execution environment provides reusable engineering skills, prefer them rather than improvising process.

Particularly useful workflows include:

- test-driven development before implementation;
- systematic debugging before attempting fixes;
- verification before completion claims;
- code review before integration;
- branch-finishing checks before merge;
- structured planning for architectural changes.

Skills guide the agent. Repository tests, CI and GitHub rules remain the enforcement layer.

## 19. Permanent principle

The operating principle for RecepVoz is:

> **Do not trust confidence. Trust reproducible evidence.**

And the implementation principle is:

> **The person or agent who writes the change also owns its QA evidence.**

A future conversation should be able to read `AGENTS.md` plus this document and immediately understand how RecepVoz code must be created, corrected, tested, reviewed and certified without relying on memory from previous chats.
