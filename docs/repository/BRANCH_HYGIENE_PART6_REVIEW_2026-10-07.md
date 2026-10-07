# Branch Hygiene Part 6 — low-risk review

**Repository:** `Nicricht/helvoca`  
**Fresh baseline main:** `41c66c8976bbce8fb6dcfd23d9ed35e5d10b331c`  
**Audit branch:** `chore/branch-hygiene-part6-review-20261007`  
**Reviewed refs:** **39**  
**Destructive actions:** **NONE**

## Scope

This pass revalidates and classifies the lower-risk subset from Part 5 without deleting any REVIEW_REQUIRED branch:

- 32 `EVIDENCE_ONLY_RETIREMENT_REVIEW`
- 2 `DUPLICATE_HEAD_MANUAL`
- 5 `MERGED_BRANCH_MOVED_MANUAL`

Fresh pre-write revalidation found:
- main unchanged at `41c66c8976bbce8fb6dcfd23d9ed35e5d10b331c`
- 93 total branches before creating this audit branch
- 0 open PRs
- all 39 target refs still present
- all 39 target SHAs unchanged from Part 5

## Final classification

| Classification | Count |
| --- | ---: |
| SAFE_RETIRE_PROVEN | 32 |
| ARCHIVE_FIRST | 3 |
| RESCUE_FIRST | 3 |
| KEEP | 1 |
| MANUAL_UNCERTAIN | 0 |
| **TOTAL** | **39** |

No branch is deleted by this PR. `SAFE_RETIRE_PROVEN` means only that this analysis has enough fresh evidence to permit a future destructive pass after the standard final SHA/protection/open-PR revalidation.

## A. Evidence-only review

