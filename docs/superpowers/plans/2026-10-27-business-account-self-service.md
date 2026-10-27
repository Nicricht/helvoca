# Business Account Self-Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure customer password recovery and email verification without affecting privileged platform-admin recovery or existing tenant access.

**Architecture:** A distinct PostgreSQL tenant-scoped, hashed, single-use account-token ledger; transactional Spring business-auth services and opt-in SMTP delivery; explicit React verification/reset forms. Stack this Draft PR on #849 to keep V104–V107 ordered ahead of new V108.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security, PostgreSQL18/Flyway, JUnit5/Testcontainers, React/TypeScript, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-27-business-account-self-service-design.md`.

## Global Constraints

- Business IDs are read from persisted identity, never a caller-supplied tenant.
- Never log or return reset/verification secrets; no SMTP LIVE by default.
- Platform admins and business accounts must remain distinct.
- Do not weaken forced RLS, rate limiting, other authentication, CI gates or support for existing accounts.
- `FRAME CHANGE: NO`; no production, demo, provider or payment mutations.

## Review Focus

1. Unknown/disabled/platform account on public reset request: indistinguishable response and no message. Task 2 tests.
2. Expired/replayed/rotated/wrong-purpose token, concurrency: no second password or verification mutation. Task 1 tests.
3. Bad SMTP config or delivery failure: no token leak and no false verified state. Task 2 tests.
4. Old existing account and platform admin after verification enforcement: no accidental lockout. Task 3 tests.
5. Browser refresh, token removal, double submission and network errors: appropriate retry/no stale auth. Task 4 tests.

---

### Task 1: Secure customer token ledger

**Files:**
- Create: `src/main/resources/db/migration/V108__business_account_tokens.sql`
- Create: `src/main/java/cl/helvoca/auth/BusinessAccountTokenService.java`
- Test: `src/test/java/cl/helvoca/auth/BusinessAccountTokenPostgresIntegrationTest.java`

**Interfaces:** Issue and consume tokens for exactly `UUID userId, UUID businessId, Purpose {RESET, VERIFY}`; issuance gives raw token to the sending boundary exactly once; storage holds SHA-256 only. Consume locks the row, validates expiry/revocation/purpose/tenant identity and atomically marks consumed.

- [ ] **Step 1:** Add Testcontainers/Flyway RED for persistence/reload, hashed-only storage, expiry, rotation, replay, wrong purpose/tenant and two parallel consumers.
- [ ] **Step 2:** Run focused Testcontainers test; record expected RED from missing migration/type.
- [ ] **Step 3:** Implement V108 and minimal token service (30m reset, 24h verify) with forced RLS and fail-closed role grants.
- [ ] **Step 4:** Run focused PostgreSQL test and inspect transaction/isolation outcomes.
- [ ] **Step 5:** Commit with a fresh PR receipt; no external provider effects.

### Task 2: Opt-in SMTP and password reset contract

**Files:**
- Modify: `pom.xml`, `src/main/resources/application.yml`
- Create: `src/main/java/cl/helvoca/auth/BusinessAccountMailService.java`
- Create: `src/main/java/cl/helvoca/auth/BusinessAccountRecoveryService.java`
- Create: `src/main/java/cl/helvoca/auth/BusinessAccountRecoveryController.java`
- Modify: `src/main/java/cl/helvoca/security/{SecurityConfig,TenantDatabaseContextFilter,ApiRateLimitFilter}.java`
- Test: `src/test/java/cl/helvoca/auth/BusinessAccountRecoveryServiceTest.java`, `src/test/java/cl/helvoca/auth/BusinessAccountRecoveryControllerTest.java`

**Interfaces:** Anonymous `POST /api/v1/auth/password-reset/request` accepts an email and always returns no-account-disclosing 202 when enabled; `POST /api/v1/auth/password-reset/confirm` accepts token/newPassword and never logs in automatically.

- [ ] **Step 1:** RED for enabled/disabled SMTP, enumeration, inactive/platform rejection, expired token, BCrypt replacement, malformed/password boundaries and strict throttle.
- [ ] **Step 2:** Run focused tests and record intended missing-behavior failure.
- [ ] **Step 3:** Implement isolated mail boundary, reset service/controller and narrow public security scopes, with default OFF.
- [ ] **Step 4:** Run focused tests; ensure no LIVE provider request.
- [ ] **Step 5:** Commit with a new exact-parent receipt.

### Task 3: Email confirmation at registration

**Files:**
- Modify: `src/main/java/cl/helvoca/auth/{RegistrationService,AuthService,AuthController}.java`
- Modify: `src/main/java/cl/helvoca/user/AppUser.java`
- Modify: `src/main/java/cl/helvoca/user/TeamInvitationService.java`
- Create: `src/main/java/cl/helvoca/auth/BusinessAccountVerificationService.java`
- Test: `src/test/java/cl/helvoca/auth/BusinessAccountVerificationIntegrationTest.java`, existing registration/auth tests.

**Interfaces:** Opt-in new signup returns pending verification with no JWT; confirm endpoint sets verified timestamp once; existing users and valid invited users continue to authenticate.

- [ ] **Step 1:** RED for new unverified account, no JWT, verified login, existing-user grandfathering, platform exemption and bad/replay token.
- [ ] **Step 2:** Run focused tests and record RED.
- [ ] **Step 3:** Add additive email verification state in V108, service and controller response variant; make flagged login fail closed while preserving OFF behavior.
- [ ] **Step 4:** Run focused and PostgreSQL tests.
- [ ] **Step 5:** Commit with a fresh receipt.

### Task 4: Customer UI, optional invite delivery and end-to-end regression

**Files:**
- Modify: `frontend/src/pages/Auth/{AuthPage.tsx,AuthPage.module.css}`, `frontend/src/app/App.tsx`
- Create: `frontend/src/pages/Auth/BusinessAccountRecoveryPage.tsx`
- Modify: `src/main/java/cl/helvoca/user/TeamInvitationService.java`
- Test: relevant `e2e/` auth/reset/verification cases and backend invite tests.
- Update: `docs/FIRST_CUSTOMER_OPERATION.md` and PR/Issue checkpoints.

**Interfaces:** Forms never expose tokens after initial fragment parsing; manual invitations still work with mail OFF; when explicitly opted in, email delivery reuses existing invitation.

- [ ] **Step 1:** RED browser cases for request, confirmation, signup pending, verification success/expiry, retry/double click/unknown link, invite email failure.
- [ ] **Step 2:** Run focused Playwright/Java tests and record RED.
- [ ] **Step 3:** Implement local forms and optional invite dispatch; preserve protected frame.
- [ ] **Step 4:** Run Playwright focused and appropriate suites, verify UI behavior.
- [ ] **Step 5:** Run Fast Gate and Full Gate on final SHA; document measured coverage and no-provider/production limitations before marking DONE.
