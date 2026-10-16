# Mercado Pago SaaS Sandbox Provider Gate

This gate is intentionally isolated from Railway production.

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

**Current gap (C5 NO-GO):** a provider-authenticated real `subscription_authorized_payment` reaching the **actual billing backend** with real PostgreSQL persistence, tenant binding, idempotent replay, subscription activation/entitlement and rejection/recovery, verified under a permitted isolated deployment. The Render C5 simulator-only receiver is **not** the billing backend: its HTTP 200 does not write invoices or entitlements. Production charge, release and automated SaaS billing are not approved.

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
A future, separately verified and disabled-by-default recovery worker must safely select
only SaaS-owned subscriptions, query bounded pages, re-fetch each invoice with
`getInvoice(invoiceId)`, and invoke existing billing reconciliation only after
validating tenant/subscription identity and payment truth. That future work requires
RED->GREEN, PostgreSQL/Testcontainers, duplicate/out-of-order and concurrency evidence,
and fresh exact-HEAD QA. No recovered invoice may bypass the original paid-invoice gate.
Never confuse this recovery method with the still-unverified *authentic signed webhook*
delivery to isolated Java plus real PostgreSQL.

Provider reference:
https://www.mercadopago.cl/developers/es/reference/online-payments/subscriptions/authorized-payment-search/get
