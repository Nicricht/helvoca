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

## 2026-10-14 confirmed evidence and remaining recovery gate

This section supersedes **only** older unverified present-tense claims below. Historical notes dated 2026-10-01 are not proof of today's backup schedule or successful recovery.

- **Railway snapshot:** The operator supplied a production Postgres > Backups screenshot displaying a volume backup at 2026-10-14 20:21, displayed size **126 MB**, with the UI announcing its next backup in four hours. The operator separately confirmed Daily / Weekly / Monthly scheduling in [master issue #823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6181515770). Neither the current Railway connector nor the screenshot proves all future scheduled jobs will succeed. Monitor actual completed snapshots and freshness. The operator reports Pro purchased; Railway connector access does not independently inspect subscription billing.
- **Already completed independent recovery:** The operator reported the exact terminal success status `C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED` after using `scripts/ops/c1_independent_remote_restore.py` on the browser-downloaded Google Drive `age` ciphertext, matching the remote SHA and original dump SHA, with a disposable **network-isolated PostgreSQL 18**. [Original operator attestation](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6160941717). The private report and passphrase have not been read by this agent. **Do not require re-downloading or restoring that same archive with a second tool just to duplicate this result.** This is not a restoration of a Railway native volume snapshot.
- **Current Railway environment:** API and PostgreSQL 18 services reported online, database volume attached, no staged changes; the `helvoca-backup-cron` application service has no deployed cron job. That dormant service is not the Railway **native volume-backup schedule**.
- **Backup escrow risk and accepted storage choice:** The ciphertext resides in the Google Drive **institutional account explicitly chosen by the operator**, who accepted the residual risk that affiliation/policies/quota may end access (see [C1 backup policy](C1_BACKUP_AUTOMATION_POLICY.md)). **Do not require a personal Google Drive account or a second account for C1.** Still verify ongoing access, available quota, a recovery contact and secure separate access to the existing `age` passphrase by an authorized custodian. Keep secrets, plaintext dumps and private attestation JSON out of GitHub.
- **Recovery targets OWNER APPROVED, ability NOT measured (2026-10-14 Chile):** The owner expressly accepted **maximum RPO target 24 hours** and **maximum RTO target 4 hours**. This signs off business objectives only; it does **not** certify achieved restoration time/data freshness or constitute a customer SLA. Still measure end-to-end recovery including a functioning API, nominate an accountable operator, and verify completed snapshot freshness at the authorized release window. A daily schedule does not guarantee a 24-hour data recovery point; isolated dump-restore duration is **not** full-service RTO. Never infer a numeric RTO from a 126 MB snapshot.
- **No in-place restore drill:** Railway volume-backup Restore on the live Postgres service may replace its attached production volume. An isolated drill must not overwrite production. PITR remained **disabled** in the operator's screenshot. PITR enablement or a new Railway environment may change resources/costs and needs separate authorization; the already successful independent offline logical restore remains useful even without PITR.

**Operational choice:** Native Railway Pro backups are already scheduled according to the operator. The separately developed `helvoca-backup-cron`/rclone OAuth automation is **not an additional mandatory C1 prerequisite** and remains disabled unless it is separately needed, cost-reviewed and authorized. Keep the independently recovered institutional Drive archive as an additional recovery layer.

**Release disposition:** `C1_RECOVERY_FROM_INDEPENDENT_COPY=PASS_OPERATOR_REPORTED`; `C1_BACKUP_SCHEDULE=OPERATOR_CONFIRMED`; `C1_CUSTODY_RPO_RTO=OPEN`; `C1_COMMERCIAL_ACCEPTANCE=NO-GO`. Before C2-B (`main` auto-deploy + Flyway V100–V103), check a sufficiently fresh recoverable backup for the actual release window, the current Flyway catalog in a SQL READ ONLY transaction, and owner-approved recovery/runbook/rollback. The restored archive from 2026-10-13 does not certify the age of a future pre-release backup.

### 2026-10-15 C1 verified archive metadata vs fresh release snapshot

- Via authenticated Google Drive metadata-by-existing-ID, the **historical** encrypted archive `helvoca-postgres18-20261013T213529Z-ced3d8.dump.age` remained readable in the selected institutional account, `HelvocaOffsite`, at **673001 bytes**, modified `2026-10-13T21:35:55.021Z`, `shared=false` and owner-only. No download, decrypt, repeat restore or hash verification was performed on 2026-10-15; preserve the **previous** owner-reported isolated PG18 recovery. A Drive search returning no children despite the direct-ID response does **not** prove deletion.
- This 2026-10-13 ciphertext by itself cannot demonstrate **24-hour RPO freshness** on 2026-10-15. The Railway native backup record (126 MB, Daily/Weekly/Monthly schedules) is separately operator-confirmed on 2026-10-14. The connected Railway service API does **not** expose backup listing/individual completion timestamps or a safe native snapshot restore to a separate instance. Neither an API healthcheck nor attached volume size proves a recoverable, fresh native snapshot.
- Railway Postgres' **actual latest successfully completed native backup** must be identified at the **approved release window**, including backup completion time and recoverability under the signed-off RPO goal. Do not presume a newer snapshot or use in-place Restore just to test the snapshot. Source of evidence: Railway operator's authenticated Postgres > Backups view, shared only as non-secret completion metadata if needed.
- RTO **four hours** is approved as a goal, still **unmeasured for the whole SaaS**. A safe isolated PostgreSQL recovery and cloud-download restore was already operator-reported PASS and must **not** be repeated merely to obtain a second passing report. Any new *timed full-service* recovery drill requiring provider costs, new Railway services or deployment is a separate explicitly authorized experiment, never an implicit obligation triggered by this documentation.
- The authentic read-only Flyway baseline probe run from the owner's authenticated Railway CLI returned `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` on `2026-10-15T02:29Z`, **operator-reported**, with a private local report not read by the agent. This validates the expected **pre-deploy** PG18 / V99 state, **not** V100–V103 migration or release permission.
- The integrated release candidate [PR #846](https://github.com/Nicricht/helvoca/pull/846) was certified in its previous exact HEAD `3ead3f0698f18780a7629aab0300b6381e863966`: 2262 Java, 620 Playwright and differential Java coverage 100% on [CI #38667741615](https://github.com/Nicricht/helvoca/actions/runs/38667741615). If this document update changes its HEAD, verify CI on the *new* HEAD before claiming current certification. The live Railway API/PG18 remained on older `main`, with no staged changes.

**Operational gate:** `RECOVERY_TARGETS=OWNER_APPROVED`, `C1_OFFSITE_RESTORE=PASS_OPERATOR_REPORTED`, `RAILWAY_NATIVE_SNAPSHOT_FRESHNESS=UNVERIFIED`, `FULL_SERVICE_RPO_RTO_MEASURED=NO`, `FLYWAY_PREDEPLOY=PASS_OPERATOR_REPORTED`, `C2_DEPLOY=NO-GO`. No automatic merge, migration, PITR, provider call, or restore follows from this note.

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
