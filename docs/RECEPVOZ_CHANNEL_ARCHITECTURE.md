# RecepVoz channel architecture

Status: **frozen for the sellable MVP**

This decision is intentionally narrow so the project can be finished without reopening provider selection during the MVP.

## Production channels

### Voice

- **Primary telephony provider:** Twilio Voice.
- Existing Twilio voice numbers, webhooks, Media Streams and AI voice routing remain in place.
- WhatsApp work must not modify, release, port or disable the current Twilio voice number.
- A future `VoiceProvider` abstraction may add another carrier, but it is not part of the MVP.

### WhatsApp

- **Primary WhatsApp provider for new work:** Meta WhatsApp Cloud API.
- New WhatsApp onboarding must not depend on Twilio WhatsApp senders, Twilio WABA bindings or Twilio WhatsApp subaccounts.
- The existing `TWILIO_WHATSAPP` implementation is legacy/fallback code only and remains disabled by default.
- No new production tenant should be onboarded through `TWILIO_WHATSAPP`.

## Multi-tenant rule

Each business keeps its own WhatsApp identity and configuration. RecepVoz shares application infrastructure and AI infrastructure, but never shares tenant data.

The target mapping is:

```text
Meta phone_number_id -> RecepVoz business_id -> tenant configuration -> AI/tools
```

Per-tenant WhatsApp configuration will include, at minimum:

- provider
- WABA id
- Meta phone number id
- public phone number
- connection status
- credential reference / encrypted credential
- enabled state
- connected timestamp

## Provider boundary

Outbound messaging continues behind the existing `MessagingProvider` boundary.

Target providers:

```text
MessagingProvider
├── META_WHATSAPP_CLOUD   primary
└── TWILIO_WHATSAPP       legacy/fallback
```

The safe production default remains **no real delivery** until Meta configuration and the tenant connection are explicitly enabled.

## MVP delivery sequence

1. Keep Twilio Voice unchanged and certified.
2. Add Meta WhatsApp webhook verification and inbound processing.
3. Resolve tenant from Meta `phone_number_id`.
4. Add Meta outbound text delivery behind `MessagingProvider`.
5. Connect inbound WhatsApp to the existing receptionist/operation tools.
6. Run a real end-to-end pilot with RecepVoz.
7. Add customer-facing WhatsApp connection/onboarding after the pilot works.

## Explicit non-goals for the MVP

- Migrating Voice away from Twilio.
- Repairing the old Bienestar Sin Vueltas WABA as the foundation of the product.
- Creating new Twilio WhatsApp subaccounts for customer onboarding.
- WhatsApp media/catalog features before text messaging is certified.
- Exposing WABA IDs, tokens, webhook URLs or provider internals to business users.
