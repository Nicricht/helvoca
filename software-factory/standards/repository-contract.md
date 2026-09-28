# Repository Contract

Each adopting repository owns its project-specific engineering facts while following the universal First-Pass Engineering method.

A repository should define:

- build, test, integration, coverage, E2E, and verification commands;
- architecture and deployment constraints;
- project invariants that must never break;
- unsafe or forbidden real external side effects;
- required branch and release rules.

## Preserve stronger checks

Adoption must **preserve stronger** existing quality gates. The universal templates are a floor, never a reason to delete or weaken project-specific CI.

## Invariants

Repositories should keep testable invariants in `docs/engineering/invariants.md`. Relevant changes must demonstrate that affected invariants still hold.

## External side effect safety

Testing must not cause a real **external side effect** such as a payment, phone call, message, destructive production mutation, physical delivery, or irreversible provider action unless an explicitly authorized project policy allows a controlled real-world validation.

## Repository authority

The repository is authoritative for exact commands and stack-specific behavior. Universal tooling may detect common ecosystems, but it must not silently invent destructive or provider-specific commands when local configuration is absent.
