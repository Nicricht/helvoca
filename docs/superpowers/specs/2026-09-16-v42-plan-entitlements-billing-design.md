# V42 Plans, Entitlements & Billing Design

## Goal

V42 turns Helvoca's commercial model into a provider-neutral, database-driven entitlement system. V41 remains the immutable source of measured usage; V42 decides what a tenant is entitled to consume and exposes plan, quota, overage, capacity and subscription state without embedding commercial numbers in Java enums.

## Context

V41 introduced `usage_meter_event`, an append-only tenant-scoped usage ledger. The current commercial layer still embeds public plan names, prices, included minutes, overage prices and concurrent-call capacity inside `PlanCode`, while `BusinessSubscriptionService` calculates usage directly from `call_session`. That creates two sources of usage truth and requires a code deployment for normal commercial changes.

V42 removes that coupling while preserving the existing public plan contract (`EMPRENDE`, `NEGOCIO`, `PRO`, `ENTERPRISE`) and the existing subscription/payment safety model.

## Architectural decisions

1. **V41 is the only usage source of truth.** Period usage is aggregated from `usage_meter_event`; V42 must not recalculate billed usage from `call_session`, `outbound_message` or channel-specific tables.
2. **Plans are data.** Plan price, public code, display name, recommendation flag and entitlements live in PostgreSQL. Adding or changing a commercial plan must not require recompiling the application.
3. **Subscriptions reference technical plan codes as strings.** `business_subscription.plan_code` and `pending_plan_code` remain VARCHAR values but no longer map to a Java enum.
4. **Entitlements are generic.** They are keyed by data such as `VOICE_SECONDS` or `CONCURRENT_CALLS`; there are no restaurant/clinic/etc. branches.
5. **Billing providers are adapters.** Mercado Pago may create checkout sessions and reconcile verified provider state, but it never owns plan rules, usage totals or service eligibility.
6. **Fail closed.** Missing subscription, missing plan, inactive plan or missing required hard entitlement blocks the affected operation instead of granting a silent fallback plan.
7. **Compatibility first.** Existing public pricing and subscription responses keep their current commercial fields. V42 may add generic entitlement detail but must not silently rename the current public plan codes.
8. **No real external effects.** V42 does not enable Mercado Pago, calls, WhatsApp, calendars or any other provider and does not change certification flags.

## Data model

### `commercial_plan`

Global reference data, not tenant-owned.

- `code VARCHAR(40) PRIMARY KEY`: stable technical code such as `BASIC`, `PRO`, `BUSINESS`, `ENTERPRISE`.
- `public_code VARCHAR(40) UNIQUE NOT NULL`: customer-facing code such as `EMPRENDE`.
- `display_name VARCHAR(80) NOT NULL`.
- `monthly_price_clp INTEGER`: nullable only when the commercial agreement has no fixed list price.
- `currency VARCHAR(3) NOT NULL DEFAULT 'CLP'`.
- `custom_pricing BOOLEAN NOT NULL`.
- `recommended BOOLEAN NOT NULL`.
- `active BOOLEAN NOT NULL`.
- `sort_order INTEGER NOT NULL`.
- timestamps.

V42 seeds the four plans with the same customer-visible commercial values currently encoded by `PlanCode`.

### `commercial_plan_entitlement`

Global plan rules, not tenant-owned.

- `plan_code VARCHAR(40)` FK to `commercial_plan`.
- `entitlement_key VARCHAR(80)`.
- `kind VARCHAR(20)`: `USAGE` or `CAPACITY`.
- `meter_key VARCHAR(80)`: required for `USAGE`, null for `CAPACITY`.
- `limit_value NUMERIC(20,6) NOT NULL`.
- `unit VARCHAR(30) NOT NULL`.
- `hard_limit BOOLEAN NOT NULL`.
- `overage_unit_size NUMERIC(20,6)`: optional conversion size for priced overage.
- `overage_price_clp INTEGER`: optional price per overage billing unit.
- primary key `(plan_code, entitlement_key)`.

Initial rules:

| Plan | `VOICE_SECONDS` | `CONCURRENT_CALLS` | Overage price |
|---|---:|---:|---:|
| BASIC / EMPRENDE | 6,000 seconds | 1 | 149 CLP / 60 seconds |
| PRO / NEGOCIO | 15,000 seconds | 3 | 129 CLP / 60 seconds |
| BUSINESS / PRO | 30,000 seconds | 10 | 109 CLP / 60 seconds |
| ENTERPRISE | 60,000 seconds | 10 | custom |

`VOICE_SECONDS` is a soft usage entitlement in V42: exceeding included usage produces overage information but does not stop an active subscription. `CONCURRENT_CALLS` is a hard capacity entitlement and preserves the current call-capacity behavior.

### Existing `business_subscription`

