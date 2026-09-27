# Self-Healing / Reconciliation Engine V6

V6 coordinates existing durable safety mechanisms instead of duplicating them.

## Goal

Detect tenant-scoped states that should not coexist, explain the evidence, and automatically repair only cases that are demonstrably idempotent and local to Helvoca-controlled state.

## Detection

`GET /api/v1/reconciliation/anomalies` is read-only and BUSINESS_ADMIN-only.

V6 detects:

- `PAYMENT_SUCCEEDED_ORDER_NOT_CONFIRMED`
- `PAYMENT_SUCCEEDED_INVENTORY_RESERVED`
- `PAYMENT_TERMINAL_INVENTORY_RESERVED`
- `OUTBOUND_STUCK_PREPARED`
- `ORPHAN_RESERVATION`
- `RECOVERABLE_WEBHOOK_FAILED`
- `JOB_STUCK`
- `JOURNEY_STATE_MISMATCH`

Each anomaly returns severity, evidence, subject/operation identifiers, whether an automatic repair is allowlisted, and the suggested action.

## Repair boundary

`POST /api/v1/reconciliation/repair` defaults to dry-run unless `dryRun=false` is explicitly supplied.

Only three repairs are allowlisted:

1. verified `SUCCEEDED` payment + active order inventory reservation -> consume the existing reservation;
2. terminal payment with no succeeded/pending/requires-action replacement + active order reservation -> release the reservation;
3. active order reservation whose referenced operation no longer exists -> release the orphan reservation.

These operations reuse `InventoryService` locking, tenant scoping, idempotency and movement/audit behavior.

V6 deliberately does **not** automatically:

- send or queue real WhatsApp traffic;
- re-fire failed payment webhooks;
- force a persistent job execution;
- rewrite a paid order from a non-confirmed state;
- rewrite commercial journey metadata.

Those cases remain recommendation-only because they may cross provider, customer or business-policy boundaries.

## Durable repair audit

Actual repairs are claimed through `reconciliation_action` with a tenant-scoped idempotency key. Outcomes are `STARTED`, `COMPLETED` or `FAILED`.

Failed repair attempts can be retried. Completed repair keys are not applied twice.

## Safety properties

- BUSINESS_ADMIN authorization.
- Tenant-scoped detection and repair.
- Dry-run by default.
- Explicit allowlist for mutation.
- No provider side effects.
- Existing inventory row locks remain authoritative.
- Durable repair audit with RLS.
- No new background auto-healer is enabled in V6. Automatic scheduling can be considered only after pilot evidence.
