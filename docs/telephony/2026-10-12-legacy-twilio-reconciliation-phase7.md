# Phase 7/10: Twilio ingress, callbacks and Media Stream continuity

**Scope:** Code-only tests in draft PR #791. Production and commercial voice remain **NO-GO**. This phase does **not** enable `VERIFIED_EXISTING` for inbound, callback or media routing. No Railway configuration, provider settings, calls, webhooks, purchases, credentials or PostgreSQL production migrations are changed.

## Inspected boundaries

- `TwilioSignatureValidationFilter` authenticates POST webhooks; `TwilioSignatureValidator` verifies HMAC with the parent or explicitly allowed child-account token.
- `TwilioVoiceController` calls `TwilioCallBindingGuard` before admission/routing. Its stream-status and call-status callbacks require the existing persisted call and consistent account and stream.
- `TwilioCallBindingGuard` checks phone activity, tenant, destination, provider, owner AC SID, PN SID, voice-verification timestamp, and persisted call identity; the old `MANAGED_PURCHASE`-only restriction remains deliberately unchanged.
- `TwilioMediaRouteSigner` signs call/phone/caller/provider identifiers. On WebSocket start, `allowsMedia` requires that a matching carrier call and tenant already exist in the DB.

## Fixed issue

The WebSocket `start` previously allowed contradictory outer/nested StreamSids, silently preferring the outer value. After admission, `media`, `mark` and `stop` frames could carry unrelated StreamSids without a recheck.

`TwilioMediaStreamEventBinding` now rejects contradictory start identifiers, and `TwilioMediaStreamHandler` validates every post-start media/mark/stop StreamSid against the admitted stream. Mismatches are closed with POLICY_VIOLATION; original call failure/stoppage is recorded using existing explicit system scope. No foreign frames reach AI or any unrelated stream.

## Synthetic regressions

- Contradictory signed start rejected before carrier binding, lifecycle or AI.
- Matching start/audio/mark/stop works unchanged.
- Foreign StreamSid on audio, mark or stop closes the socket without forwarding audio or interacting with another tenant.
- Pre-start audio/mark/stop ignored.
- `VERIFIED_EXISTING` remains denied for all existing carrier ingress, callback and media paths.
- Existing PostgreSQL Testcontainers tenant isolation, Twilio HTTP HMAC and carrier callback regressions stay in the full CI suite.

**Limits:** Tests use fake carrier identifiers and no Twilio request. The historic 1011 close / "Destination phone number is not registered" cannot be reproduced as a current production symptom without separately authorized operations. Pilot PN/AC ownership, production callbacks and live calling remain uncertified.

**Certification gate:** record exact-HEAD 7/7 CI SUCCESS plus 100% differential Java line/branch/method coverage in the PR. Do not start Phase 8 automatically.
