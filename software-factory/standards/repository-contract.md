# Repository Contract

Each adopting repository owns its project-specific engineering facts while following the universal First-Pass Engineering method.

A repository should define:

- build, test, integration, coverage, E2E, and verification commands;
- architecture and deployment constraints;
- project invariants that must never break;
- unsafe or forbidden real external side effects;
- required branch and release rules;
- a durable continuity mechanism for long or interrupted work.

## Preserve stronger checks

Adoption must **preserve stronger** existing quality gates. The universal templates are a floor, never a reason to delete or weaken project-specific CI.

## Invariants

Repositories should keep testable invariants in `docs/engineering/invariants.md`. Relevant changes must demonstrate that affected invariants still hold.

## Continuity and resume

The chat must never be the only source of truth for durable engineering progress.

MEDIUM/HIGH-risk or multi-step work should maintain a **durable checkpoint**, preferably in the Draft Pull Request plus Git history. It must identify the branch, Pull Request or durable work item, exact HEAD, completed blocks, CI/evidence for that HEAD, blockers/rulings, and next step.

Resume by reconstructing repository -> branch -> Pull Request -> HEAD -> CI -> checkpoint -> next step.

Resume must be idempotent: preserve valid evidence, avoid duplicated work and external effects, and rerun verification only when newer state invalidates it or project policy requires a final gate.

## External side effect safety

Testing must not cause a real **external side effect** such as a payment, phone call, message, destructive production mutation, physical delivery, or irreversible provider action unless an explicitly authorized project policy allows a controlled real-world validation.

## Repository authority

The repository is authoritative for exact commands and stack-specific behavior. Universal tooling may detect common ecosystems, but it must not silently invent destructive or provider-specific commands when local configuration is absent.
