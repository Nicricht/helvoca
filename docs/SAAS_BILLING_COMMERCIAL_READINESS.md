# SaaS Billing Commercial Readiness

Date: 2026-09-27  
Baseline reviewed: `main@67ca6bb78d1664dee139aeced17b779ebfcc6284`  
Certification branch: `test/saas-billing-commercial-readiness`  
Draft PR: #552

## Scope

This document certifies only the payment relationship:

```text
BUSINESS BUYS HELVOCA -> SELECTS PLAN -> PAYS -> SUBSCRIPTION -> ENTITLEMENTS
```

It does **not** certify merchant payments made by a business's own customers during calls or conversations. Those live in the separate `cl.helvoca.payment` package and use a different webhook surface.

No live credentials, real charge, deployment, or provider activation was performed in this certification.

## Source of truth: current commercial catalog

The source of truth is PostgreSQL migration V42 plus the runtime `CommercialPlanCatalogService`. Public pricing is rendered from `GET /api/v1/public/pricing`; the browser does not own prices.

| Public plan | Internal code | Monthly price | Included voice | Concurrent calls | Voice overage | Checkout |
|---|---|---:|---:|---:|---:|---|
| Emprende | BASIC | CLP 24,990 | 100 min | 1 | CLP 149/min | fixed-price |
| Negocio | PRO | CLP 39,990 | 250 min | 3 | CLP 129/min | fixed-price |
| Pro | BUSINESS | CLP 69,990 | 500 min | 10 | CLP 109/min | fixed-price |
| Enterprise | ENTERPRISE | CLP 119,990 reference value | 1,000 min | 10 | custom | blocked from self-service; quotation required |

Important: Enterprise is marked `custom_pricing = true`. `BillingSubscriptionService` refuses self-service checkout for it even though the catalog has a reference monthly value.

## Certified runtime flow

1. Public visitor loads `/pricing.html`.
2. `pricing.js` fetches `GET /api/v1/public/pricing`.
3. An authenticated `BUSINESS_ADMIN` chooses a plan.
4. Frontend posts `{"plan":"EMPRENDE|NEGOCIO|PRO"}` to `POST /api/v1/billing/checkout`.
5. Backend resolves the public plan through the database catalog.
6. Backend creates a Mercado Pago recurring preapproval through `SubscriptionPaymentGateway`.
7. Backend persists `billing_provider=mercadopago`, `pending_plan_code`, external subscription id and checkout URL.
8. The current plan remains active. A browser return or an `authorized` preapproval alone does **not** activate the new plan.
9. Mercado Pago sends signed Webhooks to `POST /webhooks/v1/mercadopago`.
10. The controller validates `x-signature` with the official Mercado Pago Java SDK and the configured webhook secret.
11. `subscription_preapproval` reconciles subscription state.
12. `subscription_authorized_payment` fetches the provider invoice and only `approved` / `processed` activates or renews service.
13. On the first approved invoice for a pending plan, `pending_plan_code` becomes `plan_code`.
14. Entitlements are then read from V42 and usage from V41 `usage_meter_event`.

## PASS / FAIL matrix

| Requirement | Result | Evidence / behavior |
|---|---|---|
| 1. Consult current plans | PASS | V42 database catalog + public pricing controller |
| 2. Select a plan | PASS | authenticated billing checkout accepts public plan code |
| 3. Start fake/mock checkout | PASS | service tests use `SubscriptionPaymentGateway` mock; no real provider call |
| 4. Receive and authenticate webhook | PASS | official SDK signature validator is mandatory; invalid signatures are rejected before reconciliation |
| 5. Bind provider event to expected tenant/plan | PASS | external reference is `helvoca:{businessId}:{planCode}`; checkout and reconciliation fail closed on mismatch |
| 6. Activate subscription only after paid invoice | PASS | `authorized` preapproval keeps current plan; only approved/processed invoice activates |
| 7. Assign correct entitlements | PASS | current plan resolves through V42; entitlement service evaluates plan rules |
| 8. Avoid duplicate invoice webhook effects | PASS after PR #552 fix | V66 persists the last applied SaaS invoice id; duplicate invoice retries are no-op while provider reconciliation holds a pessimistic row lock |
| 9. Handle rejected/cancelled invoice | PASS | subscription becomes `PAST_DUE` with 3-day grace; duplicate invoice cannot extend grace repeatedly |
| 10. Handle subscription cancellation | PASS | remote `cancelled/canceled` becomes local `CANCELED`, clears pending plan and checkout URL |
| 11. Handle renewal | PASS | approved recurring invoice moves the commercial period one month from provider debit date without changing the current plan |
| 12. Meter usage | PASS | V41 `usage_meter_event` is the only period-usage source |
| 13. Enforce minutes / overage | PASS | `VOICE_SECONDS` evaluates used, remaining and overage; current fixed plans have soft voice limits with priced overage |
| 14. Enforce concurrent calls | PASS | `CONCURRENT_CALLS` is a hard capacity entitlement consumed by call lifecycle admission |
| 15. Real Mercado Pago sandbox checkout + webhook round trip | FAIL / NOT EXECUTED | this task intentionally did not activate external credentials or perform a provider transaction |
| 16. Real production charge | FAIL / PROHIBITED IN THIS CERTIFICATION | live credentials and real charges were explicitly out of scope |

