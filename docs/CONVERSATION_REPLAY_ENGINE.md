# Conversation Replay Engine V2

Conversation Replay Engine turns a persisted RecepVoz call into a deterministic, reviewable regression fixture.

## Fast workflow

1. As a BUSINESS_ADMIN, request:
   `GET /api/v1/quality/calls/{callId}/replay`
2. Save the returned JSON under:
   `src/test/resources/quality/replays/<descriptive-name>.json`
3. Review the JSON before committing it.
4. Open the PR normally. `ConversationReplayFixtureSuiteTest` discovers every JSON fixture automatically and verifies its quality signature.

No new Java test is required for each captured replay.

## What capture removes

Before export the backend replaces known customer name, phone and email values, the call caller/destination numbers, generic email addresses, phone-like values, Chilean RUT-like identifiers and URLs. Internal entity UUIDs are replaced with stable local references such as `ENTITY_1`.

The fixture contains a one-way source fingerprint instead of the original call ID.

Automatic redaction is a safety layer, not a guarantee that arbitrary free-form personal information can never remain. Every generated fixture must be reviewed before it is committed to the repository.

## Fixture contract

Each fixture records:

- schema version;
- anonymous source fingerprint;
- ordered USER/ASSISTANT turns;
- successful and failed action outcomes using anonymous entity references;
- the expected quality pass/fail state;
- the expected set of finding codes.

The runner converts anonymous entity references to deterministic local UUIDs so duplicate-effect detection remains reproducible without exposing production identifiers.

## Current regression library

V2 includes checked-in examples for:

- healthy single-confirmation booking;
- repeated booking question;
- duplicate successful payment;
- duplicate inventory consumption;
- continuing to speak after an explicit farewell.

The existing V1 rules also detect exact repeated assistant turns and warn about assistant responses that exceed the configured conversational length budget.

## Scope

This engine is read-only. It never replays mutations against production providers, never charges a payment, never creates a booking and never changes inventory. It re-evaluates captured conversation/action evidence deterministically in CI.
