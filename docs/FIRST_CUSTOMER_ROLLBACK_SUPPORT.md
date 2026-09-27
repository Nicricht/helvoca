# First Customer Rollback & Support Runbook

## Purpose

This runbook is the operational safety net for the first controlled RecepVoz customer. It does not authorize provider activation, deployment, real calls, WhatsApp delivery, or charging by itself.

Use it together with:

- `docs/CONTROLLED_REAL_BUSINESS_PILOT_V1.md`
- `docs/FIRST_CUSTOMER_OPERATION.md`
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`

## Before activation

The pilot must have all of the following written down before any external effect is enabled:

- business/tenant id;
- RecepVoz pilot owner;
- customer-side owner;
- start and planned review/end time;
- approved channel(s);
- approved capabilities;
- explicit exclusions;
- success metric;
- human escalation destination;
- rollback decision owner;
- support contact path.

Do not invent missing names or contact details. A blank owner/contact is a NO-GO.

## Immediate stop controls

There are two independent stop controls:

1. **Tenant kill switch:** put the pilot tenant in `PAUSED`.
2. **Global kill switch:** set `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false`.

Use the tenant switch for an isolated customer issue. Use the global switch immediately when tenant scope is uncertain, there is evidence of cross-tenant behavior, or more than one external-effect path may be unsafe.

Stopping traffic comes before debugging.

## P0 incidents

Treat as P0 and stop external effects immediately when any of these occurs:

- action executed for the wrong tenant/customer;
- duplicate critical mutation that is not safely idempotent;
- unauthorized call, WhatsApp delivery, payment action, order, booking, cancellation or handoff;
- wrong price/plan/entitlement applied;
- call fails to terminate or emits audio after carrier hangup;
- credentials/secrets appear in trace, logs, UI or support artifacts;
- reconciliation reports a critical unresolved inconsistency;
- external provider behavior cannot be correlated to a known operation/call.

## P1 incidents

Pause the tenant unless the pilot owner explicitly determines the certified scope remains safe:

- repeated tool failures;
- incorrect FAQ/service answer caused by configured business data;
- severe latency that prevents completion of the contracted journey;
- handoff repeatedly unavailable;
- usage/entitlement counters materially inconsistent;
- dashboard result cannot be reconciled with persisted actions.

## Evidence to capture

Before changing or deleting anything, capture identifiers, not raw secrets:

- business id;
- call id;
- provider call id / stream id when applicable;
- operation id;
- booking/order/request id;
- job/correlation id;
- provider name and model;
- timestamp and timezone;
- error code;
- Journey Trace identifier;
- current pilot status and traffic mode.

Use Call Detail only when conversation content is required. Prefer anonymized Conversation Replay when a case must be shared.

Never paste access tokens, authorization headers, webhook secrets, payment payloads, or full customer transcripts into general support notes.

## Diagnostic order

1. Confirm tenant and pilot status.
2. Confirm traffic mode.
3. Open Journey Trace using the strongest known identifier.
4. Locate the first failing stage, not merely the final symptom.
5. Open Call Detail or domain entity only when needed.
6. Check retries, durable jobs, reconciliation and provider state.
7. Reproduce with simulator/certification harness before considering reactivation.

## Rollback rules

Rollback means returning the pilot to a known safe state, not blindly reversing database rows.

Preferred order:

1. PAUSE tenant.
2. Disable global external effects if scope is uncertain.
3. Stop pending outbound/durable work using supported domain controls.
4. Correct configuration through supported APIs/admin flows.
5. Reverse business mutations through their domain operation when reversal is supported.
6. Never repair production with ad-hoc SQL unless an explicitly reviewed incident procedure authorizes it.
7. Fix code only on a branch with tests reproducing the incident.
8. Require CI before any redeploy or reactivation.
9. Re-run the affected Golden Journey/certification pack.
10. Reactivate one tenant only after the incident owner records the reason for GO.

## Reactivation checklist

- [ ] root cause identified;
- [ ] unsafe external effects remain blocked during verification;
- [ ] regression test added when the defect is reproducible in code;
- [ ] Fast Gate green;
- [ ] Full Gate green;
- [ ] relevant Golden Journey/certification green;
- [ ] reconciliation shows no unresolved critical anomaly;
- [ ] customer-facing data/config reviewed if it contributed;
- [ ] pilot owner approves reactivation;
- [ ] tenant is moved from PAUSED only after the checks above;
- [ ] global switch is enabled only for an explicitly authorized pilot window.

## Support intake template

Record:

- Tenant:
- Reporter:
- Time observed:
- Expected behavior:
- Actual behavior:
- External effect involved:
- Call/operation/correlation id:
- Severity: P0 / P1 / P2
- Tenant paused: yes/no
- Global switch disabled: yes/no
- Journey Trace checked: yes/no
- Root cause:
- Corrective action:
- Verification evidence:
- Reactivation decision:
- Owner:

## First-pilot operating cadence

For the first customer:

- review errors and unresolved requests daily;
- review usage/minutes and external-provider failures daily;
- review reconciliation anomalies before every expansion of scope;
- do not add a second real tenant while a P0 remains unresolved;
- after the pilot window, record continue / adjust / stop and the evidence behind the decision.
