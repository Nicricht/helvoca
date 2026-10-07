# Helvoca V1 Finish — Part 2 Branch Cross

**Date:** 2026-10-07  
**Baseline:** `10fe716c462d5c8d32b4c004ddbc45bb4938c1c8`

## Executive result

The 60 retained historical refs were crossed against the four concrete V1 P1 gates from the finish matrix.

**Result: no historical branch contains a missing production capability that must be merged to finish V1.**

There is therefore:

- **0 whole-branch merge required**
- **0 production-code rescue required to unblock V1**
- **1 optional, non-blocking test-hardening source**
- **4 remaining gates that are external/operational and cannot be closed by Git archaeology**

The historical refs are no longer the critical path.

## P1 1 — Real tenant voice certification

### `fix/real-call-certification-regressions-v1`

Verdict: **OPTIONAL TEST HARDENING ONLY**

The branch moved after merged PR #509 by two test-only commits.

Current main already has the guarded cleanup implementation and the stronger exact Twilio provider CallSid binding. Current main also already covers a missing persisted booking under the renamed test `refusesCleanupWhenLinkedBookingNoLongerExists`.

Two useful regression ideas are still absent as exact tests:

1. malformed premature confirmation identifiers without a complete proposal shape must fail closed;
2. an unexpected/non-confirmed booking status must never be mutated by certification cleanup.

Both are **coverage hardening**, not missing runtime behavior. They may be re-expressed against current APIs in Part 3, but they do not block the RC.

### Other voice refs

- `fix/real-cert-cleanup-provider-guard-v1`: superseded by current main provider-CallSid guard and test.
- `perf/voice-latency-v1`: current `GeminiLiveLowLatencySetupTest` is byte-identical.
- `ci/voice-chilean-sensual-hangup-repeat-v2`: PR #493 explicitly says **DO NOT MERGE**, certification-only.
- `release/recepvoz-v1-pilot-rc`: current `VoiceCommercialLifecycleCertificationTest` is byte-identical and main has later work.

**What still closes this P1:** tenant-specific carrier/interruption evidence during an explicitly authorized pilot window, if voice is in the sold scope.

## P1 2 — Real tenant WhatsApp certification

### `feat/meta-embedded-signup-selected-phone-validation-20260920`

Verdict: **SUPERSEDED**

PR #244 explicitly says it was superseded by merged PR #243.

Current main is stronger than the historical branch: selected-phone validation is present and now additionally requires WABA app subscription and records human audit evidence.

### `fix/meta-operator-connect-fail-closed`

Verdict: **SUPERSEDED**

PR #280 explicitly says PR #282 replaced it on updated main and merged the fail-closed connect guard.

### `fix/meta-activation-require-disabled-state`

Verdict: **SUPERSEDED BY CURRENT ACTIVATION MODEL**

This branch represented an older activation invariant. Current main intentionally evolved to staged activation behind independent global delivery controls. Current tests include `activateArmsUncertifiedTenantWhenPilotPreflightIsReady`, readiness/deployment blocking, and audited/idempotent sender behavior.

Re-introducing the old branch wholesale would regress the newer model.

### Diagnostic/sandbox refs

Diagnostic WABA logging and historical Twilio WhatsApp sandbox branches cannot prove real Meta delivery.

**What still closes this P1:** actual authorized Meta tenant delivery/status evidence. A branch merge cannot manufacture provider evidence.

## P1 3 — Mercado Pago TEST round-trip

### `cert/saas-billing-sandbox-provider-run-1`

Verdict: **ARCHIVE EVIDENCE ONLY FOR V1**

Current main already contains:

- `.github/workflows/saas-billing-sandbox.yml`
- `MercadoPagoSaasSandboxProviderIT`

The current workflow is safer/more reusable: manual `workflow_dispatch`, explicit sandbox confirmation and parameterized `@testuser.com` payer.

The old branch's one-shot workflow hardcodes a historical buyer email. Its unique invoice-bridge test also hardcodes historical subscription, invoice and business identifiers. Those artifacts are useful archaeology, not the missing production path.

They still do **not** prove the remaining gate: an actual TEST buyer/provider round-trip with real signed provider webhook delivery.

### Historical commercial RC

The important SaaS billing hardening is already in current main:

- V77 billing idempotency migration: byte-identical;
- `BillingSubscriptionServiceEdgeCasesTest`: byte-identical;
- current provider certification workflow: present.

**What still closes this P1:** execute the existing current-main sandbox workflow with authorized Mercado Pago TEST credentials and capture provider evidence, if automatic SaaS billing is required for the first launch.

## P1 4 — First-customer activation evidence

### `pilot/first-business-readiness-v1`

Verdict: **FUNCTIONALLY SUPERSEDED**

The branch introduced `GET /api/v1/operations/pilot-preflight`.

Current main exposes that same endpoint through `PilotGoNoGoController`, but its implementation is substantially broader. Current `PilotGoNoGoService` composes:

- technical readiness;
- activation checklist;
- pilot lifecycle/configuration;
- Voice/WhatsApp/Payment guard state;
- global kill switch;
- inventory availability;
- stock alerts;
- reconciliation anomalies;
- pilot metrics.

The historical preflight must not be restored.

### Certification/release/onboarding branches

- `pilot/first-business-readiness-v1-certify`: certification-only, explicitly NO MERGE.
- `chore/commercial-release-candidate-v2`: safety model is now in main.
- `release/recepvoz-v1-pilot-rc`: key files are exact or evolved in main; current Golden Journey coverage is larger.
- old commercial-demo onboarding branches: cannot replace actual customer-specific completed evidence.

**What still closes this P1:** a real customer's completed onboarding/scope/owners/success criteria/payment method plus tenant Launch Cage GO.

## Part 6 special refs, reinterpreted for finishing V1

| Branch | Finish-focused ruling |
| --- | --- |
| `fix/real-call-certification-regressions-v1` | Optional non-blocking test hardening |
| `cert/saas-billing-sandbox-provider-run-1` | Archive evidence only for V1 |
| `docs/recepvoz-engineering-operating-system` | Defer post-V1 |
| `feat/frontend-orphan-branch-rescue` | Defer post-V1 archive |
| `feat/recepvoz-commercial-mvp` | Defer post-V1 archive |
| `chore/commercial-release-candidate-v1` | Defer post-V1 archive |
| `pilot/first-business-readiness-v1` | Reference only; functionally superseded |

## Part 3 contract

Part 3 should **not** resume general branch archaeology.

It should:

1. optionally re-express only the two useful missing voice regression cases against current main;
2. correct stale finish/readiness documentation where old milestone prose contradicts current source;
3. create one final V1 Release Candidate from current main;
4. run complete backend, PostgreSQL/Flyway, integration, browser, security and tenant-isolation certification;
5. merge/certify exact main/Railway if green;
6. leave real voice, WhatsApp and Mercado Pago effects closed unless explicitly authorized.

## Finish ruling

The branch forest is no longer blocking Helvoca.

**The remaining distance to V1 is release certification plus real external/customer evidence, not missing historical code.**
