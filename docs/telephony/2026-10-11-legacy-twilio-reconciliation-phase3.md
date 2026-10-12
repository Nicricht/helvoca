# Helvoca: Phase 3/10 - operator-controlled existing-number enrollment contract

**Scope:** pure backend enrollment preview and human separation-of-duties contract. This phase does **not** register, purchase, activate or modify a phone number. Its output is **reviewable only**; it is not an authorization token, persistence transaction, carrier certification of production phone ownership, or commercial GO.

## New read-only contract

Implemented in TwilioExistingEnrollmentPlanner and unit tests.

Inputs must originate in a server-side **trusted platform administration workflow** (not an ordinary customer request or phone_number update):

- Unique change ID for later audit/idempotency.
- Exact existing phone row ID, business ID and E.164 from a trusted, tenant-scoped read.
- Candidate owning Account SID from configured operator inventory, not a browser or customer-supplied SID.
- Distinct human platform operator and platform approver principal IDs, independently authenticated/authorized by the future phase 6 admin boundary.
- Fresh proof from authenticated carrier GET, obtained directly by the phase 2 TwilioExistingNumberOwnershipVerifier, never a screenshot, cached carrier response, or user-submitted PN SID.

**IMPORTANT:** Actor and Intent are internal value objects, not authentication assertions. A caller could construct them in isolation. No endpoint exposes them now; phase 6 MUST derive both human identities, roles and business scope from real authorization and audit context. Phase 3 unit tests enforce roles and separation but do not pretend to certify actual platform identity.

The planner checks all input fields before a single Twilio request, then requires:
- A persisted, stable phone row ID matching the submitted trusted target.
- Exact tenant ID and E.164, provider TWILIO.
- No pre-existing binding source. MANAGED_PURCHASE, VERIFIED_EXISTING, BYON and even blank non-null sources are conflicts, never silently overwritten.
- No historical voice_verified_at value on an unenrolled row.
- If a PN SID already exists: syntactically valid and identical to the fresh carrier result. A malformed existing SID is not normalized or replaced.
- If an owning Account SID already exists: it must exactly equal the trusted account candidate.
- Two different human IDs, requester PLATFORM_OPERATOR and reviewer PLATFORM_APPROVER, checked before carrier access.
- The carrier verifier itself must approve the expected exact E.164, owner account and optionally existing SID, with configured account/subaccount access and webhook readiness.
- Independent double-check of returned owner AC SID and PN SID. No tenant-provided SID is considered evidence.

Outcomes are short, closed diagnostic codes only. Internal candidate objects contain reference IDs for future transaction work but are annotated to be excluded from JSON and redacted from string representations. No raw Twilio errors or secrets are emitted.

The only successful decision, REVIEWABLE_ONLY, projects source VERIFIED_EXISTING **in memory**, retaining change ID, tenant, row, E.164, attested PN/AC, and two reviewers. No source value is written to the database. No code path calls PhoneNumberRepository, Twilio provisioning POST/DELETE, PhoneProvisioningService, TwilioCallBindingGuard, AI, media streams, or a controller.

## Next-phase contracts, NOT implemented now

**Phase 4:** a forward-only SQL migration to allow distinct truthful VERIFIED_EXISTING provenance and relational checks without backfilling existing records or changing existing MANAGED_PURCHASE history.

**Phase 5:** re-read the row with explicit scoped database access; obtain fresh carrier evidence and compare-and-set old row identity/provenance before committing; enforce uniqueness, concurrency, isolation, idempotency, and safe rollback with PostgreSQL Testcontainers. The in-memory REVIEWABLE_ONLY preview is NOT sufficient to write.

**Phase 6:** restricted platform admin endpoint, identity checks (trusted JWT/session), independent approval events, action audit, change ID persistence and expiry, rate limiting, and tenant-safe diagnostic response. The phase 3 Actor/Intent must NEVER become a tenant-supplied public API payload.

No deployment, real Twilio request, purchase, call, paid operation, Railway change, PostgreSQL production change, database migration, or merge to main. PR #791 must remain draft and stacked on unresolved PR #790 dependencies. The current pilot production row remains unchanged.

Exit requires a complete exact-HEAD GitHub Actions run with all seven required jobs SUCCESS and 100% applicable Java differential lines/branches/methods.
