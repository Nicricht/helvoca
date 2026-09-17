# V43 Voice -> WhatsApp continuity

V43 connects the existing V33 omnichannel identity/session model with the V34 outbound messaging engine. It does not create an industry-specific flow and it does not let the LLM invent recipients, text, prices or payment URLs.

## Customer flow

1. A verified caller interacts with Helvoca by voice.
2. Existing domain tools create or resolve a tenant-scoped operation such as PAYMENT, BOOKING, ORDER, QUOTE or DELIVERY.
3. The AI may call `send_whatsapp_operation` with only:
   - the exact backend `operationId`;
   - a supported outbound `purpose`;
   - optionally an exact verified `recipientIdentityId` when the customer has multiple verified phones.
4. `OutboundMessagingService` resolves the verified recipient and renders backend-authoritative content.
5. If real outbound delivery is disabled, the message remains `PREPARED` and the tool returns `success=false`; the AI must not claim it was sent.
6. When outbound delivery is explicitly enabled and a provider exists, the message is queued through the durable V37 outbox.
7. `TwilioWhatsAppMessagingProvider` can deliver the queued WhatsApp message using exactly one tenant phone explicitly marked `whatsapp_enabled=true`.
8. A reply from that verified phone re-enters the existing V33 identity/session path, so domain context remains attached to the same tenant/customer rather than starting an unrelated business flow.

## Safety gates

Real delivery still requires all of these independent conditions:

- verified customer identity;
- a tenant-owned backend operation;
- backend-renderable purpose/content;
- `app.outbound.delivery-enabled=true`;
- `app.outbound.provider=TWILIO_WHATSAPP`;
- V37 job worker enabled;
- valid Twilio credentials;
- exactly one active tenant phone with `whatsapp_enabled=true`.

V43 migration defaults `whatsapp_enabled` to `false` for every existing phone and enforces at most one active WhatsApp sender per tenant. Deploying V43 therefore does **not** activate or send real WhatsApp messages.

## Admin API

`PATCH /api/v1/phone-numbers/{id}/whatsapp`

```json
{
  "enabled": true
}
```

Only `BUSINESS_ADMIN` may change this flag. The phone must already be active, and enabling a second active sender for the same tenant fails closed.

## Supported outbound purposes

- `PAYMENT_LINK`
- `BOOKING_CONFIRMATION`
- `MEETING_LINK`
- `ORDER_STATUS`
- `QUOTE`
- `REMINDER`
- `DELIVERY_STATUS`

For `PAYMENT_LINK`, the URL comes from the persisted payment provider result and must be HTTPS. A checkout link never implies that payment succeeded.
