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

## Required GitHub secret

Create exactly one repository Actions secret:

`MERCADOPAGO_TEST_ACCESS_TOKEN`

Use the **Access Token from Pruebas > Credenciales de prueba** for the RecepVoz Sandbox Mercado Pago application.

Never paste that token into issues, PRs, chat, source files, logs, or Railway production variables.

## Run

After this workflow exists on the default branch:

1. GitHub > Actions.
2. Open `SaaS Billing Sandbox Provider Certification`.
3. Click `Run workflow`.
4. Keep `payer_email` as `test@testuser.com` unless Mercado Pago explicitly supplies another `@testuser.com` email for the buyer.
5. Set `confirm_sandbox` to true.
6. Run once.
7. Read the resulting checkout URL from the test output.
8. Complete only the TEST checkout using the Mercado Pago Buyer Test User.

The provider webhook + approved invoice round trip remains a separate gate until its provider-side notification path is proven.
