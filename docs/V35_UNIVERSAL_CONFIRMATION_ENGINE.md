# V35 Universal Confirmation Engine

V35 makes customer confirmation durable, revision-aware and channel-neutral.

## Contract

A confirmation belongs to a tenant, universal operation and exact operation revision. It does not belong to VOICE or WHATSAPP. The same explicitly identified customer may therefore receive a proposal on one channel and confirm it on another.

Each operation revision can have one durable confirmation with states:

`AWAITING -> CONSUMED | INVALIDATED | EXPIRED | CANCELLED`

Changing a token/revision invalidates the previous confirmation. Confirming the operation consumes it. Cancelling/expiring the operation closes it. New confirmations expire after 30 minutes.

## Compatibility

Existing ORDER, DELIVERY and PAYMENT workflows continue to own their domain-specific recalculation and safety rules. PostgreSQL mirrors their existing `business_operation.confirmation_token` into the universal confirmation registry, while `ConfirmationAwareCommercialOperationToolService` performs the common expiry/stale/ownership preflight before delegating to those workflows.

This preserves existing idempotent replay behavior while removing channel binding from the confirmation decision.

## Safety

- tenant scoped
- customer/source ownership required
- stale revisions rejected
- expired tokens rejected
- cross-channel allowed only for the same customer
- token rotation invalidates old confirmation
- no industry-specific logic
- no real external side effect added by this engine
