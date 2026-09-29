# Commercial Release Readiness — First Business V1

Date: 2026-09-29

## Decision boundary

This checkpoint certifies the current RecepVoz Release Candidate for a **controlled assisted first-business pilot**. It does not authorize merge, deploy, provider activation, real calls, WhatsApp delivery, charging, phone provisioning, or production mutation.

Technical readiness and permission to activate are separate decisions.

## Certified software baseline

The commercial recertification now targets the current reviewed and deployed application baseline:

- production application baseline: `main@8ec4d007c29a5e938140828da8bd6f34c152bbaf`;
- `helvoca/full-verification`: SUCCESS on that baseline;
- Railway deployment: SUCCESS for that exact SHA;
- recent production HTTP sample: 301 requests with 0 responses in the 5xx class;
- current commercial certification workflow derives its comparison and differential-coverage base from live `origin/main`, rather than historical hardcoded SHAs.

The reusable safe gate runs only on dedicated `cert/commercial-readiness-*` branches and still performs the focused launch-cage contracts, commercial rehearsal, complete backend suite, JavaScript validation and browser E2E before certification is accepted.

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

Production configuration review on 2026-09-29 confirms the automated checkout must remain fail-closed: Mercado Pago enablement/access-token variables are present, while the webhook secret and back URL are not currently present in the Railway production variable set. No credentials were read and no provider transaction was attempted.

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
