# First-Pass Engineering Universal System

Date: 2026-09-28
Status: Approved; amended with resumable-engineering continuity
Initial adopter: RecepVoz / Helvoca
Target reusable home: `Nicricht/software-factory`

## 1. Purpose

Build a reusable engineering system for all future software projects so the user can request a result once and the agent automatically performs the necessary product, architecture, UX, implementation, security, QA, operations, and continuity work before calling the task complete.

The optimization target is not maximum speed per edit. It is minimum total correction loops without making progress depend on one chat session.

Core outcome:

```
one instruction
  -> understand complete impact
  -> anticipate failures
  -> test
  -> implement
  -> checkpoint durable progress
  -> attack the implementation
  -> verify
  -> deliver with evidence
```

The user must not become the QA system for the agent. The chat must not become the only durable engineering ledger.

## 2. Non-goal

This system does not promise mathematically bug-free software or that no future defect can ever occur. It aims to make predictable defects, regressions, integration failures, unsafe changes, incomplete implementations, and lost work after session interruption much less likely to escape the development cycle.

Every defect discovered later must become new permanent regression evidence whenever reproducible.

## 3. Operating model

The system uses one engineering workflow, not a slow sequence of independent role handoffs.

The agent evaluates these perspectives in one pass:

- Product: what outcome is actually required?
- Architecture: where should the behavior live?
- UX/UI: can the intended user understand and complete the flow?
- Engineering: what is the smallest coherent implementation?
- Data: what integrity, transaction, migration, and concurrency rules apply?
- Security: what can be abused, leaked, bypassed, or escalated?
- QA: how can the change fail or regress?
- Operations: how is it built, released, observed, and rolled back?
- Continuity/Resilience: if the interaction ends now, can a fresh agent reconstruct exactly where the work stopped and resume without repeating valid work?

Only relevant perspectives expand into work for a given task.

## 4. Risk-adaptive workflow

Every code change is classified before implementation.

### Low risk

Typical examples:
- copy changes;
- isolated styling;
- static content;
- non-functional visual polish.

Minimum evidence:
- syntax/lint/type validation as applicable;
- targeted tests when behavior is affected;
- targeted UI/E2E when interaction is affected.

A trivial task completed in one short interaction does not need checkpoint ceremony.

### Medium risk

Typical examples:
- new form behavior;
- API endpoint;
- CRUD flow;
- business rule;
- normal frontend/backend feature;
- data transformation.

Required evidence:
- acceptance criteria;
- impact map;
- TDD for new behavior/bug fixes when applicable;
- unit tests;
- integration tests for affected boundaries;
- changed-code coverage;
- E2E for user-visible flows;
- regression suite appropriate to the project;
- durable resume checkpoint when work spans multiple blocks or can outlive one interaction.

### High risk

Typical examples:
- authentication/authorization;
- payments/billing;
- permissions;
- tenant isolation;
- inventory consistency;
- migrations;
- destructive operations;
- external messaging/telephony;
- production infrastructure;
- sensitive data.

Required evidence includes all medium-risk evidence plus applicable:
- threat/abuse review;
- tenant/ownership isolation;
- idempotency;
- concurrency/race-condition tests;
- failure/retry/timeout tests;
- adversarial tests;
- rollback/recovery verification;
- full regression gate;
- durable recovery state before long external waits or handoffs.

## 5. First-pass workflow

For each functional change:

1. Read repository rules and relevant architecture/tests.
2. Define the requested outcome and acceptance criteria.
3. Build an impact map across UI, API, business logic, data, security, integrations, operations, and continuity when relevant.
4. Identify invariants that must remain true.
5. Create a failure map before writing implementation code.
6. Select risk level and required evidence.
7. For feature/bug behavior, use RED -> GREEN -> REFACTOR unless the repository explicitly documents a justified exception.
8. Implement the smallest complete solution.
9. Run targeted verification immediately.
10. After a meaningful completed block, persist a durable checkpoint when continuity rules apply.
11. Review the completed diff adversarially.
12. Run the project-level quality gate required by the risk level.
13. Verify the exact final commit, not an earlier commit.
14. If implementation changes after certification, invalidate previous certification and verify again.
15. Update the final resume checkpoint with final HEAD and evidence.
16. Only then report completion.

## 6. Bug workflow

A reproducible bug is handled as:

