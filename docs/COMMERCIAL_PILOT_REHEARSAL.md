# Commercial Pilot Rehearsal V1

## Goal

Prove, without real provider delivery or production mutation, that the current RecepVoz V1 can behave like an assisted first-customer pilot from setup through value generation and operational review.

This is a release certification artifact, not a new product subsystem.

## Safety boundary

The rehearsal must never:

- place a real phone call;
- send a real WhatsApp message;
- perform a real payment;
- enable LIVE provider credentials;
- mutate a production tenant or production inventory.

Mercado Pago provider behavior is certified separately with TEST users and TEST credentials. This rehearsal validates the product-side contracts and the existing sandbox safety boundary.

## Rehearsed journey

The gate covers the existing contracts for:

1. reproducible demo tenant bootstrap;
2. commercial Golden Journey;
3. launch readiness / GO-NO-GO;
4. booking availability, create, reschedule and confirmation behavior;
5. duplicated/repeated-operation protections;
6. SaaS billing state transitions and entitlements;
7. Mercado Pago webhook signature/routing contracts;
8. multi-tenant / PostgreSQL RLS isolation;
9. journey trace and conversation replay;
10. voice lifecycle, closing and post-hangup behavior;
11. owner/pilot metrics;
12. existing Pilot E2E certification.

The exhaustive mode then runs:

- every backend test under Maven with JaCoCo;
- differential Java line/branch coverage;
- JavaScript syntax validation across static console assets and E2E files;
- the complete Playwright suite.

## Command

Focused rehearsal:

```bash
bash scripts/ci/commercial-pilot-rehearsal.sh
```

Exhaustive rehearsal:

```bash
COMMERCIAL_PILOT_REHEARSAL_FULL=true \
  bash scripts/ci/commercial-pilot-rehearsal.sh
```

The dedicated GitHub Actions workflow `Commercial Pilot Rehearsal V1` always runs exhaustive mode.

## PASS contract

The rehearsal is PASS only when the same SHA satisfies all of the following:

- focused commercial contracts PASS;
- existing Pilot E2E certification PASS;
- complete backend suite PASS;
- JaCoCo execution PASS;
- differential coverage threshold PASS;
- JavaScript syntax checks PASS;
- complete Playwright suite PASS;
- workflow exits with `PILOT REHEARSAL: PASS`.

Any existing test failure makes the rehearsal FAIL even if it appears unrelated to the files added here.

## Real-world boundary

A PASS does not claim to replace:

- unpredictable behavior of a real customer;
- a real PSTN carrier/network path;
- customer-specific provider credentials and phone number;
- production webhook delivery from an external provider;
- customer-approved business facts/policies;
- real money collection.

Those remain activation checks for the first live pilot.

## Evidence

Evidence is recorded only after a fresh run on the final PR HEAD. Until then, this section intentionally remains pending.

- Certified SHA: PENDING
- Commercial Pilot Rehearsal workflow: PENDING
- Standard RecepVoz CI: PENDING
- Result: PENDING
