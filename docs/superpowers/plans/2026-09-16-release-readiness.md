# Helvoca Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one green, deployable Helvoca revision with portable tests, safe channel diagnostics, exact Railway verification, and a controlled final voice-certification gate.

**Architecture:** Harden the existing Maven/Playwright pipeline first, then integrate the already-developed readiness and Twilio probe changes commit-by-commit so their original RED/GREEN history remains auditable. Keep diagnostics startup-only, opt-in, read-only, fail-closed, and sanitized. Deployment and live certification are separate gates tied to the exact release SHA.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven, JUnit 5, Mockito, PostgreSQL/Testcontainers, Node.js 22+, Playwright 1.55, GitHub Actions, Railway.

**Spec:** `docs/superpowers/specs/2026-09-16-release-readiness-design.md`

## Global Constraints

- Never expose Twilio, Gemini, OpenAI, Mercado Pago, database, or JWT secrets in logs, test output, commits, PR text, or artifacts.
- No test or diagnostic may place a call, send a message, buy a number, create a payment, or mutate provider configuration.
- Merge and deploy only a commit whose complete GitHub Actions workflow is green.
- Railway must report the exact merged SHA as the active production deployment before smoke testing.
- A real Twilio call requires action-time confirmation and an explicit destination number.
- Certification ingress and diagnostics remain disabled outside the authorized test window.

---

### Task 1: Make the test pipeline portable across Windows and Linux

**Files:**
- Modify: `src/test/java/cl/helvoca/console/ConsoleInteractionAuditTest.java`
- Modify: `playwright.config.js`
- Test: `src/test/java/cl/helvoca/console/ConsoleInteractionAuditTest.java`
- Test: `e2e/*.spec.js`

**Interfaces:**
- Consumes: classpath text resources and Node's `process.platform`.
- Produces: platform-independent text assertions and a platform-correct Python launcher for Playwright's static server.

- [ ] **Step 1: Reproduce the CRLF regression**

Run:

```powershell
mvn --batch-mode --no-transfer-progress -Dtest=ConsoleInteractionAuditTest test
```

Expected: FAIL at `Successful setup must reload persisted IDs before another edit` on a CRLF checkout.

- [ ] **Step 2: Add the minimal line-ending normalization**

Change the test resource helper to return normalized text:

```java
return new String(input.readAllBytes(), StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
```

- [ ] **Step 3: Verify the Java regression is green**

Run the focused Maven command from Step 1.

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 4: Reproduce the Playwright launcher failure**

Run:

