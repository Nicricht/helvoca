# Platform Console → React migration audit

## Decision

The standalone `platform.html` is still a unique product surface and must be migrated, not folded into business Settings or Operations.

Canonical React route: `/app/platform`.

This route is a dedicated PLATFORM_ADMIN console. It must not use the normal business AppShell because PLATFORM_ADMIN intentionally operates with SYSTEM scope and does not receive tenant business permissions.

Legacy `/platform.html` becomes compatibility-only after React parity is proven.

FRAME CHANGE: NO for the business application shell. The platform console keeps its own internal shell.

## Unique capabilities that must be preserved

### PLATFORM_ADMIN gate
- session token required;
- `GET /api/v1/auth/me` must include `PLATFORM_ADMIN`;
- wrong role or missing/expired session redirects to the public auth surface;
- all `/api/v1/platform/**` backend endpoints remain PLATFORM_ADMIN-only and execute under SYSTEM database scope.

### Demo Center
- `GET /api/v1/platform/demos/readiness`;
- `GET /api/v1/platform/demos`;
- create reusable demo profiles;
- `POST /api/v1/platform/demos/{profileId}/prepare`;
- current session lookup;
- manual backup transitions start / finish / abort;
- server-owned timeline and Proof of Value;
- conversion of READY/FINISHED DEMO session into a separate PILOT tenant.

Safety boundaries:
- DEMO, PILOT and CUSTOMER are never the same tenant;
- demo profiles do not contain provider credentials;
- external effects stay DISARMED in DEMO;
- payments stay SANDBOX_ONLY in DEMO;
- conversion copies approved configuration only, not calls/messages/provider identities/credentials;
- browser QA must mock every write endpoint.

### Assisted business provisioning
- `POST /api/v1/platform/businesses`;
- creates a real tenant and an initial one-use administrator invitation;
- accepts business name, timezone, language, optional E.164 transfer number, admin name and email;
- generated invitation must be treated as one-use access material;
- no browser test may create a real production tenant.

### Platform economics
- `GET /api/v1/platform/economics`;
- portfolio commercial estimate;
- estimated platform cost;
- estimated gross margin;
- per-business usage / plan / commercial-value view;
- provider/model cost aggregation;
- all values remain operational estimates, not payment truth or provider invoices.

## Frontend architecture

Canonical route:
- `/app/platform`

Dedicated shell:
- platform brand / PLATFORM ADMIN identity;
- internal section navigation: Demos, Clients, Administration;
- no tenant plan card;
- no tenant navigation;
- explicit logout.

Legacy compatibility:
- `/platform.html` → `/app/platform`;
- `app.js` PLATFORM_ADMIN login/session redirect → `/app/platform`;
- delete `platform.js` after React parity;
- keep `platform.html` only as a minimal public compatibility redirect.

Direct-route support:
- Vite publishes `/app/platform/index.html`;
- Spring forwards `/app/platform` and slash variant to the React index.

## RED-first acceptance contract

1. authenticated PLATFORM_ADMIN can open `/app/platform`;
2. non-platform role is rejected;
3. normal business AppShell/navigation does not render in platform console;
4. readiness, demo profiles, current session and economics load;
5. creating a demo sends the same safe profile payload and never provider credentials;
6. prepare sends exactly one POST to the selected demo profile;
7. lifecycle manual actions send only their explicit POST;
8. conversion requires explicit admin name/email and sends exactly one convert-to-pilot POST;
9. business provisioning requires explicit submit and sends exactly one POST;
10. economics copy preserves estimate-vs-payment truth;
11. legacy `/platform.html` redirects to React and does not load `platform.js`;
12. PLATFORM_ADMIN redirect in the old auth bridge points directly to `/app/platform`;
13. no new business primary-navigation item is introduced;
14. direct route works in the built Spring deployment.

## Risk

HIGH.

This console can create tenants, create demo profiles, stage demo runtime state, manually transition demo sessions and create PILOT tenants. Merge only after exact-head full CI is green, followed by exact-main CI and Railway deployment on the same SHA.


## Secure first platform-administrator onboarding (design gate, 2026-10-19)

**Status: DESIGN ONLY. No platform account, credential, privileged endpoint, production deployment or telephone call has been created.** This section scopes Issue #848 without weakening the existing two-human enrollment control. Production previously contained zero active `PLATFORM_ADMIN` accounts; ordinary business registration, team invitation and user administration deliberately cannot grant this role. The two designated humans must activate their own personal credentials separately. Email reply acknowledging participation does not substitute for authenticated platform actions.

### First-human bootstrap

* Bootstrap must use an explicitly authorized **operator-only**, initially disabled, one-use entry mechanism on a trusted administrative execution surface. It MUST NOT expose a general anonymous `/register/platform` or auto-promote any existing `BUSINESS_ADMIN`. Never log or persist cleartext passwords, bootstrap tokens or invitation tokens.
* An authorized operator may stage only an identity-bound activation invitation, not a known/shared administrator password or a completed platform identity. The invited first person must accept with their own secret and prove control of the intended inbox. Activation must be atomic, audited and permanently consume the invitation; failed, expired, replayed, revoked, or racing acceptance must fail closed.
* The first active `PLATFORM_ADMIN` must have `business_id IS NULL`, exactly the platform role, an independently chosen password hash, and a verifiable audit trail. Never mutate a currently tenant-bound customer account in place. Protect the zero-admin bootstrap boundary against simultaneous contenders, partial database commits and restart/retry.
* The first administrator may invite a second, **different human identity** through a restricted platform endpoint authenticated using a signed platform JWT and active database role, not through the business invitation API. The recipient independently accepts a single-use expiring invitation with their own password. No administrator may accept another person's invitation or approve their own Twilio enrollment request.
* Avoid putting privileged one-use tokens in routine email/provider logs, GitHub artifacts or public URLs. Use an expiring hashed token and an out-of-band private delivery channel. Authentication should be invalidated after account/role revocation; include rate limiting, audit without secret disclosure and emergency shutdown.

### Release and security gates

1. Implement RED-then-GREEN tests for bootstrap disabled-by-default; one-time setup; identity bound to address; invitation collision/replay/expiry/revocation/concurrency; same-person/multiple-email self-approval attempts; absence of tenant affiliation; cross-tenant RLS, login/JWT/DB-role coherence and revoked roles.
2. Verify persistence and concurrency with actual PostgreSQL/Testcontainers, plus API authorization and UI acceptance flow E2E; prove that the original `TwilioExistingEnrollmentAdminService` rejects self-approval and accepts two independently authenticated people only.
3. Enforce 100% applicable differential line/branch/method coverage and all protected Fast/Full CI on the exact implementation HEAD. Document operator-run initialization, rollback, secret delivery and recovery on the same PR. An offline-green PR does not authorize a production user creation, a Twilio binding or a call.
4. The original Aurora demo acceptance stays separate: active isolated DEMO tenant, previously owned voice number with two-human provider enrollment, scoped Gemini, provider-side 75-second limit, at most two attempts and a verified cumulative USD 5 budget. No use of another business's phone number.

