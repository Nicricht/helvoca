# Live Demo Center V1 Design

**Date:** 2026-10-01  
**Status:** Proposed design for implementation  
**Risk:** HIGH  
**Frontend:** FRAME CHANGE: NO

## 1. Intent

Replace the old public “3-minute demo + countdown” concept with a **PLATFORM_ADMIN-only Live Demo Center** inside the RecepVoz platform console.

The commercial goal is simple:

```text
prepare demo
-> prospect calls a real RecepVoz number
-> real voice AI handles the conversation
-> optional real WhatsApp confirmation to the same controlled participant
-> demo operation is persisted inside an isolated DEMO runtime
-> platform admin shows the result
-> if prospect is interested, convert configuration into a PILOT
```

The application does **not** show or enforce a timer. Any 3-minute target is the presenter’s external sales discipline, not product behavior.

## 2. Existing architecture to preserve

The repository already has the foundations required:

- `PLATFORM_ADMIN` console at `/platform.html`;
- protected `/api/v1/platform/**` endpoints;
- tenant-scoped phone numbers;
- inbound Twilio routing through the registered destination number;
- voice provider routing and real-call certification machinery;
- tenant-scoped WhatsApp configuration and outbound messaging;
- universal `BusinessOperation` persistence;
- simulator and sandbox safety boundaries;
- current commercial rule that merchant-payment LIVE is disabled;
- Team & Access V2 for real owners/employees after a pilot is created.

The demo redesign must reuse these foundations rather than invent a separate fake application.

## 3. Superseding PR #671

PR #671 currently implements a public static `/demo.html` with:

- a visible 3:00 countdown;
- scripted scenario cards;
- no real APIs;
- no real voice;
- no real WhatsApp.

That UX is no longer the target.

PR #671 must **not be merged as-is**.

Its useful assets may be reused later as:
- fallback scripted examples;
- presentation-copy reference;
- safe offline demo mode.

But the primary commercial experience becomes the authenticated Live Demo Center described here.

## 4. Platform navigation

Extend the existing `PLATFORM_ADMIN` console into a compact platform workspace:

```text
RecepVoz Platform
├── Demos
├── Pilots
├── Customers
└── Provision business
```

V1 only needs the **Demos** section plus the existing provisioning flow.

No customer-facing navigation receives this feature.

## 5. Core model: Demo Profile vs Live Demo Runtime

### Demo Profile

A `DemoProfile` stores reusable presentation configuration, not production operations.

Suggested fields:

- id;
- display name;
- business name shown by the AI;
- language/timezone;
- catalog/services snapshot;
- business hours snapshot;
- knowledge/FAQ snapshot;
- AI greeting/instructions;
- enabled business capabilities;
- optional presenter notes;
- source metadata;
- created/updated timestamps.

A profile may represent:

- generic Sushi;
- generic Barbería;
- generic Taller;
- a prospect-specific “Sushi Akira” profile;
- a profile built from approved PDF/Excel/photo/import data.

Profiles do not own phone numbers or provider credentials.

### Live Demo Runtime

Use one dedicated business tenant as the **Live Demo Runtime**.

This tenant owns the certified demo channels:

- real inbound voice number;
- certified voice provider configuration;
- optional certified WhatsApp sender;
- sandbox payment configuration only.

A platform admin prepares a profile into this runtime before the meeting.

This avoids:
- moving a real phone number between tenants;
- duplicating provider configuration;
- provisioning many temporary numbers;
- introducing routing ambiguity.

Only one live demo session may be armed at a time in V1.

## 6. Business mode

Add an operational lifecycle distinct from `BusinessStatus`.

Keep:

```text
BusinessStatus = ACTIVE | SUSPENDED
```

Add:

```text
BusinessMode = DEMO | PILOT | CUSTOMER
```

The dedicated Live Demo Runtime is always `DEMO`.

This distinction is authoritative for safety policy.

A DEMO business is active enough to receive certified demo traffic but cannot silently become production.

## 7. Demo Session

Each live presentation creates a `DemoSession`.

Suggested state:

```text
PREPARING
READY
ACTIVE
FINISHED
FAILED
```

Fields:

- id / public run id;
- demo profile id;
- runtime business id;
- prepared by platform user;
- expected participant phone, optional;
- started/finished timestamps;
- channel readiness snapshot;
- current session state;
- failure reason;
- source prospect/tracker reference, optional;
- configuration revision/hash.

The session gives the presenter a clean boundary around “what happened in this demonstration” without deleting all historical demo operations.

Operations/calls/messages created during the session are correlated by:
- session start/end window;
- runtime tenant;
- source references;
- and a dedicated demo-session correlation id propagated where feasible.

The result screen must show only the active/current session, not unrelated historical demo data.

