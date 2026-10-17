# Helvoca: safe reconciliation of an existing Twilio number (10 phases)

Date: 2026-10-11. Classification: **NO-GO for commercial voice**.

## Confirmed evidence, and what it does not prove

- The pilot number is present and active in the Railway PostgreSQL `phone_number` table, with provider `TWILIO`, on the expected pilot tenant. Its `external_id` is **NULL**.
- A human operator showed the number in Twilio Console, with a `PN...` identifier and `AC...` parent-account identifier. Those screenshots are useful clues, **not independent proof from the carrier API**; the number's current owning account or subaccount must be attested by an authenticated carrier read.
- Twilio Console shows the voice and status POST callback URLs targeting the current Railway API. This does **not** certify the media WebSocket or business routing.
- A prior live call reached the Media Stream but failed with `Destination phone number is not registered` (WebSocket close 1011). That call predates the current production deployment. Do not claim the bug still reproduces or has been fixed.
- The current `main` branch contains neither the final eight-part voice release nor the V100 managed-binding migration. The draft voice certification work finishes at PR #790, HEAD `994170526d98ab10178d0667a7af80ac38c8ea67`.
- The later fail-closed `TwilioCallBindingGuard` accepts only a phone having verified `external_id`, exact `twilio_account_sid`, `voice_verified_at`, `MANAGED_PURCHASE` provenance, correct tenant and an admissible call. A legacy row is not eligible automatically.
- Never set `MANAGED_PURCHASE` on an existing number simply because it appears in a Twilio screenshot. Provenance needs a distinct verified-existing enrollment path unless an auditable original managed purchase is proven.

## Non-negotiable safety invariants

1. No purchases, releases, modifications to live Twilio webhooks, carrier calls, AI sessions, billable operations or production deploys during the code-only phases.
2. No manual backfill of `external_id` alone. Changes to production DB require separate explicit authorization and rollback plan.
3. Cross-tenant ownership is never inferred from a phone string, Account SID, frontend input or a screenshot. An operator-authorized, read-only carrier GET and trusted existing tenant assignment must agree before a transaction can update bindings.
4. A carrier verification must match **E.164, PhoneNumber SID, owning Account SID/subaccount, voice capability, voice POST webhook and status POST webhook**, and must reject ambiguous results.
5. Do not expose provider SIDs, authorization tokens, carrier diagnostics or internal tenant correlations to ordinary tenant endpoints; use redacted diagnostic codes.
6. Fail closed for missing provenance, mismatching owners, stale evidence, concurrent changes, account credential outages and duplicate number claims. Never turn a failed verification into `active=true`.
7. Live certification requires the user's separately authorized cost ceiling, real test number(s), participants and appropriate consent.
8. PRs are kept draft/unmerged; GitHub Actions must certify the **exact final HEAD**, with the full required green jobs and 100% *applicable differential* lines, branches and methods. Do not assume green from an earlier SHA.

## Ten sequential phases

| Phase | Deliverable | Exit criterion |
|---|---|---|
| **1** | Baseline and read-only legacy binding inspector | Deterministic diagnosis tests, documented gaps; **zero external effects** |
| 2 | Read-only Twilio carrier attestation for pre-existing numbers | Exact number/SID/account/voice/webhook checks, strict failure and redaction, mocked carrier tests |
| 3 | Explicit operator-controlled verified-existing enrollment contract | Trusted operator approval; no automatic `MANAGED_PURCHASE` labeling and no purchase |
| 4 | Safe schema provenance extension/migration | Separate audited `VERIFIED_EXISTING` provenance with check constraints, no implicit backfills |
| 5 | Atomic reconciliation transaction and tenant RLS | Exact row/account/SID compare-and-set, race and foreign-tenant negatives in PostgreSQL Testcontainers |
| 6 | Restricted administration workflow and audit | Authorization, idempotency, safe redacted diagnostic API, no tenant-supplied SIDs |
| 7 | Twilio ingress / callback / Media Stream consistency | Same verified binding and tenant in every hop; repro and regression for WebSocket start failure |
| 8 | Gemini Live selection and readiness checks | Configured model identified by safe runtime metadata; no paid provider call |
| 9 | End-to-end and multi-tenant regression | Backend, real Testcontainers DB and browser checks; strict coverage, no real phone calls |
| 10 | Release candidate and controlled production checklist | Final exact-SHA CI, safe rollout/rollback and commercial **NO-GO until specifically authorized** |

Stop after each phase; wait for the operator to say `continúa`. Later phase numbers and implementation details can adapt to findings without weakening the guardrails.

## Phase 1 implementation and behavior

`TwilioLegacyBindingInspector` inspects **only a supplied persisted `PhoneNumber` object** and a trusted expected tenant/E.164. It produces immutable diagnostic gap codes:

- Invalid or absent trusted target, absent persisted row.
- Tenant or destination mismatch, wrong provider, inactive number.
- Missing or malformed `PN...` Phone Number SID.
- Missing managed provenance (including legitimate legacy/BYON rows).
- Missing or malformed `AC...` Account SID.
- Missing historical voice-verification timestamp.

It has **no repositories, SQL updates, HTTP clients, Twilio calls, model sessions, scheduled work or new controllers**. A report without gaps means only that the local metadata has a complete shape. It is **never** evidence of live carrier ownership, authorization to call, or a commercial GO.

Tests prove legacy `NULL` SID/provenance/account/verification, tenant mismatch, wrong provider/destination, missing row, invalid identifiers, inactivity, immutable diagnostic snapshot, and no mutation of the input entity.

## Read-only operational next checks

The screenshots show a single pilot row with a null SID. The next phase must confirm the number against the authenticated Twilio API on the correct account (not merely a parent account selected in the console), without buying another number. The existing managed-purchase-only client is insufficient to silently enroll this legacy row: the eventual reconciliation must preserve truthful ownership provenance.

A production operator may continue using read-only queries such as:

```sql
SELECT business_id, phone_number, provider, external_id, active
FROM phone_number
WHERE phone_number = '<PILOT_E164>';
```

This query is for diagnostics, not a proposal to edit production. Do not copy tokens, credentials or screenshots containing secrets into the repository.
