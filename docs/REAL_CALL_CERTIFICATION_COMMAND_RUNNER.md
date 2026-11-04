# Real-call certification command runner

RecepVoz supports a persistent, fail-closed command runner for authorized real-call certification. As of the DEMO tenant binding change, an unbound command cannot be claimed.

## Coverage follow-up · 2026-11-04

The first exact-HEAD CI run after removing the 180-second application cutoff passed Fast Gate, Golden Journey, system integration and PostgreSQL compatibility, but differential Java coverage reported two uncovered branch outcomes in the optional emergency media-deadline paths. Regression tests were added for both:
- The Spring-configured `TwilioMediaSessionDeadline(int)` constructor at disabled (`0`) and enabled emergency-override values.
- A WebSocket closing synchronously during AI startup while the deadline is disabled, exercising the null-deadline cancellation branch.

The next exact-HEAD CI run is authoritative for whether these branches are now fully covered. Do not declare QA complete until the protected Full Gate and differential Java coverage finish successfully.

## Current call-duration policy (2026-11-04)

- The command runner and one-shot startup runner do not send Twilio `TimeLimit` and do not schedule a hangup based on elapsed call time.
- `Timeout=25` controls ringing only; it does not cap the connected conversation.
- `app.telephony.twilio.max-media-session-seconds` defaults to `0` (disabled). Leave `APP_TELEPHONY_TWILIO_MAX_MEDIA_SESSION_SECONDS=0` in Aurora; positive values are an optional 30–3600 second emergency override and are not the normal call policy.
- Calls end when the caller hangs up, the model completes `end_call`, an operator explicitly requests STOP, or the carrier ends the call. Existing signed callbacks, tenant/phone/account binding, one-use token, audit and spend telemetry remain unchanged.
- Omitting Twilio `TimeLimit` removes the application's 180-second cutoff; Twilio's own account-level maximum remains (four hours by default, subject to account configuration). This is a carrier limit, not a RecepVoz conversation timer.


## Aurora Sur outbound demonstration: protected preparation (2026-10-18)

**NO-GO until independently verified.** The production caller number is assigned to an existing business with WhatsApp activity. The one-shot certification route currently resolves tenant context through that phone number, so a booking demonstration MUST NOT run against it. Do not reassign or mutate that customer's number, tenant, bookings, or WhatsApp service.

The owner authorized preparing a controlled production release **after** implementation, branch QA, tenant-isolation certification, and cost/stop verification; this historical authorization was not blanket permission to enable live flags or launch unrelated calls. The target participant consented to a test call at a destination ending in 9611. The 180-second maximum described in this historical section was removed by the 2026-11-04 duration-policy change below. Do not store the full participant number in the repository.

Acceptance before staging production: explicit isolated demo-tenant binding throughout signed Twilio ingress, call/session/media/callback/tool context and real PostgreSQL booking persistence; reject wrong account/tenant/token, replay and expiry; do not impose an application-level call-duration cutoff or scheduled elapsed-time hangup; retain the independent ringing timeout, caller/model hangup, operator STOP and carrier status callbacks; verify provider-cost visibility, Gemini runtime readiness, and tenant-scoped rollback. Follow `AGENTS.md`, `docs/engineering/QA_POLICY.md` and PR receipt V2. Do not use an in-memory simulator as PostgreSQL evidence. Document real provider call and terminal SID only after one actually exists.

## Safety invariants

