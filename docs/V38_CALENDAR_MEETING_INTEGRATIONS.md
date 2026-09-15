# V38 Calendar / Meeting Integrations

V38 adds a provider-neutral calendar synchronization boundary for Helvoca BOOKING operations.

## Domain boundary

The local Helvoca booking remains the operational source of truth. Calendar synchronization is attached to the shared `BookingOperationSyncService`, so VOICE and WHATSAPP use the same path for create, reschedule and cancel mutations.

A tenant without a connected calendar behaves exactly as before and no durable calendar job is created.

## Tenant integration

`calendar_integration` stores only integration metadata:

- tenant
- provider code
- connection status
- provider calendar identifier
- whether meeting creation is enabled
- connection/error timestamps and codes

OAuth tokens and provider secrets are deliberately not stored in this table. A provider adapter may mark a tenant connected only after provider-specific authorization succeeds and the adapter is registered for that tenant.

No real Google/Microsoft calendar provider or credentials are enabled by V38.

## Versioned event projection

`booking_calendar_event` is a tenant-scoped 1:1 projection for the external calendar event. It tracks:

- stable external event identity
- desired version
- synchronized version
- booking-state SHA-256 fingerprint
- meeting URL returned by the provider
- PENDING / SYNCED / FAILED / DELETED state

Each meaningful booking change increments the desired version. Replaying the same booking state is idempotent and does not create another version.

A stale durable job exits without calling the provider. The external event identity is stable across booking revisions, so rescheduling updates one event rather than creating new events.

## Durable execution

V38 extends V37 with `CALENDAR_EVENT_SYNC` jobs. The booking mutation, desired calendar projection and durable job enqueue share the surrounding database transaction.

Provider I/O happens later in the durable worker. The job model remains at-least-once with downstream idempotency. Calendar upsert uses a stable key per booking:

`calendar-booking:<bookingId>`

## Meetings

Meeting links are backend-owned provider output. When meeting creation is enabled:

- the provider must return a meeting URL
- the URL must be HTTPS with a valid host
- unsafe or missing URLs fail closed
- the LLM is never allowed to manufacture a meeting link

## Provider safety

The provider registry requires exactly one adapter matching the configured provider and tenant. Missing or ambiguous adapters fail closed.

Changing providers while a live external event is still associated with the old provider is rejected until those events are reconciled.

## Production safety

V38 does not activate external calendar traffic by itself. V37 workers remain disabled by default unless explicitly configured, and no real calendar provider is registered in production without provider credentials/authorization.
