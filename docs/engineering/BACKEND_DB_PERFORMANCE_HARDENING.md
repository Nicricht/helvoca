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
