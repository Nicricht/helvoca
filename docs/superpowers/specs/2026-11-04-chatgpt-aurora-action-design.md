# ChatGPT-triggered Aurora call integration

**Date:** 2026-11-04  
**Status:** Approved implementation direction; code not yet implemented.  
**Repository:** `Nicricht/helvoca`, existing Aurora feature PR #853.  
**Target environment:** Railway `demo-isolated` only.

## Intent

Let the owner request and inspect an Aurora voice test call from a dedicated ChatGPT conversation without opening Work or operating the RecepVoz browser each time. The stable part is a narrow HTTPS API in Helvoca. ChatGPT connects to it through a one-time configured GPT Action; the owner still confirms every outbound call.

The authentic Aurora call already completed on 2026-11-04 (Twilio 6780 → consented destination ending 9611, Gemini Live, 165 seconds). This work adds a reusable operator interface; it does not repeat that call.

## Approaches

1. **GPT Action backed by a scoped REST API (selected).** Works with the owner's current Plus account through a custom GPT and supports Bearer API-key authentication plus an OpenAPI schema. It requires a one-time setup of a dedicated GPT and key, not Work on each use. OpenAI currently documents GPT Actions, but its Help Center also announces a planned custom-GPT retirement/migration date of 2026-12-11. Therefore the REST API and security model are the durable deliverable; the GPT Action schema is a replaceable adapter.
2. **Custom MCP app.** Better long-term native tool integration, but current ChatGPT documentation restricts full custom MCP write actions to Business and Enterprise/Edu workspaces. The owner currently uses Plus, so this is not the available V1 route.
3. **Work / Computer Use.** Can operate the authenticated UI but repeats the manual handoff and does not create a persistent API integration. Rejected for the stated goal.

References:
- https://help.openai.com/en/articles/9442513-configuring-actions-in-gpts
- https://developers.openai.com/api/docs/actions/getting-started
- https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt

## Goals

- A PLATFORM_ADMIN can issue, inspect and revoke one active Aurora ChatGPT API key from the existing Platform console.
- The raw 256-bit key is displayed once only. PostgreSQL stores only its SHA-256 digest and a short display prefix.
- The key is permanently scoped to the already-pinned Aurora business. It cannot select a tenant, caller ID, destination number, provider or model.
- The only call target is the existing server-side `TWILIO_TEST_TO` value, which must pass the existing `TWILIO_CERTIFICATION_ALLOWED_TO` and forbidden-target checks. No destination is accepted in an API request.
- ChatGPT can read scoped readiness, submit one unique call command, and read the command's status.
- The command is admitted only when the pinned carrier/tenant route and scoped Gemini configuration are ready.
- A dedicated background worker claims only commands created by this integration. The existing generic certification worker explicitly excludes those commands.
- The action contract marks call creation as consequential; the GPT's instructions require a fresh, explicit user confirmation before every POST.
- Status reads return CallSid and command state. When Twilio has populated them, a read-only Twilio GET supplies duration and per-call Twilio price. Gemini's exact per-call charge is not exposed by the existing provider API; report it as unavailable rather than inventing a value.
- The OpenAPI schema and GPT instructions are served from the isolated app and documented in the existing call runbook.

## Non-goals and anti-goals

- No call is initiated by this implementation, migration, key creation, app startup or deployment.
- No automatic redial, retry, scheduled call, or startup caller.
- No arbitrary phone number, business, tenant, Twilio account, Gemini model or voice can be supplied by ChatGPT.
- No booking, payment, WhatsApp delivery, phone purchase, production change, merge or `main` change.
- No application call-duration cutoff is reintroduced. The existing caller/model/operator/carrier termination semantics remain.
- No lifetime attempt quota or new cumulative spend cap is introduced. Unique run IDs, idempotency, explicit confirmation, the existing allowlist and revocable credentials remain.
- No raw API key, password, bearer token, full phone number or callback token is written to logs or GitHub.

## Security and execution model

1. The admin console creates a high-entropy `rvz_aurora_...` token, stores only its SHA-256 digest in a SYSTEM-only PostgreSQL table protected by forced RLS, and displays the raw token once. Revocation is one-way; only one active key exists for the pinned Aurora business.
2. The GPT Action sends the key as `Authorization: Bearer`. Public integration endpoints remain database-DENIED by default and explicitly enter SYSTEM scope only for the narrowly scoped, key-authenticated operation.
3. The action service verifies the active key, immutable Aurora business pin, existing verified Twilio route/enrollment, and scoped Gemini readiness. It never accepts a destination or tenant from the request.
4. The POST enqueues through the existing tenant-bound command service with actor prefix `chatgpt-aurora:<key-id>`. Duplicate run IDs return conflict.
5. The dedicated worker is separately gated by `HELVOCA_CHATGPT_AURORA_RUNNER_ENABLED` (default `false`), claims only that actor prefix, rechecks the binding immediately before Twilio, performs no retry, and records the CallSid. The generic worker excludes this prefix so it cannot steal or execute these commands.
6. The status endpoint only returns commands with the pinned Aurora business ID and the ChatGPT actor prefix. Twilio detail lookup is GET-only and verifies both CallSid and AccountSid before returning masked phone suffixes.
7. The GPT must call readiness first, describe any blockers, ask the owner to confirm the call to the already-authorized destination, then submit one unique run ID and poll status. It must not infer that a queued command is a completed call.

## UI and one-time setup

The Platform console will show the active key prefix, creation/last-use metadata, a generate/revoke control, the OpenAPI schema URL and copyable GPT instructions. After the code is certified and deployed to `demo-isolated`, the owner will generate the key once and paste it into the Authentication section of a private custom GPT named **Helvoca Operator**. The OpenAPI schema can be imported by URL. This is a one-time ChatGPT account setup; future calls are requested in that GPT without Work.

## Release boundaries

- Implement on the existing DRAFT PR #853 branch; do not touch `main`.
- Keep the generic runner, startup runner, global Gemini flag and global external effects disabled.
- The dedicated ChatGPT worker remains disabled by default in code. Any isolated activation is a separate post-CI step, only after exact-HEAD certification and explicit operator review of the deployed flags.
- No provider call, booking, spend, migration against a live database, or Railway deployment is part of coding/QA.
- Required evidence: TDD RED→GREEN; key lifecycle/security tests; PostgreSQL 18 migration/RLS/uniqueness tests; command-claim isolation and worker failure tests; API/OpenAPI contract tests; frontend typecheck/build and relevant interaction evidence; exact-HEAD Fast/Full gates and PG16/PG18 migration compatibility.
