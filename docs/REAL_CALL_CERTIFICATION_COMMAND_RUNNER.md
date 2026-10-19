# Real-call certification command runner

RecepVoz supports a persistent, fail-closed command runner for authorized real-call certification. As of the DEMO tenant binding change, an unbound command cannot be claimed.


## Aurora Sur outbound demonstration: protected preparation (2026-10-18)

**NO-GO until independently verified.** The production caller number is assigned to an existing business with WhatsApp activity. The one-shot certification route currently resolves tenant context through that phone number, so a booking demonstration MUST NOT run against it. Do not reassign or mutate that customer's number, tenant, bookings, or WhatsApp service.

The owner authorized preparing a controlled production release **after** implementation, branch QA, tenant-isolation certification, and cost/stop verification; this is not authorization to enable live flags, launch calls, or exceed the existing USD 5 cumulative budget. The target participant consented to a test call at a destination ending in 9611, with a currently configured 75-second maximum. Do not store the full participant number in the repository.

Acceptance before staging production: explicit isolated demo-tenant binding throughout signed Twilio ingress, call/session/media/callback/tool context and real PostgreSQL booking persistence; reject wrong account/tenant/token, replay and expiry; enforce call duration at the Twilio provider *and* keep scoped server-side hangup as backup; verify applicable provider costs and cumulative budget, Gemini runtime readiness, and tenant-scoped rollback. Follow `AGENTS.md`, `docs/engineering/QA_POLICY.md` and PR receipt V2. Do not use an in-memory simulator as PostgreSQL evidence. Document real provider call and terminal SID only after one actually exists.

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
- Every launched call submits `TimeLimit` to Twilio using the bounded certification duration (20–180 seconds), independently of `Timeout=25` ringing, and retains the server-side safety hangup as defense in depth.
- The command stores an immutable DEMO `business_id`. The worker checks that the configured caller is an active, carrier-verified number belonging to that active DEMO tenant and that its pinned existing-number route has independent approval evidence. Ingress atomically consumes the token only for the same tenant.
- A DEMO-specific existing voice route requires `TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED=true`, exact existing-route tenant/phone/account pins and two-person enrollment audit. It does not enable the global pilot external-effects flag. Setting the route flag to false stops new ingress and Media Streams.

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

Do not add phone numbers or provider identifiers to the command. A legacy command without `business_id` remains pending and cannot launch. The platform API and one-time bootstrap bind the configured DEMO ID after validating the active route.

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

The response intentionally excludes the callback token. Requests use a dedicated per-identity rate limit of five requests per hour by default.

For the first controlled deployment smoke only, `TWILIO_CERTIFICATION_COMMAND_BOOTSTRAP_RUN_ID` may contain one unique run id. It is idempotent through the same primary key and should be cleared after the smoke test. Future runs should use the authenticated platform API.
