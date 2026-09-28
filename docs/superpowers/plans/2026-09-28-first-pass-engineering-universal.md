# First-Pass Engineering Universal System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a portable First-Pass Engineering system that makes agents analyze impact, apply risk-proportional QA, verify the final commit, and leave reusable repository guardrails without slowing every change to maximum ceremony.

**Architecture:** Keep the universal methodology in a small portable bundle under `software-factory/`, with a skill source, standards, templates, and a stack-aware verifier. Helvoca remains the first adopter and keeps its stronger existing gates; adoption adds invariants and a lightweight contract instead of replacing working CI. The eventual standalone `Nicricht/software-factory` repository can be created from this bundle without changing the internal structure.

**Tech Stack:** Markdown skill/spec files, Bash/Python verification scripts, GitHub Actions templates, existing Helvoca Maven/JaCoCo/Playwright CI.

**Spec:** `docs/superpowers/specs/2026-09-28-first-pass-engineering-universal-design.md`

## Global Constraints

- Never develop directly on `main`.
- Optimize for minimum total correction loops, not maximum ceremony.
- Preserve stronger project-specific checks instead of replacing them with weaker generic checks.
- Bug fixes require regression evidence when reproducible.
- Evidence must correspond to the exact final commit; later code changes invalidate earlier certification.
- Low-, medium-, and high-risk changes receive proportional verification.
- Real payments, calls, messages, destructive production mutations, deliveries, and irreversible provider actions must not be triggered merely to satisfy tests.
- Helvoca keeps its existing Fast Gate, full backend suite, JaCoCo differential thresholds (80% lines / 70% branches), JavaScript validation, Playwright E2E, Golden Journey, and protected `main`.
- Version 1 does not build a hosted service, dashboard, large CLI framework, or multi-agent platform.

## Review Focus

1. A repository already has stronger CI than the template: adoption must preserve it and add only missing contracts.
2. A tiny non-behavioral change is classified low risk: the system must not demand high-risk ceremony.
3. A security/data/payment/auth change is classified high risk: the system must require explicit invariant/security/failure evidence.
4. A final commit changes after a green run: stale evidence must not be accepted as completion evidence.
5. A project uses an unsupported or custom stack: the verifier must fail clearly or defer to repository-provided commands rather than silently guessing.

---

### Task 1: Define the Portable Contract and Risk Model

**Files:**
- Create: `software-factory/standards/risk-model.md`
- Create: `software-factory/standards/definition-of-done.md`
- Create: `software-factory/standards/repository-contract.md`
- Create: `software-factory/tests/test_contract.py`

**Interfaces:**
- Consumes: approved design spec.
- Produces: stable risk levels `low | medium | high`, universal Definition of Done, required local repository contract, and machine-checkable required headings/markers used by later tasks.

- [ ] **Step 1: Write failing structural contract tests**

Create `software-factory/tests/test_contract.py` using Python `unittest`. Assert that the three standard files exist and contain the required canonical markers:
- risk levels `LOW`, `MEDIUM`, `HIGH`;
- final-commit evidence invalidation rule;
- regression-test rule for reproducible bugs;
- invariant rule;
- preserve-stronger-checks rule;
- external-side-effect safety rule.

- [ ] **Step 2: Run the contract test and verify RED**

Run:

```bash
python3 -m unittest software-factory/tests/test_contract.py -v
```

Expected: FAIL because the standards files do not exist yet.

- [ ] **Step 3: Implement the three standards documents**

Keep them concise and technology-agnostic. Do not duplicate project-specific Helvoca commands.

- [ ] **Step 4: Run the contract test and verify GREEN**

Run the same `unittest` command.

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add software-factory/standards software-factory/tests/test_contract.py
git commit -m "feat(factory): define first-pass engineering contract"
```

### Task 2: Create the First-Pass Engineering Skill Source

**Files:**
- Create: `software-factory/skill/first-pass-engineering/SKILL.md`
- Create: `software-factory/tests/test_skill_contract.py`

**Interfaces:**
- Consumes: Task 1 risk model and Definition of Done.
- Produces: reusable skill source named `first-pass-engineering`, with trigger text for any kept software implementation/debug/refactor/certification task.

- [ ] **Step 1: Write failing skill artifact tests**

Assert:
- valid YAML frontmatter with `name: first-pass-engineering`;
- description begins with `Use when`;
- skill references risk classification, impact map, invariants, TDD/regression behavior, adversarial review, final-commit evidence, and verification-before-completion;
- skill does not contain Helvoca-specific commands or product names.

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest software-factory/tests/test_skill_contract.py -v
```

Expected: FAIL because `SKILL.md` is absent.

- [ ] **Step 3: Write the minimal skill**

The skill should orchestrate rather than duplicate existing skills. Where available, explicitly require:
- `superpowers:test-driven-development` for features/bugs;
- `superpowers:systematic-debugging` for unexpected failures;
- `superpowers:verification-before-completion` before success claims;
- code review/branch finishing skills at release boundaries.

