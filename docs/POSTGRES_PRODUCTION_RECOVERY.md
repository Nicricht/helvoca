# PostgreSQL production recovery and HA gate

## Purpose

RecepVoz production data must not rely on a single unverified volume. A backup is useful only when the team knows how it will be restored and what evidence is required before a recovery path is trusted.

This runbook covers the production PostgreSQL service used by Helvoca/RecepVoz.

## Current production posture

As of 2026-10-01:

- PostgreSQL runs on Railway with a persistent volume mounted at `/var/lib/postgresql/data`.
- A **DAILY Railway volume backup schedule is enabled**.
- PostgreSQL is currently single-node.
- PITR is not yet enabled because the connected Railway automation surface does not expose the PITR enable mutation.
- Production application connections remain tenant-aware and PostgreSQL RLS is the authoritative cross-tenant boundary.

Railway documents daily volume backups as retained for six days. Volume-backup restore is an in-place operation on the source service, so it must not be used merely as a production recovery drill.

## Recovery layers

### Layer 1: Railway volume backup

Purpose: recover the whole database to a scheduled snapshot.

Rules:

1. Keep the DAILY schedule enabled.
2. Never test restore by replacing the live production volume.
3. Before any real in-place restore, record the incident timestamp, desired recovery point, current deployment SHA, current database service and current volume.
4. Treat the restore as a production change and verify application health plus tenant isolation afterward.

### Layer 2: Point-in-time recovery

PITR is the preferred safe recovery drill because Railway restores into a new sibling PostgreSQL service and leaves the source service untouched.

PITR must be enabled before it is needed. The restore window starts only after the first post-enable base backup.

Enable from either:

```bash
railway postgres pitr enable --service Postgres
```

or Railway Dashboard -> Postgres -> Backups -> Enable PITR.

After PITR is enabled:

1. Confirm the source Postgres returns online.
2. Confirm PITR reports healthy archiving.
3. Wait until the first restorable timestamp exists.
4. Restore to a **new sibling service**.
5. Do not change the production application's connection string.
6. Verify the sibling contains:
   - `flyway_schema_history`;
   - `business`;
   - `app_user`;
   - `booking`;
   - `business_operation`;
   - `business_payment`.
7. Record restore duration and restore point age.
8. Delete only the temporary restored sibling after evidence is captured.

A PITR drill is successful only when the restored sibling starts, the required schema exists and the production source was never modified.

### Layer 3: provider-independent logical dump

Use `scripts/ops/postgres-restore-drill.sh` to prove that a logical dump can be restored independently of Railway snapshots.

The script requires PostgreSQL client tools:

- `pg_dump`
- `pg_restore`
- `psql`
- `createdb`
- `dropdb`

Provide connection variables for a safe administrative connection to the source server:

```bash
export PGHOST=...
export PGPORT=5432
export PGUSER=...
export PGPASSWORD=...
export PGDATABASE=...

bash scripts/ops/postgres-restore-drill.sh
```

The script:

1. creates a custom-format logical dump;
2. creates a scratch database named `helvoca_restore_drill_<timestamp>`;
3. restores into the scratch database with `--exit-on-error`;
4. verifies Flyway history and five core tables;
5. drops the scratch database automatically.

It never drops or replaces the source database.

## RPO and RTO

Until PITR is enabled and certified:

- snapshot RPO is bounded by the daily backup interval;
- actual RTO is **unknown** until a restore drill is measured.

After PITR is certified, record the observed restore duration and oldest/newest restorable timestamps here or in the incident/release evidence.

Do not invent an RTO from deployment times.

## HA gate

Do not convert PostgreSQL to HA merely because the option exists.

HA becomes the next infrastructure step when all of these are true:

- backup schedule is healthy;
- at least one isolated restore drill has passed;
- PITR is enabled and healthy;
- the application has reconnect/retry behavior compatible with a database failover;
- the additional recurring Railway cost is accepted;
- a maintenance/failover window has been selected.

When approved, prefer a Railway Postgres HA cluster with at least two streaming replicas behind Railway's HAProxy/Patroni topology. After conversion, run a controlled failover certification and verify:

- API reconnects without cross-tenant leakage;
- background jobs recover safely;
- payment and webhook idempotency remains intact;
- inventory reservations remain consistent;
- health checks return healthy after failover.

## Release evidence

A database-hardening change is not complete merely because code was committed.

Required evidence:

- fail-closed tenant-scope regression tests green;
- `PostgresRowLevelSecurityIntegrationTest` green;
- PR Full Gate green on the exact final commit;
- exact main merge SHA green;
- Railway deploy of that exact main SHA healthy;
- daily backup schedule still enabled;
- PITR/restore drill tracked separately until it can be certified without touching production.