The internal billing behavior can be certified with mocks and repository tests. The **external provider round trip is the remaining commercial gate** before using automatic billing for the first real payment.

## Idempotency correction made in this branch

Before this certification, approved/rejected invoice reconciliation had no durable marker for an already-applied SaaS invoice. Replaying a rejected invoice could recalculate the grace deadline from a later `Instant.now()`.

PR #552 adds:

- `business_subscription.last_billing_invoice_id`;
- a pessimistic row lock on the matching `business_subscription` during provider reconciliation;
- a no-op when the same provider invoice is received again;
- invoice id equality validation;
- checkout external-reference validation.

This is intentionally separate from merchant-payment webhook idempotency.

## Tests

Primary certification tests:

- `BillingSubscriptionServiceTest`
  - checkout leaves current plan unchanged until paid invoice;
  - Enterprise self-service checkout rejected;
  - pending/authorized preapproval does not activate;
  - provider subscription id mismatch fails closed;
  - checkout external-reference mismatch fails closed;
  - duplicate approved invoice is applied once;
  - duplicate rejected invoice does not extend grace;
  - approved renewal advances period;
  - cancelled subscription cancels local access and pending plan.
- `MercadoPagoWebhookControllerTest`
  - invalid signature -> 401, no reconciliation;
  - configured signed route -> authorized-payment reconciliation.
- Existing plan/entitlement coverage:
  - `CommercialPlanCatalogServiceTest`;
  - `PublicPricingControllerTest`;
  - `CommercialEntitlementServiceTest`;
  - `BusinessSubscriptionServiceTest`;
  - `CallLifecycleServiceTest` / call-capacity integration coverage.

The draft PR CI is the executable certification gate for these tests.

## Production configuration required

Values are named only; no secrets are stored here.

Required for automated Mercado Pago SaaS billing:

- `MERCADOPAGO_ENABLED=true`
- `MERCADOPAGO_ACCESS_TOKEN=<provider credential>`
- `MERCADOPAGO_WEBHOOK_SECRET=<webhook secret>`
- `MERCADOPAGO_BACK_URL=https://<public-host>/<return-path>`
- `MERCADOPAGO_WEBHOOK_TOLERANCE_SECONDS=300` unless an approved different tolerance is required

Provider-side configuration:

- register the public Helvoca webhook URL: `https://<public-host>/webhooks/v1/mercadopago`;
- enable subscription events `subscription_preapproval` and `subscription_authorized_payment`;
- use **test credentials and test users first**;
- keep live credentials disabled until the sandbox round trip passes.

The existing application also requires its normal database/JWT/runtime configuration, but those are not Mercado Pago billing secrets.

## How the first client should pay

### Recommended decision for this week

Do **not** make the first real charge through the automated SaaS checkout until one authorized Mercado Pago sandbox round trip has passed end to end with test credentials.

The code path is present and hardened, but this certification did not exercise Mercado Pago's network/API with a real test account. That makes an immediate live charge an unnecessary commercial risk.

### Fastest safe assisted bridge

If a customer is ready to sign before the external sandbox gate is complete:

1. agree the exact plan, monthly amount, included minutes, overage rule and pilot start date in writing;
2. collect the first payment through an **assisted/manual commercial method controlled by Helvoca** (for example an invoice/bank transfer process already owned by the business);
3. verify receipt manually;
4. record the customer as a paid assisted pilot in the commercial tracker/onboarding record;
5. do not claim that Mercado Pago automatic subscription billing is active;
6. do not reuse the merchant-payment subsystem to represent Helvoca's SaaS fee;
7. migrate the customer to automated recurring checkout only after the sandbox gate passes.

There is currently **no productized operator endpoint that means “manual SaaS payment received -> activate subscription”**. Therefore manual collection is an operational bridge, not a fully automated billing state transition. Avoid ad-hoc direct database edits unless a separately approved operational procedure is created.

### Exact automated flow once the sandbox gate passes

```text
BUSINESS_ADMIN
  -> chooses Emprende / Negocio / Pro
  -> POST /api/v1/billing/checkout
  -> Mercado Pago recurring checkout
  -> customer authorizes/pays
  -> signed subscription_authorized_payment webhook
  -> backend fetches invoice from Mercado Pago
  -> approved/processed invoice
  -> subscription ACTIVE
  -> pending plan becomes current plan
  -> V42 entitlements apply
  -> V41 usage is metered for the new billing period
```

Enterprise remains assisted/custom and does not use self-service checkout.

## Remaining external gate

Run exactly one provider test before enabling a real customer:

1. use Mercado Pago **test** credentials;
2. create a test payer/user;
3. select Emprende or Negocio from a non-production test tenant;
4. create checkout;
5. complete it with a Mercado Pago test payment method;
6. confirm a signed `subscription_preapproval` notification is accepted;
7. confirm a signed `subscription_authorized_payment` notification is accepted;
8. resend the same invoice notification and confirm no subscription state changes;
9. simulate a rejected recurring payment and confirm `PAST_DUE` + 3-day grace;
10. confirm a subsequent approved invoice returns the subscription to `ACTIVE`.

Only after those ten checks should `MERCADOPAGO_ENABLED` be enabled with live credentials for real SaaS collection.