V42 drops the old finite `plan_code` / `pending_plan_code` CHECK constraints, seeds the plan catalog, and adds foreign keys to `commercial_plan(code)`. The Java entity maps those columns as `String` so future plans can be inserted as data without adding enum constants.

## Runtime components

### `CommercialPlanCatalogService`

Read-only service responsible for:

- listing active plans in `sort_order`;
- resolving a technical plan code;
- resolving an active public plan code for checkout;
- returning plan entitlements;
- failing closed when a referenced plan does not exist or is inactive where active status is required.

It exposes immutable records rather than JPA entities so billing adapters cannot mutate commercial configuration.

### `CommercialEntitlementService`

Tenant-commercial decision service responsible for:

- loading the tenant subscription;
- validating subscription period/status;
- loading the referenced plan and its entitlements;
- aggregating `USAGE` entitlements from V41 `usage_meter_event` for `[current_period_start, current_period_end)`;
- computing used, remaining and overage quantities;
- returning hard `CAPACITY` limits such as `CONCURRENT_CALLS`;
- deriving the compatibility voice fields used by current UI/readiness code.

The service accepts an explicit `businessId` only on internal service methods. Public controllers continue deriving tenant identity from `TenantProvider`.

### `BusinessSubscriptionService`

Remains responsible for subscription lifecycle initialization/synchronization. It no longer reads `call_session` or `CallCommercialProperties` to calculate commercial usage. Its `SubscriptionView` is built from `CommercialEntitlementService` and keeps compatibility fields:

- plan/status/serviceAllowed;
- maxConcurrentCalls;
- includedMinutes/usedMinutes/overageMinutes;
- period/grace/provider flags.

It also adds generic entitlement detail for future channels.

### Payment gateway boundary

`SubscriptionPaymentGateway.createCheckout(...)` receives a provider-neutral immutable payment-plan value containing technical code, display name, monthly CLP price and custom-pricing flag. Mercado Pago builds its request from that value. External references remain `helvoca:<businessId>:<technicalPlanCode>`.

`BillingSubscriptionService` resolves plans through `CommercialPlanCatalogService`, never through a Java plan enum.

## Service eligibility and enforcement

Subscription status remains authoritative for whether the service is generally allowed. Existing `SubscriptionStatus` semantics and grace handling remain unchanged.

Call admission keeps its current flow but the capacity value comes from the generic `CONCURRENT_CALLS` entitlement. A missing subscription, plan or required capacity entitlement rejects the call rather than falling back to a global commercial property.

Usage overage is informational/chargeable policy, not an automatic service cutoff in V42. This preserves current behavior while making the amount auditable from V41.

## API compatibility

- `GET /api/v1/public/pricing` keeps returning `code`, `name`, `monthlyPriceClp`, `includedMinutes`, `maxConcurrentCalls`, `overagePerMinuteClp`, `customPricing`, `recommended`, now backed by PostgreSQL.
- `GET /api/v1/subscription` keeps current compatibility fields and may add `publicPlanCode`, `planName` and `entitlements`.
- `GET /api/v1/subscription/plans` becomes catalog-backed.
- `GET /api/v1/billing/status` remains BUSINESS_ADMIN-only and catalog-backed.
- Existing checkout/refresh/webhook URLs remain unchanged.

No endpoint accepts a caller-provided `business_id`.

## Security and tenant isolation

`commercial_plan` and `commercial_plan_entitlement` are global reference tables. `helvoca_runtime` receives SELECT only; DML is explicitly revoked. Tenant-owned `business_subscription` and `usage_meter_event` remain protected by V40/V41 forced RLS.

Authenticated tenant requests read only their subscription/usage. Internal ingress paths may use `helvoca_system` according to the existing `TenantAwareDataSource` system mode, which is already the controlled cross-tenant path for telephony and provider callbacks.

## Migration and rollback safety

V42 is forward-only like existing Flyway migrations. Migration order is:

1. create and seed `commercial_plan`;
2. create and seed `commercial_plan_entitlement`;
3. drop old finite plan CHECK constraints;
4. add foreign keys from subscription current/pending plan codes;
5. grant read-only runtime access to global commercial reference data.

Existing subscription rows remain valid because all historical technical codes are seeded before the foreign keys are added.

## Tests and release gate

V42 must prove:

- public plan resolution is database-backed;
- usage calculations read V41 meter data and compute remaining/overage correctly;
- absent subscription/plan/required capacity fails closed;
- checkout uses catalog plan data and retains external-reference validation;
- public pricing compatibility is preserved;
- current subscription compatibility fields derive from generic entitlements;
- no provider activation or certification flag is changed.

Release gate remains mandatory: exact PR HEAD CI green, merge that exact SHA, main CI green, Railway deploy exact merge SHA, Flyway v42 successful, Spring startup successful, and `/actuator/health` accepted by Railway.