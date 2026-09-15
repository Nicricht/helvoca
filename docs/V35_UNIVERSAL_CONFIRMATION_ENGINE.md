# V35 Universal Confirmation Engine

V35 makes customer confirmation durable, revision-aware and channel-neutral.

## Contract

A confirmation belongs to a tenant, universal operation and exact operation revision. It does not belong to VOICE or WHATSAPP. The same explicitly identified customer may therefore receive a proposal on one channel and confirm it on another.

Each operation revision can have one durable confirmation with states:

`AWAITING -> CONSUMED | INVALIDATED | EXPIRED | CANCELLED`

Changing a token/revision invalidates the previous confirmation. Confirming the operation consumes it. Cancelling/expiring the operation closes it. New confirmations expire after 30 minutes.

A consumed token remains the durable idempotency key for that operation even when a downstream workflow later advances the operation revision. A revision change that incorrectly reuses the previous token is fail-closed: the previous confirmation is invalidated and no new authorization is minted until the application rotates the token.

## ORDER, DELIVERY and PAYMENT compatibility

Existing ORDER, DELIVERY and PAYMENT workflows continue to own their domain-specific recalculation, pricing, provider and safety rules. PostgreSQL mirrors their existing `business_operation.confirmation_token` into the universal confirmation registry, while `ConfirmationAwareCommercialOperationToolService` performs the common expiry, stale-token and ownership preflight before delegating to those workflows.

The typed workflows retain their existing projection-level idempotent replay behavior. Universal confirmation additionally resolves an exact consumed token before looking only at the current operation revision, so later PAYMENT execution or webhook revision changes do not destroy the original confirmation idempotency key.

## BOOKING two-phase workflow

VOICE and WHATSAPP now share `BookingConfirmationWorkflowService` instead of creating a booking directly.

Phase 1 accepts a real catalog `serviceId` and a validated future `startAt`. It validates the active service, business hours and current overlap state, then persists a universal BOOKING operation in `AWAITING_CONFIRMATION` with a backend-owned snapshot of the proposed service, time and optional notes. It returns `operationId` and `confirmationToken`, but it does not create a `booking` row.

Phase 2 accepts the exact `operationId` and `confirmationToken`. The backend ignores replacement service/time conditions from the confirming channel, verifies tenant and customer ownership, revalidates the stored proposal, serializes access to the operation and service slot with PostgreSQL transaction advisory locks, and only then materializes the typed booking projection. If the slot became unavailable, the operation and confirmation expire without creating a second booking.

Because confirmation is customer-scoped rather than channel-scoped, a proposal created by VOICE may be confirmed through WHATSAPP by the same explicitly identified customer. The source on the resulting booking records the channel that actually confirmed it.

An exact replay of a consumed booking token returns the existing booking instead of creating another projection.

## Safety

- tenant scoped
- explicit customer identity takes precedence over weaker phone/source matching
- stale revisions rejected
- expired tokens rejected
- cross-channel confirmation allowed only for the same customer
- token rotation invalidates old confirmation
- same-token revision reuse fails closed
- BOOKING conditions are read from the persisted proposal at confirmation time
- BOOKING availability is revalidated immediately before materialization
- no industry-specific logic
- no real external side effect added by this engine