```
reproduce
  -> failing regression test
  -> verify RED
  -> fix root cause
  -> verify GREEN
  -> affected regression
  -> project quality gate
```

The test must fail for the defect being fixed, not because of unrelated setup or syntax failure.

Do not modify tests merely to hide a production defect.

## 7. Project invariants

Each repository may declare invariants in:

`docs/engineering/invariants.md`

Examples:

- one tenant can never read or mutate another tenant;
- stock cannot become negative;
- a payment cannot be executed twice;
- repeated confirmation cannot duplicate an order;
- a simulator cannot trigger real external side effects.

Risk classification and verification must consider these invariants on every relevant change.

## 8. Portable repository contract

A project adopting the system should have a small local contract:

```
AGENTS.md
.github/
  pull_request_template.md
  workflows/
    software-factory.yml
docs/engineering/
  invariants.md
  architecture.md
scripts/
  verify
```

The local files contain project-specific facts, not the whole universal methodology.

Examples of project-specific facts:
- build/test commands;
- language/runtime versions;
- coverage thresholds;
- database technology;
- E2E framework;
- production provider restrictions;
- deployment verification;
- project invariants;
- durable work/resume mechanism.

For long-running work, the preferred durable checkpoint is the Draft Pull Request plus Git history. If PRs are unavailable, use another repository-backed artifact.

## 9. Universal reusable source

The long-term canonical repository is:

`Nicricht/software-factory`

Proposed structure:

```
software-factory/
  README.md
  skill/
    first-pass-engineering/
      SKILL.md
  standards/
    definition-of-done.md
    risk-model.md
    continuity.md
    testing-policy.md
    repository-contract.md
  templates/
    AGENTS.md
    pull_request_template.md
    invariants.md
    software-factory.yml
  scripts/
    detect-project
    verify
  .github/
    workflows/
      reusable-quality-gate.yml
```

The universal source is versioned with stable tags such as `v1`, `v1.1`, `v2`.

Projects consume a pinned version rather than an unpinned moving branch.

## 10. Universal skill

The skill is named:

`first-pass-engineering`

Trigger:
Use whenever an agent is about to create, modify, refactor, debug, or certify software intended to be kept.

The skill must orchestrate existing capabilities rather than duplicate them. In environments where available, it should require or defer to:
- test-driven-development;
- systematic-debugging;
- verification-before-completion;
- code review;
- branch finishing/release verification.

It must also apply the continuity perspective to long-running work: create/update durable resume checkpoints and reconstruct durable state before resuming interrupted work.

The skill is not the enforcement boundary. It teaches the agent how to work.

## 11. Enforcement hierarchy

Quality and continuity are enforced through multiple independent layers:

1. Skill: teaches workflow, risk selection, and resume behavior.
2. `AGENTS.md`: repository-specific mandatory rules.
3. Automated tests: executable behavior evidence.
4. Coverage/static analysis/security checks: structural evidence.
5. CI: executes verification independently and durably.
6. Draft PR / durable work item: stores resume checkpoint and progress state.
7. GitHub ruleset: blocks unsafe integration.
8. Runtime/deployment verification: proves the certified artifact reached the intended environment.

A missing or ignored skill must not allow bad code into a protected branch if CI/rulesets can prevent it. A lost chat must not erase the state required to resume.

## 12. Definition of Done

A code task is not complete merely because implementation exists.

Completion requires, as applicable to the selected risk:

- acceptance criteria satisfied;
- required tests exist and pass;
- bug regression test demonstrates RED/GREEN when reproducible;
- unit/integration/E2E evidence is appropriate to the affected layers;
- changed-code coverage threshold passes;
- security/invariant checks pass;
- build passes;
- required CI gates are green;
- branch is based on the required current integration baseline;
- evidence is from the exact final commit;
- MEDIUM/HIGH or long multi-step work has durable resume state;
- a fresh conversation can identify branch, PR/work item, HEAD, completed blocks, valid CI evidence, blockers/rulings, and next step without relying on chat memory;
- production-impacting work has an explicit release/rollback path;
- no unauthorized real external side effect was triggered during testing.

## 13. Fast-path rule

The system must not turn every task into maximum ceremony.

Low-risk changes receive low-cost verification.
Medium-risk changes receive normal engineering verification.
High-risk changes receive deep verification.

