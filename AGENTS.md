# Helvoca development guardrails

These rules are mandatory for implementation work in this repository.

## MANDATORY FOR EVERY CHAT/AGENT: QA evidence before DONE

**This is a repository contract for every ChatGPT session, coding agent and human contributor that reads or changes Helvoca, regardless of model, conversation history, prompt, task size or provider.** Before editing a single file, read this `AGENTS.md`, `docs/engineering/QA_POLICY.md`, `docs/engineering/invariants.md`, the First-Pass Engineering skill and the relevant branch/PR/CI checkpoint. Never substitute recollections from a prior chat for current repository evidence. If you cannot inspect required instructions or evidence, explicitly report the limitation and **do not certify DONE**.

**Non-negotiable quality sequence:**
1. Determine the exact changed/affected scope, risk LOW/MEDIUM/HIGH, behavioral acceptance criteria, important failures, system invariants and test-level matrix **before implementation**. The agent chooses tests and owns their execution; the user is not QA.
2. For a reproducible defect, add a meaningful regression test and record an observed RED result for the intended defect before the fix, when technically feasible. Then implement the smallest safe correction and verify GREEN. State a technical reason if RED is genuinely infeasible.
3. Verify **100% of applicable changed/affected quality dimensions**, not merely "100% of tests passed": Java differential line/branch/method metrics, instrumented frontend statements/branches/functions, applicable Python/script executable coverage with measured metrics when available, 100% mapped meaningful UI interactions, real PostgreSQL persistence/tenant/invariant outcomes, and provider/integration/timeout/retry/idempotency/security/Golden Journey evidence where required. Document every technical N/A with equivalent behavioral proof, not a convenience waiver.
4. Run the Fast Gate and applicable focused tests while iterating, then the **mandatory applicable Full Gate** and exact-HEAD CI before declaring completion. Do not disable, bypass, skip, weaken, mock away or lower an existing test, security guard or threshold to force green. A failure must be resolved or explicitly reported as a blocker.
5. For external integrations (Mercado Pago, Twilio, Gemini, etc.), distinguish `LOCAL_SIMULATION_PASS`, `PUBLIC_NETWORK_PASS`, `AUTHENTIC_PROVIDER_PASS`, `PERSISTENCE_PASS`, `PRODUCTION_VERIFIED`. Never turn local fixture tests, a green workflow, an HTTP 200 or a deploy marked live into proof of provider payment, tenant entitlement or complete commercial acceptance without **the correct real-world evidence**.
6. Verify the **final commit**, not an ancestor. Record git HEAD, exact CI run URLs/conclusions, coverage report location and numerator/denominator (or justified N/A), scope and limitations, environment/deploy SHA, invariants, test matrix and remaining blockers in the **same PR**. For V1, add a concise checkpoint to Issue #823. Any subsequent engineering-contract/code edit requires re-verification on the new HEAD.
7. If an applicable check cannot be run due to account/tool permissions, unavailable provider, missing test environment or non-certified upstream dependency, report `BLOCKED` / `IN_PROGRESS` / `NO-GO` with the single precise blocker and safe next action. **Never claim 100% coverage, completed QA or a working external provider without proof.** Request user action only after verifying it cannot be done with existing tools and permissions.

**Mandatory final handoff to a new chat:** `read AGENTS.md -> QA_POLICY.md -> Issue #823 -> working PR/checkpoint -> exact HEAD -> applicable CI/coverage -> provider/deploy evidence -> remaining gate`. This requirement is binding as repo guidance, but Markdown alone cannot force an agent that ignores repository instructions; the existing CI contract checker and protected branch checks must provide the enforceable barrier. **Never bypass branch/release protection or trigger production merely to satisfy this rule.**


## MANDATORY ANTI-REDUNDANCY AUDIT BEFORE STATUS OR NEXT WORK (EVERY NEW CHAT)

