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
