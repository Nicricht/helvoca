# Controlled Real Business Pilot V1

This branch prepares RecepVoz for one tightly controlled real-business pilot.

## Safety model

No provider is activated by this branch and no real call, WhatsApp message, payment, deployment, or production inventory mutation is performed while building or certifying it.

A tenant that is not enrolled in the pilot keeps its existing behavior.

Once a tenant has a `pilot_launch_control` record, real external effects are fail-closed and require both:

1. `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=true`;
2. the tenant pilot status to be `RUNNING`.

The environment flag is the global safety switch. The existing tenant-scoped `PAUSED` state is the per-business kill switch.

**Safety scope of the switch:** "global" means global to *enrolled controlled pilots*, not to every Helvoca tenant. With no `pilot_launch_control` row, the existing guard deliberately returns `NOT_PILOT_MANAGED` and allows that tenant through this pilot-specific check even when the environment flag is false. The preflight must never claim "no real traffic" merely because this flag is off. Before any commercial release, independently verify provider/channel-level STOP and the exact pilot tenant's enrollment and `PAUSED` state. Do not assume this switch suspends non-pilot tenants or substitute it for a universal emergency stop.

## External-effect boundaries

The controlled-pilot guard is applied immediately before:

- routing pilot calls to an AI voice provider and revalidating trusted tenant STOP at the bound Twilio media WebSocket start;
- sending a real WhatsApp message through Meta or Twilio WhatsApp outbound providers;
- creating a real external payment intention.

Pausing a pilot must stop these effects without weakening the existing journey, idempotency, reconciliation, retry, or certification gates.

**Active-call stop (offline candidate, 2026-10-16):** `TwilioPilotInFlightStopMonitor` schedules checks on each node that actually owns an authenticated, active Twilio AI WebSocket, including silent calls. On pilot `PAUSED` or unavailable control state it requests a scoped carrier hangup and closes the local audio and AI once; two independent watchdog threads avoid blocking the existing hard-call deadline scheduler. This adds a bounded periodic read per active call (default every 3 seconds) and does not suspend other healthy tenants. **Provider-side terminal status, residual costs, live rollback after PAUSE and multi-instance behavior remain unverified in production.** This is not a universal STOP, and existing `NOT_PILOT_MANAGED` tenants remain permitted when their control state is readable.

**Twilio admission race closure:** `TwilioCallBindingGuard.allowsMedia` (called by `TwilioMediaStreamHandler.handleStart` after route signature verification) now checks `Effect.VOICE` against the tenant ID of the *certified phone bound to the existing nonterminal call*, under the system database context and before AI session initialization. The Spring-injected guard is required. Pilot-control read failures deny stream start; a paused managed pilot cannot initiate a new AI session even if a signed TwiML route was issued earlier. Unmanaged tenants retain their historical behavior. Existing AI streams and carrier-side calls require a separate in-flight shutdown/verification procedure; no universal STOP is implied. The scoped unit regression covers lifecycle statuses, opt-in OFF, unmanaged, DB failure, carrier misbinding and the Spring constructor; code remains **offline candidate** until exact-HEAD CI is complete.

**C2-B verified code scope (2026-10-16):** `MetaWhatsAppMessagingProvider` and `TwilioWhatsAppMessagingProvider` both consult `ControlledPilotExternalEffectGuard.Effect.WHATSAPP` before their respective outbound provider calls. Twilio previously lacked this guard; its final HTTP send boundary now rejects enrolled non-RUNNING pilots or a disabled pilot switch. `PaymentWorkflowService` also rechecks the pilot before recreating an externally missing provider order during reconciliation. These changes remain offline until their exact-HEAD GitHub CI passes and a separately authorized release deploys them. A tenant without a `pilot_launch_control` row remains `NOT_PILOT_MANAGED` and is not stopped by these checks. Pilot PAUSE does not recall an already submitted external message, end an in-flight voice stream, or universally stop all provider sessions.


## Launch sequence

1. Keep the global switch disabled.
2. Configure exactly one pilot tenant.
3. Complete technical readiness and activation checklist.
4. Exercise the complete journey with provider delivery still blocked.
5. Start the tenant pilot.
6. Enable the global switch only during an explicitly authorized controlled pilot window.
7. If an anomaly appears, pause the tenant immediately or disable the global switch.
8. Observe Journey Trace, Conversation Quality, jobs, payments, inventory, and V6 reconciliation.
9. Do not expand to another tenant until the first pilot has been reviewed.

