# Commercial External Gates V1

## Current boundary

Automated application certification is not the same as permission to activate external providers.

This document tracks gates that depend on a human, an external provider account, repository administration, or tenant-specific launch approval.

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

Status: **REAL MERCADO PAGO TEST CHECKOUT + APPROVED INVOICE READBACK VERIFIED / PROVIDER ROUND-TRIP PENDING (AUTHENTIC WEBHOOK TO HELVOCA JAVA + REAL POSTGRESQL)**

Historical **genuine TEST evidence, do not spend or repeat without a new acceptance gap**:

- [SaaS Billing Sandbox One-Shot #36360314081](https://github.com/Nicricht/helvoca/actions/runs/36360314081), terminal success: authenticated provider TEST preapproval created and read back as `pending`, using the previously verified test buyer.
- [Mercado Pago Subscription Readback #36360749617](https://github.com/Nicricht/helvoca/actions/runs/36360749617), terminal success: provider TEST invoice with nested `payment.status=approved`, distinct from invoice lifecycle `processed`.
- [SaaS Billing Sandbox Invoice Bridge #36360833820](https://github.com/Nicricht/helvoca/actions/runs/36360833820), terminal success: approved invoice consumed into simulated billing ACTIVE/entitlements/idempotency through **mock repositories/JDBC**, NOT real PostgreSQL persistence.

The separate TEST seller credential and TEST buyer already exist; do **not** ask the owner to recreate them. See `docs/SAAS_BILLING_SANDBOX_PROVIDER_GATE.md` for scope and failed example-buyer rerun. Real **signed provider** `subscription_preapproval` and `subscription_authorized_payment` delivery to the actual Helvoca **Java** webhook, persistence in a **disposable isolated PostgreSQL**, duplicate replay and rejected→approved renewal, and resulting entitlements remain **UNVERIFIED**. A successful HTTP 200 at a standalone simulator or a mock database does not satisfy this gate. No merchant LIVE charge or production provider activation is authorized.

Remaining acceptance matrix (historical proofs above must be reused):

1. TEST credentials only;
2. non-production tenant;
3. Mercado Pago `@testuser.com` buyer;
4. EMPRENDE or NEGOCIO;
5. recurring checkout created in historical TEST provider run (**PASS historical**);
6. test buyer completed the historical provider flow producing an approved invoice (**PASS historical**);
7. signed `subscription_preapproval` accepted;
8. signed `subscription_authorized_payment` accepted;
9. duplicate invoice notification produces no second state transition;
10. rejected renewal produces PAST_DUE + grace;
11. subsequent approved invoice returns ACTIVE.

Live credentials are not part of this gate.

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

**High-risk STOP coverage:** unmanaged tenants, unguarded routes, in-flight jobs, provider effects and already-sent payment attempts need independent containment, incident-owner approval and terminal verification. The pilot flag alone is NOT evidence that all effects ceased. If the scope of STOP cannot be proved, keep P0 / NO-GO and do not reactivate. See `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md` for the exact stop decision tree and proof checklist.

Use `FIRST_CUSTOMER_ROLLBACK_SUPPORT.md` for incidents and reactivation.

## Commercial activation rule

Do not call the pilot CUSTOMER merely because software tests are green.

A real activation requires all applicable external gates above to be completed with evidence for that tenant.
