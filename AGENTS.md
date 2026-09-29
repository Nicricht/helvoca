# Helvoca development guardrails

These rules are mandatory for implementation work in this repository.

## Goal

Optimize for one short correction loop: design broadly, test cheaply first, and run the expensive certification once.

## Required workflow

1. Never develop directly on `main`. Refresh `main`, then create a dedicated branch.
2. Before coding, define acceptance criteria and failure cases for the complete functional block.
3. Add or update regression tests for every bug fixed. A bug is not closed until a test would fail if it returned.
4. Run the Fast Gate before requesting or waiting for the Full Gate:
   `bash scripts/ci/fast-gate.sh <base-sha>`
5. Review the change adversarially before Full Gate: nulls, retries, duplicate calls, stale state, partial success, cross-tenant data, concurrency, provider failures and repeated user input.
6. Pull requests must pass the Full Gate. The Full Gate runs all backend tests, JaCoCo differential coverage, JavaScript validation and browser E2E.
7. New or modified executable Java lines must maintain at least 80% differential line coverage and 70% differential branch coverage when branches are present.
8. Never merge a red or incomplete PR. Re-check that the branch is not behind current `main` immediately before merge.
9. A complete, exact-HEAD-green PR should be merged promptly instead of being left indefinitely in Draft. Re-check that its head is still current and mergeable immediately before merge.
10. After merge, require green CI on the exact `main` merge SHA. For production-bound work, normal Railway deployment is allowed once the release gates for that change are satisfied; verify Railway deploys that exact SHA and confirm runtime health before calling the work complete.
11. Delete or otherwise retire merged source branches once their merge and deployment evidence is durable. Keep a branch open only when it still contains unmerged work, a documented blocker, or deliberately deferred future scope.
12. Do not impose a blanket `NO MERGE` or `NO DEPLOY` rule on normal engineering work. Use an explicit hold only when the task specifically requires a review/freeze, when release evidence is incomplete, or when deployment would trigger an unsafe/unapproved external effect.
13. Production calls, payments, messages and other destructive or customer-visible external effects still require their project-specific safety/authorization rules and must never be triggered merely to satisfy a test.

## Speed rule

Do not run the complete suite after every small edit. Use targeted tests through Fast Gate while iterating. Run Full Gate once the functional block and its regression tests are complete.

## Truth rule

A successful tool request, proposal, queued action or accepted provider request is not equivalent to a completed business outcome. Tests and application state must prove the terminal outcome explicitly.


## Delivery and branch hygiene

The default lifecycle is:

`branch -> Draft PR -> RED/GREEN implementation -> Fast Gate -> Full Gate on exact HEAD -> merge -> main CI -> Railway exact-SHA deployment when production-bound -> runtime verification -> source-branch cleanup`.

Draft is a working state, not a permanent parking state.

Prefer integrating completed stacked work into the designated integration branch as soon as its exact HEAD is certified. Once the integration branch itself is certified and releaseable, merge it through the normal protected-branch workflow instead of accumulating another generation of completed feature branches.

A temporary hold is valid only when it has a concrete reason and next step. Record that reason in the PR checkpoint. “Do not merge/deploy” must never be copied forward mechanically from an older task.