## Provider-error privacy rule (2026-10-15)

The opt-in Twilio certification startup runner previously appended a truncated
raw Twilio HTTP error response (up to 400 characters) to an exception, which
could then reach shared application logs. The C4/C6 safety fix retains a
status-only diagnostic and replaces the provider body with the fixed marker
`[provider response omitted]`, regardless of content or length. A targeted
unit regression covers short/long/empty/null provider bodies. The exact-HEAD
Full Gate initially failed its mandatory 100% differential method threshold
(265/266) because the pre-existing phone-number log masking helper `mask`
was counted as an affected method but had no direct test. A second regression
now verifies null/empty/short and E.164-shaped numbers remain hidden or
limited to their final four digits. Only package-level test visibility was
added to this helper; the masking behavior itself is unchanged. The subsequent
Full Gate also exposed an uncovered existing URL helper `trimTrailingSlash`
(266/267 methods): targeted regression now asserts no trailing slash, one,
many, preserved interior separators, root-only and empty input. Its logic is
unchanged, with package-level test access only. The next Full Gate
[run #38733288443](https://github.com/Nicricht/helvoca/actions/runs/38733288443)
ran 2,273 Java tests with zero failures/errors/skips but reported an
uncovered adjacent URL-form helper `form`: changed lines 1101/1101 (100%),
branches 869/869 (100%), and methods 267/268 (99.6%). Browser E2E was
skipped by this fail-fast gate; specialized suites were 11/11 successful.
The additional regression now verifies percent-encoding of Twilio E.164
numbers, spaces, reserved key/value delimiters, UTF-8 characters, and
empty values. The helper implementation and resulting requests are
unchanged; only package-level test visibility was added. Another exact-HEAD
Full Gate remains required; none of these failures were waived. This does not
alter provider requests, call duration, quotas, authorization, consent or
hangup behavior, and it does not prove a real consented call was run.
Additional exception messages from other paths require separate review.

## Certification

The branch must keep the existing Pilot End-to-End Certification V1, Full Gate, PostgreSQL/Flyway/Testcontainers, JaCoCo, and Playwright green. No coverage exception and no `continue-on-error` is allowed.


## GO / NO-GO preflight

The tenant-scoped endpoint `GET /api/v1/operations/pilot-preflight` composes the existing readiness, activation, lifecycle, inventory, metrics, and V6 reconciliation surfaces into one launch decision.

A `GO` requires:
- all technical readiness checks to pass;
- all required activation checklist steps to be complete;
- pilot responsibility, objective, contact, and planned end to be configured;
- the tenant to be enrolled behind the external-effect guard;
- the global switch to remain safe for the current pilot lifecycle;
- tracked sellable inventory to be available;
- no open out-of-stock blocker;
- no V6 reconciliation anomaly.

The response also reports the current traffic mode:
- `BLOCKED_GLOBAL`
- `BLOCKED_TENANT`
- `LIVE_ALLOWED`
- `NOT_ENROLLED`

The dashboard renders this as the **Launch cage** card alongside orders, payment state, inventory availability, stock alerts, and reconciliation anomalies.

A `GO` is readiness evidence only. It never enables providers, changes the global switch, starts a pilot, or deploys code.

## First-customer operations and rollback

Before enrolling a real business, also complete:

- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`
- `docs/FIRST_CUSTOMER_OPERATION.md`
- `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md`
- `docs/COMMERCIAL_EXTERNAL_GATES_V1.md`

The launch cage is a technical safety boundary. It does not replace customer-specific approval, human voice certification, provider sandbox proof, repository protection, or an assigned incident owner.


## C2-B · Twilio one-call terminal GET readback (offline probe prepared 2026-10-16)

The operator-side, Python-stdlib-only **scripts/ops/c2b_twilio_call_terminal_readonly.py** prepares one bounded authenticated GET of the Twilio Call resource, according to https://www.twilio.com/docs/voice/api/call-resource (Accounts/{AccountSid}/Calls/{CallSid}.json). It makes **no HTTP request** at import, in ordinary CI, or without BOTH an explicit --probe-read-only argument and private C2B_CALL_READBACK_APPROVED=yes. It never sends POST, changes a Twilio number, starts a call, modifies PostgreSQL or approves production.

**Required independent preconditions:** a consented, already existing, one-tenant pilot call; committed pilot PAUSE; trusted DB linkage of tenant, phone, AccountSid and CallSid; named incident owner and authorized window. This tool cannot establish these preconditions. Do not run it against the previously observed 10 unmanaged tenants or invent a pilot call ID. Supply expected SIDs only through PRIVATE environment variables C2B_EXPECTED_ACCOUNT_SID and C2B_EXPECTED_CALL_SID, a private C2B_PILOT_TENANT_ID, existing TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN, optional HELVOCA_TWILIO_SUBACCOUNT_SID, LOCALAPPDATA, and the deliberate approval flag. The presence of a tenant UUID in the environment is not proof of database ownership. Never paste IDs or credentials in GitHub, issue comments, command arguments or chat. A provider GET may carry service/API policies; no live provider call has been authorized by this runbook.

Within a separately authorized and prepared private operator environment, run the script with **--probe-read-only** exactly once. The tool verifies SID syntax and configured parent/subaccount scope, exact AccountSid/CallSid in the resource, HTTPS GET to the fixed Twilio host, redirect rejection, bounded body, valid status and private-only reporting. Terminal resource statuses are completed, canceled, failed, busy and no-answer; queued, ringing and in-progress remain NOT_OBSERVED. Any mismatch, unexpected state, invalid response or timeout fails closed. Twilio states that the Call GET is eventually consistent, so an early nonterminal GET is a pending observation, NOT proof of a failed hangup. Do not invent automatic retries.

The private evidence file under LOCALAPPDATA/Helvoca/private-evidence stores timestamp, boolean identity match and carrier status only: never the account/call SID, tenant, token, raw response, phone or transcript. Even when terminal GET is observed, it explicitly records **authenticated callback=UNVERIFIED; committed PAUSE=UNVERIFIED; DB and local AI STOP=UNVERIFIED; commercial_go=false**. A terminal GET is one piece of the C2-B gate, not the gate itself. Correlate it independently with signed Twilio status callback and database reconciliation of the same verified CallSid, persisted PAUSE, closed AI and WebSocket, hangup request, latency, residual audio/cost and incident-owner acceptance. An application DB FAILED status alone is not terminal carrier proof.

**Offline QA:** scripts/ops/test_c2b_twilio_call_terminal_readonly.py exercises 14 network-mocked tests including exact GET route, identity mismatches, terminal/nonterminal state, opt-in, redirects, malformed/oversized response, provider failures, private evidence and fail-closed NO-GO. Locally, coverage.py with branch measurement reported 97/97 executable statements, 4/4 instrumented branches, **100% for this new script only**. This is not full-repository coverage nor authentic carrier evidence. New commit requires exact-HEAD GitHub Full CI; prior 14/14 certificate is only for 98a31530.

**Status:** C2B_TERMINAL_READBACK_TOOL=OFFLINE_TESTED_PENDING_EXACT_HEAD_CI; C2B_AUTHENTIC_TERMINALITY=UNVERIFIED; C2B_RELEASE=NO-GO. No merge, deployment, real provider call, production database write, flag edit or payment has occurred for this tool.


**CI coverage instrumentation checkpoint (2026-10-16, new-head certification required):** The existing Stage D Twilio read-only workflow now installs a pinned `coverage.py` version and measures `c2b_twilio_call_terminal_readonly.py` with `--branch` during 14 mocked tests. It extracts exact covered/total statements and branches from coverage JSON, rejects absent instrumentation and either dimension below 100%, and emits safe aggregate counts only. A red result is a **real QA blocker** to repair with meaningful tests, not grounds to weaken thresholds. The previous local 97/97 and 4/4 claim is **not independently reproducible from the previous GitHub job**, which ran unittest only; the current workflow result is the authority. All network calls in the test suite remain patched; `pip` package installation is CI tooling, not a Twilio/production request. No provider call, credentials, real tenant, Railway mutation or release is authorized by this CI update.
