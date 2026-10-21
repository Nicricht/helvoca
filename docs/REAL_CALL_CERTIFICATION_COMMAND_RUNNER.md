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
- A PostgreSQL row lock reserves at most two call attempts per DEMO tenant across concurrent workers. A failed or uncertain provider creation still consumes its slot. The DEMO worker rejects a configured `TimeLimit` above 75 seconds before reaching Twilio. This is not a substitute for verifying the live cumulative USD 5 provider spend before enabling the runner.
- A DEMO-specific existing voice route requires `TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED=true`, exact existing-route tenant/phone/account pins and two-person enrollment audit. It does not enable the global pilot external-effects flag. Setting the route flag to false stops new ingress and Media Streams.
- `TWILIO_CERTIFICATION_DEMO_GEMINI_ENABLED=true` permits Gemini Live only for that exact certified DEMO phone and matching call tenant when the API key/model/endpoint are ready. Customer tenants still follow the independent global Gemini flag. Set the DEMO flag to false for immediate new-session rollback.

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
INSERT INTO twilio_certification_demo_call_quota (business_id)
VALUES ('<certified DEMO business UUID>') ON CONFLICT DO NOTHING;
INSERT INTO twilio_certification_command (run_id, business_id)
VALUES ('aurora-demo-20261019-001', '<certified DEMO business UUID>');
```

Do not add phone numbers or provider identifiers to the command. A legacy command without `business_id` remains pending and cannot launch. The platform API and one-time bootstrap create the quota row and bind the configured DEMO ID after validating the active route. A direct SQL command without a quota row remains pending.

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
RLS/security tests; release still verifies the USD 5 maximum, two attempts,
75-second carrier limit, Gemini tenant isolation and live stop procedure.
Preserve the certified outbound call PR #847 without rewriting it.