## 8. “Prepare Demo” flow

From `Platform -> Demos`:

1. choose a profile;
2. review profile data;
3. click **Prepare live demo**;
4. backend validates there is no other ACTIVE live demo session;
5. backend validates the runtime tenant is `DEMO`;
6. profile configuration is staged into the Live Demo Runtime;
7. readiness is checked;
8. if every required channel is ready, session becomes `READY`.

Preparation must be idempotent for the same profile revision/session request.

Do not silently purchase/provision phone numbers during preparation.

## 9. Readiness panel

The demo page shows factual readiness, never optimistic placeholders.

Example:

```text
Sushi Akira · DEMO

Voice number          READY
Voice AI              READY
Business data         READY
Operations            READY
WhatsApp              READY / NOT CONFIGURED
Payment               SANDBOX
External effects      ARMED / DISARMED
```

Possible states:

- READY
- NOT_CONFIGURED
- UNAVAILABLE
- FAILED
- SANDBOX_ONLY
- DISARMED

No countdown.

No “3 minutes remaining”.

## 10. Live voice demonstration

The preferred demo begins with an **inbound real call**.

Flow:

```text
prospect phone
-> real demo number
-> Twilio webhook
-> registered Live Demo Runtime phone_number
-> business_id resolution
-> real voice provider
-> real tenant profile/configuration
-> shared business tools
-> persisted call + operation evidence
```

The prospect should be able to ask natural follow-ups and corrections.

The presenter does not choose the AI answer in advance.

The demo must remain bounded by the runtime tenant’s capabilities and existing policy engine.

## 11. Real WhatsApp in the demo

Real WhatsApp may be demonstrated only when the demo sender is currently configured and certified.

V1 safe rule:

- the outbound recipient must be the participant phone associated with the current demo session, or a platform-configured demo allowlist;
- arbitrary recipients are rejected;
- the DEMO runtime cannot start campaigns or bulk messaging;
- the presenter explicitly arms external effects for the session;
- every send is audited with demo session correlation;
- provider delivery status remains distinct from “business outcome completed”.

The strongest first flow is:

```text
real inbound call
-> operation produced
-> prospect asks for / consents to confirmation
-> real WhatsApp confirmation to the same participant
```

This demonstrates continuity without making the demo tenant a general-purpose messaging account.

## 12. External-effect arming

A DEMO tenant may have real certified channels, but outbound external effects are disabled by default.

Introduce a short-lived `DemoExternalEffectGrant` or equivalent session policy.

It records:

- demo session id;
- effect types enabled;
- allowed recipient phone(s);
- issued by platform admin;
- issued/expiry timestamps;
- revoked/consumed state as appropriate.

Possible V1 effect types:

- `VOICE_INBOUND` is passive and requires channel readiness, not recipient arming;
- `WHATSAPP_OUTBOUND` requires explicit arming;
- `PHONE_OUTBOUND` remains disabled unless a separately certified use case is designed;
- `PAYMENT_LIVE` is prohibited.

Default expiry should be short and bounded to the meeting/session.

No global production provider flag should be weakened merely to make demos easier.

## 13. Payment behavior

Merchant payment LIVE remains unavailable and must not be simulated as successful.

Demo Center displays:

```text
Payment: SANDBOX
```

If a payment flow is shown:
- provider is sandbox/test;
- checkout intent is labeled sandbox;
- no real card charge is claimed;
- payment success remains provider/sandbox verified.

When commercial merchant LIVE is implemented and separately certified in the future, this design may be extended. V1 must not pre-authorize that future behavior.

## 14. Demo operations

Operations created by the live call are **real persisted RecepVoz operations inside the DEMO tenant**.

They may include:
- order;
- booking;
- quote;
- lead;
- request;
- delivery where configured.

This is not fake UI state.

However:
- they belong only to the DEMO runtime;
- they must not be counted as customer revenue;
- they must not trigger real staff workflows outside the demo runtime;
- no real customer inventory or calendar is modified;
- demo analytics must remain distinguishable from PILOT/CUSTOMER analytics.

## 15. Live result screen

During/after the call, Platform -> Demos shows a current-session timeline:

```text
Call connected
AI answered
Catalog queried
Order quoted
Customer changed quantity
Order confirmed in DEMO tenant
WhatsApp confirmation sent
Provider delivery confirmed
```

The presenter can open:
- call transcript;
- operation detail;
- message delivery evidence;
- current operation status.

The screen must distinguish:

- requested;
- queued;
- provider accepted;
- delivered;
- confirmed business operation;
- paid.

Never collapse these into one generic “Success”.

## 16. No timer

This is a hard product rule.

The UI must not contain:
- countdown;
- elapsed-time target;
- “3 minutes” badges;
- warnings about time running out.

