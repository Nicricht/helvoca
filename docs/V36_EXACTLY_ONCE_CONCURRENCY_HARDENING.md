# V36 Exactly-Once / Concurrency Hardening

V36 hardens commercial confirmation against concurrent execution of the same universal operation.

## Execution fence

`ConfirmationAwareCommercialOperationToolService` acquires a PostgreSQL transaction advisory lock for the exact `(businessId, operationId)` before universal confirmation authorization and before delegating to ORDER, DELIVERY or PAYMENT workflows.

The lock is held until the surrounding transaction commits or rolls back. A second concurrent confirmation for the same tenant operation waits for the first transaction. After the first execution commits, the waiting request observes the consumed confirmation and existing typed projection and follows the existing idempotent replay path instead of materializing or sending the operation twice.

The advisory key is a deterministic 64-bit FNV-1a hash over both UUIDs. Hash collisions can only cause unnecessary serialization, not cross-tenant authorization or data access, because tenant/operation ownership is still checked independently by the application and database constraints.

## PAYMENT external effects

PAYMENT is the critical case because provider creation happens before the local payment projection is committed. The operation fence now runs before the payment workflow can call a provider. This closes the race where two confirmations could both reach the provider before one local insert lost on `UNIQUE(operation_id)`.

The provider request still uses the stable key:

`<operationId>` (UUID estable de la operación PAYMENT)

That key remains the crash/retry idempotency boundary for providers that honor idempotency keys. The V36 execution fence protects concurrent in-flight attempts inside Helvoca; the provider key protects retried remote creation after uncertain network outcomes.

## Database integrity

V36 adds tenant-aware composite foreign keys from the main typed projections to `business_operation(id, business_id)`:

- `business_order`
- `business_delivery`
- `business_payment`
- `booking`

Existing `UNIQUE(operation_id)` constraints remain the final database guard against duplicate materialization. The composite foreign keys additionally guarantee that a typed projection cannot reference an operation owned by another tenant.

## BOOKING

BOOKING already entered V36 with two independent transaction fences from V35:

- operation lock, preventing duplicate confirmation of one proposal
- service-slot lock, preventing two different proposals from materializing the same service/time slot concurrently

V36 keeps that protection and aligns ORDER, DELIVERY and PAYMENT with the same concurrency principle.

## Guarantee boundary

The V36 guarantee is exactly-once materialization per universal operation for Helvoca-controlled transactional projections, plus serialized remote PAYMENT creation with a stable provider idempotency key.

A third-party provider that ignores its idempotency key cannot be made mathematically exactly-once across arbitrary network failure purely from the caller side. Durable dispatch/recovery for broader asynchronous side effects belongs to the following Persistent Job / Outbox Engine roadmap block.
