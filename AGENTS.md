# Helvoca development guardrails

These rules are mandatory for implementation work in this repository.

## Goal

Optimize for one short correction loop: design broadly, test cheaply first, and run the expensive certification once.

## Mandatory engineering operating system

Before implementation work, read and follow:

- `docs/RECEPVOZ_ENGINEERING_OPERATING_SYSTEM.md`

For every code change, the implementing agent acts as **developer + QA** and assumes the additional professional roles required by the change: product/functional analysis, architecture/backend/data, UX/UI/accessibility, AI/voice/telephony, security/privacy/multi-tenant, QA/SDET/adversarial testing, and DevOps/SRE/release.

Testing is part of implementation, not a separate optional task. A future chat or coding agent must be able to enter this repository with no prior conversation context, read these repository instructions, and follow the same engineering and verification standard.

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
9. After merge, verify the exact commit deployed to Railway and confirm runtime readiness before calling the work complete.
10. Production calls, payments, messages and destructive operations require the project-specific safety rules and must never be triggered merely to satisfy a test.

## Speed rule

Do not run the complete suite after every small edit. Use targeted tests through Fast Gate while iterating. Run Full Gate once the functional block and its regression tests are complete.

## Truth rule

A successful tool request, proposal, queued action or accepted provider request is not equivalent to a completed business outcome. Tests and application state must prove the terminal outcome explicitly.
