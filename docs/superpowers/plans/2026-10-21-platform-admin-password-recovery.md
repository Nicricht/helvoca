# Platform Administrator Password Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore access for an existing Aurora DEMO platform administrator without direct password SQL, identity replacement, or privilege escalation.

**Architecture:** Extend the existing hashed invitation ledger with a `RECOVERY` purpose. An operator-only, default-off startup runner may issue or rotate one expiring recovery token for an already-active tenant-less `PLATFORM_ADMIN`; the existing public acceptance endpoint consumes it transactionally, replaces only that user's BCrypt password hash, and preserves identity and roles.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, PostgreSQL 18/Flyway, JUnit 5, Testcontainers.

**Spec:** PR #849 `FRESH_CONTEXT_EVIDENCE_RECEIPT_V2` comment 6318297843 and Issue #848 operational checkpoint.

## Global Constraints

- No raw token, password, email, or hash in logs, GitHub, or responses.
- Feature remains disabled unless `HELVOCA_PLATFORM_ADMIN_RECOVERY_ENABLED=true`.
- Recovery applies only to one existing active tenant-less `PLATFORM_ADMIN` matched by normalized email.
- Token is 32 random bytes encoded as 43-character Base64URL, stored as SHA-256 only, expires after 30 minutes, rotates atomically, and is single-use.
- No new user or role is created; current identity and role links remain unchanged.
- No production deployment, provider activation, call, payment, or direct SQL password mutation.

## Review Focus

- Unknown, inactive, tenant-owned, or non-platform email must fail without issuing a token.
- Expired, revoked, malformed, or replayed tokens must not change a password.
- Rotating a recovery token must revoke the old pending token atomically.
- Acceptance must update exactly one existing platform account and keep its identity and roles.
- Concurrent/repeated acceptance must yield one terminal success and no inconsistent state.

---

### Task 1: PostgreSQL recovery contract

**Files:**
- Create: `src/main/resources/db/migration/V107__platform_admin_password_recovery.sql`
- Test: `src/test/java/cl/helvoca/platform/PlatformAdminInvitePostgresIntegrationTest.java`

**Interfaces:**
- Produces: `platform_admin_invitation.purpose='RECOVERY'` with valid operator-origin and terminal-state constraints.

- [ ] Write PostgreSQL integration tests for hash-only issuance, target validation, rotation, expiry, replay, role preservation, and successful login with only the new password.
- [ ] Run the focused test and record RED because `RECOVERY` is not accepted by V106/service behavior.
- [ ] Add the minimal V107 forward migration and service persistence behavior.
- [ ] Run the focused test to GREEN.

### Task 2: Operator-only runner and acceptance

**Files:**
- Create: `src/main/java/cl/helvoca/platform/PlatformAdminRecoveryRunner.java`
- Modify: `src/main/java/cl/helvoca/platform/PlatformAdminInviteService.java`
- Test: `src/test/java/cl/helvoca/platform/PlatformAdminOnboardingControllerTest.java`

**Interfaces:**
- Produces: `recover(String email, String token)` and recovery handling through existing `accept(String token, String personalPassword)`.

- [ ] Write runner delegation and recovery acceptance tests first.
- [ ] Run focused tests and record RED for missing runner/service API.
- [ ] Implement the default-off runner and smallest transaction-safe service changes.
- [ ] Run focused unit and PostgreSQL tests to GREEN.

### Task 3: Documentation and exact-HEAD certification

**Files:**
- Modify: `docs/REAL_CALL_CERTIFICATION_COMMAND_RUNNER.md`

**Interfaces:**
- Produces: safe Aurora-only recovery procedure and rollback instructions.

- [ ] Document flag names, token generation requirements, 30-minute expiry, disable-after-use, and prohibited direct SQL reset.
- [ ] Run formatting/static checks, focused tests, differential coverage, Fast Gate and Full Gate.
- [ ] Commit and push only to PR #849 branch after a fresh receipt for each commit.
- [ ] Deploy only to Aurora `demo-isolated` after exact-HEAD CI passes; verify V107, create one recovery token privately, let Nicolás set the password personally, disable recovery, then verify independent login/JWT.
- [ ] Record exact evidence in PR #849, Issue #848 and Issue #823 without secrets.
