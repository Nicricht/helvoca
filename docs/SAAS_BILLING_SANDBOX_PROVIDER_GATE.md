# Mercado Pago SaaS Sandbox Provider Gate

This gate is intentionally isolated from Railway production.

## Newer C5 checkpoint: authentic TEST persistence versus organic provider delivery (2026-10-18)

**The original mock bridge described later in this runbook is historical, not the current TEST persistence state.** A genuine provider TEST invoice with nested payment.status=approved/accredited was reconciled exactly once via an explicitly named **OPERATOR_GENERATED_TEST_RECOVERY** HMAC request to the deployed Helvoca **Java controller and real Railway TEST PostgreSQL18**. Business/subscription became **ACTIVE**, BASIC/EMPRENDE CLP 24,990, **four entitlements PASS** ([authentic PostgreSQL recovery act](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6240000990)). The signed simulated payment topic returned HTTP 200, **transport-only**, and did not trigger SaaS reconciliation ([simulation act](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6233839072)).

The bounded SaaS invoice recovery worker, rotation past 20 tenants, locks, idempotency and rejected→PAST_DUE / approved→ACTIVE logic already exist and are QA-certified **offline**. The previously deployed TEST worker remained **OFF**; a newer TEST commit/flag patch was still **STAGED**. The candidate **1671d9c** had [14/14 workflows SUCCESS](https://github.com/Nicricht/helvoca/actions/runs/39108350614), with 2,349 Java, 620 Playwright, and JaCoCo **100% differential** 1,308/1,308 lines, 1,003/1,003 branches, 315/315 methods (**not global**). This is historical exact-HEAD evidence; after this documentary commit verify the new SHA's own CI.

**Only the different provider boundary remains unverified:** an **organic signed subscription_authorized_payment** from Mercado Pago itself arriving at Helvoca Java and reconciling real isolated PostgreSQL18, with authentic provider subscription notification/replay where applicable. The [official delivery/registration ambiguity and support draft](https://github.com/Nicricht/helvoca/pull/846#issuecomment-6241952771) are already documented; no repeated checkout, invoice, manual HMAC recovery or new worker is justified. This provider gate does not prevent a separate scoped **web/simulator-assisted pilot** that excludes automated billing. No LIVE charge, TEST change or merge is authorized by this note.

## What it proves

The manual GitHub Actions workflow:

`SaaS Billing Sandbox Provider Certification`

uses the real `MercadoPagoSubscriptionGateway` with Mercado Pago TEST credentials to:

1. create one recurring Emprende sandbox checkout;
2. validate that Mercado Pago returns a HTTPS checkout URL and subscription id;
3. read the same remote subscription back from Mercado Pago;
4. verify the external reference remains bound to the generated test business id.

It does not use live credentials, does not touch production tenant data, and does not certify the webhook/payment-approved phase by itself.

## Existing TEST credential and buyer: verify before asking

The repository Actions secret `MERCADOPAGO_TEST_ACCESS_TOKEN` **already exists** and has previous authenticated TEST seller provenance checks (see PR #834). Reuse it; **do not ask the owner to recreate or copy the token** without new evidence that it is absent or invalid.

A distinct **Buyer Test User already exists**. Its account email was used in the historical successful run below and is archived only in its original test-only workflow. Do not copy that identifier into new documentation, public logs, or chats. The generic API example `test@testuser.com` is **not proof of the existing buyer account**; it is now rejected by the gated runner and workflow before any provider call.

Never paste credentials, seller/buyer passwords, secrets or private account responses into issues, PRs, chat, logs or Railway production variables.

## Run

After this workflow exists on the default branch:

1. GitHub > Actions.
2. Open `SaaS Billing Sandbox Provider Certification`.
3. Click `Run workflow`.
4. Select **the previously existing, actual Buyer Test User account email** from Mercado Pago Developers > Cuentas de prueba > Comprador, if an authorized *new* provider trial becomes necessary. The workflow intentionally has **no default** and rejects the placeholder `test@testuser.com`.
5. Set `confirm_sandbox` to true.
6. Run **only if an acceptance gap makes a new provider write indispensable**; do not repeat an earlier certified checkout simply to obtain another success badge.
7. Handle any checkout URL and provider identifiers as sensitive test evidence. Complete only the sandbox flow using the existing Buyer Test User, with no LIVE payments.

## Prior authentic TEST evidence: do not repeat

The historical branch `cert/saas-billing-sandbox-provider-run-1` already executed an authenticated Mercado Pago checkout using an existing Buyer TEST account, with the following original Actions evidence:

- [#36360314081](https://github.com/Nicricht/helvoca/actions/runs/36360314081): `MercadoPagoSaasSandboxProviderIT` **1/1 PASS** after a real provider create and readback; subscription status was `pending`.
- [#36360749617](https://github.com/Nicricht/helvoca/actions/runs/36360749617): real authenticated provider readback found **one authorized-payment invoice**, invoice lifecycle `processed`, with nested provider **payment.status=approved**. No raw buyer details are needed to preserve this evidence.
- [#36360833820](https://github.com/Nicricht/helvoca/actions/runs/36360833820): an isolated bridge test **1/1 PASS** consumed this real provider invoice and asserted local `ACTIVE`, entitlements and duplicate-save idempotency, **but used mock repositories and mock JDBC**, so it did **not** certify persistence in real PostgreSQL or the current production deployment.

On October 14, 2026, [workflow #38563207070](https://github.com/Nicricht/helvoca/actions/runs/38563207070) tried a **new** `/preapproval` with the undocumented/unverified example email `test@testuser.com` and received HTTP 400. Without provider error details we cannot assert that the email was the sole cause; the fix in PR #839 adds a safe, categorical 400 diagnostic and preflight rejection of example buyer input. That failed attempt **does not revoke the earlier successful TEST creation and approved payment evidence**.

**Historical gap text, superseded by the newer checkpoint above:** the Render C5 simulator-only receiver still cannot prove real billing persistence, but TEST Helvoca Java+PostgreSQL18 **was later verified** with operator-generated signed recovery and actual ACTIVE entitlement state. **Organic provider-sent subscription webhook remains NOT_VERIFIED**; production charge, release and automated SaaS LIVE billing remain unauthorized.

For V1, record the current gate and required approvals in Issue #823 before any new external call. C1/C2 production safeguards stay in force.

The provider webhook + approved invoice round trip remains a separate gate until its provider-side notification path is proven.

## 2026-10-16: bounded read-only invoice discovery (C5, PR #846)

Helvoca's own SaaS subscription invoices are distinct from tenant customers' merchant payments.
The existing `cl.helvoca.payment.MerchantPaymentStatusReconciliationWorker` **must not**
be used as a SaaS billing worker.

A new method `SubscriptionPaymentGateway.searchInvoices(externalSubscriptionId, offset, limit)`
uses Mercado Pago Java SDK 3.7.0 `InvoiceClient.search(MPSearchRequest, MPRequestOptions)`
to query `GET /authorized_payments/search`, with an explicit `preapproval_id` filter.
The method only returns a bounded page of invoice IDs and total count; it **never**
retrieves entitlements, changes PostgreSQL, or sends payments. Limits are 1 through 20;
offset must be nonnegative. Invalid IDs, inconsistent provider pagination,
missing invoice IDs and mismatched preapproval IDs fail closed.

This is an internal gateway method, **not an active scheduler or public HTTP endpoint**.
Nothing is automatically polled, and no real provider search is executed by unit tests.
The automatic recovery worker below is separately verified and remains
disabled-by-default. No recovered invoice may bypass the original paid-invoice gate.

## 2026-10-17: disabled automatic SaaS invoice recovery (C5, PR #846)

`SaasBillingInvoiceRecoveryWorker` reuses the existing `searchInvoices`, `getInvoice`
and `BillingSubscriptionService.reconcileAuthorizedPayment` paths. It does not create
payments or subscriptions. When explicitly enabled with
`HELVOCA_SAAS_BILLING_INVOICE_RECOVERY_ENABLED=true`, it:

- selects only Mercado Pago SaaS subscriptions in `TRIALING`, `ACTIVE` or `PAST_DUE`
  with a real external subscription ID, using a bounded page of at most 20 rows;
- rotates the candidate page across scheduled polls, ordered by `(updatedAt, id)` so a
  population larger than 20 cannot be indefinitely starved by a fixed page zero;
  a short or empty page restarts the scan, and a failed discovery retains its cursor.
  Cursor lives per worker process (not shared across replicas or durable after restart);
  advisory subscription locks still guard concurrent application of invoices;
- takes a PostgreSQL transaction-scoped advisory lease per external subscription,
  re-reads the row under the SYSTEM database scope, and re-validates provider,
  subscription identity and eligibility before any provider call;
- searches one bounded invoice page, deduplicates IDs, re-fetches each invoice, and
  fails closed on malformed pages, mismatched subscriptions, unknown payment states
  or provider errors;
- orders valid invoices by debit date and invokes only the existing reconciler. That
  reconciler activates or promotes a plan only for `payment.status=approved`, keeps
  rejected payments in grace/`PAST_DUE`, and rejects stale invoices that arrive after
  a newer period has already been established.

Repeated polls are durable-idempotent through the existing locked subscription row and
invoice/status markers. Errors are contained per candidate/invoice; no entitlement
change is attempted for another business. Synthetic invoice IDs were used for the new
tests; the already-recovered TEST invoice was not queried or replayed.

**Historical local-only note superseded:** offline worker QA, PostgreSQL/Testcontainers, backend, Playwright and Fast/Full Gates were subsequently certified on exact candidate HEAD 1671d9c ([14/14 workflows PASS](https://github.com/Nicricht/helvoca/actions/runs/39108350614)). This does **not** certify organic provider notification or authorize worker enablement. Do not rebuild/retest successful invoice recovery merely because an older paragraph mentioned incomplete CI.

## 2026-10-17: official organic notification path and remaining blocker

Mercado Pago's official documentation for subscriptions without an associated plan
states that authorized payments are generated recurrently and that rejected invoices
may enter `recycling` while the provider retries them. For notification topics, the
official additional-notifications page says to enable `subscription_authorized_payment`
for authorized-payment updates and also enable the associated `payments` topic; it
mentions `subscription_preapproval` for pending-subscription updates:

- [Authorized payments for subscriptions without an associated plan](https://www.mercadopago.com/developers/es/docs/subscriptions/integration-configuration/subscription-no-associated-plan/authorized-payments)
- [Additional information about notifications](https://www.mercadopago.com/developers/es/docs/your-integrations/notifications/additional-info)

This documents the provider-side mechanism, but it does not certify Helvoca's organic
delivery. The TEST evidence in Issue #823 / PR #846 still shows no organic
`subscription_authorized_payment` in provider history or the configured receiver, and
the current SDK checkout builder does not expose an equivalent notification-topic
configuration field. The remaining blocker is therefore unchanged: establish the
sanctioned configuration path for this SDK/account and capture one authentic signed
organic event at the real Java + PostgreSQL backend, with tenant isolation and durable
idempotency. The recovery worker is only a bounded resilience mechanism; it is not a
substitute for that webhook certification.

Provider reference:
https://www.mercadopago.cl/developers/es/reference/online-payments/subscriptions/authorized-payment-search/get