- The worker is disabled by default with `TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED=false`.
- A command contains only a unique `run_id`. It cannot choose a phone number.
- The destination is always `TWILIO_TEST_TO`.
- That destination must exactly equal `TWILIO_CERTIFICATION_ALLOWED_TO`.
- A destination equal to `TWILIO_CERTIFICATION_FORBIDDEN_TO` is always rejected.
- The business caller number must exactly equal `TWILIO_TEST_FROM`.
- Each command is claimed atomically with `FOR UPDATE SKIP LOCKED`.
- Each claim receives a random callback token with a five-minute TTL.
- The global `TWILIO_CERTIFICATION_INGRESS_ENABLED` flag can remain `false`.
- Twilio must present the one-shot token on its signed webhook request.
- The token can be consumed only once and is bound to the resulting Twilio Call SID.
- Neither certification launch path submits Twilio `TimeLimit` or schedules an elapsed-time hangup. `Timeout=25` remains only the ringing timeout. The Twilio account-level maximum still applies (four hours by default; see the provider documentation).
- The command stores an immutable DEMO `business_id`. The worker checks that the configured caller is an active, carrier-verified number belonging to that active DEMO tenant and that its pinned existing-number route has independent approval evidence. Ingress atomically consumes the token only for the same tenant.
- **No cumulative call-attempt quota:** each explicitly operator-requested DEMO call has its own immutable `run_id` and tenant-bound command row. The worker claims distinct pending commands atomically using `FOR UPDATE OF c SKIP LOCKED`; there is no count-based admission or automatic retry. Migration `V105__certification_demo_call_quota.sql` and its `reserved_calls BETWEEN 0 AND 2` constraint remain in the historical schema, but the current application never reads, increments or writes that old counter. Preserve all historical rows and their counts without resets, deletion or SQL workarounds. No new migration is needed for the runtime change. A new command still requires explicit authorization, immutable DEMO tenant binding and a one-use callback token; no application-level call-duration cap is applied. This does **not** establish a cumulative Twilio/Gemini spend cap; spending is a separate operational risk. The separate startup runner stays OFF and must never be used to evade command/tenant safety controls.
- A DEMO-specific existing voice route requires `TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED=true`, exact existing-route tenant/phone/account pins and two-person enrollment audit. It does not enable the global pilot external-effects flag. Setting the route flag to false stops new ingress and Media Streams.
- `TWILIO_CERTIFICATION_DEMO_GEMINI_ENABLED=true` permits Gemini Live only for that exact certified DEMO phone and matching call tenant when the API key/model/endpoint are ready. Customer tenants still follow the independent global Gemini flag. Set the DEMO flag to false for immediate new-session rollback.

## Aurora scoped Gemini media admission regression · 2026-11-01

The first real Aurora attempt reached authenticated Twilio ingress and the
router selected `gemini`, but `TwilioMediaStreamHandler` rejected the stream
before creating a Gemini session. The router already used
`GeminiLiveVoiceProvider.configuredFor(businessPhone)` for the Aurora-scoped
route; the media handler incorrectly rechecked `provider.configured()`,
which requires the global `GEMINI_LIVE_ENABLED` switch.

The handler now applies the same scoped readiness decision **only after**
the signed media-route check and `TwilioCallBindingGuard.allowsMedia(...)`
succeed:

- Gemini uses `configuredFor(businessPhone)`, which requires the DEMO-specific
  Gemini opt-in, ready local model/key/endpoint configuration and the exact
  pinned Aurora phone/business binding.
- Every other provider continues to use `provider.configured()`.
- Keep global `GEMINI_LIVE_ENABLED=false`. Do not open Gemini to other tenants.
- The positive regression proves a bound scoped Gemini stream can create and
  start its session while global readiness is false. The negative regression
  proves a signed stream whose scoped binding is denied is closed before any
  call lifecycle or AI session is created.

This code change does not enable the runner, route, Gemini DEMO flag, startup
call or global pilot effects. It is not evidence of a successful telephone
conversation; exact-HEAD CI and isolated deployment verification remain
required before the next live attempt.

**Attempt-history checkpoint:** two Aurora commands have already been recorded:
`aurora-demo-20261101-001` failed before carrier creation but remains in the
attempt history, and `aurora-demo-20261101-002` created CallSid
`CA428da039e94ca8902c20c73d61c242dc` (Twilio completed after 1 second).
These records are not a lifetime attempt ceiling and must not be deleted or
reset. A further call still requires a distinct, explicitly requested command;
there are no automatic retries. The owner authorized one additional call and
withdrew the former USD 5 cap for this test. At the time of this historical call the 180-second maximum was still active; the 2026-11-04 duration-policy change below supersedes that cap. The 6780→9611 route remains pinned. Never use 2512.

### Completed additional voice attempt · 2026-11-03

The separately authorized outbound startup-runner test completed as CallSid
`CA0ff94a37035279f155ff48c91e14b6ab`: Twilio status `completed`, duration
56 seconds, recorded charge USD 0.07460. Gemini Live audio connected. The
tenant-scoped transcript/setup persistence correction and PostgreSQL 18
regression are recorded in Issue #848.