Checkpointing is proportional too. Tiny work does not need ceremony; long-running work must be recoverable.

The system optimizes for prevention per unit of time, not maximum test count.

## 14. Existing-code rule

Before modifying an existing flow, the agent must inspect the whole relevant path and existing tests instead of editing only the first matching file.

A complete impact path may include:

`UI -> API -> service -> state -> database -> integration -> observability`

Only affected portions need modification, but all relevant portions must be considered.

## 15. External-effect safety

Testing must not cause real:
- payments;
- phone calls;
- messages;
- destructive production mutations;
- deliveries;
- irreversible provider actions;

unless the user explicitly authorizes a controlled real-world validation and the repository/project policy permits it.

Mocks, sandboxes, simulators, disposable environments, or read-only checks are preferred.

Resuming work must never repeat an external side effect merely because the previous interaction state was lost.

## 16. CI portability

The universal system should detect or be configured for common ecosystems:

- Java/Maven or Gradle;
- Node/TypeScript;
- Python;
- Go;
- Rust.

The reusable workflow should delegate to repository-provided commands rather than guessing complex project-specific semantics.

The repository remains authoritative for:
- test command;
- integration command;
- coverage command/threshold;
- E2E command;
- build command.

Long-running CI should execute in a durable external system when available so results survive chat/session loss.

## 17. First adopter: Helvoca

Helvoca already contains much of the desired enforcement:
- `AGENTS.md`;
- Fast Gate;
- Maven tests;
- JaCoCo differential coverage;
- JavaScript validation;
- Playwright E2E;
- Golden Journey;
- protected `main`;
- required GitHub checks.

The adoption work should therefore be incremental:
- align Helvoca's `AGENTS.md` with this universal contract;
- add explicit project invariants;
- preserve existing stronger gates;
- add a Draft-PR resume checkpoint for multi-step work;
- avoid replacing working Helvoca-specific scripts with weaker generic ones;
- treat Helvoca as the first compatibility test for the universal system.

## 18. Installation experience

For a future repository the desired user interaction is:

> Install my Software Factory in this repository.

The installer/adoption workflow should:

1. inspect the stack and existing quality infrastructure;
2. preserve stronger existing checks;
3. create only missing local contract files;
4. configure the reusable quality workflow;
5. identify project invariants and unsafe external effects;
6. open a Draft PR;
7. initialize the resume checkpoint for long-running adoption;
8. prove the installation itself through CI.

No direct `main` mutation.

## 19. Resume protocol

Interrupted work resumes from durable state, not conversational memory.

A fresh agent reconstructs:

`repository -> branch -> Pull Request/work item -> exact HEAD -> CI/evidence -> completed blocks -> blockers/rulings -> next step`

The checkpoint should be updated after meaningful blocks and before long waits.

Resume is idempotent:
- do not repeat completed work whose evidence remains valid for the same HEAD;
- do not duplicate external effects;
- if code or engineering-contract state changes, invalidate only the evidence that became stale plus mandatory final gates.

A session may interrupt interaction, but it must not force the user to reconstruct engineering state from scratch.

## 20. Success criteria

Version 1 is successful when:

- a new conversation can discover the repository rules without prior chat memory;
- an interrupted conversation can reconstruct active work from durable Git/PR/CI state and continue at the first unfinished step;
- valid work/evidence is not needlessly repeated after a resume;
- a normal feature request automatically receives risk-proportional engineering/QA;
- a reproducible bug cannot be considered fixed without regression evidence;
- a changed final commit cannot reuse stale certification;
- protected branches reject required red checks;
- high-risk project invariants are explicitly testable;
- the same universal methodology can be adopted by a second repository without copying project-specific Helvoca assumptions;
- the user normally needs to state only the desired product outcome, not manually assign QA, UX, security, architecture, DevOps, or continuity roles.

## 21. Implementation boundaries for v1

Version 1 should be intentionally small.

Build:
- universal skill source;
- universal standards, including continuity/resume;
- portable repository templates;
- project risk/invariant contract;
- Draft-PR resume checkpoint;
- reusable CI entry point;
- Helvoca adoption as first validation.

Do not build:
- a large custom CLI framework;
- a hosted service;
- a dashboard;
- an AI multi-agent orchestration platform;
- language-specific plugins for every ecosystem.

Those can be added later only if actual usage shows they are needed.
