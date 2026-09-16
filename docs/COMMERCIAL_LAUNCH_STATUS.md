# Helvoca — Commercial Launch Status

## Decision

Helvoca can be sold now as an assisted pilot. The immediate commercial objective is to close and successfully activate the first paying customers with a controlled, measurable scope.

Engineering must support sales, not postpone them. New product work should be driven by recurring evidence from prospects and customers unless it closes a production reliability or security gap.

## Production baseline

- Stable production baseline: V42 Plans / Entitlements / Billing.
- V41 `usage_meter_event` remains the measured-usage source of truth.
- V42 commercial plans and entitlements are PostgreSQL-backed and enforced fail-closed.
- Billing providers remain adapters; they do not own plan rules, prices, usage or service eligibility.
- V42 release gate was completed on main revision `e41bdeebf9cf6413fde01a6fe815549a3f7dcb76`: PR CI green, main CI green, Railway deployed the exact SHA, Flyway advanced production from v41 to v42, Spring started and Railway accepted the configured `/actuator/health` gate.

## Ready to sell now

- business information and knowledge;
- catalog/services/products;
- bookings and availability;
- orders;
- quotes;
- leads;
- requests;
- delivery/pickup workflows;
- customer confirmation flows;
- human handoff;
- tenant policies and automation controls;
- immutable operation history;
- usage metering;
- database-backed plans, usage entitlements and concurrent-call capacity;
- public pricing and subscription commercial views;
- voice/WhatsApp shared domain behavior when those channels are configured for the tenant.

## Available in the core but activation-dependent

The following capabilities require concrete tenant/provider configuration before they can be included in a pilot promise:

- real voice provider and telephony;
- WhatsApp delivery;
- outbound messaging;
- external calendar/meeting provider;
- real payment merchant/provider.

Supporting an adapter in the codebase never means that the external provider is automatically active for every tenant.

## Commercial model for first customers

- onboarding remains assisted while the first customers establish real operating data;
- configuration is reviewed with the customer before activation;
- contracted plan, limits and pilot scope are documented explicitly;
- plan enforcement comes from V42 and measured consumption from V41;
- no external integration is presented as active until it has been tested for that tenant;
- no ROI, SLA or unlimited-channel promise is made without a written commercial commitment.

## Current acquisition pipeline

`docs/FIRST_PROSPECTS_TRACKER.csv` contains the first 20 real Providencia prospects. All unmet needs remain marked as hypotheses to validate until a business actually confirms them. Do not fake contact, interest, qualification, demos or customer status.

Pipeline:

```text
NEW -> CONTACTED -> QUALIFIED -> DEMO -> PILOT -> CUSTOMER
                               `-> CLOSED
```

Immediate operating target:

- contact real prospects every business day;
- convert conversations into a demo or a clearly recorded objection;
- close the first assisted pilot;
- configure the accepted tenant with official customer data;
- activate only the external channels explicitly included and tested for that tenant.

## Launch assets

- `/sales.html` — public sales landing;
- `/pricing.html` — public pricing;
- `docs/FIRST_SALE_TOMORROW.md` — field sales script and demo checklist;
- `docs/FIRST_SALES_SPRINT.md` — acquisition sprint and pipeline;
- `docs/FIRST_PROSPECTS_TRACKER.csv` — live prospect tracker;
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` — onboarding form after pilot acceptance.

## Exit criteria for assisted-pilot stage

Move from assisted pilots to broader self-service acquisition only after:

- plan/entitlement enforcement has produced stable real-customer usage;
- billing/provisioning behavior has been operationally tested with authorized merchant accounts where applicable;
- onboarding no longer requires routine engineering intervention;
- external channel activation has repeatable runbooks;
- the first customers have produced enough real usage, objections and support data to shape the next product decisions.