The platform may record timestamps for observability, but they are not presentation controls.

The presenter controls meeting duration externally.

## 17. Presenter assistance

The Live Demo Center may show small presenter notes such as:

- Ask them to call the number themselves.
- Ask a normal question.
- Ask them to change something.
- If appropriate, ask them to request WhatsApp confirmation.
- Show the persisted result.

These are optional hints, not a scripted wizard.

The user can freely continue the live conversation.

## 18. Demo profile creation

V1 supports:

### Template profile

Create from a reusable platform template:
- restaurant/commerce;
- appointments/services;
- workshop/professional services;
- sales/leads.

Avoid hardcoded backend industry logic. Templates are seed data/capability bundles.

### Prospect-specific profile

Copy a template and edit:
- business name;
- catalog/services;
- prices;
- hours;
- FAQs;
- greeting/rules.

### Import-derived profile

Reuse the existing business import architecture where possible.

Import remains:
```text
source -> extraction -> human review -> approved profile
```

Never let unreviewed extracted data automatically become live demo truth.

## 19. Convert Demo to Pilot

The platform action is:

```text
Convert configuration to pilot
```

It does **not** convert the runtime tenant itself into the customer.

Instead:

1. create a new `PILOT` business;
2. copy approved configuration only;
3. do not copy demo calls/messages/operations/customers;
4. create/invite the real `BUSINESS_OWNER`;
5. leave channels disabled until explicitly configured/certified for the pilot;
6. preserve provenance showing which demo profile seeded the pilot.

Configuration eligible for copy:
- business profile;
- approved catalog/services;
- prices;
- hours;
- knowledge/FAQ;
- AI greeting/instructions;
- capability selections.

Do not copy:
- demo operations;
- demo call history;
- demo customers;
- participant phone identity;
- external-effect grants;
- provider session identifiers;
- sandbox payment events.

## 20. Relationship to Workspaces V1 (#674)

Order of implementation becomes:

```text
Live Demo Center
-> Demo-to-Pilot conversion
-> Workspaces V1
-> first real pilot
```

Team & Access V2 is already production-ready for the eventual pilot owner and staff.

Workspaces V1 remains the employee-operational layer after the business becomes PILOT/CUSTOMER.

The Live Demo Center is a platform-sales tool and does not replace #674.

## 21. Security

All Demo Center APIs live under `/api/v1/platform/demos/**`.

Requirements:

- `PLATFORM_ADMIN` only;
- all platform mutations audited;
- no caller-supplied arbitrary tenant for external effects;
- runtime business id is server-owned configuration;
- only DEMO-mode runtime may be prepared;
- profile staging validates tenant mode before mutation;
- active session uniqueness is enforced transactionally;
- allowed WhatsApp recipients are server-validated;
- effect grant expiry is server-side;
- provider credentials are never returned to browser;
- cross-tenant data never appears in current-session results;
- platform admin access does not grant generic tenant business permissions outside explicit platform services.

## 22. Data model proposal

New persistence:

```text
demo_profile
demo_session
demo_external_effect_grant
```

Add to `business`:

```text
mode VARCHAR(...) NOT NULL DEFAULT 'CUSTOMER'
```

Migration compatibility rule:
- historical real tenants become `CUSTOMER` by default;
- the designated runtime tenant is explicitly set to `DEMO`;
- future assisted trial businesses may be created as `PILOT`, not CUSTOMER, when the provisioning flow is extended.

Do not overload `BusinessStatus.ACTIVE/SUSPENDED` with commercial lifecycle.

## 23. API proposal

```text
GET    /api/v1/platform/demos
POST   /api/v1/platform/demos
GET    /api/v1/platform/demos/{profileId}
PUT    /api/v1/platform/demos/{profileId}

POST   /api/v1/platform/demos/{profileId}/prepare
GET    /api/v1/platform/demo-sessions/{sessionId}
POST   /api/v1/platform/demo-sessions/{sessionId}/start
POST   /api/v1/platform/demo-sessions/{sessionId}/finish

POST   /api/v1/platform/demo-sessions/{sessionId}/effects/arm
DELETE /api/v1/platform/demo-sessions/{sessionId}/effects

GET    /api/v1/platform/demo-sessions/{sessionId}/timeline

POST   /api/v1/platform/demo-sessions/{sessionId}/convert-to-pilot
```

Exact endpoints may be refined during implementation planning, but external-effect arming must remain explicit.

## 24. Frontend

`FRAME CHANGE: NO`.

Extend the existing platform console locally.

Editable platform slots:
- demo profile list;
- demo readiness card;
- current live session;
- timeline/result;
- presenter hints;
- convert-to-pilot action.

Do not modify the customer authenticated frame to host platform demo controls.

No timer component is allowed.

## 25. Failure behavior

