# Helvoca Release Readiness Design

## Objective

Prepare Helvoca for a production voice certification from a single, reviewed release revision. The release must have portable local tests, green GitHub CI, safe non-secret provider diagnostics, an exact Railway deployment, and a controlled real-call certification as the final gate.

## Current State

- `main` is deployed at commit `7f498d9a2b96fa8d38268735cef3df0beeea0c94` and its GitHub Actions run is green.
- Production health, the public sales page, the public pricing catalog, and authentication boundaries respond correctly.
- The local Windows checkout exposes two portability problems: `ConsoleInteractionAuditTest` assumes LF line endings and Playwright starts its static server through the Unix-only `python3` command.
- PostgreSQL integration tests require a working Docker engine. Docker Desktop is installed locally but its Linux engine did not become available during the baseline run.
- PR #111 contains non-secret channel runtime readiness and has a green implementation commit, but remains a draft.
- PR #112 contains a read-only Twilio credential probe, but its current GitHub Actions run fails.
- Repository documentation explicitly treats a real call with audio, interruption, tool calling, persistence, booking creation, and booking cancellation as the final voice certification.

## Release Strategy

Use a dedicated `codex/release-readiness` branch based on `main`. Do not merge either pending PR blindly. Reproduce each change locally, retain only code that passes focused review and tests, and keep every diagnostic fail-closed and free of secret values.

The release proceeds in four gates:

1. Portable test infrastructure.
2. Safe readiness and provider diagnostics.
3. Green CI and exact production deployment.
4. Explicitly authorized real-call certification.

Failure at any gate stops progression to later gates.

## Portable Test Infrastructure

### Java audit test

Normalize the loaded static JavaScript text to LF inside `ConsoleInteractionAuditTest` before assertions that span lines. The production JavaScript remains unchanged. A focused regression test run on Windows must change from failing to passing, while GitHub Actions continues to pass on Linux.

### Playwright web server

Select the Python launcher in `playwright.config.js` from `process.platform`: `python` on Windows and `python3` elsewhere. This avoids a new runtime dependency and preserves the existing static-server behavior.

The browser suite must continue to execute seven tests in Chromium with one worker and zero retries.

## Channel Runtime Readiness

Integrate the behavior from PR #111 only after code review. The readiness service may report whether voice and WhatsApp prerequisites are configured, but it must never expose credentials, tokens, full account identifiers, customer data, or provider payloads.

The result must distinguish:

- configured versus unconfigured;
- enabled versus disabled;
- ready versus blocked;
- a stable, non-secret reason code suitable for operations.

This check must not send a call, message, payment, or provider mutation.

## Provider Connectivity Probe

Diagnose PR #112's CI failure before integration. The Twilio probe must remain opt-in, one-shot, read-only, and fail-closed. It may authenticate and perform a harmless account-level read, but it must not buy a number, place a call, send a message, alter a webhook, or print credentials.

Probe results must contain only a stable readiness state and sanitized reason. Provider exception bodies, authorization headers, tokens, phone numbers, and account identifiers must not be logged.

Gemini or OpenAI probes are included only if an equivalent existing read-only mechanism already exists. No new paid request is added merely to broaden diagnostics.

## Verification Matrix

The release candidate must pass all of the following:

- syntax validation for every JavaScript file used by CI;
- the focused regression tests for Windows line endings and Playwright startup;
- the complete Maven test suite, including PostgreSQL/Testcontainers integration tests;
- all seven Playwright E2E tests;
- focused readiness and Twilio diagnostic tests;
- `git diff --check` and a clean worktree;
- GitHub Actions on the exact release SHA;
- Railway deployment of that same SHA;
- production `UP` health, HTTP 200 for sales and pricing, unique pricing codes, and HTTP 401 for private unauthenticated endpoints.

If the local Docker engine remains unavailable, the integration-test gate must be satisfied by the exact release SHA in GitHub Actions; a local environment failure must not be represented as a product pass.

## Deployment

Push the release branch and create a pull request against `main`. Merge only after all required checks pass. Confirm that Railway reports the merged SHA as the active production deployment, then repeat the production smoke checks against `https://helvoca-api-production.up.railway.app`.

No provider flag, certification flag, credential, database record, phone-number ownership, or billing state is changed as part of deployment.

## Real-Call Certification

The final certification is a paid external action and requires action-time confirmation plus an explicit destination number. Immediately before initiating the call, report the destination and that Twilio/provider usage may incur cost.

The call passes only when evidence shows:

- Twilio connects the call to the deployed release;
- bidirectional audio starts;
- the selected live AI provider becomes ready;
- interruption works without ending the session;
- business information and available slots come from backend tools;
- a certification booking is created through an authorized tool;
- the same certification booking is cancelled;
- transcript, actions, completion state, and summary are persisted;
- no unrelated customer or tenant data is modified.

The certification booking and caller identity must be explicitly marked as certification data and scoped to the authorized tenant. If any required condition fails, voice remains `NOT CERTIFIED` and the product stays fail-closed for the affected provider.

## Rollback and Safety

- Code rollback uses the previous known-good production SHA.
- Provider diagnostics are disabled after their one-shot execution.
- Certification ingress remains disabled except for the authorized test window.
- No credentials are committed, printed, copied into test artifacts, or included in PR text.
- No real WhatsApp message, payment, phone purchase, or customer-facing outbound action is part of this release.

## Completion Criteria

Helvoca is release-ready when the exact deployed SHA has green CI and production smoke checks. Helvoca voice is certified only after the separately confirmed real call passes every criterion above. These states must be reported separately.
