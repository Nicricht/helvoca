# Commercial Release Hardening Snapshot

Date: 2026-09-27
Repository: `Nicricht/helvoca`
Base branch: `main`
Base SHA: `67ca6bb78d1664dee139aeced17b779ebfcc6284`
Hardening branch: `chore/commercial-release-hardening`

## Scope

This branch is release hardening only. It adds no product feature and does not enable providers, deploy code, place calls, send WhatsApp messages, charge money, or mutate production inventory.

## CI baseline and hardening

Current `main` has `.github/workflows/ci.yml` with:

- **Fast Gate**: job `fast-gate`, invoking `scripts/ci/fast-gate.sh`.
- **Full Gate**: job `test`, running JavaScript syntax validation, the full Maven backend suite with JaCoCo, differential Java coverage on pull requests, and Playwright browser E2E.

The latest merged change at the base SHA came through PR #547. Its source HEAD `955738eba68fc85381817f923f18c9225305014d` was certified by RecepVoz CI run `36347701447`, which completed successfully.

The base workflow had one release-safety gap: an ordinary push to `main` ran `fast-gate` and `test` only when the commit message contained `[verify]`.

This hardening branch fixes only that CI condition:

- pull requests still run Fast Gate + Full Gate;
- manual `workflow_dispatch` still runs both;
- every push to `main` will also run both, without relying on a commit-message escape hatch.

The change is isolated in commit `cc4d4818edcede34efef698f2faa33fc57db4bfb`.

## Open PR audit

There are 18 open PRs at this snapshot.

### Current / still material

| PR | State versus current main | Interpretation |
| --- | --- | --- |
| #548 `perf/latency-certification-readonly-v1` | based exactly on current main, 4 unique commits | Current and isolated. Keep separate from release hardening. |
| #541 `pilot/controlled-real-business-v1` | 31 unique commits, 6 commits behind main | Material for a controlled live-business pilot. Rebase/update and re-certify before any merge decision. |
| #543 `pilot/first-business-readiness-v1` | stacked on #541 | Material only after its stack base is resolved; do not merge directly to main out of sequence. |
| #492 voice behavior/hangup fix | very stale relative to main, still has unique voice behavior | Potentially material voice work, but outside this hardening mission. Do not merge blindly. |
| #493 voice certification branch | stacked certification work around #492 | Keep with the voice workstream; not a direct release-hardening merge source. |

### Confirmed absorbed or superseded

| PR | Evidence |
| --- | --- |
| #501 owner commercial sales pipeline | PR head is an ancestor of current main; 0 unique commits versus main. |
| #498 owner customer commercial timeline | PR head is an ancestor of current main; 0 unique commits versus main. |
| #495 inventory admin workspace / alerts / restock | PR head is an ancestor of current main; 0 unique commits versus main. |
| #488 omnichannel inventory integration | PR head is an ancestor of current main; 0 unique commits versus main. |
| #527 older certified-stack release PR | superseded by merged PR #529, `release: RecepVoz certified stack v1`. |
| #510 real-cert cleanup provider guard | superseded by merged PR #511 / main commit `106d159757aef8514ebd21512b8cc3ba58f888bc`. |
| #504 Conversation Quality Engine V1 | carried into the certified stack that reached main through PR #529. |
| #465 Inventory V1 | later inventory stack reached main; the old full branch is no longer a clean merge source. |
| #522 legacy Gemini fallback normalization | current main already migrates blank/null global Despina fallback to Leda; newer main commits implement the invariant. |
| #464 Gemini certification client-turn fix | main contains commit `0829dd908ab28060095c2c29615c4688ebe9d1ca` implementing client-content certification turns. |
| #429 Mercado Pago UUID idempotency | main contains commit `c099878d8c3050c9a216059e0d233ece8b7ae5af` implementing provider-safe UUID idempotency. |
| #384 committed booking audit-log test | main contains post-commit `BOOKING_CONFIRMED` logging plus `BookingConfirmationPostCommitLogIntegrationTest`. |

These PRs should not be merged again merely because they remain open.

### Obsolete for the commercial release

PR #392 changes Meta diagnostics from a truncated WABA identifier to the complete WABA identifier. Current main intentionally still logs only `wabaIdEnding`. The full-identifier diagnostic is not required for a commercial release and should not be introduced by this hardening work.

## Main branch protection

The current branch metadata for `main` reports:

- `protected: false`;
- protection `enabled: false`;
- required status-check enforcement `off`;
- no required status-check contexts;
- repository rulesets: none.

Therefore `main` is currently **not protected**.

The classic branch-protection detail endpoint additionally returns HTTP 403 to the connected GitHub App (`Resource not accessible by integration`). The repository connection has repository admin permission, but the available integration does not expose an administration write action for branch protection/rulesets, so this task cannot safely apply the protection itself.

Minimum required protection for `main`:

1. require a pull request before merge;
2. require successful `fast-gate`;
3. require successful `test` (the Full Gate);
4. prevent normal direct pushes to `main`;
5. disallow force pushes.

No force push, merge, deploy, provider activation, call, WhatsApp delivery, charge, or production-inventory mutation was performed by this hardening task.

## Release identity

Proposed first commercial pilot tag:

`recepvoz-v1-pilot`

If an immutable pre-release sequence is desired before the final pilot tag, use:

- `recepvoz-v1-pilot.1`
- `recepvoz-v1-pilot.2`
- etc.

Do not create the tag from this task. The final tag must point at the exact approved release commit after required protection is configured and the release candidate has green Fast Gate + Full Gate.

## Real blockers before declaring the repository commercially hardened

1. **Main is unprotected.** Direct pushes and force-push protection are not enforced by GitHub branch protection/rulesets.
2. **The hardening PR must finish green.** The release-hardening branch changes CI semantics and must itself pass Fast Gate + Full Gate.
3. **Open PR hygiene is noisy.** Absorbed/superseded PRs remain open and can be mistaken for pending release requirements.
4. **Live-pilot scope remains separate.** If “commercial release” means enabling a real controlled business pilot, #541 and stacked #543 still require synchronization, review, certification, and explicit merge authorization.
5. **Voice work remains separate.** #492/#493 contain unique stale voice changes and must be resolved by the voice workstream rather than folded into repository hardening.

## Release boundary

The release-hardening branch is intentionally small:

- release snapshot/documentation;
- CI enforcement so every eventual push to `main` runs Fast Gate + Full Gate.

It does not merge any feature PR and does not change product runtime behavior.