That additional authorization has been consumed. The former two-slot limit
is historical only; the updated command runner admits any number of **new,
explicitly authorized** commands without changing old quota rows or history.
Unlimited attempts do not create automatic redials or imply unlimited cost
authorization. No further provider call is part of this code change. Keep all
call-launch flags OFF.


## One-time production enablement

After the feature has passed CI and the exact merged SHA is deployed, enroll the carrier-owned DEMO number through the two-person platform process, verify callbacks and configured route pins, and set the DEMO tenant pin. Then set:

```
TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED=true
TWILIO_CERTIFICATION_DEMO_BUSINESS_ID=<existing active DEMO business UUID>
TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED=true
```

The existing voice route must also be pinned and enabled for this exact DEMO phone. Do not enable global certification ingress or global pilot external effects. Roll back the DEMO route and runner flags to false and hang up the known Call SID if necessary.

## Request one authorized call

Insert a unique run id into PostgreSQL:

```sql
INSERT INTO twilio_certification_command (run_id, business_id)
VALUES ('aurora-demo-20261019-001', '<certified DEMO business UUID>');
```

Do not add phone numbers or provider identifiers to the command. A legacy command without `business_id` remains pending and cannot launch. The platform API and one-time bootstrap bind the configured DEMO ID after validating the active route. No quota row is needed. A legacy command lacking `business_id` remains pending and is not claimed.

The worker claims the row and updates its state through:

`PENDING -> CLAIMED -> INGRESS_CONSUMED`

Failures become `FAILED`.

## Inspect a run

```sql
SELECT run_id,
       status,
       requested_at,
       claimed_at,
       expires_at,
       provider_call_sid,
       completed_at,
       failure_reason
FROM twilio_certification_command
WHERE run_id = 'latency-20260927-001';
```

A reused `run_id` is rejected by the primary key. A consumed or expired callback token cannot authorize another webhook.

## Latency certification

For a latency-only call, do not request a booking or other side effect. Use normal short conversation turns and inspect:

- `VOICE_HYBRID_VAD_END`
- `VOICE_RESPONSE_LATENCY`
- tool latency events, if any
- first-audio timing

Compare median, worst response and responses above 3 seconds against the previous certified baseline.


## Platform API

Normal certification requests no longer require direct SQL access.

A `PLATFORM_ADMIN` can create a run with:

```http
POST /api/v1/platform/certification-runs
Content-Type: application/json

{"runId":"latency-api-20260927-001"}
```

The request accepts only `runId`. It cannot supply a destination phone number, voice, provider Call SID, callback token or certification flags.

Inspect status with:

```http
GET /api/v1/platform/certification-runs/latency-api-20260927-001
```

The response intentionally excludes the callback token. Requests no longer have a special five-per-hour quota. They use the same default authenticated API burst throttle (600 requests per minute), with independent actor authentication and authorization still required. This traffic-safety throttle is not a cumulative limit on authorized call attempts.

For the first controlled deployment smoke only, `TWILIO_CERTIFICATION_COMMAND_BOOTSTRAP_RUN_ID` may contain one unique run id. It is idempotent through the same primary key and should be cleared after the smoke test. Future runs should use the authenticated platform API.

## Platform administrator onboarding (design gate, not yet activated)
### Administrator onboarding in PR #849 (Aurora isolated only; NOT merged to production)

> **2026-10-20 Aurora isolation runtime regression:** enabling `HELVOCA_PLATFORM_ADMIN_INVITES_ENABLED=true`
> on isolated Railway deployment `f00ed592-ec5c-417a-ab16-0c0d61ef5fb1` caused
> application startup failure: Spring Method Security could not CGLIB-proxy
> the `final` `PlatformAdminInviteController`. The isolated environment was
> recovered ONLINE with that flag OFF in deployment
> `faa94f10-5abc-4332-afa4-14bcdaf1c120`, without touching production,
> Twilio or Gemini. PR #849 adds a regression for class-based security advice
> and removes the controller's `final` modifier. **Do not enable the invite
> route or redeploy the revised code as certified** until exact-new-HEAD
> Fast/Full QA reaches terminal PASS and a separate isolated release gate is
> completed. The bootstrap invitation's persistence has **not** been
> independently confirmed merely by recovering the application: check
> PostgreSQL's invitation row and expiry, without exposing token hashes.
> No phone call or provider demonstration was executed.

