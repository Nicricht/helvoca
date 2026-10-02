# Backend / Database Performance Hardening

Branch: `perf/backend-db-performance-hardening`

Base: `3174ff02e62100feb970f9afea6c73334eaab6e6`

Risk: **HIGH**

## Production evidence before changes

### PostgreSQL
- CPU average, last 24h: ~0.21%
- Memory average, last 24h: ~61 MB of ~1 GB
- Disk: ~0.126 GB of 0.5 GB
- No active FATAL/PANIC errors on the current deployment
- Flyway schema version 93; no pending migration on the last certified deployment

### API
- Current-deployment HTTP sample: 263 requests
- p50: 4 ms
- p95: 130 ms
- p99: 239 ms
- max: 454 ms
- `GET /api/v1/commercial/orders`: 52 ms in production sample
- CPU is lightly loaded
- Memory has approached the ~1 GB Railway limit during the last hour; historical 7-day aggregate includes samples above 1 GB, requiring investigation before tuning

### Historical defect signal
On 2026-10-01 production logs showed a repeated database permission failure on `twilio_certification_command`. That failure is not present in the current deployment logs and must not be assumed active without fresh evidence.

## Acceptance criteria
1. Find concrete memory-pressure sources before changing JVM/pool limits.
2. Do not add speculative indexes; require query-plan or repository-access evidence.
3. Add regression/observability coverage for concrete defects.
4. Preserve tenant isolation and external-effect safety.
5. Fast Gate + Full Gate green on exact final HEAD.
6. Sync latest `main` before merge.
7. Exact-main CI + Railway exact-SHA health verification after merge.

## Next step
Inspect runtime allocation/concurrency patterns, scheduled jobs, repository query shapes, schema indexes, and current production deployment/CI state. Implement only evidence-backed changes.


## Audit findings

### Evidence-backed defects
1. `CommercialOperationsAdminService.orders()` fetched every tenant order, then truncated to 100 in Java.
2. The same surface loaded order lines one order at a time, creating an N+1 pattern: up to 101 repository queries for one 100-order response.
3. Deliveries, quotes and leads also fetched all tenant rows before truncating to 100 in Java.

Existing PostgreSQL indexes already support the intended access paths:
- `business_order(business_id, created_at DESC)`
- `business_delivery(business_id, created_at DESC)`
- `business_quote(business_id, created_at DESC)`
- `business_lead(business_id, created_at DESC)`
- `business_order_line(order_id, created_at)`

No duplicate index migration is warranted.

### Memory / pool investigation
Critical in-memory structures were reviewed:
- Twilio WebSocket state is removed on connection close.
- Gemini pending audio is bounded and cleared.
- call-summary in-flight IDs are removed in `finally`.
- certification schedulers shut down.
- simulator session state is removed by the simulator finish flow.

Production Railway logs for the last day do not show Hikari `Failed to validate connection` / `Pool is empty` warnings. Therefore no JVM or Hikari tuning is being introduced without heap/direct-buffer evidence.

## RED evidence
GitHub Actions run #3371 on commit `29da21cee0c6b316b182e3bbddb632b169f33d42` failed exactly at:
`CommercialOperationsAdminServiceTest.ordersUsesBoundedRepositoryQueryAndBatchLoadsLines`
with expected 2 results but actual 0, because production code still called the legacy unbounded repository method.

## Implemented hardening
- PostgreSQL performs the 100-row bound for orders, deliveries, quotes and leads.
- Order lines are fetched in one batch for the bounded order set.
- Single-order mutation detail still uses the focused single-order line query.
- Real PostgreSQL integration coverage verifies the 100-row tenant-scoped repository behavior.
- Focused unit coverage verifies the batch line-loading contract and bounded list methods.

## Final verification pending
Fast Gate, Full Gate, exact-head sync with `main`, merge, exact-main CI and Railway verification remain pending for the final HEAD.


## Dashboard hardening

### Second evidence-backed defect
`OperationsDashboardService` loaded all tenant bookings, customers, requests and open unanswered questions into Java memory in order to calculate four counters and render at most ten request/question rows.

### RED evidence
GitHub Actions run #3384 on commit `65aed50fb3ab9b1a13d006b2312c34d0715b0e12` failed exactly at:
`OperationsDashboardServiceTest.dashboardCountsAndRecentListsStayBoundedInRepositories`
with expected bookingsToday=7 but actual=0, proving the service still ignored the new bounded/count repository operations.

### GREEN implementation
Dashboard now delegates to PostgreSQL:
- today's non-cancelled booking count;
- today's new-customer count;
- open/in-progress request count;
- open unanswered-question count;
- most recent 10 requests;
- most recent 10 open unanswered questions.

It no longer materializes the full tenant tables for these dashboard values.

### GREEN evidence
Implementation commit `529ff88c7a6ded5c081fb419a51101f5cc83d91c`:
- Fast Gate success;
- Golden Journey success;
- 356 targeted tests, 0 failures;
- `OperationsDashboardServiceTest` green.

PostgreSQL integration commit `7e472a4253fd81d0b8276a47da6512a6ceefdb06`:
- Fast Gate success;
- Golden Journey success;
- 357 targeted tests, 0 failures;
- `OperationsDashboardReadRepositoryIntegrationTest` green against PostgreSQL 16;
- validates tenant-scoped counts and SQL Top-10 behavior.

## Scope decision
No JVM heap limit, Hikari pool setting, or new PostgreSQL index is changed in this PR. Production evidence did not justify those changes. The hardening is intentionally limited to observed unbounded read amplification and N+1 behavior.