Its core decision:
1. read repository rules;
2. classify risk;
3. map impact/invariants/failures;
4. select evidence;
5. implement with TDD where applicable;
6. adversarially review;
7. verify exact final state;
8. only then claim completion.

- [ ] **Step 4: Run and verify GREEN**

Run both Task 1 and Task 2 unittest modules.

- [ ] **Step 5: Skill behavior verification gate**

Because a discipline skill must ultimately be tested against fresh-agent pressure scenarios, add a clear `NOT YET DEPLOYED` marker until a fresh-context behavioral test can be run. Do not claim the skill is installed or behavior-certified merely from static tests.

Document three required future pressure scenarios:
- time pressure encourages skipping tests;
- existing green run is stale after a final code edit;
- low-risk copy change tempts unnecessary full high-risk ceremony.

- [ ] **Step 6: Commit**

```bash
git add software-factory/skill software-factory/tests/test_skill_contract.py
git commit -m "feat(factory): add first-pass engineering skill source"
```

### Task 3: Create the Reusable Repository Templates

**Files:**
- Create: `software-factory/templates/AGENTS.md`
- Create: `software-factory/templates/pull_request_template.md`
- Create: `software-factory/templates/invariants.md`
- Create: `software-factory/templates/software-factory.yml`
- Create: `software-factory/tests/test_templates.py`

**Interfaces:**
- Consumes: Task 1 standards.
- Produces: small local repository contract that projects can adopt without copying the entire methodology.

- [ ] **Step 1: Write failing template tests**

Assert that:
- `AGENTS.md` requires reading local project rules and First-Pass Engineering;
- PR template records risk level, acceptance criteria, affected invariants, test evidence, exact final commit evidence, and release/rollback impact when relevant;
- invariants template explains how to state truths that must never break;
- workflow template invokes a local `scripts/verify` command and does not hardcode Java/Node/Python semantics.

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest software-factory/tests/test_templates.py -v
```

Expected: FAIL because templates are absent.

- [ ] **Step 3: Implement the four templates**

Keep local templates short. They should point to the universal methodology rather than duplicate it.

- [ ] **Step 4: Run and verify GREEN**

Run all `software-factory/tests/test_*.py`.

- [ ] **Step 5: Commit**

```bash
git add software-factory/templates software-factory/tests/test_templates.py
git commit -m "feat(factory): add portable repository templates"
```

### Task 4: Build a Stack-Aware Verification Entry Point

**Files:**
- Create: `software-factory/scripts/detect_project.py`
- Create: `software-factory/scripts/verify.py`
- Create: `software-factory/templates/software-factory.json.example`
- Create: `software-factory/tests/test_detect_project.py`
- Create: `software-factory/tests/test_verify.py`

**Interfaces:**
- Produces:
  - `detect_project(root: Path) -> ProjectDetection`
  - `load_verification_config(root: Path) -> VerificationConfig`
  - `verification_commands(config, risk) -> list[list[str]]`
- Consumes repository-local explicit commands when provided.
- Never executes guessed destructive/provider commands.

- [ ] **Step 1: Write failing detection tests**

Use temporary directories to assert:
- `pom.xml` detects Maven/Java;
- `build.gradle` or `build.gradle.kts` detects Gradle;
- `package.json` detects Node;
- `pyproject.toml` detects Python;
- `go.mod` detects Go;
- `Cargo.toml` detects Rust;
- ambiguous/custom stack returns a clear result requiring local config instead of inventing commands.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest software-factory/tests/test_detect_project.py -v
```

Expected: FAIL before implementation.

- [ ] **Step 3: Implement detection only**

Detection reports facts. It does not yet run commands.

- [ ] **Step 4: Verify GREEN**

Run the detection tests.

- [ ] **Step 5: Write failing verifier tests**

Assert:
- repository-provided commands override defaults;
- low risk omits high-risk-only commands;
- high risk includes configured security/adversarial commands;
- empty/unconfigured custom stack fails with actionable output;
- no command containing explicitly configured forbidden real-effect markers can be selected without an explicit opt-in flag.

- [ ] **Step 6: Verify RED, then implement minimal verifier**

The verifier should print the plan before running it and stop on first failing command. Keep configuration declarative and small.

- [ ] **Step 7: Verify GREEN**

Run all factory unit tests.

- [ ] **Step 8: Commit**

```bash
git add software-factory/scripts software-factory/templates/software-factory.json.example software-factory/tests
git commit -m "feat(factory): add portable verification runner"
```

### Task 5: Add the Reusable GitHub Actions Entry Point

**Files:**
- Create: `software-factory/.github/workflows/reusable-quality-gate.yml`
- Create: `software-factory/tests/test_workflow_contract.py`

**Interfaces:**
- Consumes: repository checkout and repository-local `scripts/verify` or configured equivalent.
- Produces: a reusable `workflow_call` quality job parameterized by risk and optional config path.

- [ ] **Step 1: Write failing workflow contract tests**

Assert YAML contains:
- `on: workflow_call`;
- checkout;
- explicit risk input;
- execution of the repository-local verification entry point;
- no production deployment step;
- no secret values embedded in source.

