# Branch hygiene Part 5 — REVIEW_REQUIRED analysis

**Repository:** `Nicricht/helvoca`  
**Exact main:** `9aa1038caf321a7cf5ed44b7111372a64aeb537b`  
**Branches analyzed:** **92**  
**Deletion authorized:** **NO**

## Executive classification

| Triage | Count | Meaning |
| --- | ---: | --- |
| `DUPLICATE_HEAD_MANUAL` | **2** | Same HEAD as another review branch but runtime-bearing; dedupe only after choosing the canonical ref. |
| `EVIDENCE_ONLY_RETIREMENT_REVIEW` | **32** | Only docs/tests/CI/scripts evidence; lower-risk later retirement candidate after confirmation. |
| `MERGED_BRANCH_MOVED_MANUAL` | **5** | Prior merged history exists and the surviving branch moved afterward; inspect manually. |
| `RESCUE_OR_SUPERSESSION_REVIEW` | **53** | Contains runtime-bearing content; determine whether to rescue or prove it superseded. |

## PR-history split

- `CLOSED_UNMERGED_PR`: **77**
- `HAS_MERGED_PR`: **5**
- `NO_PR_HISTORY`: **10**

## Duplicate HEAD groups

- `8866fb0bdb629098ca6ef08f6379fccb7ecc248b`: `pilot/first-business-readiness-v1`, `pilot/first-business-readiness-v1-certify-2`

## Branch matrix

