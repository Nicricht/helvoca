# Universal Operation Event Log

## Purpose

V29 adds an immutable, tenant-scoped history for every `business_operation` in Helvoca.

The capture layer lives in PostgreSQL rather than in individual workflow services. That means ORDER, DELIVERY, BOOKING, PAYMENT, QUOTE, LEAD, REQUEST and future adapters cannot silently skip event creation just because a Java caller forgot to emit an event.

## Storage

`business_operation_event` stores:

- monotonic `sequence_no`;
- `business_id` snapshot;
- `operation_id` snapshot;
- operation type;
- semantic `event_type`;
- channel (`VOICE`, `WHATSAPP`, `MANUAL`, `API`);
- source reference;
- operation revision;
- current and previous universal status;
- actor classification (`SYSTEM`, `HUMAN`, `AI`, `PROVIDER`);
- sanitized JSON payload;
- immutable creation timestamp.

The business and operation IDs are intentionally snapshots rather than foreign keys. The log must survive deletion of operational rows and must not block normal cleanup of those tables.

## Immutability

The application repository is read-only and PostgreSQL rejects `UPDATE` and `DELETE` against `business_operation_event` through `trg_business_operation_event_immutable`.

Operational deletions are captured as `<TYPE>_DELETED` before the history is left behind as an immutable snapshot.

## Semantic events

Examples include:

- `ORDER_QUOTED`
- `ORDER_UPDATED`
- `ORDER_CONFIRMED`
- `ORDER_CANCELLED`
- `BOOKING_CREATED`
- `DELIVERY_QUOTED`
- `PAYMENT_QUOTED`
- `PAYMENT_STATUS_CHANGED`
- `REQUEST_CREATED`
- `<TYPE>_SNAPSHOT_IMPORTED` for operations that existed before V29.

Status transitions produce semantic names from the universal operation type and state. Changes to a verified payment status are specifically attributed to `PROVIDER`.

## Privacy and payload policy

The event trigger does **not** copy arbitrary `metadata_json`, contact name, contact phone, delivery address, checkout URLs, tokens, credentials, card data or CVV.

The initial payload is deliberately narrow and may contain only operational facts such as:

- status transition;
- total;
- currency;
- fulfillment type;
- delivery fee;
- verified payment status transition.

New fields must be explicitly reviewed before being added to the immutable payload.

## Tenant isolation

Read API:

`GET /api/v1/operation-events`

Optional filter:

`GET /api/v1/operation-events?operationId=<uuid>`

The service always obtains `business_id` from `TenantProvider.requireBusinessId()`. A caller cannot provide another tenant ID through the request.

Access is restricted to `BUSINESS_ADMIN` and `OPERATOR`.

The endpoint returns at most the latest 100 events, newest first.

## Historical migration

When V29 is applied, each pre-existing operation receives one `<TYPE>_SNAPSHOT_IMPORTED` event containing its current non-sensitive operational state. V29 does not attempt to fabricate historical transitions that happened before the event log existed.

## Relationship with AuditLog

`AuditLog` remains useful for administrative actions such as configuration changes. `business_operation_event` is different: it is the canonical append-only timeline of universal business operations.
