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

Status: **AUTOMATED HARNESS GREEN / PROVIDER ROUND-TRIP PENDING**

PR #586 certifies the fail-closed one-shot sandbox harness. It cannot complete the provider round-trip without an authorized Mercado Pago TEST account.

Required external proof:

1. TEST credentials only;
2. non-production tenant;
3. Mercado Pago `@testuser.com` buyer;
4. EMPRENDE or NEGOCIO;
5. recurring checkout created;
6. test buyer completes provider flow;
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

- global external-effect kill switch;
- tenant PAUSED kill switch;
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

Use `FIRST_CUSTOMER_ROLLBACK_SUPPORT.md` for incidents and reactivation.

## Commercial activation rule

Do not call the pilot CUSTOMER merely because software tests are green.

A real activation requires all applicable external gates above to be completed with evidence for that tenant.