| Branch | History | Ahead | Files | Runtime | Triage |
| --- | --- | ---: | ---: | --- | --- |
| `pilot/first-business-readiness-v1` | CLOSED_UNMERGED_PR | 6 | 5 | yes | `DUPLICATE_HEAD_MANUAL` |
| `pilot/first-business-readiness-v1-certify-2` | CLOSED_UNMERGED_PR | 6 | 5 | yes | `DUPLICATE_HEAD_MANUAL` |
| `cert/saas-billing-sandbox-provider-run-1` | NO_PR_HISTORY | 11 | 7 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `chore/verify-agenda-navigation-production-25d089` | CLOSED_UNMERGED_PR | 2 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `chore/verify-agenda-production-smoke-51ce746` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `chore/verify-orders-navigation-production-5b2f951` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `design/frontend-finalization-v1` | CLOSED_UNMERGED_PR | 3 | 2 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `design/universal-workspaces-v1` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `diagnostic/e2e-main-20260917` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `docs/commercial-external-gates-v1` | CLOSED_UNMERGED_PR | 5 | 5 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `docs/frontend-reference-visual-spec` | CLOSED_UNMERGED_PR | 5 | 5 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `docs/recepvoz-engineering-operating-system` | CLOSED_UNMERGED_PR | 3 | 3 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `feat/frontend-orphan-branch-rescue` | CLOSED_UNMERGED_PR | 3 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `feat/react-customers-migration` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `feat/recepvoz-commercial-mvp` | NO_PR_HISTORY | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `fix/booking-confirmation-fixture-hours-20260918` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `fix/react-app-entry-cutover` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `fix/whatsapp-booking-intent-routing-red` | NO_PR_HISTORY | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `ops/cph-receptionist-simulation-20260926` | CLOSED_UNMERGED_PR | 2 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `ops/cph-simulation-rehearsal-20260926` | CLOSED_UNMERGED_PR | 2 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `pilot/first-real-customer-v1` | CLOSED_UNMERGED_PR | 3 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `qa/frontend-full-20260918` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `qa/frontend-full-20260918-2` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `tdd/operations-asset-cache-bust` | NO_PR_HISTORY | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/booking-confirmed-structured-log` | CLOSED_UNMERGED_PR | 2 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/commercial-release-readiness-first-business-v1` | CLOSED_UNMERGED_PR | 15 | 7 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/frontend-foundation-html-red` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/frontend-foundation-red-baseline` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/golden-journey-commercial-v1-hardening` | CLOSED_UNMERGED_PR | 6 | 2 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/release-candidate-first-business-hardening` | CLOSED_UNMERGED_PR | 3 | 2 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/twilio-whatsapp-outbound-request` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `test/whatsapp-twilio-outbound-request` | NO_PR_HISTORY | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `ux/home-operational-red` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `verify/frontend-redesign-full-20260917` | CLOSED_UNMERGED_PR | 1 | 1 | no | `EVIDENCE_ONLY_RETIREMENT_REVIEW` |
| `chore/commercial-release-candidate-v1` | HAS_MERGED_PR | 1 | 1 | no | `MERGED_BRANCH_MOVED_MANUAL` |
| `fix/chilean-greeting-and-cert-transcript` | HAS_MERGED_PR | 7 | 7 | yes | `MERGED_BRANCH_MOVED_MANUAL` |
| `fix/complete-goodbye-and-voice-profiles` | HAS_MERGED_PR | 20 | 13 | yes | `MERGED_BRANCH_MOVED_MANUAL` |
| `fix/real-call-certification-regressions-v1` | HAS_MERGED_PR | 2 | 2 | no | `MERGED_BRANCH_MOVED_MANUAL` |
| `security-hardening` | HAS_MERGED_PR | 9 | 6 | yes | `MERGED_BRANCH_MOVED_MANUAL` |
| `chore/commercial-release-candidate-v2` | CLOSED_UNMERGED_PR | 1 | 17 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `ci/voice-chilean-sensual-hangup-repeat-v2` | CLOSED_UNMERGED_PR | 9 | 7 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `diag/channel-provider-probes` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `diag/log-full-waba-id` | CLOSED_UNMERGED_PR | 1 | 1 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `diag/whatsapp-booking-proposal-probe` | NO_PR_HISTORY | 1 | 1 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/capability-aware-readiness-20260918` | CLOSED_UNMERGED_PR | 1 | 8 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/catalog-product-stock-identity-20260918` | CLOSED_UNMERGED_PR | 1 | 5 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/contextual-conversations-navigation` | CLOSED_UNMERGED_PR | 1 | 12 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/conversation-quality-engine-v1` | CLOSED_UNMERGED_PR | 9 | 7 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/frontend-appearance-themes` | CLOSED_UNMERGED_PR | 47 | 31 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/frontend-finalization-batch-1` | CLOSED_UNMERGED_PR | 19 | 8 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/frontend-operational-redesign` | CLOSED_UNMERGED_PR | 27 | 24 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/frontend-visual-refresh-home` | CLOSED_UNMERGED_PR | 18 | 10 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/home-pulse-visual-integration` | CLOSED_UNMERGED_PR | 3 | 13 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/inventory-v1` | CLOSED_UNMERGED_PR | 74 | 58 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/landing-hero-dark-v1` | CLOSED_UNMERGED_PR | 11 | 4 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/live-demo-inbound-timeline` | CLOSED_UNMERGED_PR | 28 | 21 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/live-demo-session-preparation` | NO_PR_HISTORY | 11 | 9 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/meta-embedded-signup-selected-phone-validation-20260920` | CLOSED_UNMERGED_PR | 3 | 3 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/phone-setup-v2` | CLOSED_UNMERGED_PR | 22 | 200 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/react-agenda-migration` | CLOSED_UNMERGED_PR | 2 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/react-conversations-migration` | CLOSED_UNMERGED_PR | 9 | 8 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/react-settings-full-cutover` | CLOSED_UNMERGED_PR | 94 | 52 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/self-service-twilio-provisioning` | CLOSED_UNMERGED_PR | 21 | 190 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/sprint5-agent-configuration` | CLOSED_UNMERGED_PR | 27 | 27 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/sulafat-production-voice-v1` | CLOSED_UNMERGED_PR | 10 | 9 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/three-minute-sales-demo` | CLOSED_UNMERGED_PR | 11 | 7 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `feat/v1-horizontal-operations` | CLOSED_UNMERGED_PR | 50 | 49 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix-booking-flow` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/channel-runtime-readiness` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/correct-whatsapp-booking-time` | CLOSED_UNMERGED_PR | 3 | 3 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/e2e-mutation-observer-20260917` | CLOSED_UNMERGED_PR | 8 | 5 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/gemini-certification-client-turns` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/home-canonical-entry` | CLOSED_UNMERGED_PR | 3 | 13 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/mercadopago-order-idempotency-uuid` | CLOSED_UNMERGED_PR | 6 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/meta-activation-require-disabled-state` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/meta-operator-connect-fail-closed` | CLOSED_UNMERGED_PR | 3 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/normalize-gemini-fallback-voice` | CLOSED_UNMERGED_PR | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/preserve-payment-journey-link` | NO_PR_HISTORY | 1 | 1 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/real-cert-cleanup-provider-guard-v1` | CLOSED_UNMERGED_PR | 3 | 3 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/twilio-sms-fallback-20260918` | CLOSED_UNMERGED_PR | 7 | 7 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/voice-chilean-sensual-hangup-repeat-v2` | CLOSED_UNMERGED_PR | 8 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `fix/whatsapp-sandbox-sender-20260918` | CLOSED_UNMERGED_PR | 4 | 4 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `integration/commercial-demo-onboarding-v1` | CLOSED_UNMERGED_PR | 5 | 5 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `integration/commercial-demo-onboarding-v2` | CLOSED_UNMERGED_PR | 5 | 5 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `perf/gemini-tool-schema-latency-v1` | NO_PR_HISTORY | 2 | 2 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `perf/voice-latency-v1` | NO_PR_HISTORY | 8 | 7 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `pilot/first-business-readiness-v1-certify` | CLOSED_UNMERGED_PR | 5 | 5 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `release/recepvoz-certified-stack-v1` | CLOSED_UNMERGED_PR | 11 | 28 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `release/recepvoz-v1-pilot-rc` | CLOSED_UNMERGED_PR | 36 | 34 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `test/commercial-demo-postgres-certification` | CLOSED_UNMERGED_PR | 7 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `test/commercial-demo-postgres-certification-v2` | CLOSED_UNMERGED_PR | 6 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |
| `test/golden-journey-release-contract-v2` | CLOSED_UNMERGED_PR | 12 | 6 | yes | `RESCUE_OR_SUPERSESSION_REVIEW` |

## Guardrail

This report is analytical only. No REVIEW_REQUIRED branch is authorized for deletion by Part 5.
Any retirement pass must revalidate exact SHA, current compare, open-PR status, protection, and selected canonical duplicate refs at execution time.
