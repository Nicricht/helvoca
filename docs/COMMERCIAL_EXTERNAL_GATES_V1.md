# Commercial External Gates V1

## Current boundary

Automated application certification is not the same as permission to activate external providers.

This document tracks gates that depend on a human, an external provider account, repository administration, or tenant-specific launch approval.

**Checkpoint evidence-first (2026-10-18):** read the controlling checkpoint in docs/PLAN_MAESTRO_CIERRE_COMERCIAL.md, Issue #823 and PR #846 before declaring any new work. The last candidate before this documentary edit, **1671d9c**, has [14/14 completed-success workflows](https://github.com/Nicricht/helvoca/actions/runs/39108350614). It remains DRAFT/unmerged. After this document commit, its CI is historical, not certification of the new HEAD.

## Gate A — Human voice / carrier certification

Status: **CONTROLLED HUMAN CALL COMPLETE / FULL TENANT-SPECIFIC CARRIER MATRIX STILL REQUIRED WHEN VOICE IS IN SCOPE**

A controlled human call was completed on September 27, 2026 using Gemini Live, Sulafat and Hybrid VAD. The repository records Sulafat consistency, acceptable perceived response latency, a complete farewell and clean `end_call`; the human listening final also selected Sulafat as the primary youthful female production voice.

That evidence closes voice selection and the basic controlled-call gate. It does **not** by itself prove that every carrier/interaction condition below was exercised and recorded for the specific first-customer tenant. If real voice is included in that pilot, the authorized tenant-specific channel certification must confirm:

- Sulafat remains the same female voice throughout;
- Chilean Spanish sounds natural for the target customer;
- pace is natural and concise;
- barge-in stops current playback;
- two quick interruptions do not resume stale audio;
- known data is not asked again;
- corrections replace previous values;
- farewell is complete;
- carrier hangup occurs after the last audible word;
- no residual audio occurs after hangup.

Do not place another real call merely to satisfy CI. Tenant-specific real-carrier confirmation is allowed only inside an explicitly authorized launch window with the pilot safety cage active.

## Gate B — Mercado Pago SaaS sandbox provider round-trip

Status: **GENUINE TEST APPROVED INVOICE + REAL JAVA/POSTGRESQL18 RECOVERY VERIFIED; ORGANIC SIGNED PROVIDER NOTIFICATION NOT VERIFIED**

**Already completed, not to be recreated:**

