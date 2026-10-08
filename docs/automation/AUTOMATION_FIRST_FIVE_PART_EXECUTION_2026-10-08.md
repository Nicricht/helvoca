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

## Part 3/5: request lifecycle and human attention (implemented in branch)

- Flyway `V95__request_lifecycle_evidence.sql` adds a tenant-scoped,
  runtime append-only `business_request_transition_event` ledger.
  It records true `BUSINESS_USER` transitions separately from the
  pre-existing operation-event trigger's channel-inferred actor.
- The ledger has tenant-composite foreign keys for request/operation,
  PostgreSQL RLS FORCE with runtime SELECT/INSERT grants, an automatic
  evidence-id deduplication constraint and actor/evidence validation.
- A database trigger rejects illegal state transitions even from a direct
  SQL update. Requests in `RESOLVED` or `CANCELLED` are terminal.
- `BusinessRequestService.setStatus` now pessimistically locks the
  tenant-owned request row, allows idempotent same-state requests without
  writes, checks transitions, updates the universal operation and appends
  a human-actor lifecycle record in the *same transaction*.
- `GET /api/v1/requests/{id}/history` exposes the tenant-scoped
  transition history. The caller must hold an existing business role.
- `HumanAttentionService` and
  `GET /api/v1/operations/attention` provide one authorized, read-only
  list of active handoffs and open/in-progress requests. An active handoff
  supersedes the matching request by operation ID. The response is capped
  at 100 items; it is **not** an all-time historical count.
- Resolving a handoff does not falsely imply resolving the customer's
  request. The open request then remains visible for follow-up.
- JUnit and PostgreSQL/Testcontainers tests cover locking, valid/invalid
  transitions, no-op idempotence, human-actor audit, deduplication and
  cross-tenant access, pending CI certification.

**Fail-closed boundary:** This part does not activate automated closure.
The current `REQUEST_CREATED` event is not evidence of successful
customer communication. Provider message delivery and actual voice
playback/acknowledgement must be correlated to the *specific* request
before wiring `RESOLVE`. Similarly, no extra model or outbound call
should be created only to confirm a request.

The existing human handoff retry/escalation remains in place. Explicit
customer-requested handoff still requires a verified handoff record.

**Database note:** Request lifecycle audit follows the owning request's
retention lifecycle: deleting that request under the existing data
retention process cascades the associated transition records. Runtime
cannot update or directly delete ledger events.

## Part 4/5: Home and Orders React (implemented in branch, pending CI)

- Home now queries `GET /api/v1/operations/attention` for authorized
  business administrators and operators only. React Query synchronizes
  authoritative cases every 60 seconds while visible, on focus/reconnect
  and after explicit mutations. No extra LLM or provider call is made.
- The premium Home attention panel shows actionable `REQUEST` and
  `HANDOFF` cases with their real status/priority/date and no
  fabricated AI completion metrics. Active handoffs replace their matching
  request in the backend read model, so there is one visible case.
- Human status actions use existing authenticated request/handoff
  endpoints. User confirms terminal transitions. A handoff closure
  does not imply the customer's request was resolved.
- A failed read renders **"No pudimos verificar los pendientes"**, never
  a false `Todo bajo control`. Unauthorized roles do not even request
  the restricted endpoint. Request actions honor `REQUESTS_MANAGE`
  when the server supplies permission claims.
- Questions without knowledge and recent call failures remain visible as
  `Señales adicionales` separate from the canonical human-case count,
  because they can overlap with existing handoffs.
- `OperationsSupportPanel` (full historical requests, audit filtering,
  export) is now secondary and mounts only when Home's user chooses
  `Consultar historial de solicitudes y auditoría`. Its title is
  `Historial y auditoría`, not a second frontline inbox.
- Orders no longer embeds general request tracking. The authorized
  `CUSTOMERS_EXPORT` functionality was preserved in a compact
  `CustomerExportTools` panel only in the Customers perspective.
  Orders, contextual quotes, conversation and event history are preserved.
- E2E regressions cover live role-gated inbox, load/error/empty,
  explicit human actions, cross-panel cache invalidation, no automatic
  external writes, customer exports, and responsive routes.
- The shared application frame, typography and existing premium visual
  assets remain unchanged. Reduced motion is respected.

**Important limit:** Part 4 does NOT enable AI-driven automatic resolution:
the Part 3 request ledger exists, but a provider-confirmed specific
`DELIVERED` WhatsApp reply or actual audio playback/acknowledgement
linked to a specific resolved request still needs evidence integration
and certification before backend automatic status updates can be
turned on. No new AI/provider calls are needed just for case bookkeeping.

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

Parts 1–4 are implemented *as code*; certify the latest PR commit before advancing. They are **not** a
customer-visible end-to-end automation until Part 5 and verified delivery-driven resolution are implemented and
certified. Keep the PR draft until the complete release is ready.