- Voice not ready -> session cannot become READY; UI states the exact missing readiness.
- WhatsApp unavailable -> voice demo may proceed; WhatsApp is marked NOT_CONFIGURED.
- Provider outage during demo -> record failure truthfully; do not fabricate delivered/connected state.
- Duplicate Prepare -> idempotent for same profile revision/session request.
- Another ACTIVE session -> reject or require finishing it first.
- Recipient outside allowed session/allowlist -> reject before provider call.
- Demo runtime is not `DEMO` -> fail closed.
- Profile configuration staging partially fails -> do not arm session; rollback transaction/config change where feasible.
- Missing operation projection -> show “needs review” rather than invented result.
- Payment request -> sandbox only.

## 26. Verification

Treat implementation as HIGH risk.

### Unit

- business mode rules;
- active demo session uniqueness;
- profile staging;
- external-effect grant expiry/recipient rules;
- readiness aggregation;
- convert-to-pilot copies configuration only;
- no timer UI contract.

### Integration/security

- PLATFORM_ADMIN required;
- normal tenant user receives 403 for all platform demo APIs;
- DEMO runtime only;
- WhatsApp recipient mismatch fails before provider;
- cross-tenant session timeline cannot leak operations;
- demo effects cannot execute after expiry;
- payment live remains prohibited;
- historical business migration defaults correctly.

### Voice

- real inbound voice uses the registered demo number and resolves the demo runtime tenant;
- no routing override can escape the designated demo tenant;
- provider readiness state is truthful.

### Browser E2E

- Platform -> Demos shows profiles;
- prepare profile;
- readiness states;
- no timer text or timer node;
- active session timeline renders;
- unavailable WhatsApp does not block voice-only demo;
- conversion to pilot requires explicit confirmation and shows configuration-only semantics;
- mobile/desktop platform views do not overflow.

### External-effect certification

Automated CI uses mocks/sandbox only.

Real provider certification is a separate, explicit release step using:
- designated demo number;
- designated test/participant phone;
- certified tenant/provider config;
- no arbitrary recipients;
- no merchant live payment.

## 27. Implementation phases

### Phase A: Demo Center foundation
- persistence;
- business mode;
- platform profile CRUD;
- Live Demo Runtime designation;
- readiness UI;
- no timer.

### Phase B: Real inbound voice
- prepare profile into runtime;
- session lifecycle;
- real inbound call correlation;
- live timeline.

### Phase C: Controlled WhatsApp
- short-lived effect grant;
- participant/allowlist policy;
- real confirmation send;
- provider delivery evidence.

### Phase D: Demo -> Pilot
- configuration-only copy;
- owner invitation;
- channel reset/disarmed state.

### Phase E: Import-assisted profile creation
- source import;
- extraction;
- mandatory review;
- save as profile.

## 28. Explicit non-goals V1

Do not add:
- countdown/timer;
- bulk WhatsApp;
- arbitrary outbound calling;
- merchant payment LIVE;
- automatic provisioning/purchasing during a meeting;
- multi-presenter concurrency;
- many simultaneous live demo runtimes;
- demo operations copied into pilot;
- client-facing Demo Center;
- industry-specific backend branches;
- automatic activation of pilot channels;
- Workspaces V1 implementation inside this same block.

## 29. Acceptance criteria

The implementation is complete only when:

1. #671 is not merged in its current countdown/static-primary form;
2. Platform Admin has an authenticated Demos section;
3. no application timer exists for demos;
4. profiles can be prepared into one dedicated DEMO runtime;
5. readiness truthfully reports voice/WhatsApp/payment capability state;
6. a real inbound call can route through the demo runtime using real AI;
7. resulting demo calls/operations are persistently visible in the session timeline;
8. real WhatsApp, when enabled, is restricted to the active demo participant/allowlist and explicitly armed;
9. merchant payment remains sandbox-only;
10. demo data is isolated from PILOT/CUSTOMER data;
11. Convert to Pilot copies configuration only;
12. all platform demo APIs require PLATFORM_ADMIN;
13. no real external effect occurs from CI;
14. exact-head Fast Gate + Full Gate pass;
15. real-channel certification, when performed, uses explicitly authorized demo endpoints and is documented separately;
16. post-merge main CI and Railway exact-SHA health are verified before completion is claimed.

## 30. Product outcome

The seller no longer presents a fake scripted countdown.

They open their own RecepVoz platform account, choose the prospect profile, verify readiness and say:

> “Llámalo tú.”

The prospect experiences the real voice stack, can change the conversation naturally, can receive a controlled real WhatsApp confirmation when certified, and the seller immediately shows the persisted evidence.

If the prospect wants to continue, the approved configuration becomes a new PILOT without carrying any demo history or fictitious operations into the customer environment.
