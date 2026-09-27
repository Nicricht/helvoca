# Commercial Release Hardening Snapshot

Date: 2026-09-27
Repository: `Nicricht/helvoca`
Base branch: `main`
Base SHA: `67ca6bb78d1664dee139aeced17b779ebfcc6284`

## Scope

This document is release hardening only. It does not add product features, enable providers, deploy code, place calls, send WhatsApp messages, charge money, or mutate production inventory.

## Current CI contract

The repository already has two useful release gates in `.github/workflows/ci.yml`:

- **Fast Gate**: job `fast-gate`, running `scripts/ci/fast-gate.sh`.
- **Full Gate**: job `test`, which runs JavaScript validation, the full backend suite with JaCoCo, differential Java coverage on pull requests, and browser E2E.

The workflow runs both gates on pull requests and on manual `workflow_dispatch`. A normal push to `main` does **not** automatically run the full gate unless the commit message contains `[verify]`.

Latest merged change at this snapshot:

- PR #547, source HEAD `955738eba68fc85381817f923f18c9225305014d`
- merge commit on `main`: `67ca6bb78d1664dee139aeced17b779ebfcc6284`
- CI run `36347701447`: **SUCCESS**
- `fast-gate`: **SUCCESS**
- `meta-webhook-public-smoke`: **SUCCESS**
- full `test` job: **SUCCESS**
  - JavaScript validation: success
  - backend + JaCoCo: success
  - differential coverage: success
  - browser E2E: success

The combined status currently attached to the exact merge commit on `main` exposes the Railway deployment status, but not a full-verification status for that merge commit. Therefore an exact commercial tag should be cut only after the exact candidate commit has been certified by the release gate, for example with a dedicated PR head or `workflow_dispatch`.

## Open PR audit

### Active / still relevant

| PR | State versus current main | Release-hardening interpretation |
| --- | --- | --- |
| #548 `perf/latency-certification-readonly-v1` | 4 ahead / 0 behind | Current and isolated. Not obsolete. Keep separate from this hardening PR. |
| #541 `pilot/controlled-real-business-v1` | 31 ahead / 6 behind | Active first-real-business safety stack. Must be resynchronized and re-certified before any eventual merge decision. |
| #543 `pilot/first-business-readiness-v1` | stacked on #541; 15 unique commits and substantially behind current main when compared directly | Active stacked readiness work. Do not merge directly to `main` out of sequence. |

For a **live controlled pilot**, #541/#543 remain material work. For a demo or assisted non-live commercial flow, they are not required to be merged merely to prepare this repository release snapshot.

### Confirmed absorbed or superseded

| PR | Reason |
| --- | --- |
| #501 Owner commercial sales pipeline | 0 commits ahead of current `main`. |
| #498 Owner customer commercial timeline | 0 commits ahead of current `main`. |
| #495 Inventory admin workspace / alerts / restock | 0 commits ahead of current `main`. |
| #488 Omnichannel inventory integration | 0 commits ahead of current `main`. |
| #527 Older certified-stack release PR | Superseded by merged release PR #529. |
| #510 Real-cert cleanup provider guard | Superseded by merged PR #511. |
| #504 Conversation Quality Engine V1 | Included in merged certified stack PR #529. |
| #465 Inventory V1 | Included by the certified stack that reached `main` through PR #529. |

These PRs should not be used as new merge sources for the commercial release.

### Stale PRs requiring an explicit close-or-retain decision

These branches are hundreds of commits behind current `main` while still containing unique commits:

- #522 legacy Gemini fallback normalization
- #493 voice certification branch
- #492 voice behavior fix branch
- #464 Gemini certification client-turn fix
- #429 Mercado Pago order idempotency UUID work
- #392 full WABA identifier diagnostic logging
- #384 committed booking audit-log test

Several have newer merged successors in the same functional area. They should **not** be merged blindly. Before closing any of them, verify whether their remaining unique diff still represents an invariant missing from current `main`.

PR #392 should not be part of a commercial release merely for diagnostics: release logging should minimize identifiers and sensitive operational data.

## Main branch protection

What can be proven from the connected GitHub integration:

- repository rulesets endpoint returns no rulesets;
- classic branch-protection details for `main` cannot be read because the GitHub App integration receives HTTP 403 `Resource not accessible by integration`;
- therefore protection of `main` is **not verified** by this snapshot.

Minimum target protection for `main`:

1. require a pull request before merge;
2. require successful status checks at minimum for:
   - `fast-gate`
   - `test`
3. block direct pushes for normal development;
4. disallow force pushes;
5. keep merge authorization separate from CI execution.

Because the connected integration lacks the required administration access, this protection cannot be safely configured from this task.

## Release identity

Proposed commercial pilot tag:

`recepvoz-v1-pilot`

Do **not** create the tag yet.

Tag only the exact commit that satisfies all of the following:

- branch protection has been manually verified/configured;
- release candidate is based on the intended current `main`;
- Fast Gate is green;
- Full Gate is green on the exact candidate;
- no unresolved live-pilot blocker intended for that release remains;
- merge/tag action has explicit authorization.

If another pilot iteration is needed before a stable tag, use pre-release tags such as `recepvoz-v1-pilot.1`, `recepvoz-v1-pilot.2`, etc.

## Real blockers before declaring the commercial release hardened

1. **Main protection is unverified.** No ruleset exists and classic branch protection is inaccessible to the current GitHub App.
2. **Exact release commit certification must be explicit.** Full verification is authoritative on PRs/workflow dispatch, not on every ordinary push to `main`.
3. **Open PR hygiene is noisy.** Confirmed absorbed/superseded PRs remain open and obscure the true release surface.
4. **If the release means a live controlled first-business pilot**, #541 and stacked #543 must be synchronized with current `main`, re-certified, reviewed, and explicitly authorized before merge.
5. **No tag should be cut before the above release boundary is fixed.**

## Release decision

Current `main` has a strong CI contract and its latest merged change came from a green Fast Gate + Full Gate path. The repository is close to a clean commercial release boundary, but it should not yet be labeled as the final protected commercial pilot release until branch protection is verified and the intended live-pilot scope (#541/#543 or not) is resolved.

This hardening branch intentionally makes no runtime or product behavior changes.
