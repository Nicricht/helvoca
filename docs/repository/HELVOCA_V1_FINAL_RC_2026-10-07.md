# Helvoca V1 Final Release Candidate

**Date:** 2026-10-07  
**RC branch:** `release/helvoca-v1-final-rc-20261007`  
**Base main:** `1387cf428da5d40640f823a7b84482865936131b`

## Scope

This RC deliberately freezes product scope.

It adds no new runtime feature. It contains only:

1. regression coverage proving malformed/premature booking confirmation identifiers fail closed;
2. regression coverage proving the real-call certification cleanup refuses an unexpected booking state;
3. README drift correction so already-implemented V1 capabilities are no longer listed as future milestones.

## V1 runtime baseline

The audited current product already contains:

- React product surfaces;
- Spring Boot API;
- PostgreSQL/Flyway tenant data model;
- authentication, roles, permissions and tenant isolation;
- business configuration, schedules and exceptions;
- customers;
- Agenda/bookings;
- orders/operations;
- inventory;
- human handoff;
- voice multi-provider stack;
- Meta WhatsApp stack;
- SaaS billing/entitlements/usage;
- onboarding/import;
- launch cage, readiness, kill switches and observability.

## External/operational gates

These are not missing historical code:

- tenant-specific real voice certification, when voice is sold;
- tenant-specific real Meta WhatsApp certification, when WhatsApp is sold;
- Mercado Pago TEST provider round-trip, when automatic SaaS billing is required;
- first-customer onboarding/ownership/scope/success evidence and Launch Cage GO.

No real call, WhatsApp delivery or payment-provider transaction is performed by this RC certification.

## Release rule

This branch may merge only after the repository's complete CI gates are green on its exact HEAD.

After merge:

- exact-main CI must be green;
- Railway must deploy the exact merged main SHA successfully;
- API and PostgreSQL must be online;
- no pending Railway work may remain.

The V1 software release is then considered technically closed. Remaining provider/customer gates are activation work for the sold scope, not unfinished platform development.
