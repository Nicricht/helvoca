# Commercial External Gates V1

## Current boundary

Automated application certification is not the same as permission to activate external providers.

This document tracks the remaining gates that depend on a human, an external provider account, or repository administration.

## Gate A — Human voice / carrier certification

Status: **PENDING EXTERNAL AUTHORIZATION**

The automated voice lifecycle suite proves the software contract. A human-controlled real call must still confirm:

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

Do not run this gate while real-call certification is prohibited.

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

Status: **ADMIN ACTION REQUIRED**

The connected GitHub integration does not have repository administration permission for branch-protection APIs, so this cannot be truthfully applied from the current ChatGPT connection.

Required settings for `main`:

- require pull request before merge;
- require successful `fast-gate`;
- require successful full `test` job;
- require branch to be up to date before merge when compatible with the team workflow;
- block force pushes;
- block branch deletion;
- prevent normal direct pushes except explicitly approved administrators/break-glass policy;
- keep required checks unchanged rather than bypassing them for a release.

After an administrator applies the rule, verify by attempting policy inspection and by confirming a non-green PR cannot merge.

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
