# RecepVoz automation-first: five-part implementation

Date: 2026-10-08
Repository: `Nicricht/helvoca`
Starting baseline: `main@07ccd3c96e022ad51f5f39d9d02d3532fa603b84`
Branch: `feat/automation-first-receipts-20261008`

## Product contract

Keep AI calls and AI WhatsApp conversations. Do not make a second model
invocation merely to decide whether a backend operation completed. Reuse
existing server-authoritative operations, event history and human handoffs.

The existing customer's call/message and the original AI conversation still
consume their normal provider usage. This feature aims for **zero new model
invocations** for *follow-up classification*, not zero infrastructure cost.

Never infer completed physical work (order preparation, delivery, fulfilled
service), payment success, customer agreement, or complaint settlement from
a successful tool invocation. Never claim human involvement without a durable
handoff. Tenant security, existing permissions and provider safety switches
remain authoritative.

## Part 1/5: fail-closed decision engine (implemented in this branch)

- `RequestResolutionDecisionEngine`: deterministic Java decision function,
  with no model, provider, database or network operations.
- Return `RESOLVE`, `KEEP_OPEN` or `ESCALATE` plus a stable reason code.
- Read-only information and booking-request completion are eligible only
  with all the following: server-verified execution success, a confirmed
  channel receipt, immutable event reference, business and request operation
  references, and no remaining business action.
- Customer-requested human attention never disappears; escalation is only
  recommended when the effective policy permits it. Existing
  `HumanHandoffService` must persist/confirm a handoff before anyone says
  a human was notified.
- Nonterminal failures never close a request; known unresolvable failures
  may recommend escalation. Order, quote, lead, complaint, payment and
  unknown work never close from a generic tool-success flag.
- JUnit tests cover the positive cases and the missing-proof, unconfirmed
  delivery, physical-work, forbidden-kind, retry/fallback, and handoff cases.

**Scope limitation:** Part 1 is deliberately not connected to production
request status changes. The input `Evidence` is an internal contract, *not*
proof by itself. Callers in Parts 2-3 must validate event/operation ownership
and real channel receipt via backend/database, in a transaction, before
performing any close/escalate action. Never accept evidence booleans or UUIDs
directly from a browser or from untrusted LLM output.

No Flyway migration, outbound message, payment, provider write, modified
existing endpoint or frontend release occurs in Part 1.

## Part 2/5: trusted voice and WhatsApp creation evidence (implemented in branch)

- `CallTraceService.recordTool` now sends **successful** `create_request`
  results to `RequestCreationObservationDispatcher`, using backend-produced
  `requestId` and `operationId` and the trusted call/tenant identities.
  All other tool results, including failures, preserve original behavior.
- `UniversalWhatsAppToolService.createRequestWithConversationContext` sends
  the actual created `BusinessRequest` IDs to that same dispatcher.
  This does not change the tool response and does not send another message.
- The dispatcher observes **only after the enclosing transaction commits**.
  Rollback, no transaction, incomplete IDs and non-AI sources do not record
  a trusted creation. A post-commit read error cannot roll back an operation
  already committed.
- `RequestToolOutcomeObservationService` opens a fresh read-only transaction
  in the originating tenant and verifies `business_request`,
  `business_operation` and the immutable `REQUEST_CREATED` operation event.
  It verifies matching business, operation, source conversation, channel and
  original AI actor classification.
- Micrometer counter `helvoca.request_creation_observed` has only
  `channel` and `trusted` labels (no tenant IDs or customer data).
- **Important fail-closed boundary:** a `REQUEST_CREATED` event proves
  creation, not resolution. `Execution.UNKNOWN` and
  `CustomerReceipt.UNCONFIRMED` always cause `KEEP_OPEN`.
  No auto-resolution or human handoff is triggered in Part 2.
- JUnit tests cover voice and WhatsApp wiring, tenant/channel/event integrity,
  forged references, missing events, rollback/no-transaction, error isolation,
  and no changes to AI tool output.

**Still required in Part 3:** resolve the request's actual underlying outcome
and the confirmed recipient delivery/playback signal using trusted provider
evidence; introduce a safe, transactional lifecycle with evidence correlation,
not inferred from the model narrative. A WhatsApp `SENT` receipt is not
necessarily customer `DELIVERED`, and phone playback completion must not be
assumed from text generation or tool success.

**Costs/scope:** No new model invocation, provider message, call, payment,
or background poll is introduced by this stage. Each created request adds a
small, post-commit, read-only database observation.

## Part 3/5: PostgreSQL lifecycle and unified human attention (not started)

- Use `business_request`, `business_operation`, `business_operation_event`
  and `human_handoff` as the sources of truth. Avoid duplicate tickets.
- Require evidence for automatic `RESOLVED`; validate transitions, actor
  identity and tenant/operation/event linkage. Add a minimal versioned Flyway
  migration only if existing schema cannot express these guarantees.
- Keep human handoff audit and history durable. Deduplicate repeated events
  idempotently, and do not escalate on retryable failures.
- Provide one tenant-filtered read contract for unresolved human attention.

## Part 4/5: React surfaces (not started)

- Move the main human-exception inbox into `HomePage` with real action links.
- Keep `OrdersPage` focused on orders and contextual quotations.
- Retain authorized audit/history access without a second general-purpose
  `Seguimiento operativo` inbox in Operations.
- Do not change the global app shell or show fictional AI metrics.
- Refresh affected React Query data efficiently; respect permissions, loading
  and offline conditions.

## Part 5/5: system certification and controlled delivery (not started)

- JUnit unit and integration tests; PostgreSQL concurrency, tenant/RLS,
  idempotency, lifecycle and actor attribution verification.
- Playwright for desktop/mobile and role-specific Inbox/Orders interactions.
- Assert zero *extra* AI/provider calls due solely to follow-up classification.
- Certify exact-HEAD CI, review, squash merge only after checks pass,
  exact-main CI, Railway exact-SHA deployment and health.
- Do not activate real outgoing WhatsApp, voice test calls, external calendar
  or payment actions as a side effect.

## Important existing components to reuse

- `OperationPolicyService`: tenant- and operation-specific policy.
- `SafeOperationRetryEngine`: bounded retries and unresolvable escalation.
- `HumanHandoffService`: durable scoped handoff with deduplication.
- `UniversalOperationWorkflowService`: operation and request creation.
- `BusinessRequestService`: existing request lifecycle.
- `BusinessOperationEventService`: immutable event-log reads.
- `OperationsDashboardService`: current Home attention data.
- `PersistentJobWorker`: durable background jobs; disabled by default.

## Release state

Parts 1 and 2 are implemented *as code*; certify the latest PR commit before advancing. They are **not** a
customer-visible end-to-end automation until Parts 3-5 are implemented and
certified. Keep the PR draft until the complete release is ready.
