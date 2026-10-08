# Safe request-to-reply evidence: three-part release

Repository: `Nicricht/helvoca`. PR: #764 (draft). Branch: `feat/request-reply-correlation-20261008`.
Starting baseline: `main@51e77b1991892e6ec5b1ead78dda5f776ae7e00d`.

## Product rule

The actual user conversation with Gemini/OpenAI and existing Twilio/Meta provider messages remain in place. Never spend another model invocation simply to infer what a backend tool already returned. **No new calls, WhatsApp sends, payments or background polling are introduced** by this work.

Distinguish three *different* facts:

1. **Created**: V95 `REQUEST_CREATED` is a durable server-side operation event.
2. **Delivered**: Meta, through a verified signature and tenant-bound sender route, reports the status of the **exact** stored reply associated with the request. V97 receipts can establish DELIVERED/READ only.
3. **Resolved**: The actual customer need has been completed. Neither (1) nor (2), individually or together, establish this. Order preparation, bookings, refunds, payment, complaints and human assistance require separate authoritative outcome evidence.

Never auto-resolve a business request solely because a `create_request` tool succeeded or a WhatsApp reply was marked delivered/read. No closure is activated in Parts 1 or 2.

## Part 1 (implemented)

- V96 immutable request/reply correlation table scoped by tenant and conversation.
- Java captures request + operation IDs directly from successful backend `create_request` tool calls in the current WhatsApp turn.
- Persisted reply must be stored in the same transaction before correlation is inserted.
- Invalid, incomplete and mismatched references do not create evidence.
- Repeat tools and callback retries are idempotent by composite unique keys.
- No status changes or outbound messages.

## Part 2 (implemented)

- V97 append-only `business_request_reply_delivery_event` with tenant-composite FK, RLS FORCE, strict provider/status constraints and a database verification trigger.
- Evidence only originates from the existing `MetaWhatsAppDeliveryStatusService` invoked after the webhook validates Meta's signature and resolves the sender to a business.
- The existing message provider ID and `DELIVERED`/`READ` receipt are validated against the **specific V96 correlation** and persisted `messaging_message` row, then inserted in the same database transaction.
- No evidence for queued/sent/failed, invalid IDs, wrong business, missing request correlation, retries or stale provider IDs.
- No extra provider/model call, billing operation, scheduling job or status change.
- Twilio WhatsApp conversational replies are **not** retroactively promoted to verified V97 evidence; that channel needs equivalent persisted reply correlation and receipt verification in its own follow-up.
- The `RequestResolutionDecisionEngine` still refuses closure when the remaining business action is unknown or required; V97 never sets its execution result to VERIFIED_SUCCESS. The missing element is an authoritative completion event for the *actual intent*, not another model opinion.

## Part 3 (next)

- Full CI exact-HEAD Java/JaCoCo/Playwright/Postgres; fix real coverage defects without weakening thresholds.
- Demonstrate tenant isolation, duplicate callbacks, missing receipt, rollback, and the human inbox keeping unresolved work open.
- UI indication of evidence level may only use server-computed fields; no browser-supplied evidence flags.
- Merge with branch protection only when CI is green; observe exact-main CI, Railway deploy, Flyway V96/V97 and health.
- Create a **separately reviewed explicit completion source** before enabling any automatic RESOLVE action. A future eligible category must have a server-verifiable outcome, validated channel receipt, ownership, active policy, terminal-state lock, an immutable audit event and retries/idempotency.

## CI gate

The first Part 1 full test workflow run `37857563728` failed **differential branch coverage** (23/40 = 57.5%, required >=70%) while Fast Gate, Golden Journey, system E2E and functional tests passed. Additional focused branch tests in Part 2 address this. Do not claim the whole release green until the latest commit is checked.