- [Original provider TEST checkout #36360314081](https://github.com/Nicricht/helvoca/actions/runs/36360314081) and [approved/accredited invoice #36360749617](https://github.com/Nicricht/helvoca/actions/runs/36360749617): real seller/buyer TEST, recurring preapproval, authorized invoice. The [early bridge #36360833820](https://github.com/Nicricht/helvoca/actions/runs/36360833820) used mocked repositories/JDBC **only at that historical stage**, and is superseded as evidence of persistence by later real PostgreSQL results.
- [One signed operator-generated TEST recovery](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6240000990): the existing approved provider invoice was reconciled exactly once through the actual Helvoca Java controller and isolated Railway PostgreSQL18. Business and subscription became **ACTIVE**, BASIC/EMPRENDE CLP 24,990, **4 entitlements PASS**. The request was explicitly labelled OPERATOR_GENERATED_TEST_RECOVERY, **not organic Mercado Pago delivery**.
- [One signed simulated payment webhook](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6233839072) returned HTTP 200 at the TEST Java endpoint. This proved simulated transport, **not SaaS application** or an organic provider callback.
- Existing bounded invoice-discovery and SaaS recovery worker, >20-tenant pagination, PostgreSQL advisory locks, tenant isolation, duplicate/rejected/reapproved transitions are QA-certified **offline**; the previously deployed TEST worker remained **OFF**. A newer TEST change is **STAGED** but not authorized/applied. [Exact candidate CI](https://github.com/Nicricht/helvoca/actions/runs/39108350614).

**Distinct outstanding provider acceptance:** prove one **organic signed subscription_authorized_payment** genuinely emitted by Mercado Pago that reaches deployed Helvoca Java and updates real isolated PostgreSQL18, including real-provider callback correlation. Organic subscription_preapproval and end-to-end provider-organic replay/renewal behavior remain **NOT_VERIFIED**, although corresponding local/idempotency logic is already tested. Provider TEST notification mode for planless subscriptions is ambiguous in official documentation; the [diagnosis and drafted support request](https://github.com/Nicricht/helvoca/pull/846#issuecomment-6241952771) are already prepared and **not sent**. Do not redo checkout, payment, manual recovery or worker development to close a different gate.

**Scope boundary:** organic provider billing certification is required before selling **automated SaaS billing as LIVE-certified**, not before offering the documented **web/simulator-assisted pilot** that explicitly excludes automated billing and other unverified channels. No LIVE payment, deployment, provider settings change or third-party contact is authorized.

## Gate C — GitHub main protection

Status: **COMPLETE / ENFORCED**

Repository ruleset `Protect main` (ID `24088016`) is active for the default branch and was verified through the GitHub rulesets API.

Enforced controls:

- pull request required before merge;
- required GitHub Actions check `fast-gate`;
- required GitHub Actions check `test`;
- strict up-to-date required-status-check policy;
- non-fast-forward / force pushes blocked;
- branch deletion blocked;
- no bypass for the current user.

The enforcement was also exercised during release convergence: PR #603 was rejected when its successful checks belonged to a branch that was behind the current `main`. The branch must be updated and the required checks must pass again before merge.

**Candidate Full Gate protection (2026-10-18):** existing required test job on **unmerged PR #846** now depends on Fast Gate, Golden Journey, system-integration-e2e, Home/Inventory coverage and V99-on-V103 legacy-binary compatibility, and fails on any non-success applicable upstream result (including skipped/cancelled). [CI #39108350614](https://github.com/Nicricht/helvoca/actions/runs/39108350614) completed 14/14 SUCCESS, 2,349 Java, 620 Playwright, and JaCoCo differential 1,308/1,308 lines, 1,003/1,003 branches, 315/315 methods. This is **not global 100% coverage**, and the PR is not on main.

## Gate D — First real customer operational readiness

Status: **SAFETY CAGE IMPLEMENTED / CUSTOMER-SPECIFIC DATA PENDING**

Main already contains Controlled Real Business Pilot V1 with:

- **pilot-managed-route flag**, `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false`, **not a universal kill switch**: tenants with no `pilot_launch_control` row return `NOT_PILOT_MANAGED` / `allowed=true` even if the flag is false, as proved by `ControlledPilotExternalEffectGuardTest.nonPilotTenantPreservesExistingBehaviorEvenWhenGlobalPilotSwitchIsOff`;
- **pilot tenant** `PAUSED` control, which must be verified to have a real `pilot_launch_control` row and actual denial on each relevant guarded route;
- preflight GO/NO-GO;
- guard before voice, Meta WhatsApp and external payment-intent creation;
- Launch Cage operational visibility;
- reconciliation and anomaly checks.

Customer-specific activation still requires:

- completed `FIRST_CUSTOMER_ONBOARDING_FORM.md`;
- named owner on both sides;
- approved services/prices/hours/FAQ/policies;
- approved channel/capability scope;
- explicit success metric;
- human handoff destination when sold;
- completed external channel certification for each enabled channel;
- rollback/support owner;
- no unresolved P0.

**Already implemented and QA-certified offline:** pilot-scoped admission guards, Twilio voice WebSocket/active-session STOP monitor, authorized hangup request + local media/AI closure, WhatsApp and external payment-recovery guards ([prior act](https://github.com/Nicricht/helvoca/pull/846#issuecomment-6218802189)). A production read-only PG18 snapshot on 2026-10-16 found **10 tenants, all unmanaged, zero pilots and zero pending/failed/running jobs at that time** ([act](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6216392464)); that historical sample cannot certify current live carrier terminality.

**High-risk STOP acceptance still open for an actual authorized pilot:** unmanaged tenants, unguarded routes, in-flight jobs, provider effects and already-sent payment attempts need independent containment, incident-owner approval and terminal verification. The pilot flag alone is NOT evidence that all effects ceased. If the scope of STOP cannot be proved, keep P0 / NO-GO and do not reactivate. See `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md` for the exact stop decision tree and proof checklist.

Use `FIRST_CUSTOMER_ROLLBACK_SUPPORT.md` for incidents and reactivation.

## Commercial activation rule

Do not call the pilot CUSTOMER merely because software tests are green.

Only provider gates **applicable to the channels/capabilities actually sold** are required for that tenant. **PILOTO_ASISTIDO_VENDIBLE** can be offered with web/simulator, FAQ and BOOKING without activating live voice, WhatsApp, automated SaaS billing or merchant collection. Offering that narrow pilot is not deploying the candidate, activating a tenant, receiving payment, or declaring **HELVOCA_V1_COMERCIAL=TERMINADA**. Do not send sales drafts, merge or activate external effects without specific authorization.