| Branch | Classification | Evidence / ruling |
| --- | --- | --- |
| `cert/saas-billing-sandbox-provider-run-1` | **RESCUE_FIRST** | No PR history; 5 provider probe workflows plus MercadoPagoSaasSandboxInvoiceBridgeIT are absent from current main. Preserve useful sandbox/provider certification logic before retirement. |
| `chore/verify-agenda-navigation-production-25d089` | **SAFE_RETIRE_PROVEN** | PR #716 explicitly says temporary verification-only, no merge, and closes after evidence capture for already-deployed main. |
| `chore/verify-agenda-production-smoke-51ce746` | **SAFE_RETIRE_PROVEN** | PR #713 explicitly says temporary verification-only external HTTP probes against already-deployed exact main. |
| `chore/verify-orders-navigation-production-5b2f951` | **SAFE_RETIRE_PROVEN** | PR #723 explicitly says temporary verification-only, no merge, for already-deployed Operations navigation. |
| `design/frontend-finalization-v1` | **SAFE_RETIRE_PROVEN** | PR #613 marks this design document superseded by the dark Foundation, integration #618 and reference-driven spec #642. |
| `design/universal-workspaces-v1` | **SAFE_RETIRE_PROVEN** | PR #674 title is [OBSOLETE]; design-only branch, no production implementation. |
| `diagnostic/e2e-main-20260917` | **SAFE_RETIRE_PROVEN** | Historical reproduction note only (4 lines), with no runtime content; later exact-main CI and repository history supersede the diagnostic branch. |
| `docs/commercial-external-gates-v1` | **SAFE_RETIRE_PROVEN** | Current main retains all five corresponding operational docs: three exact blobs and two newer successors; stale external-gate wording is intentionally superseded. |
| `docs/frontend-reference-visual-spec` | **SAFE_RETIRE_PROVEN** | Current main retains all five canonical frontend reference documents; one is byte-identical and the other four are later revisions. |
| `docs/recepvoz-engineering-operating-system` | **RESCUE_FIRST** | PR #616 intended permanent engineering/QA governance. The 696-line operating-system document and its contract test are absent from main; inspect and selectively rescue before retirement. |
| `feat/frontend-orphan-branch-rescue` | **ARCHIVE_FIRST** | Unique 136-line orphan-frontend archaeology document can materially inform later runtime-branch review; archive before retiring the ref. |
| `feat/react-customers-migration` | **SAFE_RETIRE_PROVEN** | PR #707 explicitly records 'development cancelled'; current main docs state Customers are contextual views inside Agenda/Operations, not a standalone React destination. |
| `feat/recepvoz-commercial-mvp` | **ARCHIVE_FIRST** | Unique 198-line early commercial-MVP document has no PR history and is absent from main; archive for product-history value before retirement. |
| `fix/booking-confirmation-fixture-hours-20260918` | **SAFE_RETIRE_PROVEN** | PR #161 changed tests only to stabilize time fixtures. Current main has a substantially evolved BookingConfirmationWorkflowIntegrationTest using a current futureBusinessTime fixture and exact-main CI is green. |
| `fix/react-app-entry-cutover` | **SAFE_RETIRE_PROVEN** | RED-only PR #719 was superseded by merged PR #750, which moved public authentication to React and routes business login/returning users into /app. |
| `fix/whatsapp-booking-intent-routing-red` | **SAFE_RETIRE_PROVEN** | Its UniversalWhatsAppBookingIntentRoutingTest blob is byte-identical to current main. |
| `ops/cph-receptionist-simulation-20260926` | **SAFE_RETIRE_PROVEN** | PR #459 states this was a one-off simulated receptionist workflow, NO MERGE, with no real telephony/WhatsApp/payment effects. |
| `ops/cph-simulation-rehearsal-20260926` | **SAFE_RETIRE_PROVEN** | PR #457 states this was a one-off simulated onboarding rehearsal, NO MERGE, with no real channels or pilot activation. |
| `pilot/first-real-customer-v1` | **SAFE_RETIRE_PROVEN** | Current main retains the same prospect-tracker schema and row count with newer row states; PR #609 preserves the historical outreach evidence. |
| `qa/frontend-full-20260918` | **SAFE_RETIRE_PROVEN** | PR #137 explicitly says temporary QA-only PR; branch contains only a one-line QA evidence file. |
| `qa/frontend-full-20260918-2` | **SAFE_RETIRE_PROVEN** | PR #138 explicitly says temporary QA-only round 2; branch contains only a one-line QA evidence file. |
| `tdd/operations-asset-cache-bust` | **SAFE_RETIRE_PROVEN** | The only change asserts cache-busting for legacy operations-business.css on operations.html. Current main routes legacy operations into the React internal Operations surface and retired legacy root assets. |
| `test/booking-confirmed-structured-log` | **SAFE_RETIRE_PROVEN** | Current main contains post-commit BOOKING_CONFIRMED logging and BookingConfirmationPostCommitLogIntegrationTest; current hardening docs explicitly record PR #384 as already implemented. |
| `test/commercial-release-readiness-first-business-v1` | **SAFE_RETIRE_PROVEN** | Current main contains 6/7 branch files or current successors. The only missing rc-first-business-safe-ci.yml was an isolated branch-only workflow; PR #644 explicitly says no product features and NO MERGE. |
| `test/frontend-foundation-html-red` | **SAFE_RETIRE_PROVEN** | PR #630 explicitly marks this completed RED/CI experiment as historical and points to canonical implementation PR #625. |
| `test/frontend-foundation-red-baseline` | **SAFE_RETIRE_PROVEN** | PR #628 explicitly marks this completed RED baseline as historical and points to canonical implementation PR #625. |
| `test/golden-journey-commercial-v1-hardening` | **SAFE_RETIRE_PROVEN** | Current main contains the same Golden Journey test class with stronger successor scenarios, including slot-occupied-after-proposal and simultaneous confirmation retries. |
| `test/release-candidate-first-business-hardening` | **SAFE_RETIRE_PROVEN** | PR #641 explicitly says the production-safe scenarios were consumed by canonical RC commit 4af59d4; the isolated workflow was intentionally not copied. |
| `test/twilio-whatsapp-outbound-request` | **SAFE_RETIRE_PROVEN** | Current main's TwilioWhatsAppMessagingProviderTest contains a renamed/expanded exact-request success-path test plus the same negative sender tests and additional certification coverage. |
| `test/whatsapp-twilio-outbound-request` | **SAFE_RETIRE_PROVEN** | Current main's TwilioWhatsAppMessagingProviderTest supersedes this earlier request-shape test with the current exact-request test and a larger suite. |
| `ux/home-operational-red` | **SAFE_RETIRE_PROVEN** | PR #129 explicitly says TDD RED branch only, implementation landed directly on main, and the temporary draft should close unmerged. |
| `verify/frontend-redesign-full-20260917` | **SAFE_RETIRE_PROVEN** | PR #136 explicitly says temporary verification PR, full Java + Playwright only, DO NOT MERGE. |

