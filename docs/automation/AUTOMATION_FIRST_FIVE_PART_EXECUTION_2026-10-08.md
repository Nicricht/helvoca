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

## Part 2/5: trusted voice and WhatsApp event integration (not started)

- Examine `RealtimeToolService`, `UniversalWhatsAppToolService`, existing
  `SafeOperationRetryEngine`, `BusinessOperationEvent`, call and message
  delivery evidence.
- Build trusted, tenant-scoped evidence adapters from persisted tool outcomes.
- Call the decision engine only after the underlying operation and delivery
  events have been durably committed.
- Reuse the existing AI interaction. Do not add an LLM classification round-trip.
- Treat the absence of verified customer delivery evidence conservatively.

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

Part 1 is complete *as code* once its PR checks succeed. It is **not** a
customer-visible end-to-end automation until Parts 2-5 are implemented and
certified. Keep the PR draft until the complete release is ready.
