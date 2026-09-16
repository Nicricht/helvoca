# Helvoca — Commercial Launch Status

## Decision

Helvoca can be sold now as an assisted pilot. The first commercial objective is not mass self-service acquisition; it is to close and successfully activate the first paying customers with a controlled scope.

## Production baseline

- Stable production baseline: V41 Usage & Cost Metering.
- V42 Plans / Entitlements / Billing is still under development and must not be treated as production-ready until its own release gate is green.
- V42 is not a blocker for prospecting, demos or assisted onboarding.

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
- voice/WhatsApp shared domain behavior when those channels are configured for the tenant.

## Available in the core but activation-dependent

The following capabilities require concrete tenant/provider configuration before they can be included in a pilot promise:

- real voice provider and telephony;
- WhatsApp delivery;
- outbound messaging;
- external calendar/meeting provider;
- real payment merchant/provider.

## Commercial model until V42 certification

- onboarding is assisted;
- configuration is reviewed with the customer before activation;
- plan, limits and pilot scope are documented explicitly;
- no external integration is presented as active until it has been tested for that tenant;
- no ROI, SLA or unlimited-channel promise is made without a written commercial commitment.

## Launch assets

- `/sales.html` — public sales landing;
- `/pricing.html` — public pricing;
- `docs/FIRST_SALE_TOMORROW.md` — field sales script and demo checklist;
- `docs/FIRST_SALES_SPRINT.md` — acquisition sprint and pipeline;
- `docs/FIRST_PROSPECTS_TRACKER.csv` — prospect tracker template;
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` — onboarding form after pilot acceptance.

## Exit criteria for assisted-pilot stage

Move from assisted pilots to broader self-service acquisition only after:

- V42 is certified in production;
- plan/entitlement enforcement is stable;
- billing/provisioning behavior is operationally tested;
- onboarding no longer requires routine engineering intervention;
- external channel activation has repeatable runbooks;
- at least the first customers have produced real usage and support data.