The onboarding backend is separate from the Twilio demo binding and is
disabled unless the operator opts in. For first-time authorized setup,
configure HELVOCA_PLATFORM_BOOTSTRAP_ENABLED=true plus a human's
HELVOCA_PLATFORM_BOOTSTRAP_NAME, HELVOCA_PLATFORM_BOOTSTRAP_EMAIL and
HELVOCA_PLATFORM_BOOTSTRAP_TOKEN (43-character base64url encoding of
32 cryptographically random bytes). This one-time startup creates only an
invitation hash in PostgreSQL, not a user or an authenticated session.
Never paste the raw token into issues, workflow outputs or app logs.
After acceptance, turn off the bootstrap flag; a restart with an existing
platform account must never mint a new invitation. Existing business emails
are never silently transformed into platform accounts.

Only when HELVOCA_PLATFORM_ADMIN_INVITES_ENABLED=true, the holder of the
private activation token may POST to
/api/v1/auth/invitations/platform/accept with token + their own
10..72-character password. The token is consumed in a PostgreSQL
transaction and cannot be used twice. They must subsequently log in
via the existing application authentication endpoint.

A first independently authenticated platform admin may then POST
/api/v1/platform/admin-invitations with a second human's name and
independent email, receive a one-time invitation token in a no-store
authenticated HTTPS response, and deliver it privately to that person.
The second person activates their own account using the same POST
acceptance route. Admin invites can be revoked with DELETE
/api/v1/platform/admin-invitations/{invitationId}. Platform actions
verify the signed JWT against the ACTIVE tenant-less DB role.

These APIs are not an email-delivery service. A two-person reply or
invitation is not evidence of Twilio approval until each separately
authenticated platform account has performed the existing enrollment
request, approve and execute operations. The current API implementation
and any new V106 migration require exact-HEAD full CI and production
release approval. Do not enable flags, merge or deploy on this plan alone.

### Operator-controlled platform password recovery

If an existing, active, tenant-less `PLATFORM_ADMIN` forgets the personal
password, do not edit `app_user.password_hash` directly, rerun bootstrap,
promote another user, or replace the identity. PR #849 provides a default-off
recovery path backed by Flyway V107 and the same hash-only invitation ledger.

Generate a new 32-byte cryptographically random value encoded as unpadded
Base64URL (43 characters), deliver it only through the private operator
channel, never reuse it for another account or environment, and configure only
the isolated target environment:

```
HELVOCA_PLATFORM_ADMIN_RECOVERY_ENABLED=true
HELVOCA_PLATFORM_ADMIN_RECOVERY_EMAIL=<existing platform administrator email>
HELVOCA_PLATFORM_ADMIN_RECOVERY_TOKEN=<43-character private token>
```

On startup the runner verifies that the email belongs to exactly one active
tenant-less platform administrator, stores only the SHA-256 fingerprint, and
creates a 30-minute `RECOVERY` claim. Reusing the same live configuration is
idempotent; rotating the token revokes the prior pending recovery. Invalid,
expired, revoked, or replayed tokens fail closed.

The administrator personally submits the private token plus a new 10–72
character password to the existing
`POST /api/v1/auth/invitations/platform/accept` endpoint. Acceptance updates
only the existing account's BCrypt password hash and consumes the claim in the
same PostgreSQL transaction; user ID, tenant-less scope, active state and role
links remain unchanged. No login or JWT is generated automatically.

After successful acceptance, set
`HELVOCA_PLATFORM_ADMIN_RECOVERY_ENABLED=false`, clear the recovery email and
token variables, redeploy the isolated service, and verify a new independent
login plus `/api/v1/auth/me`. Previously issued JWTs expire according to the
normal access-token lifetime; if compromise rather than simple loss is
suspected, keep privileged operations paused until that window has elapsed.
This recovery flow does not authorize invitation revocation, Twilio
enrollment, provider activation, a real call, spending, merge, or production
deployment.


The verified-existing number enrollment is a normal two-person PLATFORM_ADMIN
workflow, including for DEMO. The first PLATFORM_ADMIN cannot be created
through normal registration or tenant invitations. Never elevate a customer
login, invent a second approver or patch user_role manually to bypass approval.

