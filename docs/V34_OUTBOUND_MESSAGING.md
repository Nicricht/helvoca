# V34 Outbound Messaging Engine

V34 introduces a durable, tenant-scoped outbound messaging boundary.

## Core rules

- The LLM never supplies an arbitrary destination or sensitive URL to a provider.
- Recipients are resolved from `customer_identity` and must be explicitly verified.
- If a customer has no verified phone, preparation fails closed.
- If a customer has multiple verified phones, the caller must choose an explicit verified identity.
- Message content is rendered from backend-owned operation/payment state.
- Payment links are read from `business_payment.checkout_url`, require HTTPS, and do not imply payment success.
- The same purpose + operation revision + recipient is idempotent.
- All rows are tenant scoped and protected with composite tenant foreign keys.
- Provider delivery is behind `MessagingProvider` and `app.outbound.delivery-enabled`.
- Delivery is disabled by default. V34 certification therefore cannot contact a real customer accidentally.

## Supported purposes

- PAYMENT_LINK
- BOOKING_CONFIRMATION
- MEETING_LINK
- ORDER_STATUS
- QUOTE
- REMINDER
- DELIVERY_STATUS

## Provider boundary

`MessagingProvider` receives only a backend-authorized recipient, content, message id and idempotency key. Provider-specific credentials remain outside PostgreSQL. Concrete provider integrations can be added without changing the conversation/domain core.

## Release gate

V34 must pass exact-head PR CI, controlled merge, exact-main CI, Railway exact-SHA deployment, Flyway V33→V34, application startup and healthcheck before being called complete.
