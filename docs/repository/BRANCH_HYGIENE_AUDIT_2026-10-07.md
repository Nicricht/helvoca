# Repository branch hygiene audit — 2026-10-07

**Repository:** `Nicricht/helvoca`  
**Audit PR:** #752  
**Audit branch:** `chore/branch-hygiene-20261007`  
**Baseline main:** `0780f372a1ac8963f0e1c937596e4ea20bc4c051`  
**FRAME CHANGE:** NO

## Executive result

The repository-wide branch hygiene audit and first destructive cleanup pass are complete.

Initial live snapshot after opening the audit branch:

- total branches: **702**
- protected branches: **1** (`main`)
- merged PRs scanned: **622**
- closed PRs scanned: **744**
- open PRs: **1** (#752)

Conservative partition:

| Category | Count | Ruling |
| --- | ---: | --- |
| SAFE_EXACT_MERGED | **579** | deleted in Part 2 after runtime revalidation |
| SAFE_CONTENT_EQUIVALENT | **29** | retained |
| REVIEW_REQUIRED | **92** | retained |
| SPECIAL_KEEP | **2** | retained during execution |
| **TOTAL** | **702** | |

## Part 1 — audit

Part 1 recomputed branch and pull-request state directly from GitHub and deleted **zero** refs.

### SAFE_EXACT_MERGED — 579

A branch qualified only when its current HEAD exactly matched the recorded head SHA of a merged pull request, while excluding `main`, protected refs and open-PR heads.

Durable certificate:

`docs/repository/branch-hygiene-safe-exact-merged-2026-10-07.json`

### SAFE_CONTENT_EQUIVALENT — 29

These branches are not exact merged-head matches, but conservative compare evidence shows no unique branch-side file content:

- `ahead_by == 0`, or
- the compare is not marked too large and reports zero changed files.

They were intentionally excluded from the destructive pass.

Durable evidence:

`docs/repository/branch-hygiene-safe-content-equivalent-2026-10-07.json`

### REVIEW_REQUIRED — 92

These branches retain non-empty branch-side file deltas versus `main` and are not proven redundant. They were not deleted.

Durable evidence:

`docs/repository/branch-hygiene-review-required-2026-10-07.json`

The raw compare batches are retained as:

`docs/repository/branch-hygiene-nonexact-batch-*.json`

## Part 2 — exact merged-head cleanup

Execution commit:

`7ddb8cad674461bbb40660342903008b0a5cd5ef`

GitHub Actions run:

- workflow: **Branch hygiene Part 2 exact merged cleanup**
- run ID: **37570687075**
- conclusion: **SUCCESS**
- certified candidates: **579**
- deleted: **579**
- skipped: **0**
- failed or unverified: **0**
- exact candidates remaining: **0**
- branch count after execution: **123**

Before each DELETE ref call, the executor revalidated:

1. the branch still existed;
2. it was not protected;
3. it was not `main` or the audit branch;
4. it was not the head of an open PR;
5. its current SHA still matched the certified SHA;
6. the associated PR was still merged;
7. the merged PR head SHA still matched exactly;
8. a final branch/SHA read still matched immediately before deletion.

Execution artifact:

- name: `branch-hygiene-part2-evidence`
- artifact ID: `11461081814`
- SHA-256: `745e44884eebbf6dc8ca8f1d1c03591f20d2923d890410710b8a5f5c031632bd`

## Independent post-delete re-audit

A separate fresh GitHub enumeration after the workflow confirmed:

- current branches: **123**
- deleted SAFE_EXACT_MERGED still present: **0 / 579**
- SAFE_CONTENT_EQUIVALENT present: **29 / 29**
- REVIEW_REQUIRED present: **92 / 92**
- special refs present during audit finalization:
  - `main`
  - `chore/branch-hygiene-20261007`
- unexpected remaining branches: **0**
- protected branches: **main only**
- open PRs at finalization: **#752 only**

Post-cleanup partition:

`29 SAFE_CONTENT_EQUIVALENT + 92 REVIEW_REQUIRED + main + audit branch = 123`.

## Railway / production verification

The branch cleanup did not alter the production source branch and did not trigger a production deployment.

Post-cleanup Railway verification:

- environment: `production`
- no staged changes
- no pending work
- `helvoca-api`: online, 1/1 replicas running, 0 crashes, 0 warnings, 0 criticals, 0 recent failures
- PostgreSQL: online, 1/1 replicas running, 0 warnings, 0 criticals, 0 recent failures
- latest API deployment: `de4a3298-db03-4c0f-a160-8f0b98e00853`
- deployed commit: `0780f372a1ac8963f0e1c937596e4ea20bc4c051`
- deployed branch: `main`

## Recovery

Deleting these branches removed branch refs only. Their merged pull requests and commit SHAs remain recoverable from GitHub history.

## Finalization

The temporary destructive executor was deliberately limited to the audit branch and is removed before integration so it cannot become a standing repository capability.

This audit does **not** authorize deletion of the remaining 29 SAFE_CONTENT_EQUIVALENT branches or any of the 92 REVIEW_REQUIRED branches.

Any future cleanup of those branches requires a separate fresh decision and fresh runtime evidence.