A future independent platform onboarding mechanism must provide:
- An operator-controlled, opt-in, one-time bootstrap invitation for the
  first real human only, with an operator-generated cryptographically strong
  secret supplied over a private channel; default OFF; no hard-coded accounts.
- A platform-only second invitation; immutable inviter identity comes from
  a signed JWT AND a current ACTIVE PLATFORM_ADMIN DB row.
- Secret token hashes only in PostgreSQL, expiry, revocation, replay rejection
  and transactional single-use acceptance; each person chooses their password.
- A tenant-less platform identity: app_user.business_id IS NULL; preserve
  unrelated customer accounts (same email is rejected, never promoted).
- Independently signed-in humans before the request/approve/execute lifecycle.
  Email consent is not an authenticated platform approval.

Do not turn on a bootstrap feature in shared Railway production merely
because this runbook or CI exists. Require RED/GREEN and full PostgreSQL
RLS/security tests; release still verifies tenant isolation, Gemini tenant
readiness, provider-cost visibility and the live operator STOP procedure.
Attempt admission allows unlimited distinct manually requested commands, not
automatic redials. No application-level call-duration cutoff is active.
Preserve the certified outbound call PR #847 without rewriting it.


## Carrier-owned existing-number enrollment from an empty tenant phone table (draft change, 2026-10-30)

An existing Twilio number is not a new purchase. Do not use the self-service
`POST /api/v1/phone-numbers` path for an unassigned number: this requires a
previously managed and verified row. Do not create a false `MANAGED_PURCHASE`
record, add a carrier PhoneSid by hand, or bypass the platform-admin
two-human enrollment. The isolated Aurora tenant currently has no row for
its existing Twilio number ending 6780 and remains in `CUSTOMER` mode.

The proposed narrow platform workflow adds a **non-active staging step**
for an independently carrier-attested, already-owned number. A staging
record does not grant carrier rights, activate voice, fill a PhoneSid, or
replace the existing request/approve/execute audit. It must fail closed for
wrong tenant, missing platform role, incorrect Twilio account, incomplete
webhooks, carrier failure, and duplicate number. The following independent
approval and DEMO-only admission remain separate requirements.

**Status: DRAFT, no permission to configure Twilio or execute real calls.**
The staging code requires signed PLATFORM_ADMIN + SYSTEM scope, an exact
`TWILIO_CERTIFICATION_DEMO_BUSINESS_ID`, active business and real GET
carrier proof with matching webhook and callback. It creates a row with
`active=false`, no PhoneSid/provenance and no voice rights.

A separate `TWILIO_CERTIFICATION_SCOPED_CUSTOMER_DEMO_ENABLED` flag (default
`false`) permits the existing isolated CUSTOMER business through the
*DEMO certification route only*. This still requires the pinned tenant,
phone UUID, carrier account and immutable two-human APPLIED approval. It
does not enable global voice or override any other tenant policy.

Complete exact-HEAD Fast/Full QA, authorized independent enrollment,
exact-number route verification and cost limits before any isolated
deployment or provider change.


## Single-operator Aurora DEMO carrier enrollment (PR #853, pending exact-HEAD CI)

The owner expressly retains one `PLATFORM_ADMIN` for V1. The existing two-human carrier enrollment path and its database constraints remain unchanged. An alternative, narrowly scoped route is implemented for a signed-in platform owner to authorize one pre-existing Twilio number for the isolated Aurora demonstration. It is **OFF by default** and is **not proof of a completed provider enrollment or permission to call**.

