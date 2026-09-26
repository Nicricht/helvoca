# V37 Persistent Job / Outbox Engine

V37 adds a durable, tenant-scoped work queue backed by PostgreSQL. It replaces the assumption that delayed or retryable work can live only in the application process.

## Delivery model

The engine is intentionally **at-least-once**. A worker may die after an external side effect succeeds but before the local job is marked `SUCCEEDED`. After the lease expires another worker is allowed to retry the job. Therefore every side-effecting handler must use its own stable idempotency boundary.

For outbound messaging, the existing V34 message idempotency key is passed through to the provider. V36 uses the same principle for payment provider creation with `<operationId> (UUID)`.

This avoids claiming impossible cross-network exactly-once semantics while still guaranteeing durable recovery and duplicate-safe execution when downstream providers honor idempotency.

## Persistent job lifecycle

`persistent_job` stores:

- tenant (`business_id`)
- optional universal operation (`operation_id`)
- controlled `job_type`
- idempotency key unique per tenant
- JSON payload interpreted only by a registered backend handler
- `attempt_count` and `max_attempts`
- `next_attempt_at`
- lease owner and expiration
- last failure code/message
- terminal timestamps

States:

`PENDING -> RUNNING -> SUCCEEDED`

Retryable failure:

`RUNNING -> FAILED -> RUNNING`

Terminal paths:

`RUNNING -> DEAD_LETTER`

`PENDING|FAILED -> CANCELLED`

## Claiming and concurrency

Workers claim one row with `FOR UPDATE SKIP LOCKED` and atomically change it to `RUNNING` with a bounded lease. The claim transaction completes before the handler executes, so external I/O does not hold a database row lock.

Multiple workers can process different jobs concurrently. They cannot claim the same live lease. If a worker dies, the lease expires and another worker may reclaim the job. A stale lease that has already consumed the final allowed attempt is moved to `DEAD_LETTER` rather than remaining `RUNNING` forever.

## Retry policy

Retries use persisted `next_attempt_at` with exponential backoff starting at 5 seconds and capped at 15 minutes. Attempt budgets are bounded from 1 to 100. Permanent handler failures go directly to dead-letter. Unknown runtime failures are retried only inside the configured attempt budget.

## Handler safety

The database does not execute arbitrary payload instructions. `job_type` maps to a compiled `PersistentJobHandler` registered by the application. Unknown or duplicate handlers fail closed.

The initial handler is:

`OUTBOUND_MESSAGE_DISPATCH`

Its payload contains only the durable outbound message identifier. Tenant ownership is revalidated when dispatch loads the message.

## Outbound outbox integration

`POST /api/v1/outbound-messages/{messageId}/queue` creates or reuses the durable dispatch job and moves the message to `QUEUED` in the same transaction. Repeating the request returns the same job because the key is:

`outbound-message-dispatch:<messageId>`

The old synchronous dispatch endpoint remains available for explicit administrative use. V37 does not silently change existing API behavior.

## Operational visibility

`GET /api/v1/jobs` returns the tenant's most recent jobs without exposing raw payload contents. Pending or failed jobs may be cancelled with `POST /api/v1/jobs/{jobId}/cancel`.

## Production safety

The worker is disabled by default with `app.jobs.enabled=false`.

Even if the worker is enabled, outbound delivery still requires the independent V34 safety switch `app.outbound.delivery-enabled=true` and a configured provider. V37 does not enable real WhatsApp messages, payments, calls or any certification flag.