### Evidence-only summary

- **SAFE_RETIRE_PROVEN:** 28
- **ARCHIVE_FIRST:** 2
- **RESCUE_FIRST:** 2
- **KEEP:** 0
- **MANUAL_UNCERTAIN:** 0

## B. Duplicate HEAD decision

Shared HEAD:

`8866fb0bdb629098ca6ef08f6379fccb7ecc248b`

- **Canonical ref:** `pilot/first-business-readiness-v1`
- **Redundant ref candidate:** `pilot/first-business-readiness-v1-certify-2`

Why:

- PR #543 is the functional readiness/preflight branch and documents the product intent.
- PR #545 calls itself **certification-only**, **DRAFT / NO MERGE**, and certifies the corrected exact stack HEAD.
- Both refs point to the exact same commit.
- Runtime-bearing content is therefore preserved by keeping the canonical functional ref.

Ruling: keep the first ref; the `-certify-2` ref is **SAFE_RETIRE_PROVEN for a future deletion pass**, subject to a fresh lease/SHA check.

## C. Merged branches that moved later

| Branch | Classification | Post-merge ruling |
| --- | --- | --- |
| `chore/commercial-release-candidate-v1` | **ARCHIVE_FIRST** | PR #560 merged at head 4949bb5. One later docs-only commit records final integrated RC PASS evidence; current main has a newer RC document but not every historical line. Archive the post-merge certification note first. |
| `fix/chilean-greeting-and-cert-transcript` | **SAFE_RETIRE_PROVEN** | PR #477 merged at c7ac1e1. The sole later commit b2307ed changes CallCertificationAuditRunner, whose branch blob is byte-identical to current main. |
| `fix/complete-goodbye-and-voice-profiles` | **SAFE_RETIRE_PROVEN** | PR #486 merged at e6b8ee2. Two later commits enforce voice consistency/no repeated confirmation; current main carries successor prompt policy for same-gender identity, NO REPETIR and one compact confirmation, with current regression coverage. |
| `fix/real-call-certification-regressions-v1` | **RESCUE_FIRST** | PR #509 merged at d769978. Two later commits add fail-closed regression tests that are not present verbatim in current main; rescue or re-express that coverage before retiring. |
| `security-hardening` | **SAFE_RETIRE_PROVEN** | PR #55 merged at 94a7bf4. The two later commits add and then remove the same ServiceItemRepository pessimistic-lock helper; compare 94a7bf4...cdb94ef shows zero net file changes. |

## Do not delete yet

The following refs still require preservation work before any deletion:

### RESCUE_FIRST
- `cert/saas-billing-sandbox-provider-run-1`
- `docs/recepvoz-engineering-operating-system`
- `fix/real-call-certification-regressions-v1`

### ARCHIVE_FIRST
- `feat/frontend-orphan-branch-rescue`
- `feat/recepvoz-commercial-mvp`
- `chore/commercial-release-candidate-v1`

### KEEP
- `pilot/first-business-readiness-v1`

## Future destructive-pass safety contract

Before deleting any `SAFE_RETIRE_PROVEN` ref, re-check immediately:

1. branch still exists;
2. branch is not protected;
3. branch is not an open-PR head;
4. branch SHA is unchanged from the audited value;
5. the classification remains valid against current main;
6. any required archive/rescue has already been completed;
7. no concurrent work changed the repository assumptions.

No `update_ref` operation should be used to simulate deletion.
