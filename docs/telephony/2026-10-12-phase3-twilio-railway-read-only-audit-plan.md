# Helvoca · Part 3/8 · Twilio and Railway PostgreSQL audit preparation

**12 October 2026. Status: OFFLINE PREPARATION ONLY. PRODUCTION / COMMERCIAL: NO-GO.**

This is an operator runbook and an evidence schema, **not a record that a real carrier or database audit occurred**. It grants no production access. Preparation is limited to version-controlled source, local synthetic fixtures and CI that does not contact production. **No Twilio, Railway, PostgreSQL, Gemini or external provider request is authorized by this document.**

## 1. Exact source baseline and separation of responsibilities

- Integration parent: PR #792, `85be445e86ddefb15e58a4904d09e002cb1150e9`, source tree including PR #790 and PR #791; production `main` remains separate.
- Intended pilot: an already-owned Twilio phone, **never buy a replacement**. The real E.164, PN SID, AC SID and tenant identifiers belong only in a restricted operator evidence vault, not in PR bodies, CI output, screenshots, general logs or client responses.
- Offline tools already present: `TwilioLegacyBindingInspector` for persisted-row diagnostics; `TwilioExistingNumberOwnershipVerifier` for a **future explicitly authorized** authenticated carrier read; the restricted two-person approval workflow is **not** part of this read-only audit.
- Runtime call guard `TwilioCallBindingGuard` accepts only `MANAGED_PURCHASE`; `VERIFIED_EXISTING` is intentionally non-routable. The production launch decision is **NO-GO**, independent of any audit result.
- Never treat the local default `gemini-3.8-live`, a nonempty API key setting, a successful CI run or a screenshot as provider/runtime attestation.

## 2. Authorization matrix and hard stops

| Scope | Authorization in Part 3 | Acceptable operation |
| --- | --- | --- |
| A0: offline repository review and synthetic tests | Authorized by task | Read GitHub code and run isolated local tests without provider connections |
| A1-T: current Twilio ownership inspection | **NOT AUTHORIZED** | Future operator-approved **GET-only** carrier queries, bounded to one named number and owning account/subaccount |
| A1-D: current Railway PostgreSQL inspection | **NOT AUTHORIZED** | Future operator-approved **read-only transaction**, one named tenant and number, no connection until approval |
| A1-C: deployed non-secret configuration metadata | **NOT AUTHORIZED** | Future operator-approved safe metadata review, never output API keys or connection strings |
| A2: enroll/reconcile, alter DB or callback, flags, deploy or merge | **NOT AUTHORIZED** | Requires independent review and separate explicit written approval |
| A3: a real telephone call, Gemini session or charge | **NOT AUTHORIZED** | Requires a later live-pilot decision, consent, time window, budget and emergency stop |

**Hard stops:** missing individual A1 authorization; mismatched tenant, E.164, PN SID or account; ambiguous or partial carrier response; unregistered pilot scope; provider authentication failure; unknown/changed webhooks; any inferred provenance; unexpectedly writable DB session; any exposure of tokens/identifiers. Stop, record an opaque failure code and keep **NO-GO**. A request to "CONTINÚA" is permission to develop the next part, **not** A1/A2/A3 authorization.

**CI caution:** `.github/workflows/ci.yml` has `meta-webhook-public-smoke`, which makes HTTPS calls to the deployed production public domains on pull requests targeting `main`. Do **not** open a main-targeted PR or start that workflow as part of the no-production-access audit. Preserve the existing smoke test and all seven mandatory jobs; skipping, replacing or pretending they passed would be misleading. A draft PR targeted at the isolated integration branch does not trigger the normal main-targeted PR workflow. Full exact-HEAD 7/7 certification remains **PENDING** until a separately permitted safe execution plan exists. Do not claim 100% differential coverage on a new HEAD without its actual gate results.

## 3. A1-T carrier audit: specified but not executed

Use only `TwilioExistingNumberOwnershipVerifier.verify(destinationE164, ownerAccountSid, expectedPhoneSid)` under a trusted platform operator after **specific written** A1-T approval, or an equivalently reviewed authenticated **GET-only** carrier workflow. It currently checks:

1. Valid E.164 `+[1-9]...`, `AC` account SID and optional `PN` SID; owning account is the configured parent or explicitly allowlisted `HELVOCA_TWILIO_SUBACCOUNT_SID`.
2. Authenticated GET of `/2010-04-01/Accounts/{sid}.json` and owner/parent state.
3. GET of `/IncomingPhoneNumbers.json?PhoneNumber=...`; exactly one result, no next page or ambiguous match.
4. GET of `/IncomingPhoneNumbers/{sid}.json`; **both list and detail** must match E.164, PN SID and owning AC SID.
5. Number must be `in-use`, accept voice, advertise voice capability, use `POST /webhooks/v1/twilio/voice` and `POST /webhooks/v1/twilio/status` at the configured secure `TWILIO_PUBLIC_BASE_URL`, and have no conflicting Voice Application SID/trunk binding.

Output only redacted codes such as `VERIFIED`, `ACCOUNT_NOT_ALLOWED`, `PHONE_IDENTITY_MISMATCH`, `WEBHOOK_MISMATCH`, `VOICE_UNAVAILABLE`, `AMBIGUOUS_NUMBER` or `PROVIDER_UNAVAILABLE` to ordinary project records. `VERIFIED` establishes **carrier read evidence at that instant only**. It does not attest a Railway row, tenant, live routing, bidirectional Media Streams or production readiness. Never log raw provider responses, authentication headers or full identifiers.

