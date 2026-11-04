# ChatGPT Aurora Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose a secure, one-time-configured ChatGPT GPT Action that can request and inspect calls only for the pinned Aurora demo route.

**Architecture:** A SYSTEM-only PostgreSQL key ledger stores only a digest of a high-entropy revocable key. A narrow API validates that key, the immutable Aurora tenant/phone binding, scoped Gemini readiness and a fixed server-side destination; it enqueues the existing tenant-bound command. A dedicated opt-in worker claims only this integration's commands while the generic worker excludes them. A Platform console panel manages the key, and a static OpenAPI schema configures the private GPT Action.

**Tech Stack:** Java 21, Spring Boot, Spring Security, JdbcTemplate, PostgreSQL 18/Flyway, JUnit 5/Testcontainers, React 18/TypeScript, Vite, OpenAPI 3.1, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-04-chatgpt-aurora-action-design.md`

## Global Constraints

- Keep all implementation on the existing DRAFT PR #853 branch; never modify `main` or production.
- The only permitted business is the configured Aurora tenant in `demo-isolated`.
- The only permitted caller/destination are the existing server-side Twilio values and allowlist; no phone number is accepted from ChatGPT.
- Global Gemini, generic certification runner, startup caller and global external effects remain OFF.
- The dedicated worker is default-OFF and claims only `requested_by LIKE 'chatgpt-aurora:%'`; the generic worker must exclude this prefix.
- No startup-triggered call, automatic retry, duplicate call, booking, payment, WhatsApp, number purchase, or real provider call during coding/QA.
- The POST action is consequential and requires explicit confirmation in the GPT instructions and ChatGPT Action contract.
- Preserve the no-application-duration-cutoff policy; do not add Twilio `TimeLimit` or elapsed-time hangup.
- Store only a SHA-256 digest of a 256-bit random API key. Display the raw key once and never persist it in browser storage, logs or Git.
- Twilio per-call duration/price may be returned only from a verified read-only GET. Do not invent a Gemini per-call charge; disclose when Google billing does not expose a CallSid-level amount.
- Do not add a lifetime call-attempt quota or cumulative spend cap.
- Exact-HEAD Fast/Full gates, PostgreSQL 18 integration/RLS, PG16/PG18 Flyway compatibility and relevant frontend gates are mandatory before any deployment claim.

## Review Focus

1. A revoked, malformed, guessed or wrong-tenant key must never enqueue a command; the test must prove no database write or provider action.
2. A ChatGPT key must not read another key's run, a platform-admin run, or a different tenant's run; return not-found without leaking whether the run exists.
3. The generic worker must never claim a ChatGPT command, and the dedicated worker must never claim an ordinary certification command.
4. A disabled runner, invalid route, missing Gemini key/model, failed carrier binding, or changed destination allowlist must fail closed before enqueue or before Twilio.
5. Duplicate run IDs, repeated POSTs and concurrent worker polling must produce at most one command/CallSid; there are no automatic retries.
6. A status lookup must verify both Twilio CallSid and AccountSid, mask phone numbers, and remain useful if Twilio GET is temporarily unavailable.

---

### Task 1: API-key lifecycle and PostgreSQL ledger

**Files:**
- Create: `src/main/resources/db/migration/V111__chatgpt_aurora_api_keys.sql`
- Create: `src/main/java/cl/helvoca/platform/chatgpt/AuroraChatGptApiKeyStore.java`
- Create: `src/main/java/cl/helvoca/platform/chatgpt/AuroraChatGptApiKeyService.java`
- Test: `src/test/java/cl/helvoca/platform/chatgpt/AuroraChatGptApiKeyServiceTest.java`
- Test: `src/test/java/cl/helvoca/platform/chatgpt/AuroraChatGptApiKeyPostgresIntegrationTest.java`

**Interfaces:**
- `AuroraChatGptApiKeyService.issue(UUID businessId, String actor)` returns a one-time `IssuedKey(keyId, token, keyPrefix, createdAt)`.
- `authenticate(String bearerToken)` returns an active key identity or an authentication failure.
- `status(UUID businessId)` returns metadata only; it never returns the token.
- `revoke(UUID businessId, String actor)` revokes the one active key.
- `AuroraChatGptApiKeyStore` persists only `key_hash`, `key_prefix`, business ID and audit metadata.

- [ ] **Step 1: Write failing service tests.** Assert 32 bytes of cryptographically random entropy, URL-safe token format, digest-only persistence, one-time return, malformed/unknown/revoked key rejection, no secret in status, no second active key, and one-way revocation.
- [ ] **Step 2: Run the focused test and verify RED** for the missing service/store behavior.
- [ ] **Step 3: Write the PostgreSQL integration tests.** Verify V111 applies on PostgreSQL 18; only `helvoca_system` can access the forced-RLS key/audit tables; a partial unique index enforces one active key per business; key identity is immutable; revocation is one-way; audit rows cannot be updated/deleted.
- [ ] **Step 4: Implement the migration, store and service.** Use SecureRandom(32 bytes), Base64 URL without padding, SHA-256 digest, one-way revocation and no raw-token logging.
- [ ] **Step 5: Run focused unit and PostgreSQL integration tests.** Expected: all new cases pass; no provider request is made.
- [ ] **Step 6: Commit the task** with a fresh pre-change receipt for the exact parent SHA.

### Task 2: Scoped ChatGPT action API and isolated command worker

**Files:**
- Create: `src/main/java/cl/helvoca/platform/chatgpt/AuroraChatGptIntegrationController.java`
- Create: `src/main/java/cl/helvoca/platform/chatgpt/AuroraChatGptIntegrationService.java`
- Create: `src/main/java/cl/helvoca/telephony/twilio/TwilioChatGptAuroraCommandWorker.java`
- Modify: `src/main/java/cl/helvoca/platform/PlatformCertificationRunService.java`
- Modify: `src/main/java/cl/helvoca/telephony/twilio/TwilioCertificationCommandStore.java`
- Modify: `src/main/java/cl/helvoca/telephony/twilio/TwilioCertificationCallStatusReadOnlyRunner.java`
- Modify: `src/main/java/cl/helvoca/telephony/twilio/TwilioCertificationCommandCallLauncher.java`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Modify: `src/main/java/cl/helvoca/security/ApiRateLimitFilter.java`
- Modify: `.env.example`
- Test: `src/test/java/cl/helvoca/platform/chatgpt/AuroraChatGptIntegrationControllerTest.java`
- Test: `src/test/java/cl/helvoca/platform/chatgpt/AuroraChatGptIntegrationServiceTest.java`
- Test: `src/test/java/cl/helvoca/telephony/twilio/TwilioChatGptAuroraCommandWorkerTest.java`
- Modify: `src/test/java/cl/helvoca/telephony/twilio/TwilioCertificationCommandStoreTest.java`
- Modify or create: PostgreSQL integration coverage for actor-prefix claims and cross-tenant status reads.

**Interfaces:**
- `GET /api/v1/integrations/chatgpt/aurora/readiness` returns safe scoped readiness and blockers.
- `POST /api/v1/integrations/chatgpt/aurora/call-runs` accepts only a unique `runId` with the `chatgpt-aurora-` prefix; it never accepts a phone number, business ID, model or provider.
- `GET /api/v1/integrations/chatgpt/aurora/call-runs/{runId}` returns only a run created by this integration for the pinned Aurora business, plus verified read-only Twilio details when available.
- Admin key management endpoints live under `/api/v1/platform/integrations/chatgpt/aurora` and remain PLATFORM_ADMIN-only.
- The dedicated worker uses `HELVOCA_CHATGPT_AURORA_RUNNER_ENABLED=false` by default and claims only the integration actor prefix.
- The generic worker excludes the ChatGPT prefix.

- [ ] **Step 1: Write failing tests** for 401 invalid key, 409 not-ready, fixed tenant, no arbitrary destination, duplicate run ID, status isolation, safe Twilio GET, and the two mutually exclusive worker claim paths.
- [ ] **Step 2: Run focused tests and verify RED** for each missing API/worker behavior.
- [ ] **Step 3: Implement action controller/service.** Validate key first; enter SYSTEM database scope only for the validated integration; recheck the binding and scoped Gemini readiness; enqueue with `requested_by=chatgpt-aurora:<key-id>`; never log secrets.
- [ ] **Step 4: Implement worker isolation.** Add a claim query filtered to the actor prefix; exclude that prefix from the generic worker; recheck the binding immediately before Twilio; no retry; record success/failure.
- [ ] **Step 5: Implement status details.** Expose the existing read-only Twilio GET as a reusable method; verify CallSid and AccountSid; return duration/price only when supplied by Twilio; mask phone suffixes; label Gemini per-call cost unavailable.
- [ ] **Step 6: Add API security and IP rate limiting.** Permit only the integration route through the HTTP security matcher; all methods still require the scoped Bearer key. Unauthenticated database context remains DENIED; service uses explicit SYSTEM scope after key validation.
- [ ] **Step 7: Run focused unit, controller/security and PostgreSQL integration tests.** Expected: ChatGPT worker claims only its own commands; generic worker cannot claim them; no Twilio request occurs in tests.
- [ ] **Step 8: Commit the task** with a fresh pre-change receipt for the exact parent SHA.

### Task 3: Platform key UI, OpenAPI schema and GPT instructions

**Files:**
- Modify: `frontend/src/features/platform/api.ts`
- Modify: `frontend/src/pages/Platform/PlatformPage.tsx`
- Create: `frontend/src/pages/Platform/ChatGptAuroraIntegrationPanel.tsx`
- Create: `src/main/resources/static/app/chatgpt-aurora-openapi.yaml`
- Test/verify: frontend TypeScript build and applicable browser interaction gate.

**Interfaces:**
- Platform UI displays active key prefix and timestamps, creates a key once, reveals it only in transient component state, supports copy and revoke, and links to the OpenAPI schema plus copyable GPT instructions.
- OpenAPI server is `https://helvoca-aurora-api-demo-isolated.up.railway.app`.
- OpenAPI uses Bearer auth; call creation is marked `x-openai-isConsequential: true`.
- GPT instructions require readiness first, explicit user confirmation before every call, one unique run ID, and status polling. No auto-call or auto-retry.

