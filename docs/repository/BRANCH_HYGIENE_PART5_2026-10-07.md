# Repository branch hygiene Part 5 — REVIEW_REQUIRED analysis

**Repository:** `Nicricht/helvoca`  
**PR:** #754  
**Baseline main:** `9aa1038caf321a7cf5ed44b7111372a64aeb537b`  
**Audit branch:** `chore/branch-hygiene-review-analysis-20261007`  
**FRAME CHANGE:** NO  
**Deletion authorized:** **NO**

## Scope

Analyze the remaining **92 REVIEW_REQUIRED** branches after Parts 1–4.

Part 5 is analytical only. No REVIEW_REQUIRED ref is deleted by this work.

The fresh analysis recomputed, against exact current `main`:

1. current branch SHA;
2. compare status, ahead/behind counts and changed files;
3. pull-request history;
4. whether surviving branches moved after a merged PR;
5. duplicate branch HEADs;
6. file-surface classification;
7. conservative future triage.

Machine-readable evidence:

`docs/repository/branch-hygiene-review-analysis-2026-10-07.json`

Human-readable branch matrix:

`docs/repository/branch-hygiene-review-analysis-2026-10-07.md`

## Fresh repository baseline

At analysis start:

- canonical branches before Part 5 audit branch: **93**
- analysis-time branches including audit branch: **94**
- canonical partition: `main + 92 REVIEW_REQUIRED`
- protected branches: **main only**
- pre-audit open PRs: **0**
- analysis PR: **#754 only**
- REVIEW_REQUIRED missing: **0**

## PR-history split

Fresh GitHub PR history over the 92 branches:

- **77 CLOSED_UNMERGED_PR**
- **10 NO_PR_HISTORY**
- **5 HAS_MERGED_PR**

The five branches with merged PR history survived with a different current branch SHA than their relevant PR head and therefore remain manual-review cases:

- `chore/commercial-release-candidate-v1`
- `fix/chilean-greeting-and-cert-transcript`
- `fix/complete-goodbye-and-voice-profiles`
- `fix/real-call-certification-regressions-v1`
- `security-hardening`

## Conservative triage

Fresh current-main comparison produced:

| Triage | Count | Ruling |
| --- | ---: | --- |
| `EVIDENCE_ONLY_RETIREMENT_REVIEW` | **32** | no runtime-bearing files; candidate for a separate evidence/archive review |
| `RESCUE_OR_SUPERSESSION_REVIEW` | **53** | runtime-bearing content exists; decide rescue vs provable supersession |
| `MERGED_BRANCH_MOVED_MANUAL` | **5** | merged history plus later branch movement; manual inspection required |
| `DUPLICATE_HEAD_MANUAL` | **2** | same live HEAD, but runtime-bearing content; choose a canonical ref before any dedupe |
| **TOTAL** | **92** | |

Runtime-bearing branches: **58**  
Non-runtime-only branches: **34**

The difference between the 34 non-runtime-only branches and the 32 evidence-only triage branches is the duplicate-HEAD pair, which is kept manual because it represents runtime-bearing work.

## Duplicate HEAD

Exactly one duplicate live HEAD group exists:

`8866fb0bdb629098ca6ef08f6379fccb7ecc248b`

- `pilot/first-business-readiness-v1`
- `pilot/first-business-readiness-v1-certify-2`

Both point to the same commit, but the commit contains runtime-bearing content. Part 5 does **not** choose a canonical ref or delete either branch.

## File surfaces

The 92 branches collectively touch these surfaces. Counts are multi-label and therefore overlap:

- CI workflows: **15**
- database migrations: **11**
- docs: **35**
- browser/E2E tests: **29**
- Java tests: **53**
- legacy static frontend: **20**
- production Java: **50**
- React frontend: **5**
- runtime configuration: **9**
- scripts: **5**
- other/unclassified runtime-relevant files: **3**

## Functional families

Functional-family counts are intentionally multi-label because historical branches often span more than one product area:

- frontend / React / UX: **36**
- voice / AI / telephony: **26**
- WhatsApp / messaging / channels: **16**
- commercial / billing / pilot / release: **34**
- booking / agenda: **19**
- inventory / catalog: **14**
- security / governance / CI / ops: **36**
- other cross-cutting: **4**

This is why deleting the remaining set as one batch would be unsafe.

## Largest review surfaces

Highest current changed-file counts versus `main`:

1. `feat/phone-setup-v2` — **200 files**, 22 commits ahead
2. `feat/self-service-twilio-provisioning` — **190 files**, 21 ahead
3. `feat/inventory-v1` — **58 files**, 74 ahead
4. `feat/react-settings-full-cutover` — **52 files**, 94 ahead
5. `feat/v1-horizontal-operations` — **49 files**, 50 ahead
6. `release/recepvoz-v1-pilot-rc` — **34 files**, 36 ahead
7. `feat/frontend-appearance-themes` — **31 files**, 47 ahead
8. `release/recepvoz-certified-stack-v1` — **28 files**, 11 ahead
9. `feat/sprint5-agent-configuration` — **27 files**, 27 ahead
10. `feat/frontend-operational-redesign` — **24 files**, 27 ahead

These should be treated as archaeological feature branches, not cleanup noise.

## Recommended next sequence

Part 5 recommends splitting future work instead of attempting another broad deletion:

1. **Part 6A — evidence-only review:** inspect the 32 non-runtime branches for archival value, duplication, or supersession. No automatic delete merely because they are non-runtime.
2. **Part 6B — duplicate-head decision:** choose the canonical ref for the two identical pilot branches, then revalidate before retiring at most the redundant ref.
3. **Part 6C — merged-moved review:** manually inspect the five branches that moved after merged PR history.
4. **Part 7 — runtime archaeology:** review the 53 runtime-bearing branches by product family and decide rescue, cherry-pick/reimplementation, or documented supersession.

## Safety conclusion

**Part 5 finds no basis for another 92-branch bulk deletion.**

The remaining repository state is qualitatively different from Parts 2 and 4: these branches contain unique history or unique file content.

Any future retirement must be a separate fresh task with:
- exact SHA revalidation;
- current-main compare evidence;
- open-PR and protection checks;
- explicit canonical-ref choice for duplicate heads;
- proof that no desired runtime or evidence content is being lost.