- Migration `V109__twilio_single_operator_demo_enrollment.sql` creates a separate SYSTEM-only PostgreSQL ledger and append-only actor audit; the old V102/V103 dual-approval tables, constraints and routes are preserved.
- This path accepts only the exact `TWILIO_CERTIFICATION_DEMO_BUSINESS_ID` tenant, `TWILIO_TEST_FROM` number and `RAILWAY_ENVIRONMENT_NAME=demo-isolated`, and is gated by `TWILIO_SINGLE_OPERATOR_DEMO_ENROLLMENT_ENABLED=true`. It never grants a tenant endpoint or reassigns an unrelated number.
- Platform JWT and active database role are checked for each request; confirmation additionally requires a recent JWT (issued in the preceding five minutes), the **current platform-account password** submitted only through authenticated HTTPS, exact re-entry of the destination number and a pending unexpired 15-minute request. Failed passwords are recorded with at most three attempts per request. Do not paste passwords, bearer tokens or private provider credentials into GitHub, chat or logs.
- The authenticated carrier verification is performed before a request and again before confirming. It must validate Twilio account, PhoneSid, voice capability and the exact primary/status webhooks. PostgreSQL atomically records the phone as `VERIFIED_EXISTING` and active, the verified carrier PN/AC, the single-operator attestation and a `CONFIRMED` audit event. Route admission checks that exact attestation, active role, phone/business/account pins, DEMO pilot guard and isolated environment; a request without final audited proof cannot admit a call. The confirmation is one-use and must fail closed on expiry, wrong account/tenant/number, any carrier failure or conflicting row.
- The old `/api/v1/platform/twilio-existing-enrollments/stage` still stages the carrier-owned number as **inactive** after a real provider GET. Once safely staged and while the default-off single-operator feature is deliberately enabled **only in Aurora DEMO**, a logged-in platform administrator may POST `/api/v1/platform/twilio-single-operator-demo` with `businessId`, `phoneId` and `ownerAccountSid`. The response contains `changeId`. To confirm, POST `/api/v1/platform/twilio-single-operator-demo/{changeId}/confirm` with private `currentPassword` and `expectedPhoneNumber`. GET the same `{changeId}` path to inspect the final state. All operations require a real platform JWT.
- **No phone calls are enabled merely by deploying or registering.** Route and runner remain separately disabled until signed Twilio ingress, independent callback/phone attestation, voice/Gemini readiness, consent, exact tenant/phone/account binding and a STOP plan pass in the isolated tenant. The current policy has no application-level duration cutoff; the Twilio provider maximum remains. Attempt admission allows unlimited distinct manually requested commands, not automatic redials. Do not touch the unrelated 2512 number or activate globally. After an authorized call, record the real CallSid, terminal outcome, persisted fictional booking and actual spend, then turn temporary switches OFF.

**Historical acceptance snapshot (2026-10-30):** This NO-GO state was superseded by the 2026-11-03 isolated deployment and the 2026-11-04 completed Aurora call. Retain it as history only; consult the latest Issue #848 checkpoint for current provider readiness and call state.

## Startup runner callback URL selection · 2026-11-03

The startup runner has two internal direction values for its Twilio voice URL:

- `inbound-certification` selects `/webhooks/v1/twilio/inbound-certification`.
- `outbound-test` (including unknown or absent direction values after normalization) selects `/webhooks/v1/twilio/outbound-test`.
- Both paths retain the separate `/webhooks/v1/twilio/status` status callback; neither submits a provider-side `TimeLimit`.

The regression test `TwilioCertificationStartupRunnerTest.createCallUsesInboundCertificationWebhookForInboundDirection` captures the form body at a mocked `HttpClient` boundary and asserts the inbound URL. The neighboring outbound contract test asserts the authenticated request, status callback and ringing timeout while verifying that no provider-side `TimeLimit` is sent. These are local provider-contract tests only: the mock does not contact Twilio, enable the inbound webhook, prove carrier acceptance, start Gemini, or establish a real call.

This note records URL-selection behavior and test scope; it does **not** change the operator procedure or authorize any flag change. Keep the startup runner, certification ingress, Aurora DEMO route and Gemini flags OFF unless a separate release authorization and all existing gates are satisfied. No further Aurora call is part of this test work.


## Startup runner scheduler coverage follow-up · 2026-11-04

The exact-HEAD differential Java coverage report identified one uncovered executable line and one uncovered synthetic method in the delayed startup-runner success task (`TwilioCertificationStartupRunner.java`). The report was `323/324` changed executable lines and `92/93` changed methods, with all changed branches covered.

A package-visible kickoff-executor factory now makes the delayed task deterministic in tests. `TwilioCertificationStartupRunnerTest.startupKickoffLogsCreatedCallAndShutsDownSchedulerAfterSuccessfulCall` captures and invokes the scheduled task synchronously using a mocked Twilio HTTP response, then verifies the HTTP boundary and executor shutdown. `kickoffExecutorCreatesDaemonWorkerThread` exercises the default scheduler factory and its daemon-thread behavior.

These are local tests only. They do not make a real Twilio request, enable a runner, place a call, or change the Aurora environment. Exact-HEAD CI must still prove the differential line, branch and method gates at 100%.
