# Repository branch hygiene Part 4 — content-equivalent retirement

**Repository:** `Nicricht/helvoca`  
**PR:** #753  
**Baseline main:** `86197d1166c162d0d4b3a8f3fe0a4a7cacfb3a15`  
**Audit branch:** `chore/branch-hygiene-equivalent-20261007`  
**FRAME CHANGE:** NO

## Baseline

Before Part 4:
- total branches: **122**
- open PRs: **0**
- protected branches: **main only**
- retained categories: **29 SAFE_CONTENT_EQUIVALENT + 92 REVIEW_REQUIRED + main**

Creating the audit branch temporarily raised the live count to **123**.

## Fresh revalidation

All 29 previously classified SAFE_CONTENT_EQUIVALENT branches were compared again against exact `main@86197d1166c162d0d4b3a8f3fe0a4a7cacfb3a15`.

Result:
- **26** had `ahead_by = 0`
- **3** were one-commit divergent but had **zero changed files**
- **0** had unique file content
- **29 / 29** remained safe retirement candidates

SHA-pinned certificate:

`docs/repository/branch-hygiene-safe-content-equivalent-2026-10-07-v2.json`

## Runtime safety contract

Before each DELETE ref call, the executor required:

1. exact `main` still matched the certified SHA;
2. the candidate still existed;
3. the candidate was not protected;
4. the candidate was not `main` or the audit branch;
5. the candidate was not the head of an open PR;
6. current branch SHA still matched the certificate;
7. a fresh compare still proved `ahead_by == 0` or zero changed files;
8. a final branch read immediately before deletion still matched the same SHA.

Any failed invariant meant **skip**.

The 92 REVIEW_REQUIRED branches were explicitly excluded.

## Part 4 execution

GitHub Actions:
- workflow: **Branch hygiene Part 4 content-equivalent cleanup**
- run ID: **37580110631**
- execution HEAD: `102dff17ebcdc1c4d6eaa177cd4bd8a2c97913f5`
- conclusion: **SUCCESS**

Results:
- certified candidates: **29**
- deleted: **29**
- skipped: **0**
- failed/unverified: **0**
- candidates remaining: **0**
- REVIEW_REQUIRED missing: **0**
- post-delete branch count: **94**

Execution evidence:
- artifact: `branch-hygiene-part4-evidence`
- artifact ID: `11464566084`
- artifact SHA-256: `635f56363a91ee1a55475fcea7b2c1ee95e1e9f737d276857697ad10c832fd5a`

## Independent post-delete re-audit

Fresh GitHub enumeration after the workflow independently confirmed:
- total branches: **94**
- protected branches: **main only**
- open PRs: **#753 only**
- Part 4 candidates remaining: **0 / 29**
- REVIEW_REQUIRED retained: **92 / 92**
- audit branch retained during finalization
- unexpected remaining branches: **0**

Partition while this PR remains open:

`main + 92 REVIEW_REQUIRED + audit branch = 94`.

## Railway / production safety

The destructive branch cleanup did not modify `main` and did not trigger a Railway deployment.

Post-cleanup production verification:
- `helvoca-api`: online, 1/1 replicas running
- PostgreSQL: online, 1/1 replicas running
- no warnings or criticals
- no recent failures
- no pending work
- latest API deployment remains `7a44c364-290c-4266-a482-a4b85ac10650`
- deployed commit remains `86197d1166c162d0d4b3a8f3fe0a4a7cacfb3a15`
- deployed branch remains `main`

## Finalization

The destructive workflow is a one-shot audit executor and is removed from the final PR diff before integration.

After #753 is merged and its source branch retired, the expected repository state is:
- **93 total branches**
- **main**
- **92 REVIEW_REQUIRED**
- no SAFE_EXACT_MERGED branches from Part 2
- no SAFE_CONTENT_EQUIVALENT branches from Part 4

No REVIEW_REQUIRED branch is authorized for deletion by this task.
