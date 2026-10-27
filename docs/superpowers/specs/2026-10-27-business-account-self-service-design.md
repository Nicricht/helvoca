# Business account self-service · design (2026-10-27)

## Intent

Allow a real business owner to prove control of an email address and recover a
forgotten password without platform-operator intervention. Reuse Helvoca's
existing business signup, BCrypt, JWT, PostgreSQL tenant isolation, trial and
invitations. This is a separate, stacked Draft PR #851; PR #849 remains the
unchanged platform-admin release candidate.

## Existing truth

- `POST /api/v1/auth/register` creates a `business`, its owner/admin and a
  14-day BASIC trial, then returns a login JWT.
- Platform administrator recovery V107 is an operator-only, default-off
  **tenant-less** workflow. It must never be repurposed for business users.
- `TeamInvitationService` creates a single-use private link, not an email.
- API throttling already protects registration and login; no customer email
  sender, customer password-reset endpoint or email-verification state exists.

## Goals and behavior

1. Customer reset request responds the same way for unknown, inactive and
   eligible email addresses. It never reveals an account, reset token or URL.
   Only an active account linked to a business can receive a reset message.
2. Tokens are at least 256 unpredictable bits, stored only as a digest and
   bound to an exact user/business/purpose. They expire, can be rotated, and
   are consumed once transactionally. Reset uses 30 minutes; email
   verification uses 24 hours. Reject malformed, expired, reused and wrong
   purpose tokens without changing account state.
3. Reset changes only the existing business user's BCrypt password; it never
   grants a role, creates an identity, logs the user in or changes a platform
   administrator. Existing stateless JWTs retain their configured lifetime;
   immediate global token revocation is not claimed.
4. When business-email verification is explicitly enabled, new public
   registrations create an unverified business owner, do not return a JWT
   before verification, and send a one-use verification link. Existing
   accounts are backfilled as verified. A verified user may then log in.
   Invitations already accepted through a possession-bound team token count
   as verification of that invited email. Platform identities are exempt.
5. Email delivery uses an opt-in configurable SMTP sender and a fixed,
   operator-configured HTTPS public origin. Absent or invalid config cannot
   silently enable a production email. No real messages are sent by tests.
6. UI provides a request/reset form and a verify/resend experience. A token
   travels in a URL fragment (not query parameters), is promptly removed from
   browser history, and is never written to logs or application analytics.
7. The existing manual team-invitation link remains available. Automated
   delivery may be enabled separately and must not create duplicate invites.

## Non-goals and constraints

- No Railway server per customer, new microservice, new auth vendor,
  automatic telephone purchase, phone-number approval bypass, payments,
  WhatsApp/voice activation, or actual SMTP message before user authorization.
- Feature flags default OFF. No new code is deployed from this PR until its
  exact SHA is reviewed, CI certified and released under project gates.
- Preserve existing registrations, businesses, roles, passwords and plan
  entitlements. Do not turn a `business_id IS NULL` user into a tenant user.
- PostgreSQL18 migration must be additive and FORCE RLS. Anonymous token
  endpoints receive only narrow application-validated SYSTEM DB context.
- FRONTEND FRAME CHANGE: NO.

## Approaches considered

1. **Reuse V107 platform recovery ledger:** fewer new tables but would mix
   tenant-owned users with privileged tenant-less invitation semantics.
   Rejected for isolation and privilege risk.
2. **Independent tenant-scoped token ledger plus SMTP adapter (selected):**
   additive Flyway migration, one-use transactional tokens, one controlled
   outbound delivery boundary and the existing Spring/React application.
   Slightly more code, preserves safety boundaries and future provider
   portability.
3. **External identity provider:** managed email verification and resets,
   but changes JWT trust, accounts and tenancy for existing customers.
   Deferred until a proven need justifies migration.

## Acceptance and verification

- TDD failure before implementation; focused unit/security/contract tests;
  PostgreSQL18 Flyway/RLS, atomic/replay/expiry/isolation tests; browser E2E
  for every affected form and error branch; simulated SMTP only.
- Exact-HEAD Fast Gate and applicable Full Gate; 100% differential coverage of
  affected Java statements/branches/methods and frontend interactions.
- Report separate CODE/OFFLINE, SMTP DELIVERY, PROVIDER and PRODUCTION gates.
- No merge, deploy, email send, payment or call follows from CI success.
