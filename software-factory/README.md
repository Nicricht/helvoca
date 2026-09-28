# Software Factory: First-Pass Engineering

This directory is the **universal** source bundle for a reusable software-development quality system. It is intentionally separate from Helvoca-specific rules so the same method can be adopted by another repository.

## What is universal

- `skill/first-pass-engineering/SKILL.md`: agent workflow, risk selection, and continuity/resume rules.
- `standards/`: risk model, repository contract, continuity contract, and Definition of Done.
- `scripts/`: stack detection and risk-aware verification runner.
- `.github/workflows/reusable-quality-gate.yml`: reusable GitHub Actions entry point.
- `templates/`: small files copied into an adopting repository.

## What stays project-specific

Each project remains authoritative for:
- architecture and invariants;
- exact test/build/coverage/E2E commands;
- provider and production safety rules;
- release and rollback behavior;
- stronger existing CI gates.

Adoption must preserve stronger project checks rather than replace them.

## Risk levels

- **LOW**: non-behavioral or isolated low-blast-radius changes. Use fast targeted evidence.
- **MEDIUM**: normal features/business behavior. Use TDD where applicable, unit/integration coverage, affected E2E, regression gates, and durable checkpoints for multi-step work.
- **HIGH**: auth, permissions, payments, tenant isolation, inventory, migrations, external effects, sensitive data, or infrastructure. Add security, idempotency, concurrency, provider-failure, adversarial, recovery, full-regression evidence, and durable recovery state as applicable.

## Resumable engineering

A chat is an interaction surface, not the durable engineering ledger.

For MEDIUM/HIGH or multi-step work likely to outlive one interaction, maintain a **resume checkpoint** in the Draft Pull Request or another repository-backed artifact. Record:

- branch and Pull Request/work item;
- exact HEAD;
- completed blocks;
- CI/evidence valid for that HEAD;
- blockers or rulings;
- next step.

A fresh conversation resumes by reconstructing:

`repository -> branch -> PR -> HEAD -> CI -> checkpoint -> next step`

Resume is idempotent. Do not repeat completed work whose evidence is still valid for the same HEAD, do not duplicate external effects, and invalidate only evidence made stale by newer code/contract changes plus any required final project gate.

Prefer durable external CI for long-running builds and certification so the work can continue even if the conversation disconnects.

## Adopt in another repository

Copy the `software-factory/` bundle into the repository, then install the local contract:

1. Copy `templates/AGENTS.md` to the repository root as `AGENTS.md`, merging in stronger existing rules rather than overwriting them.
2. Copy `templates/pull_request_template.md` to `.github/pull_request_template.md`.
3. Copy `templates/invariants.md` to `docs/engineering/invariants.md` and replace examples with real testable project truths.
4. Copy `templates/verify` to `scripts/verify`.
5. Create `.software-factory.json` from `templates/software-factory.json.example` when the project has multiple stacks or custom commands.
6. Use `templates/software-factory.yml` as the local GitHub Actions entry point, or call the reusable workflow from a future pinned standalone release.
7. Protect the integration branch so required CI checks must pass before merge.
8. Keep the Draft PR Resume checkpoint current for long-running work.

Then a user can normally request the product outcome once. The agent reads `AGENTS.md`, applies `first-pass-engineering`, classifies LOW/MEDIUM/HIGH risk, keeps durable progress when needed, and runs `scripts/verify` plus any stronger project gates.

## Verification runner

`scripts/verify` delegates to:

`python3 software-factory/scripts/verify.py`

For a single recognized stack, conservative safe defaults are available. Multi-stack or unknown projects require local configuration instead of silently guessing commands.

Repository-provided verification commands override defaults.

## First adopter

Helvoca is the first adopter. Its existing Fast Gate, Maven tests, JaCoCo differential coverage, JavaScript validation, Playwright E2E, Golden Journey, provider safety rules, protected `main`, and Draft-PR resume checkpoint remain authoritative.

## Skill certification

**NOT YET BEHAVIOR-CERTIFIED**

The skill source has structural contract tests, but it must still pass fresh-context pressure scenarios before being treated as a trusted installed personal skill:
- time pressure to skip tests;
- stale green evidence after a final code edit;
- unnecessary HIGH-risk ceremony for a LOW-risk copy change;
- session loss during multi-step work, followed by a fresh agent resuming from durable state without repeating valid work.

## Standalone repository

The intended long-term canonical home is a standalone repository such as `Nicricht/software-factory`, versioned with pinned releases such as `v1`, `v1.1`, and `v2`.

This directory is structured so it can be moved there without embedding Helvoca-specific implementation assumptions.
