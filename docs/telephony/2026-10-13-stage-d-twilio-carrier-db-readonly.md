# Helvoca · Stage D GET-only Twilio x PG18 production binding audit

Date: 2026-10-13. **Status: AUDIT TOOL READY FOR OPERATOR EXECUTION; REAL CARRIER PROOF PENDING.**

## Already established

- Stage A: private pg_dump PostgreSQL 18 restored successfully on isolated Docker (operator output).
- Stage B: V100–V103 applied with psql to a *disposable restored copy*, while synthetic Spring Boot Flyway/PostgreSQL 18 tests passed separately.
- Stage C: 7/7 internal offline CI gates, PR #808 draft, `main` unchanged.
- Stage D: operator-provided private report `PRODUCTION_FLYWAY_BASELINE_VERIFIED`, with no blockers, proving according to the read-only script that the live production DB remains on V99 and does not yet have V100–V103.
- Railway backend has variable **names** `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL`, and a configured subaccount SID. **Values are never copied into GitHub or this document.**

## A single bounded read-only execution, on user's own Windows

`scripts/ops/twilio_carrier_db_readonly_audit.py` is intentionally **not** a production application endpoint and **not** part of CI's network traffic. The operator runs it locally using the already authenticated Railway CLI's **read-only variable injection**:

```powershell
$p = Join-Path $env:TEMP 'helvoca-stage-d-carrier-db.py'
$u = 'https://raw.githubusercontent.com/Nicricht/helvoca/1f06e98879f1b839e19267571066723df7940680/scripts/ops/twilio_carrier_db_readonly_audit.py'
Invoke-WebRequest -Uri $u -OutFile $p -ErrorAction Stop
if ((Get-FileHash $p -Algorithm SHA256).Hash.ToLowerInvariant() -ne '18df3e48dbea8ba5837768e8f3c3e1d668ad60feb11ea19fc2347b1d90919237') {
    throw 'Stage D: script hash mismatch'
}
railway run --project 41988ee6-f9f6-48eb-b726-abb973bb5740 --environment 8bbc755a-58b5-46e8-8227-eefa4adbbce4 --service edbfc4d2-3487-4cfb-ab7c-52fdb4060c4a --no-local py -3 $p --phone '<pilot-e164>' --tenant '<pilot-tenant-uuid>'
```

The operator passes the **specific already known pilot** E.164 and tenant UUID. Replace only `<pilot-e164>` and `<pilot-tenant-uuid>`; the example leaves these personal values outside GitHub. The script is pinned to commit `1f06e98879f1b839e19267571066723df7940680`, verified SHA-256 `18df3e48dbea8ba5837768e8f3c3e1d668ad60feb11ea19fc2347b1d90919237`, and was exercised by 18 offline safety checks. All Twilio credentials stay in the local process environment. No user needs to paste secrets into chat, GitHub, command-line arguments or terminal output.

Internally the tool:

1. Uses SSH into the pinned Railway PostgreSQL service, **one hard-coded `BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY` + SELECT**; E.164 is validated against a strict ASCII pattern before building the SQL. It collects at most three matching `phone_number` rows in memory and uses `to_jsonb(p)->>'field'` so absent V100 columns on live V99 result in `NULL` instead of a migration.
2. Uses Python standard-library HTTPS exclusively against the `https://api.twilio.com` REST 2010 account/IncomingPhoneNumbers endpoints with **GET** and an in-memory Basic Auth header from the injected Railway backend environment. It checks the parent and exactly one configured subaccount if present, requiring exactly one owner, no pagination ambiguity, then verifies account details and number list + detail.
3. Checks current E.164, PN SID, account SID/subaccount, tenant UUID, provider, active state, Twilio voice capability, secure configured `POST` webhooks and no conflicting voice application/trunk, plus DB provenance and verified timestamp. Any missing or conflicting evidence yields **BLOCKED**, without repairs.
4. Stores only status, opaque failure codes, timestamps and safe counts in a randomly named private JSON under `%LOCALAPPDATA%\Helvoca\private-evidence` (or `private-backups` fallback). Does not print/write SIDs, customer data, phone numbers, Twilio authentication, HTTP bodies, database rows or webhook URLs. Does not emit raw stderr. No GitHub uploads, CI secrets, deployed code, mutations, calls or paid requests.
5. Always prints `COMMERCIAL_GO=NO-GO` equivalently in the evidence even for a perfect metadata match; this proof does **not** demonstrate real media-stream audio, AI model, rate limits or commercial safety.

**Source-version caveat:** live Flyway V99 cannot contain the V100 provenance columns as migrated through Flyway. Therefore `DB_ACCOUNT_SID_MISSING_OR_MISMATCH`, `DB_PROVENANCE_UNVERIFIED` and `DB_VOICE_TIMESTAMP_MISSING` are anticipated blockers if the legacy rows have not been reconciled. These must **not** be auto-filled. No migration or deployment is authorized by this audit.

## Stop conditions and remaining gates

The current provider / database matching result is `STAGE_D_TWILIO_CARRIER_DB=PENDING_OPERATOR_EXECUTION`, not success. Once the actual safe result arrives, record only the status and blocker codes in the draft PR, no sensitive report content. The account ownership and cross-tenant binding may have changed since previous screenshots; do not certify from stale observations.

Before live calls / commercial release: separately verify rollout switches and pilot PAUSED row, durable encrypted backup, approved change window/rollback and explicit consent. `PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO` throughout.

**Do not merge**, run Flyway in Railway, edit `phone_number`/external IDs, change Twilio webhooks, purchase numbers, or make test calls as part of this audit.
