# Helvoca engineering invariants

These are system truths and release gates that relevant changes must preserve. Each item names existing automated evidence where available. A policy invariant is not a claim that every historical branch has already been certified against it.

## Tenant isolation

A tenant must never read or mutate another tenant's protected data.

Evidence includes `PostgresRowLevelSecurityIntegrationTest`, tenant-aware repository/service tests, and cross-tenant commercial integration tests.

## Inventory integrity

Concurrent reservations must never reserve more physical stock than is available, and payment/webhook replay must not consume the same reserved inventory twice.

Evidence includes `InventoryPostgresConcurrencyIntegrationTest`.

## Confirmation and duplicate order safety

A consumed confirmation token must behave idempotently. Repeated confirmation must not create a duplicate order or duplicate terminal business effect.

Evidence includes `UniversalConfirmationIntegrationTest` and order workflow idempotency/regression coverage.

## Payment truth

Preparing or requesting a payment is not equivalent to a completed payment. Provider intent creation, confirmation, webhook processing, and terminal payment state must remain distinct, and retries must use stable idempotency protection.

Evidence includes `PaymentWorkflowServiceTest`, payment webhook tests, reconciliation tests, and commercial journey tests.

## Simulator and test external side effect safety

Simulator and automated test paths must never be used to intentionally trigger a real external side effect such as a production phone call, WhatsApp message, payment, destructive production mutation, or physical delivery merely to make a test pass.

Where simulator/provider isolation is being changed, the release gate must include explicit side-effect isolation tests or sandbox/mocked provider evidence before integration.

## Final-state truth

A queued request, accepted provider call, draft, quote, or partial success must never be reported as the final business outcome unless application state proves the terminal state.

## Verification freshness

Evidence from an earlier commit does not certify a later implementation or engineering-contract change. Relevant gates must be rerun on the exact final commit.
