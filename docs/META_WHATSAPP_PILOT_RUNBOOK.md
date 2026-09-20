# Meta WhatsApp pilot runbook

Last verified against public Meta developer documentation: 2026-09-20.

This runbook prepares the first real Meta WhatsApp Cloud API pilot for RecepVoz / Helvoca without accidentally opening traffic early.

## Scope

This document covers pilot preparation only.

It does not authorize:
- real outbound WhatsApp traffic,
- changing Railway secrets,
- enabling global Meta delivery,
- enabling persistent jobs,
- or sending a certification message.

Those actions remain separate explicit steps.

## Current safety gates

Keep all of these closed during preparation:

```
TWILIO_WHATSAPP_ENABLED=false
META_WHATSAPP_ENABLED=false
HELVOCA_OUTBOUND_DELIVERY_ENABLED=false
HELVOCA_OUTBOUND_PROVIDER=NONE
APP_JOBS_ENABLED=false
```

A tenant Meta configuration must also stay disabled until the real pilot step.

## Meta-side prerequisites

Before staging a real tenant in Helvoca, the Meta side must already provide:

1. A WhatsApp Business Account (WABA) owned or authorized for the pilot business.
2. A business phone number added to that WABA.
3. The business phone number registered for Cloud API.
4. The Meta `phone_number_id` for that number.
5. An access token that is valid for the pilot integration.
6. The Meta app secret used for webhook signature validation.
7. A webhook verify token chosen by RecepVoz.
8. The webhook subscription configured for the WhatsApp account.

Meta's current developer documentation states that a business phone number must be registered before it can send and receive via Cloud API.

### App permissions verified for WhatsApp app review

Meta's current sample App Review submission for WhatsApp Business Platform explicitly requires both of these permissions:

- `whatsapp_business_messaging`
- `whatsapp_business_management`

Do not add broader permissions to the pilot app unless a current Meta flow explicitly requires them. In particular, this runbook does not currently treat `business_management` as a verified pilot requirement.

## Secrets and identifiers

Never commit these values to Git:

- Meta access token.
- Meta app secret.
- Webhook verify token.

Helvoca stores only an opaque credential alias in the tenant database.

Example:

```
credential_ref = PILOT_01
```

The matching deployment secret is resolved from:

```
HELVOCA_META_WHATSAPP_PILOT_01_ACCESS_TOKEN
```

The token itself must exist only in the deployment secret store.

The `phone_number_id` is an external Meta identifier, not a secret.

## Pilot staging sequence

### 1. Keep every traffic gate closed

Before touching the tenant, confirm the global Meta integration, outbound delivery and persistent jobs are still disabled.

### 2. Stage the tenant configuration

Use:

```
PUT /api/v1/channels/whatsapp/meta/config
```

with the pilot tenant authenticated as `BUSINESS_ADMIN`.

Required fields:

```json
{
  "phoneRecordId": "<Helvoca phone row UUID>",
  "provider": "META_WHATSAPP_CLOUD",
  "phone_number_id": "<Meta phone_number_id>",
  "credentialRef": "PILOT_01"
}
```

This operation intentionally leaves:
- tenant Meta configuration disabled,
- phone WhatsApp disabled,
- real delivery disabled.

It also clears previous WhatsApp certification because a changed external sender must be certified again.

### 3. Check local configuration state

Call:

```
GET /api/v1/channels/whatsapp/meta/config
```

Expected state after correct staging:

```
CONFIGURED_DISABLED
```

### 4. Check channel health

Call:

```
GET /api/v1/channels/whatsapp/meta/health
```

This is a local readiness check only. It does not call Meta.

During preparation, a non-`READY` result can be expected because global delivery remains intentionally closed.

### 5. Run certification preflight

Call:

```
GET /api/v1/channels/whatsapp/meta/certification/readiness
```

The desired preparation result is:

```
READY_FOR_PILOT_CERTIFICATION
```

This requires, among other things:
- one unambiguous Meta phone,
- valid `phone_number_id`,
- deployment credential available,
- webhook verification security configured,
- phone active,
- tenant Meta disabled,
- phone WhatsApp disabled,
- global Meta disabled,
- outbound delivery disabled,
- outbound provider set to `NONE`,
- persistent jobs disabled.

This endpoint never writes `whatsapp_certified_at`.

## Webhook callback

Helvoca already exposes the Meta webhook under:

```
/webhooks/v1/meta/whatsapp
```

The public production callback will therefore use the production RecepVoz host plus that path.

Before entering it in Meta, verify the deployed host and exact callback URL from the live deployment. Do not infer the production host from source code alone.

Webhook verification uses:
- the configured verify token for the GET challenge,
- the Meta app secret for `X-Hub-Signature-256` validation on POST callbacks.

Do not disable signature validation for the pilot.

## Stop conditions

Do not continue to real certification if any of these are true:

- more than one Meta phone is configured for the tenant,
- credential alias does not resolve to a deployment token,
- app secret is missing,
- verify token is missing,
- webhook validation is disabled,
- phone is inactive,
- `phone_number_id` is malformed,
- any real-traffic gate is already open unexpectedly,
- the Meta number is not registered for Cloud API,
- tenant ownership of the chosen phone record is not confirmed.

## Real-pilot boundary

The next stage, which is intentionally not performed by this runbook, is the controlled real pilot:

1. verify the deployed Meta webhook callback,
2. add the real secrets in the deployment secret store,
3. stage the real tenant identifiers,
4. verify `READY_FOR_PILOT_CERTIFICATION`,
5. explicitly open only the gates needed for the pilot,
6. receive the first real inbound message,
7. perform the first real outbound only with explicit authorization,
8. persist the real provider message id and delivery callbacks,
9. mark certification only after an actual successful delivery.

## Official Meta references

- Business phone numbers:
  https://developers.facebook.com/documentation/business-messaging/whatsapp/business-phone-numbers/phone-numbers
- Register a business phone number:
  https://developers.facebook.com/documentation/business-messaging/whatsapp/business-phone-numbers/registration
- Meta developer documentation root for WhatsApp Business Platform:
  https://developers.facebook.com/documentation/business-messaging/whatsapp

Meta changes Cloud API onboarding, permissions and UI over time. Before the real pilot, verify the current app-dashboard requirements and Graph API version directly in Meta's current documentation and the configured Meta app.
