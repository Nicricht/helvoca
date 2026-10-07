# Repository branch hygiene Part 4 — content-equivalent retirement

**Repository:** `Nicricht/helvoca`  
**Baseline main:** `86197d1166c162d0d4b3a8f3fe0a4a7cacfb3a15`  
**Audit branch:** `chore/branch-hygiene-equivalent-20261007`  
**FRAME CHANGE:** NO

## Fresh baseline

Before Part 4:
- total branches: **122**
- open PRs: **0**
- protected branches: **main only**
- retained categories: **29 SAFE_CONTENT_EQUIVALENT + 92 REVIEW_REQUIRED + main**

Creating this audit branch temporarily raises the live count to **123**.

## Fresh revalidation of the 29 branches

All 29 prior SAFE_CONTENT_EQUIVALENT branches were re-compared against the new exact `main`.

Result:
- **26** are strictly behind `main` with `ahead_by = 0`
- **3** are commit-diverged by one commit but have **zero changed files**
- **0** have unique file content
- **29 / 29** remain eligible for a second retirement pass

The complete SHA-pinned certificate is:

`docs/repository/branch-hygiene-safe-content-equivalent-2026-10-07-v2.json`

## Runtime deletion contract

A candidate may be deleted only if, immediately before deletion:

1. `main` still equals `86197d1166c162d0d4b3a8f3fe0a4a7cacfb3a15`;
2. the candidate branch still exists;
3. it is not protected;
4. it is not `main` or this audit branch;
5. it is not the head of an open PR;
6. its current SHA still equals the certified SHA;
7. a fresh GitHub compare against exact `main` still proves either:
   - `ahead_by == 0`, or
   - zero changed files;
8. a final branch read immediately before DELETE still returns the same SHA.

Any failed invariant means **skip**.

## Explicit exclusions

The 92 REVIEW_REQUIRED branches are not authorized for deletion by Part 4.

## Expected post-delete state

While this audit branch remains open:
- total branches: **94**
- 29 content-equivalent candidates remaining: **0**
- REVIEW_REQUIRED: **92 / 92**
- plus `main` and this audit branch

After this PR is merged and its source branch retired:
- final branches: **93**
- partition: `main + 92 REVIEW_REQUIRED`
