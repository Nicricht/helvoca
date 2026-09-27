# Golden Journey Commercial Release Contract

This document defines the automated commercial release contract for RecepVoz.

## Release rule

The GitHub Actions check `golden-journey-release-contract` runs the complete
`scripts/ci/pilot-e2e-certification.sh` pack on pull requests, manual CI runs,
and every push to `main`.

The `production-gate` job depends on this check, and the Full Gate also depends
on it whenever the Full Gate is scheduled.

A release candidate is not considered commercially certified when this check is
red or incomplete.

## Contract scenarios

The Golden Journey must prove the happy path end to end:

- correct greeting and business information;
- service and availability discovery;
- customer identification without re-asking closed fields;
- two-phase booking confirmation;
- idempotent replay without duplicate bookings;
- successful reschedule and confirmation lookup;
- exactly one farewell followed by session closure;
- durable transcript, summary, trace, operation events and booking state;
- tenant isolation.

It must also fail safely under the critical negative scenarios:

1. the requested slot becomes occupied after proposal but before confirmation;
2. the same confirmation is submitted concurrently twice;
3. a reschedule target becomes unavailable and the original booking must remain intact;
4. another tenant attempts to mutate a booking it does not own.

## Safety boundary

This contract is sandbox-only. It must not:

- place a real telephone call;
- send a real WhatsApp message;
- perform a real payment;
- activate a real provider;
- mutate production data.

The suite uses Testcontainers, certification calls, fake/safe provider paths,
and deferred call closure.

## Local/CI command

```bash
bash scripts/ci/pilot-e2e-certification.sh
```

The Golden Journey test is intentionally part of the pilot certification pack so
the same release contract is exercised by both targeted CI and the broader pilot
certification surface.
