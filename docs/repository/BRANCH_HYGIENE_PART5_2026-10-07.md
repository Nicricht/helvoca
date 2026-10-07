# Repository branch hygiene Part 5 — REVIEW_REQUIRED analysis

**Repository:** `Nicricht/helvoca`  
**Baseline main:** `9aa1038caf321a7cf5ed44b7111372a64aeb537b`  
**Audit branch:** `chore/branch-hygiene-review-analysis-20261007`  
**FRAME CHANGE:** NO

## Scope

Analyze the remaining **92 REVIEW_REQUIRED** branches after Parts 1–4.

This Part 5 authorizes **analysis only**. It does not authorize deletion of any REVIEW_REQUIRED ref.

The analysis will recompute against the current exact `main`:

1. current branch SHA;
2. current compare status, ahead/behind counts and changed files;
3. pull-request history and whether the branch moved after a merged PR;
4. duplicate branch HEADs;
5. file-surface classification (production code, migrations/config, frontend, tests, docs, CI/ops);
6. conservative triage for future rescue, supersession review, or possible retirement.

Any future deletion requires a separate explicit, fresh safety decision.
