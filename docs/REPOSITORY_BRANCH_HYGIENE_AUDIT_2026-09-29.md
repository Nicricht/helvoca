# Repository Branch Hygiene Audit — 2026-09-29

## Scope

Repository: `Nicricht/helvoca`

This audit replaces branch-count guessing with Git/PR evidence. It records which branches are already represented in `main`, which still contain deliberate work, and which require manual review before retirement.

## Current baseline

After merging PR #618, current integration commit is:

- `main@5806be23ae54036738ee99d8c904070be7a517c6`
- PR #618: merged
- canonical frontend integration is now in `main`
- PR #617 was automatically recognized as merged because its head is an ancestor of #618

At audit time there are **604 branches outside `main`**:

| Category | Count | Ruling |
| --- | ---: | --- |
| Current branch head exactly matches a previously merged PR head | 504 | **RETIRE / DELETE** after durable merge/deploy evidence |
| Branch advanced after its last merged PR | 5 | **REVIEW** before deletion |
| Branch has an open PR | 8 | **KEEP / RESOLVE** according to the table below |
| Branch has only closed, unmerged PR history | 70 | **REVIEW / SUPERSEDED / RESCUE** |
| Branch has no PR history | 17 | **REVIEW**; this count includes the present audit branch |

The large branch count therefore does **not** mean 604 independent missing features. Most of the repository is branch-retention debt.

## Five merged branches that later advanced

Do not delete these merely because they once had a merged PR:

- `chore/commercial-release-candidate-v1` — previous merged PR #560
- `fix/chilean-greeting-and-cert-transcript` — previous merged PR #477
- `fix/complete-goodbye-and-voice-profiles` — previous merged PR #486
- `fix/real-call-certification-regressions-v1` — previous merged PR #509
- `security-hardening` — previous merged PR #55

Each needs a fresh comparison against current `main` before retirement.

## Open PR disposition

| PR | Branch | Decision |
| --- | --- | --- |
| #642 | `docs/frontend-reference-visual-spec` | **CONSUME** into this hygiene rescue. Five unique visual-product documents are copied here. Close #642 once this rescue is merged. |
| #616 | `docs/recepvoz-engineering-operating-system` | **SUPERSEDED** by the First-Pass Engineering system integrated through #617/#618 plus the lifecycle policy in #645. Do not merge the old contract because it encodes older governance wording. |
| #637 | `feat/frontend-appearance-themes` | **KEEP — DELIBERATELY DEFERRED**. Genuine post-RC product feature with schema/API/UI changes. Re-certify against current main before future integration. |
| #643 | `feat/frontend-orphan-branch-rescue` | **SUPERSEDE** with this repository-wide audit. Its substantive finding was correct: the frontend integration was the visibility gap. That gap is now closed by merged #618. |
| #615 | `feat/hardware-store-demo-tenant` | **KEEP — VALUABLE STACKED WORK**. Depends on #611 and contains the fictitious hardware-store demo tenant. Reconcile onto current main before integration. |
| #609 | `pilot/first-real-customer-v1` | **KEEP — ACTIVE COMMERCIAL OPERATIONS**. Contains the first-prospects tracker and a dated follow-up dependency. It is not product code to merge blindly. |
| #611 | `test/adversarial-hardware-store-v1` | **KEEP — VALUABLE CERTIFICATION WORK**. Contains simulator safety fixes, adversarial fixtures and regression coverage. Reconcile onto current main before integration. |
| #644 | `test/commercial-release-readiness-first-business-v1` | **KEEP — RECONCILE**. Software-readiness evidence is valuable, but the PR was stacked on a closed RC-hardening branch and must be refreshed against current main after #618. |

## Frontend branch ruling

The following historical frontend branches are **not missing product work anymore**:

- `feat/frontend-foundation-dark`
- `feat/frontend-dashboard-finish`
- `feat/frontend-onboarding-settings-finish`
- `feat/frontend-commerce-operations-finish`
- `feat/frontend-conversations-calls-finish`
- `feat/frontend-billing-account-finish`
- the certified final visual/integration work contained by PR #618

Their accepted behavior is now represented by merged PR #618 on `main`.

Historical landing/finalization/operational-redesign branches remain references only unless a future task demonstrates a concrete missing behavior.

## Rescued durable artifacts

This branch rescues the unique visual-product documentation from PR #642:

- `docs/frontend/VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`
- `docs/frontend/SCREEN_BLUEPRINTS.md`
- `docs/frontend/COMPONENT_CONTRACTS.md`
- `docs/frontend/VISUAL_ACCEPTANCE_MATRIX.md`
- `docs/frontend/VISUAL_RECONSTRUCTION_ROADMAP.md`

No old production frontend implementation is reintroduced by this rescue.

## Cleanup policy

A branch may be deleted when one of these is proven:

1. its current head is exactly the head of a merged PR and no post-merge commits exist;
2. its unique content was deliberately consumed into a newer canonical branch/PR;
3. it is explicitly superseded and contains no intentionally deferred future scope.

Do **not** delete:

- branches with post-merge commits until compared;
- #637, #611, #615, #609 or #644 while their deliberate future/operational scope remains unresolved;
- any closed-unmerged branch whose unique content has not been classified.

## Production lifecycle status

PR #618 was merged only after its exact head was current with `main`, mergeable, and the required `fast-gate` and `test` checks were successful.

The resulting exact `main` SHA is `5806be23ae54036738ee99d8c904070be7a517c6`.

At the time this audit file was first written:

- GitHub push CI for that exact SHA had started and was still in progress;
- Railway automatically queued deployment of that exact SHA.

Do not call the production lifecycle complete until both reach terminal success and runtime health is verified.

## Next cleanup sequence

1. Verify GitHub CI and Railway deployment for `5806be23ae54036738ee99d8c904070be7a517c6`.
2. Merge this documentation-only rescue once its exact-head gates are green.
3. Close #642 and #643 as consumed/superseded.
4. Close #616 as superseded by current First-Pass Engineering governance.
5. Retire the 504 exact-merged-head branches.
6. Review the 5 post-merge-advanced branches.
7. Reconcile the remaining deliberate open work (#637, #611/#615, #609, #644) separately rather than bulk-merging stale history.