- [ ] **Step 1: Add typed API functions and the panel interactions** using existing Platform UI styles.
- [ ] **Step 2: Verify one-time-secret behavior**: key is not rendered after refresh, not stored in localStorage/sessionStorage, and disappears when component state is cleared; revoke removes future API access.
- [ ] **Step 3: Add OpenAPI schema and GPT instructions** with no destination input and a consequential POST.
- [ ] **Step 4: Add the panel to PlatformPage** and a navigation anchor.
- [ ] **Step 5: Run `npm run typecheck` and `npm run build` plus applicable browser/interaction QA.** Expected: both builds succeed and all mapped interactions behave as specified.
- [ ] **Step 6: Commit the task** with a fresh pre-change receipt for the exact parent SHA.

### Task 4: Runbook, exact-HEAD certification and checkpoint

**Files:**
- Modify: `docs/REAL_CALL_CERTIFICATION_COMMAND_RUNNER.md`
- Update: PR #853 checkpoint and Issue #823 (single V1 board).

- [ ] **Step 1: Document** one-time GPT setup, API key lifecycle, readiness/status flow, worker flag, revocation, cost visibility limits, and rollback. Record that custom GPT Actions are a replaceable adapter with a documented OpenAI migration/retirement notice.
- [ ] **Step 2: Run exact-HEAD Fast Gate and applicable Full Gate.** Require differential Java line/branch/method coverage at 100%, applicable frontend interaction/coverage gates, Golden Journey, system E2E, PG18 migration/RLS and PG16/PG18 compatibility.
- [ ] **Step 3: Inspect the final diff adversarially** against every Review Focus item and the system invariants.
- [ ] **Step 4: Record exact final SHA, CI URLs/conclusions, test counts/coverage, API security evidence, and the explicit fact that no call, booking, spend, deployment or main/production change occurred.**
- [ ] **Step 5: Stop at DRAFT PR.** Deployment and isolated runner activation are a separate release action after the exact-HEAD gates and owner review; do not deploy or enable flags as part of this plan.

## Expected outcome

A reusable scoped API and OpenAPI adapter are ready in a DRAFT PR. The owner performs a one-time key generation and private GPT Action setup after the isolated deployment is separately approved. Thereafter, each call is explicitly confirmed inside the dedicated GPT; status, CallSid, duration and Twilio price are retrievable without Work. Gemini per-call billing remains unavailable unless Google exposes a matching per-call cost record.
