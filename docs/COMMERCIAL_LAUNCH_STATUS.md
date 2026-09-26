# Helvoca — Commercial Launch Status

## Decision

Helvoca can be sold now as an assisted pilot. The immediate commercial objective is to close and successfully activate the first paying customers with a controlled, measurable scope.

Engineering must support sales, not postpone them. New product work should be driven by recurring evidence from prospects and customers unless it closes a production reliability or security gap.

## Sellable MVP freeze — 2026-09-26

The sellable assisted-pilot MVP is now **feature-frozen**.

The production baseline is main revision `cb8618a4acbbf882a356e08bba8f69e6ab37de57`, which includes PR #451 secure one-time team invitations. Railway deployed that exact revision successfully, Flyway advanced production to V65, Spring started, and the configured `/actuator/health` gate passed.

Until the first paying pilot is running with real customer usage, new product work is accepted only when it is one of these:

- a production bug that blocks a real pilot;
- a security, privacy or tenant-isolation issue;
- a reliability problem demonstrated by real traffic;
- a provider/integration defect required by an already-agreed pilot scope;
- a repeated customer/prospect need supported by concrete evidence.

The following are explicitly **not** launch blockers and must not reopen the MVP by themselves:

- additional AI, telephony, messaging or payment providers;
- another CRM/dashboard/redesign;
- speculative automation;
- extra analytics not needed to operate the first pilots;
- new channels or broad self-service expansion;
- cosmetic refactors that do not remove an operational risk.

The next product milestone is not another feature. It is:

```text
REAL BUSINESS -> CONFIGURED -> GO -> LIVE PILOT -> PAYMENT -> CUSTOMER
```

## Production baseline

- Stable commercial core: V42 Plans / Entitlements / Billing.
- V41 `usage_meter_event` remains the measured-usage source of truth.
- V42 commercial plans and entitlements are PostgreSQL-backed and enforced fail-closed.
- V65 adds tenant-scoped, one-time team invitations.
- Billing providers remain adapters; they do not own plan rules, prices, usage or service eligibility.
- Current sellable deployment: `cb8618a4acbbf882a356e08bba8f69e6ab37de57`.
- Railway production status: successful deployment with `/actuator/health` passing.

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
- voice/WhatsApp shared domain behavior when those channels are configured for the tenant;
- self-guided business activation path with assisted launch control;
- secure one-time team invitations.

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
