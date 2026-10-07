# Repository branch hygiene audit — 2026-10-07

**Status:** Part 1 complete / no refs deleted
**Repository:** `Nicricht/helvoca`
**Baseline main:** `0780f372a1ac8963f0e1c937596e4ea20bc4c051`
**FRAME CHANGE:** NO

## Fresh current snapshot

The audit was recomputed from GitHub after PR #751 and after opening this audit PR.

- current total branches: **702**
- non-main branches: **701**
- protected branches: **1**
- protected/default branch: `main`
- closed pull requests scanned: **744**
- merged pull requests scanned: **622**
- current open pull requests: **1**
- open PR: **#752**, head `chore/branch-hygiene-20261007`
- branch refs deleted in Part 1: **0**

The earlier 701-branch number was the pre-audit-branch baseline. Creating `chore/branch-hygiene-20261007` raised the live total to 702.

## Final conservative classification

The 702 current branches partition exactly into:

| Category | Count | Automatic Part 2 deletion? |
| --- | ---: | --- |
| SAFE_EXACT_MERGED | **579** | **YES**, after runtime rechecks |
| SAFE_CONTENT_EQUIVALENT | **29** | **NO** in the first pass |
| REVIEW_REQUIRED | **92** | **NO** |
| SPECIAL_KEEP | **2** | **NO** |
| **TOTAL** | **702** | |

Partition check: `579 + 29 + 92 + 2 = 702`.

## SAFE_EXACT_MERGED — 579

Invariant:

1. branch is not `main`;
2. branch is not protected;
3. current branch HEAD SHA exactly equals a recorded `head.sha` from a merged pull request;
4. therefore the ref has not moved since that merged PR head.

The complete machine-readable candidate list is:

`docs/repository/branch-hygiene-safe-exact-merged-2026-10-07.json`

This is the only category authorized for the first automatic retirement pass in Part 2.

## SAFE_CONTENT_EQUIVALENT — 29

These branches are not exact merged-head matches, but a conservative GitHub compare against `main` proves no unique file content remains on the branch side:

- `ahead_by == 0`, or
- the compare is not marked too large and returns zero changed files.

They are intentionally **not** included in the first automatic delete pass. They can be considered separately after the exact class is retired.

Complete evidence:

`docs/repository/branch-hygiene-safe-content-equivalent-2026-10-07.json`

Branches:

- `chore/recepvoz-section-assets-20261003`
- `design/live-demo-center-v1-rebase-cabafd`
- `diag/whatsapp-booking-flow-state`
- `feat/booking-payment-attendance-revenue`
- `feat/frontend-billing-account-finish`
- `feat/frontend-commerce-operations-finish`
- `feat/frontend-conversations-calls-finish`
- `feat/frontend-dashboard-finish`
- `feat/frontend-foundation-dark`
- `feat/frontend-onboarding-settings-finish`
- `feat/home-owner-value-dashboard`
- `feat/inventory-admin-alerts-restock-v1`
- `feat/omnichannel-core`
- `feat/omnichannel-inventory-integration-v1`
- `feat/owner-commercial-timeline-v1`
- `feat/platform-assisted-onboarding`
- `feat/sales-business-analytics`
- `feat/universal-business-import`
- `fix/frontend-desktop-nav-labels`
- `fix/whatsapp-separate-twilio-credentials-20260919`
- `release/recepvoz-v1-pilot`
- `release/recepvoz-v1.0`
- `test/business-import-real-world-hardening`
- `test/frontend-foundation-red-ci`
- `test/rc-preflight-red-base`
- `test/rc-preflight-red-v1`
- `verify-main-post-rc-529`
- `verify-main-post-v6-534`
- `verify/main-recepvoz-v1-final`

## REVIEW_REQUIRED — 92

These branches still have a non-empty branch-side file delta in GitHub compare and therefore are **not proven redundant**.

They include moved/reused historical branches and old branches without a same-name merged PR. They must not be automatically deleted.

Complete compare evidence:

`docs/repository/branch-hygiene-review-required-2026-10-07.json`

## SPECIAL_KEEP — 2

- `main` — protected/default branch.
- `chore/branch-hygiene-20261007` — active head of open draft PR #752 and contains this audit.

## Runtime safety contract for Part 2

Before deleting any SAFE_EXACT_MERGED ref, recompute and require all of the following at execution time:

1. branch still exists;
2. branch is not protected;
3. branch is not `main`;
4. branch is not the head of any open PR;
5. current branch HEAD still exactly equals a merged PR head SHA.

Any failed invariant means **skip**, not force.

No SAFE_CONTENT_EQUIVALENT, REVIEW_REQUIRED or SPECIAL_KEEP branch may be deleted by the first automated pass.

## Recovery

Deleting an exact merged-head branch removes only the branch ref. The merged PR and exact commit SHA remain in GitHub history.

The Part 2 execution report must record branch name, SHA, merged PR number, deletion result, and any skip reason.

## Tool limitation

The connected GitHub toolset currently exposes `update_ref` but no direct safe `delete_branch` / `delete_ref` mutation.

`update_ref` must never be used to simulate deletion.

Part 2 must re-check the available tool catalog. If direct deletion is still unavailable, use only a narrowly scoped repository-side executor that performs the runtime safety contract above and emits a machine-readable report.

## Part 1 acceptance

Part 1 is complete because:

- `main` and Railway production were revalidated;
- all current branches were enumerated;
- all closed/merged PR history required for matching was scanned;
- exact branch HEAD vs merged-PR-head matching was recomputed;
- every non-exact historical branch was compared conservatively against `main`;
- current open PR heads and protected refs were separated;
- complete machine-readable lists are persisted;
- **zero branch refs were deleted**.