- [ ] **Step 2: Verify RED**

Run the workflow contract unittest.

- [ ] **Step 3: Implement the reusable workflow**

Do not attempt to replace language-specific CI. The reusable gate calls the repository's verifier, which is authoritative for its own commands.

- [ ] **Step 4: Verify GREEN**

Run all factory tests.

- [ ] **Step 5: Commit**

```bash
git add software-factory/.github/workflows software-factory/tests/test_workflow_contract.py
git commit -m "feat(factory): add reusable quality gate"
```

### Task 6: Adopt the Contract in Helvoca Without Weakening Existing CI

**Files:**
- Modify: `AGENTS.md`
- Modify: `.github/pull_request_template.md`
- Create: `docs/engineering/invariants.md`
- Create: `software-factory/adopters/helvoca.json`
- Create: `src/test/java/cl/helvoca/architecture/EngineeringGuardrailsContractTest.java`

**Interfaces:**
- Consumes: existing Helvoca Fast Gate, Full Gate, JaCoCo, Playwright, Golden Journey, safety rules, and protected main.
- Produces: explicit First-Pass risk/invariant contract for future agents without replacing existing commands.

- [ ] **Step 1: Write the failing Helvoca guardrail test**

The Java test should assert the repository contract contains:
- First-Pass Engineering reference;
- exact 80% differential line threshold;
- exact 70% differential branch threshold;
- final-commit re-verification rule;
- no direct `main` development;
- regression-test requirement for bugs;
- invariants file exists and contains at least:
  - tenant isolation;
  - simulator external-effect isolation;
  - no false payment completion;
  - no duplicate confirmation/order invariant.

- [ ] **Step 2: Run targeted test and verify RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=EngineeringGuardrailsContractTest test
```

Expected: FAIL until adoption files are updated.

- [ ] **Step 3: Update Helvoca guardrails and PR template**

Preserve all current stronger rules. Add:
- risk declaration;
- impact/invariant review;
- final-commit evidence rule;
- explicit “user is not the QA system” completion principle in operational language.

- [ ] **Step 4: Create Helvoca invariants**

Document only true, testable project invariants already supported by architecture/tests. Do not invent guarantees that the product does not currently enforce.

- [ ] **Step 5: Add adopter config**

`software-factory/adopters/helvoca.json` points to the existing project commands/gates rather than creating a weaker parallel CI.

- [ ] **Step 6: Verify GREEN**

Run:
- targeted guardrail test;
- all `software-factory` Python tests;
- Helvoca Fast Gate.

- [ ] **Step 7: Commit**

```bash
git add AGENTS.md .github/pull_request_template.md docs/engineering software-factory/adopters src/test/java/cl/helvoca/architecture
git commit -m "chore: adopt first-pass engineering guardrails"
```

### Task 7: End-to-End Certification of the First Adopter

**Files:**
- Modify only if verification finds a real defect.
- Update: `software-factory/README.md`
- Update: `docs/superpowers/specs/2026-09-28-first-pass-engineering-universal-design.md` only if implementation reality requires a documented clarification.

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: evidence that Helvoca can adopt the universal methodology without weakening its existing quality system.

- [ ] **Step 1: Document installation/use**

README must explain:
- universal vs project-specific responsibilities;
- how to copy/adopt the bundle in another repository;
- how risk levels map to verification;
- that the skill source is not considered behavior-certified until fresh-agent pressure tests are completed;
- how a future standalone `Nicricht/software-factory` repository can be created from this directory.

- [ ] **Step 2: Run the factory test suite**

```bash
python3 -m unittest discover -s software-factory/tests -p 'test_*.py' -v
```

Expected: all PASS.

- [ ] **Step 3: Run Helvoca targeted guardrail test**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=EngineeringGuardrailsContractTest test
```

Expected: PASS.

- [ ] **Step 4: Run Helvoca Fast Gate**

```bash
bash scripts/ci/fast-gate.sh <base-sha>
```

Expected: PASS.

- [ ] **Step 5: Run the existing full GitHub CI on the exact final head**

Required evidence:
- Fast Gate green;
- Golden Journey green;
- full backend suite green;
- JaCoCo differential gate green;
- browser E2E green;
- exact final head SHA recorded.

- [ ] **Step 6: Adversarial self-review**

Verify:
- no `main` mutation;
- no production deployment;
- no real external effects;
- no existing Helvoca gate was removed or weakened;
- low-risk path remains light;
- high-risk path explicitly expands checks;
- stale evidence cannot satisfy the Definition of Done.

- [ ] **Step 7: Create/update Draft PR**

Keep the PR DRAFT. Include the exact final CI run and limitations:
- portable bundle implemented;
- Helvoca first-adopter validation complete;
- standalone repository creation and fresh-agent skill pressure certification remain separate follow-up operations unless available in the current tool environment.

- [ ] **Step 8: Final commit if documentation changed after CI, then re-run certification**

Any post-certification code/config change invalidates the prior run. Documentation-only changes that alter the engineering contract also require the relevant contract tests and final CI according to repository policy.
