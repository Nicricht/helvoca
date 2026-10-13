# Stage A: PostgreSQL 18 offline restore, operator evidence

Evidence scope: **user-provided Windows PowerShell transcript, 2026-10-13**.
This attestation is intentionally restricted to nonsensitive metadata and the
observed program result. The assistant has **not** directly inspected the local
archive or private JSON report. No database dump, customer records, credentials,
or private report are stored in GitHub.

## Evidence received

The operator ran `scripts/ops/railway_pg18_snapshot_drill.py` using Railway CLI
authenticated locally and the independent Docker PostgreSQL 18 test.

The tool printed:

```text
Backup stored ONLY on this computer: [private Windows directory]/helvoca-postgres18-20261013T041250Z-355b64.dump
SHA-256: 46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18
RESTORE VERIFIED in network-disabled disposable PostgreSQL 18.
Local evidence: [private Windows directory]/helvoca-postgres18-20261013T041250Z-355b64.private.json
```

## Interpretation, limitations and gates

- **LOCAL_RESTORE_VERIFIED_BY_OPERATOR_OUTPUT=true**: the provided output
  satisfies this script's final success path. Source is the operator's
  local transcript, **not** an independent third-party inspection.
- The script validates `PGDMP` signature and minimum size, calculates SHA-256,
  feeds the archive into isolated local `postgres:18` using `pg_restore`,
  checks presence of five core tables plus nonempty Flyway history, and
  terminates/removes the temporary Docker container.
- The transcript alone does **not** establish the archive's exact byte size,
  full row-by-row equality with live production, current offsite redundancy,
  disk encryption, or RLS enforcement. Do not invent these facts.
- Production was not the restore destination. The script's production
  operation is an SSH `pg_dump` (read-only); no database restoration was
  requested on Railway.
- **Do not upload** the local archive or private JSON report to any public
  repository or CI logs. Preserve a secure, encrypted independent copy.
- **Flyway V100–V103, DB constraints, RLS/tenant-isolation and rollback
  compatibility remain to be tested in Stage B** before any new code deploy.
- Stage A evidence gate: `STAGE_A_LOCAL_RESTORE_VERIFIED`
  (operator-reported evidence). `STAGE_B_MIGRATIONS=NOT_STARTED`.
- Deployment, calling and commercialization gates remain
  `PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`,
  `COMMERCIAL_GO=NO-GO`.

This attestation does **not** change Railway services, volumes, databases,
secrets, Twilio settings or branch integration state.
