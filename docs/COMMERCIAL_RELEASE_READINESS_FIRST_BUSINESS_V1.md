# Commercial Release Readiness — First Business V1

Date: 2026-09-29

## Decision boundary

This checkpoint certifies the current RecepVoz Release Candidate for a **controlled assisted first-business pilot**. It does not authorize merge, deploy, provider activation, real calls, WhatsApp delivery, charging, phone provisioning, or production mutation.

Technical readiness and permission to activate are separate decisions.

## Certified software baseline

- main ancestor: `8dc165d102a4157fdc6e3cd38b51b76b70b388d1`
- certified frontend / first-business RC source: `dbba4d7260c96763c13a0c56d4c1cb108d4986ca`
- RC safe CI: `36522962888` — SUCCESS
- backend: 1330 / 1330
- Golden Journey sandbox: 59 / 59
- browser E2E: 118 / 118
- production source changes in RC hardening: none

The RC source is a descendant of the current reviewed `main` baseline, not a divergent branch.

## Commercial gates

### A. Voice / carrier

**PARTIAL EXTERNAL GATE**

Repository evidence records:
- September 27 human listening final selected Sulafat;
- one controlled human call with Gemini Live + Sulafat + Hybrid VAD;
- Sulafat remained consistent;
- perceived response latency was within the defined target;
- farewell + `end_call` completed cleanly.

The repository does not contain explicit recorded human evidence for every carrier/interruption item listed in `COMMERCIAL_EXTERNAL_GATES_V1.md`. If real voice is part of the first-customer scope, complete that tenant-specific carrier matrix only inside an explicitly authorized launch window. Do not place another real call merely to satisfy CI.

### B. Mercado Pago SaaS billing

**PROVIDER TEST ROUND-TRIP PENDING**

The fail-closed sandbox harness and billing state machine are tested. The missing proof is an authorized Mercado Pago TEST-account round trip with:
- TEST credentials;
- TEST buyer;
- recurring sandbox checkout;
- signed subscription webhooks;
- duplicate invoice idempotency;
- rejected renewal -> PAST_DUE + grace;
- subsequent approved renewal -> ACTIVE.

Until that external gate passes, do not claim automated Mercado Pago recurring billing is active.

A first assisted customer may use a separately authorized manual commercial collection process documented by Helvoca. That is an operational bridge, not an automated subscription state transition.

### C. Repository protection

**COMPLETE / ENFORCED**

The existing `Protect main` ruleset requires PRs, current required checks and strict up-to-date status. Force pushes and branch deletion are blocked.

### D. First-customer launch cage

**IMPLEMENTED / CUSTOMER-SPECIFIC ACTIVATION PENDING**

The software already contains:
- global external-effect kill switch;
- tenant `PAUSED` kill switch;
- pilot launch lifecycle;
- preflight GO / NO-GO;
- guards immediately before voice, Meta WhatsApp and external payment-intent effects;
- Launch Cage dashboard visibility;
- inventory and reconciliation blockers;
- rollback/support runbook.

A technical `GO` is readiness evidence only. It does not enable providers, start the pilot or deploy code.

## Required customer-specific evidence before activation

Do not change a prospect to a real active customer until the applicable items are recorded:

- completed `FIRST_CUSTOMER_ONBOARDING_FORM.md`;
- named customer owner and RecepVoz owner;
- approved services/products, exact prices, hours, FAQ and policies;
- exact pilot capability/channel scope;
- written success metric;
- human handoff destination if offered;
- external certification for every enabled real channel;
- rollback/support owner;
- authorized way the customer pays RecepVoz;
- billing/contact data confirmed;
- terms/privacy available;
- no unresolved P0 for the sold scope;
- Launch Cage preflight GO for that tenant immediately before the authorized window.

## Launch sequence

1. Keep global external effects disabled.
2. Configure exactly one approved tenant.
3. Complete onboarding and activation evidence.
4. Rehearse the sold journey with external delivery blocked.
5. Confirm Launch Cage GO while traffic remains blocked.
6. Obtain explicit authorization for the controlled window.
7. Start the tenant pilot.
8. Enable only the external effects included in the approved scope.
9. Watch Journey Trace, Conversation Quality, jobs, inventory, billing state and reconciliation.
10. On anomaly, pause the tenant or disable the global switch immediately.
11. Do not add a second real tenant while a P0 is unresolved.
12. Record continue / adjust / stop at the end of the pilot window.

## Current ruling

**Software release readiness:** GREEN for controlled assisted-pilot preparation.

**Automatic Mercado Pago SaaS collection:** NOT YET EXTERNALLY CERTIFIED.

**Real-customer activation:** NOT AUTHORIZED BY THIS CHECKPOINT. It requires tenant-specific completed evidence and an explicit controlled launch authorization.

**Merge/deploy/external effects:** none performed by this certification.
