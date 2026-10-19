# Real-call certification command runner

RecepVoz supports a persistent, fail-closed command runner for authorized real-call certification without changing Railway variables or redeploying for every call.


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
- Every launched call retains the configured safety hangup bounded to 20–180 seconds.

## One-time production enablement

After the feature has passed CI and the exact merged SHA is deployed, set:

```
TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED=true
```

This is the only feature toggle that needs to stay enabled. Do not enable global certification ingress.

## Request one authorized call

Insert a unique run id into PostgreSQL:

```sql
INSERT INTO twilio_certification_command (run_id)
VALUES ('latency-20260927-001');
```

Do not add phone numbers or provider identifiers to the command.

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