## 4. A1-D Railway PostgreSQL audit: read-only design, NOT run

Only after independent A1-D authorization, a named operator uses an explicitly read-only database account/session with appropriate system-scope permissions. Enforce `BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;`, parameterized input, row/statement limits, and `ROLLBACK;` on exit. Verify `transaction_read_only = on` before any business query; **never** call an application enrollment endpoint or a write-oriented ORM service as a shortcut.

Check schema/migrations first: `phone_number` columns `id, business_id, phone_number, provider, external_id, twilio_binding_source, twilio_account_sid, voice_verified_at, active`; `pilot_launch_control`; and migrations `V100` through `V103` recorded in Flyway history. If schema is older, stop: do not auto-run Flyway in production.

**Operator-only parameterized query outline (review before authorization; not executed by this phase):**

```sql
-- Execute only with A1-D permission, an authorized DB role and bound parameters.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SHOW transaction_read_only;
SELECT id, business_id, phone_number, provider, active,
       external_id, twilio_account_sid, twilio_binding_source, voice_verified_at
  FROM public.phone_number
 WHERE business_id = $1::uuid AND phone_number = $2::text;
-- Separate privileged read: is there *any* other tenant claiming the same E.164/PN?
-- Count across properly authorized SYSTEM scope; do not bypass RLS accidentally.
SELECT business_id, status
  FROM public.pilot_launch_control WHERE business_id = $1::uuid;
ROLLBACK;
```

Here `$1`/`$2` stand for bound values in a prepared client query, **not** SQL text interpolation or literal psql substitution. This outline is intentionally not a runnable production script. Only a trusted authorized operator may view the raw response; copy the result to a protected evidence store, not CI or GitHub. A read-only transaction protects against accidental writes but **does not itself grant or guarantee RLS isolation**: verify session role, forced-RLS policies and cross-tenant access controls independently. `0 rows`, more than one candidate, a foreign tenant, unexpected active status or missing verified provenance must yield **NO-GO**, never a best-guess repair.

Review `pilot_launch_control` in the same authorized session. `ControlledPilotExternalEffectGuard.evaluate` returns **`NOT_PILOT_MANAGED` with allowed=true** when no pilot row exists, even if the global flag is false. Thus require an actual pilot row in **PAUSED** state before relying on pilot kill switches. Confirm this with the restricted evidence owner; do not insert or update the pilot row during A1-D.

Read-only inspection of internal `twilio_existing_enrollment_request`, `twilio_existing_enrollment_claim` and audit tables, if later required, is SYSTEM-only due to forced RLS; request separate minimal privileges and redact participant IDs. The current Phase 3 does not read them in production.

## 5. Reconciliation of evidence (manual after A1-T and A1-D)

| Evidence to compare | Required interpretation | Failure code |
| --- | --- | --- |
| Authorized pilot tenant and DB row | Exact tenant UUID and phone-row identity, correct provider and intended E.164 | `TENANT_MISMATCH` |
| Carrier E.164 and persisted E.164 | Exact canonical E.164 match, no competing assignment | `DESTINATION_MISMATCH` |
| Carrier PN SID and stored external_id | Exact match if DB SID exists; `NULL` is an unresolved gap, not authorization to backfill | `PHONE_SID_MISSING` / `PHONE_IDENTITY_MISMATCH` |
| Carrier owning AC SID/subaccount and DB `twilio_account_sid` | Exact match, carrier account verified and allowlisted | `ACCOUNT_SID_MISSING` / `ACCOUNT_NOT_ALLOWED` |
| Carrier status, voice capability, hooks, HTTP method and HTTPS base URL | All match current configured provider contract; WebSocket path checked separately from HTTP hooks | `VOICE_UNAVAILABLE` / `WEBHOOK_MISMATCH` |
| DB provenance and verification timestamp | `VERIFIED_EXISTING` only after separate authorized reconciliation; historical NULL is unverified | `PROVENANCE_UNVERIFIED` |
| Pilot row and global external-effects flag | Row present and PAUSED, kill switch validated, NOT_PILOT_MANAGED is a gap | `PILOT_NOT_REGISTERED` |
| Twilio signature, CallSid, StreamSid, media and Gemini audio | **NOT VERIFIED** by metadata/GET-only audit; requires later authorized live test | `LIVE_EVIDENCE_PENDING` |

Use an opaque change/ticket ID, UTC timestamp, named operator/approver, authorized scope, provider/DB observation times, field-level pass/fail codes and evidence-vault pointers. Do not publish raw SIDs, phone numbers, account tokens, connection strings, caller details or Gemini credentials. Never infer `MANAGED_PURCHASE` from a provider screenshot or ownership GET.

## 6. Release gates and stop conditions

- **Offline audit plan and repository tests:** can be reviewed without production access.
- **Carrier/DB current truth:** **UNVERIFIED**, pending separate A1-T/A1-D consent.
- **Subaccount/webhooks/tenant consistency:** **UNVERIFIED** against production.
- **Effective Railway flags, credentials presence and deployed Gemini Live model:** **UNVERIFIED**.
- **Existing-number routing:** intentionally **BLOCKED** by `TwilioCallBindingGuard`.
- **Live call, interruptibility, costs, user consent and commercial certification:** **NOT TESTED**, production **NO-GO**.

After separate approval, any discrepancy calls for investigation and another explicit change plan; do not repair by editing `external_id`, provenance, webhooks or customer rows in the audit. Preserve evidence and stop. **Do not begin Part 4 automatically.**