```powershell
npm install --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

Expected before the fix on Windows: web server exits because `python3` is unavailable.

- [ ] **Step 5: Select the launcher by platform**

Add above `module.exports`:

```javascript
const python = process.platform === 'win32' ? 'python' : 'python3';
```

Change `webServer.command` to:

```javascript
command: `${python} -m http.server 4173 --bind 127.0.0.1 --directory src/main/resources/static`,
```

- [ ] **Step 6: Verify all browser tests**

Run `npm run test:e2e`.

Expected: `7 passed` with exit code 0.

- [ ] **Step 7: Commit the portability fix**

```powershell
git add src/test/java/cl/helvoca/console/ConsoleInteractionAuditTest.java playwright.config.js
git commit -m "test: make verification portable across platforms"
```

### Task 2: Integrate safe channel runtime readiness

**Files:**
- Create: `src/test/java/cl/helvoca/operations/ChannelRuntimeReadinessServiceTest.java`
- Create: `src/main/java/cl/helvoca/operations/ChannelRuntimeReadinessService.java`

**Interfaces:**
- Consumes: `TwilioProperties`, `WhatsAppProperties`, and `VoiceCallRouter.readiness()`.
- Produces: `ChannelRuntimeReadinessService.snapshot()` returning non-secret Twilio, voice, and WhatsApp readiness records.

- [ ] **Step 1: Apply the existing RED commit from PR #111**

```powershell
git cherry-pick e9e296a
```

- [ ] **Step 2: Verify the readiness contract fails because implementation is absent**

Run:

```powershell
mvn --batch-mode --no-transfer-progress -Dtest=ChannelRuntimeReadinessServiceTest test
```

Expected: compilation failure naming missing `ChannelRuntimeReadinessService`.

- [ ] **Step 3: Apply the minimal GREEN implementation**

```powershell
git cherry-pick 7e96cd9
```

- [ ] **Step 4: Verify readiness and secret-redaction behavior**

Run the focused Maven command from Step 2.

Expected: 2 tests pass; the readiness record contains no configured SID or token.

- [ ] **Step 5: Review fail-closed semantics**

Confirm the snapshot is not voice-ready unless credentials, HTTPS public base URL, WSS media URL, and a healthy voice provider are all present. Confirm WhatsApp readiness additionally requires its explicit enable flag.

### Task 3: Repair and integrate the read-only Twilio diagnostic probe

**Files:**
- Create: `src/test/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbeTest.java`
- Create: `src/main/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbe.java`
- Modify: `src/test/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbeTest.java`
- Modify: `src/main/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbe.java`

**Interfaces:**
- Consumes: `TwilioProperties`, `TWILIO_DIAGNOSTIC_PROBE`, and a Java `HttpClient`.
- Produces: a package-visible `probe()` returning `ProbeResult(success, code, detail)` with only stable sanitized output.

- [ ] **Step 1: Apply the existing RED commit from PR #112**

```powershell
git cherry-pick 2fa3088
```

- [ ] **Step 2: Verify the probe contract fails because implementation is absent**

Run:

```powershell
mvn --batch-mode --no-transfer-progress -Dtest=TwilioDiagnosticStartupProbeTest test
```

Expected: compilation failure naming missing `TwilioDiagnosticStartupProbe`.

- [ ] **Step 3: Apply the existing implementation commit**

```powershell
git cherry-pick 5fdbcdd
```

- [ ] **Step 4: Run the focused tests and capture the actual PR #112 failure**

Run the focused Maven command from Step 2. If it fails, preserve the exact compiler/test output and trace it to the smallest responsible boundary before editing.

- [ ] **Step 5: Add a failing sanitization regression**

Add a test in `TwilioDiagnosticStartupProbeTest` that makes `HttpClient.send` throw an exception whose message contains both `ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` and `test-token`, then assert:

```java
assertFalse(result.detail().contains("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
assertFalse(result.detail().contains("test-token"));
assertEquals("Twilio account API could not be reached", result.detail());
```

Run the focused test and confirm it fails because the existing `safeMessage` returns the exception message.

- [ ] **Step 6: Replace provider exception text with a stable detail**

For the general exception path return:

```java
return new ProbeResult(false, "NETWORK_ERROR", "Twilio account API could not be reached");
```

Remove `safeMessage` when no longer used.

- [ ] **Step 7: Verify all probe cases**

Run the focused Maven test.

Expected: missing configuration performs no HTTP request; configured credentials use exactly one GET; 401/403 maps to `AUTH_FAILED`; transport failures reveal no secret values.

- [ ] **Step 8: Commit the diagnostic hardening**

```powershell
git add src/main/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbe.java src/test/java/cl/helvoca/telephony/twilio/TwilioDiagnosticStartupProbeTest.java
git commit -m "fix: sanitize Twilio diagnostic failures"
```

### Task 4: Execute the complete release verification matrix

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `pom.xml`
- Verify: `package.json`
- Verify: `compose.yml`

**Interfaces:**
- Consumes: Java 21, Maven, Node.js, Chromium, and a PostgreSQL-capable Docker engine.
- Produces: reproducible logs proving compilation, unit, integration, browser, and static validation status.

- [ ] **Step 1: Validate JavaScript syntax**

```powershell
Get-ChildItem 'src/main/resources/static/*.js' | ForEach-Object { node --check $_.FullName; if ($LASTEXITCODE -ne 0) { throw "Syntax failure: $($_.Name)" } }
```

- [ ] **Step 2: Verify Docker/Testcontainers availability**

Run `docker info`. If unavailable, attempt to start the preinstalled Docker Desktop and wait for `docker info` to succeed. Do not alter Windows security or privacy settings.

- [ ] **Step 3: Run the complete backend suite**

```powershell
mvn --batch-mode --no-transfer-progress test
```

Expected with Docker available: all Maven tests pass with zero failures and zero errors.

- [ ] **Step 4: Run the complete browser suite**

```powershell
npm run test:e2e
```

Expected: 7 tests pass.

- [ ] **Step 5: Check repository integrity**

```powershell
git diff --check
git status --short --branch
```

Expected: no unstaged generated artifacts and only intended release commits.

### Task 5: Publish the release candidate and require green CI

**Files:**
- Verify: `.github/workflows/ci.yml`
- Create externally: GitHub pull request from `codex/release-readiness` to `main`.

**Interfaces:**
- Consumes: authenticated Git remote and GitHub Actions.
- Produces: a reviewed PR whose exact head SHA has a successful `RecepVoz CI` run.

- [ ] **Step 1: Push the release branch**

```powershell
git push -u origin codex/release-readiness
```

- [ ] **Step 2: Create the pull request**

Use title `chore: harden release readiness and provider diagnostics`. The body must summarize portability fixes, non-secret readiness, read-only Twilio probing, local verification, and the fact that no call or provider mutation was executed.

- [ ] **Step 3: Wait for the exact PR-head CI run**

Require every workflow step to pass. If CI fails, inspect the first failing step, reproduce locally when possible, and apply systematic debugging before another push.

- [ ] **Step 4: Merge only the green head**

Record the merged SHA and verify the successful `main` workflow for that exact SHA.

### Task 6: Verify the exact Railway production deployment

**Files:**
- No repository changes.

**Interfaces:**
- Consumes: GitHub deployment status and the Railway production URL.
- Produces: evidence that production runs the merged SHA and satisfies the public smoke contract.

- [ ] **Step 1: Wait for Railway deployment**

Confirm GitHub Deployments marks the merged SHA active in `Helvoca / production`.

- [ ] **Step 2: Run production smoke assertions**

Verify:

```text
GET /actuator/health                 -> 200, status UP
GET /sales.html                     -> 200, Helvoca title
GET /api/v1/public/pricing          -> 200, unique plan codes
GET /api/v1/auth/me without token   -> 401
```

- [ ] **Step 3: Run the provider diagnostic only if production configuration authorizes it**

Enable `TWILIO_DIAGNOSTIC_PROBE` only for one controlled startup, inspect the sanitized result, then disable it and verify the subsequent deployment is healthy. Do not continue to a call if the probe is not `OK`.

### Task 7: Execute the final real-call certification

**Files:**
- No repository changes unless the certification exposes a reproducible defect.

**Interfaces:**
- Consumes: an explicitly confirmed E.164 destination number, the certified tenant, Twilio, and a healthy live voice provider.
- Produces: call ID, provider, persisted transcript/actions/summary, booking-create evidence, and booking-cancel evidence.

- [ ] **Step 1: Request action-time confirmation**

Immediately before the call, state the E.164 destination and that Twilio/AI usage may incur cost. Do not place the call until the user confirms.

- [ ] **Step 2: Open the minimum certification window**

Enable only the existing one-shot certification controls and authorized caller/tenant. Keep unrelated outbound, WhatsApp, payment, and provisioning features disabled.

- [ ] **Step 3: Place one certification call**

Exercise greeting, interruption, backend information, availability, booking creation, booking cancellation, and a clean call ending.

- [ ] **Step 4: Verify persisted evidence**

Require the call to be completed with transcript, actions, summary, created-and-cancelled certification booking, and no unrelated tenant mutation.

- [ ] **Step 5: Close the certification window**

Disable certification ingress and one-shot diagnostic flags, verify production health again, and report release readiness separately from voice certification.
