# Repository branch hygiene audit — 2026-10-07

**Status:** Audited / no refs deleted yet
**Repository:** `Nicricht/helvoca`
**Baseline main:** `0780f372a1ac8963f0e1c937596e4ea20bc4c051`
**FRAME CHANGE:** NO

## Purpose

Reduce historical branch debt without deleting work that has not been proven redundant.

This cleanup is deliberately conservative. Branch age, naming style, or apparent purpose is **not** enough to delete a ref.

## Fresh repository snapshot

A fresh GitHub audit after PR #751 found:

- total branches: **701**
- protected branches: **1**
- protected branch: `main`
- non-main branches: **700**
- closed pull requests scanned: **744**
- merged pull requests scanned: **622**
- open pull requests at the baseline: **0**

## High-confidence classification

### Class A — exact merged-head branches

**579 branches**

A branch belongs to Class A only when all of the following are true:

1. it is not `main`;
2. its current branch HEAD SHA exactly equals the recorded `head.sha` of a pull request;
3. that pull request is actually merged;
4. the branch has not moved since that merge.

This is the strongest automated retirement class.

Examples from the newest end of the set include:

- `chore/legacy-root-cleanup` -> PR #751
- `feat/react-public-entry-migration` -> PR #750
- `feat/react-pricing-migration` -> PR #749
- `feat/react-sales-landing` -> PR #748
- `feat/react-invite-migration` -> PR #747
- `feat/react-platform-console-migration` -> PR #746
- `refactor/retire-legacy-phone-numbers` -> PR #745
- `feat/react-business-import-migration` -> PR #744
- `feat/react-internal-operations-migration` -> PR #743
- `feat/react-simulator-migration` -> PR #742

Because each branch still points to the exact PR head that GitHub records for the merged PR, the branch ref is redundant with permanent pull-request and commit history.

### Class B — branch name was merged, but the branch moved afterward

**11 branches**

These are **not automatically deletable**.

A merged PR exists for the same branch name, but the current branch SHA differs from the SHA that was merged. This can mean the branch was reused or received additional commits.

Examples include:

- `chore/commercial-release-candidate-v1`
- `feat/booking-payment-attendance-revenue`
- `feat/home-owner-value-dashboard`
- `feat/sales-business-analytics`
- `feat/universal-business-import`
- `fix/frontend-desktop-nav-labels`
- `security-hardening`
- `test/business-import-real-world-hardening`

Class B requires an explicit compare/content audit before any retirement.

### Class C — no merged PR found under the same branch name

**110 branches**

These are also **not automatically deletable**.

This group includes historical diagnostics, verification refs, experiments, abandoned migrations and possibly unique work. The absence of a same-name merged PR is not proof that the branch is valuable, but it is also not proof that it is redundant.

Examples include:

- `feat/react-agenda-migration`
- `feat/react-conversations-migration`
- `feat/react-customers-migration`
- `feat/react-settings-full-cutover`
- `feat/frontend-orphan-branch-rescue`
- `feat/frontend-dashboard-finish`
- `feat/landing-hero-dark-v1`
- `cert/saas-billing-sandbox-provider-run-1`
- several diagnostic and production-verification branches

Class C requires separate evidence before retirement.

## Safety policy for deletion

Part 2 may delete **only Class A** automatically.

Before deleting each ref, the cleanup executor must re-check at execution time:

1. branch still exists;
2. branch is not protected;
3. branch is not `main`;
4. no open pull request currently uses the branch as its head;
5. current branch SHA still exactly matches a merged PR head SHA.

If any check fails, skip the branch.

No Class B or Class C ref may be deleted by the automated pass.

## Recovery model

Deleting a Class A branch removes only the branch ref.

Recovery remains possible because GitHub permanently records the merged pull request and its exact head SHA. A retired branch can therefore be recreated from the PR head SHA if needed.

The execution report must record:
- branch name;
- SHA;
- merged PR number;
- deletion result or skip reason.

## Execution design

The connected GitHub API available in this chat does not expose a direct branch-ref delete action.

Therefore Part 2 should use a narrowly scoped, one-time GitHub Actions cleanup executor with `contents: write` that:

1. recomputes Class A from GitHub at runtime;
2. performs all safety checks again;
3. emits a machine-readable audit artifact before deletion;
4. deletes only refs that still satisfy the Class A invariant;
5. never deletes the current workflow branch, `main`, protected refs, open-PR heads, Class B, or Class C;
6. reports exact deleted/skipped totals.

The executor itself should then be removed after the cleanup is certified.

## Expected first-pass effect

If repository state does not change between audit and execution:

- branches before: **701**
- high-confidence Class A candidates: **579**
- expected branches after Class A retirement: approximately **122**
  - `main`
  - 11 Class B refs
  - 110 Class C refs

The final number may be higher if runtime safety checks skip any candidate.

## Part 1 acceptance

Part 1 is complete when:

- fresh counts are recorded;
- the high-confidence criterion is explicit;
- Class B/C are protected from automatic deletion;
- the execution and recovery procedure is documented;
- no branch refs have been deleted yet.