**Applies to every ChatGPT conversation, agent, and human handoff** when the owner asks "qué tenemos", "qué falta", "continúa", "termina", "cuánto queda", to plan or modify V1, or to authorize release. An old chat summary, an old TODO, and even an outdated master-plan table are **not sufficient proof** that a task is still pending.

**Required evidence-delta protocol, BEFORE answering or coding:**

1. **Read authoritative state first, not just the latest message:** this \`AGENTS.md\`, \`docs/PLAN_MAESTRO_CIERRE_COMERCIAL.md\` (its *latest controlling checkpoint*, not older historical sections), Issue #823 (especially recent checkpoints and previously closed C0–C7 gates), the existing PR #846/current working PR, exact branch HEAD and its terminal CI. Inspect code, prior PRs/runs and affected runbooks for any alleged missing capability. Read connected Railway/provider/backup resource metadata safely when the claimed gap depends on the live environment. Check evidence timestamps and the *identity of the environment and tenant* before concluding.
2. **Build a private evidence-delta ledger for each relevant C0–C7 gate:** \`DELIVERED_AND_VERIFIED\` (name the existing proof and scope), \`DONE_OFFLINE_NOT_DEPLOYED\` (implemented and QA-certified but not live), \`EXTERNAL_ACCEPTANCE_PENDING\` (specific authentic provider/tenant/production outcome not proved), or \`NOT_VERIFIED\` (evidence inaccessible or outdated, **never** "absent"). A gate may have both completed substeps and one genuinely open external acceptance criterion. This ledger is an analysis aid, **not** a new master plan or replacement for Issue #823.
3. **A blocker is admissible only with a verifiable evidence-delta:** cite the **exact unmet acceptance criterion**, the strongest existing related proof (commit, CI run, restore/test attestation, provider readback, deployed SHA, or tenant state), **why that proof does not close the precise new criterion**, and the single smallest next action. If those four elements cannot be established, mark \`NOT_VERIFIED\` and investigate; do **not** announce that feature, account, test, backup, or integration is missing.
4. **Do not re-run historical success as if it were missing.** A completed Drive-to-PostgreSQL18 encrypted backup restore, an authentic approved Mercado Pago TEST invoice, or the historical consented Twilio/Gemini call stays \`DELIVERED_AND_VERIFIED\` **for the scope it actually proved**. A fresh release-window native snapshot recovery, signed Mercado Pago webhook to deployed Java plus real PostgreSQL, or call terminality for the *new authorized tenant* may be different acceptance gaps: describe **only that narrower delta**, never ask to recreate the entire integration or repeat the historical action.
5. **Do not promote optional improvements to mandatory V1 blockers.** New workers, a recurring SaaS invoice discovery scheduler, extra backup automation, duplicate platforms, extra numbers, more providers, additional phases or higher-cost infrastructure are not required merely because they might be useful. The only obligatory C0–C7 requirements come from the approved V1 scope; introduce a new mandatory gate only with an actual owner-approved scope change or a demonstrated P0 security/correctness failure. Do not redefine a marketable assisted web pilot as the same milestone as fully automated commercial V1: preserve the distinction \`PILOTO_ASISTIDO_VENDIBLE\` versus \`HELVOCA_V1_COMERCIAL=TERMINADA\`.
6. **Never equate unlike proof:** code present ≠ deployed; 14/14 CI green ≠ authentic provider delivery; a local mock invoice ≠ paid invoice; valid historical DR ≠ continuously measured RPO/RTO; service online ≠ candidate PR deployed; pilot switch OFF ≠ universal STOP for unmanaged tenants; \`NEW\` prospect ≠ accepted pilot. Conversely, never downgrade a previously proven bounded result merely because a different downstream criterion remains open.
7. **Before saying "what remains", do a contradiction pass:** search the plan, Issue #823, PR/checkpoints, current code and available service state for counterevidence to each proposed pending item. If proof exists, REMOVE it from the open-task list, preserve it as completed, or rewrite it strictly as an unverified *additional* criterion. If the owner identifies a mistaken claim, correct it publicly, update the existing PR/Issue checkpoint once, and do not repeat it in another chat.
8. **Required user-facing summary:** separate **YA HECHO Y EVIDENCIADO**, **HECHO EN RAMA / AÚN NO DESPLEGADO**, **ÚNICAS BRECHAS REALES DE ACEPTACIÓN**, and **SIGUIENTE ACCIÓN CONCRETA**. Link evidence where possible. Never report invented overall percentage or treat deferred optional polish as release-blocking. Keep the result concise and grounded in the latest source; ask the owner only for a *proven* indispensable permission, not for discovery or repetitive QA.

**This is enforced as repository policy integrity by \`scripts/ci/check-qa-governance.py\` in the Fast Gate and protected by \`EngineeringGuardrailsContractTest\`; such checks prove the rule remains in the repository, not that a future AI has read or obeyed it.** A new agent without repository access must say \`NOT_VERIFIED\` and cannot truthfully claim a repo-wide audit. This policy provides no authority to merge PR #846, deploy to Railway, write to production, use live providers, spend money, or contact customers.

## V1 commercial completion: single controlling plan

When the user asks to continue, finish, commercialize, or report remaining work on Helvoca V1, start from **`docs/PLAN_MAESTRO_CIERRE_COMERCIAL.md`** and **[Issue #823](https://github.com/Nicricht/helvoca/issues/823)**. They alone track the final product scope, gates C0–C7, evidence and completion. This adds no permission to merge, deploy, migrate, buy numbers, contact paying providers, place live calls, charge customers, or expose secrets. Reuse valid CI/restore/provider evidence; do not restart completed ten-phase programs, reimplement existing voice/billing, or invent more project-wide phases. Before each task identify a Cn gate, actual missing evidence, exact acceptance criterion, current HEAD and risk. Maintain a dated checkpoint in #823, and request only specific indispensable operator actions. `AGENTS.md` security/QA/safety rules always still apply. Optional integrations are explicitly excluded from the commercial V1 contract unless the owner expands scope.

Helvoca follows **First-Pass Engineering**: understand the complete impact before coding, implement the smallest coherent solution, verify the exact final commit, and preserve durable state so interrupted work can resume safely. The user is not the QA system.

Before implementation, read `software-factory/skill/first-pass-engineering/SKILL.md` when it is present so a fresh conversation does not depend on prior chat memory.

## Verify before asking the owner (mandatory for every ChatGPT/agent session)

**First verify, then ask. The owner is not the discovery, configuration, or QA system.** Before requesting any user action, inspect the connected repository, master Issue #823, current PRs/branches, previous results, relevant documentation, provider/service configuration that available tools can read, and existing resources (accounts, test users, secrets **metadata**, deployment services, environment variables, workflows, tests, backups and permissions) as applicable. Do **not** ask the owner to create, reconfigure, purchase, copy, rotate, screenshot, or rerun an item that may already exist without first checking the available evidence.

Follow this sequence on **every** task, including a new ChatGPT conversation:

1. **Recover context**: read this `AGENTS.md`, the master C0–C7 Issue #823 for V1, the relevant PR/checkpoint, exact HEAD, prior CI and runbook before making a proposal. Honor already completed work and earlier decisions.
2. **Inventory before intervention**: use existing connected tools for read-only inspection and safe, narrowly scoped verification when authorized and appropriate. Check resource existence, environment, ownership, and state **without exposing credential values**. Example: if an Actions secret is already shown to exist, do not instruct the owner to create it again; if the connector cannot read its value, use a safe, non-mutating diagnostic where permitted. Do not infer a secret's value, validity or permissions from its name alone.
3. **Do the technical work autonomously**: diagnose, implement, add regression tests, run relevant CI, correct failures, and document a verified result; do not offload code edits, command execution, screenshots or repetitive UI navigation to the owner simply for convenience.
4. **Escalate only a proven blocker**: ask the owner for **one precise necessary action** only if the required authority, identity confirmation, private credential operation, cost approval, or inaccessible UI cannot be completed with available permissions and safe methods. Explain what was checked, the exact blocker, and why that action is unavoidable. Do not make speculative or stacked requests.
5. **No duplicate work or expenses**: reuse valid verification for the same commit/environment, existing resources and provider test accounts. Only repeat work when a specific changed condition, expired evidence, reproducible issue or new acceptance gate requires it. Keep spending, live calls, payments, production writes, merges and migrations gated by their existing explicit approvals.
6. **Document the outcome where the next agent will look**: update the same PR, affected runbook and Issue #823 (for V1) with found existing resources (safe metadata only), what was verified, what was not, exact SHA/CI, risks, blockers and the next action. **Never publish passwords, tokens, private keys, raw identity responses or personal data.** If a connected tool lacks access, mark `UNVERIFIED`, not `ABSENT`.
7. **Own and correct mistaken requests**: if a new check shows an earlier instruction to the owner was unnecessary, withdraw that instruction, reuse the existing resource, repair the implementation/verification if needed, and document the root cause so nobody repeats the mistake.

This rule supplements the mandatory completion-documentation policy; it **never** authorizes spending, calling external customers, real payment transactions, production migration, deployment or a merge into `main`. If the user has explicitly restricted external actions, those restrictions still apply.

## Mandatory documentation on every meaningful completed work item

**Completion without durable documentation is NOT completion.** Before declaring a feature, bug fix, test suite, integration, audit, configuration change, or C0–C7 milestone finished, follow [`docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md`](docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md). The implementing agent is responsible; never make the user reconstruct prior work.

1. **Before starting**, read the applicable existing documentation, issue/PR checkpoint, exact branch HEAD and valid CI. Find and reuse prior implementation, decisions and evidence; do not repeat a task/test/provider side effect merely because the conversation has changed.
2. **During multi-step or interrupted work**, update the same PR's resume checkpoint with what is actually done, what is pending, any blocker and the single next action. In-progress, attempted, locally tested, CI-approved and production-certified are different states.
3. **After each meaningful finished block, in the SAME working PR**, update the affected feature/runbook/architecture/operational documentation when behavior or procedures changed. Record **what and why**, scope and paths, acceptance criteria, exact branch/commit SHA, PR/Issue URLs, CI run URLs and actual outcomes, environment (offline/TEST/production), outstanding risks, decisions, explicitly unperformed external actions and the **next precise step**. Link to existing evidence rather than duplicating documents or rerunning successful tests.
4. **Keep one status ledger**: for commercial Helvoca V1, update the existing [master Issue #823](https://github.com/Nicricht/helvoca/issues/823) under the affected C0–C7 gate and maintain its linked master plan. Do not invent parallel master plans, extra phases or unlinked status files. Other work uses its existing issue/PR.
5. **Only report DONE when documentation and verification are both current for the exact final commit**. If any files change after a CI success, say the earlier verification is stale and recertify the affected scope. If production/live-provider checks remain unavailable, document `PENDING`/`NO-GO`, never `PASS`.
6. **Protect secrets and customers**: never put API keys, tokens, dumps, passwords, decrypted personal data, sensitive logs or private evidence in Git, GitHub issues, PRs or chat. Record only a safe proof reference or redacted outcome.
7. **Handoff standard**: a fresh agent must be able to reconstruct `repo → master gate → branch/PR → exact HEAD → completed changes → exact CI/evidence → blockers/holds → next safe action` using GitHub alone. If it cannot, the task is not ready for handoff.

A small one-shot LOW-risk change may use a concise completion record in the existing PR; do not create bureaucracy or duplicate files for trivial edits. The documentation obligation itself always applies.

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
