# Phase 9/10: regression of Twilio tenant isolation, synthetic carrier ingress, and frontend activation UX

## Scope

Code-only regression on PR #791 after Phase 8 exact-HEAD certification. No production resources, Railway database or runtime flags, real Twilio or Google network calls, purchases, webhooks, keys or migrations are modified. Phase 10 is NOT started.

### Two-tenant PostgreSQL Testcontainers scenario

`TwilioTenantBindingIntegrationTest` seeds two independent tenants with a real PostgreSQL 16 + Flyway schema and RLS. Tenant A holds a synthetically attested, active `VERIFIED_EXISTING` number, with correct fake AccountSid, PhoneNumberSid, voice-verified timestamp, CallSid and MediaStreamSid. Tenant B holds an otherwise equivalent `MANAGED_PURCHASE` number.

Assertions enforce that **all four Phase 7 carrier access checks** reject Tenant A: incoming, media, call-status and stream-status callback. Tenant B's managed call works normally, but cannot be replayed against Tenant A's account or destination. Tenant-scoped reads cannot see the other tenant's phone or call despite an unrestricted test fixture writer. This intentionally does not permit `VERIFIED_EXISTING` calls.

### Frontend and onboarding consistency

`VoiceSetupReadinessServiceTest` confirms that an active `VERIFIED_EXISTING` number with internally consistent SID, account and voice verification still gives `CONFIGURATION_INCOMPLETE`, PHONE incomplete, no live certification and no commercial voice readiness. Existing managed-call readiness and carrier failures remain covered.

`e2e/react-voice-setup-readiness.spec.js` simulates an active imported number and the fail-closed server guide. Refresh remains read-only; no phone purchases, activation, calls or non-GET requests may arise from guide rendering. All 614+ Playwright browser scenarios remain part of the full test suite.

### Release boundaries

The frontend guide is a view of the trusted server readiness response, not direct evidence of Twilio ownership. Gemini 3.8 model metadata and offline selected-provider readiness were handled in Phase 8; they are not proof of a successful real call. Production and commercial activation remain **NO-GO** until explicit operator authorization, separate pilot phone reconciliation, cost caps, and genuine carrier/model handshake validation.

## Exit gate

Seven required GitHub CI jobs `SUCCESS` on exact final HEAD, including Java backend and Testcontainers, frontend coverage, system-integration E2E and 100% changed Java line/branch/method coverage. Do not merge or begin Phase 10 without an explicit continuation.
