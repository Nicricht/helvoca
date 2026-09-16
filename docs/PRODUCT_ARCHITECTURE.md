# Helvoca Product Architecture

## Product focus

Helvoca is a universal multi-tenant conversational operations platform. It is not a separate application per industry. A tenant enables reusable capabilities and business rules while the same conversational core serves voice, WhatsApp and future channels.

The conceptual flow is:

```text
Customer
  -> Voice / WhatsApp
  -> AI conversation
  -> Conversation State
  -> Policy / Automation Engine
  -> Business Operation
  -> ORDER / BOOKING / QUOTE / LEAD / REQUEST / DELIVERY / PAYMENT
  -> external systems when configured
  -> immutable Event Log
```

Examples such as restaurants, clinics, workshops, stores or salons are tenant configurations, never branches such as `if restaurant` or `if clinic`.

## Architectural authority

Helvoca owns business truth. The LLM converses and proposes tool usage, but the backend decides:

- tenant identity and authorization
- catalog values and prices
- availability and scheduling rules
- operation state transitions
- confirmation requirements
- retry and idempotency rules
- commercial entitlements and capacity
- provider eligibility and configuration

Provider responses cannot redefine tenant isolation, plan limits, prices, operation rules or service eligibility.

## Shared domain services

Voice and WhatsApp use the same domain services and business-operation model. Channel adapters translate ingress and egress only. Business behavior belongs in shared application/domain services.

The default operating policy is:

1. automation first;
2. customer confirmation where policy requires it;
3. human handoff as the durable last resort.

Missing required commercial or operational configuration fails closed.

## Universal capabilities

Tenants compose capabilities instead of selecting a hardcoded industry application. Current operation families include:

```text
CATALOG
ORDER
BOOKING
QUOTE
LEAD
REQUEST
DELIVERY / PICKUP
PAYMENT
```

New capabilities should extend generic contracts and policy data rather than introduce industry conditionals.

## Provider boundaries

External providers remain replaceable adapters.

### Voice AI

Core contracts include provider-neutral voice abstractions and routing. OpenAI Live and Gemini Live integrations can be selected/configured without moving business rules into the provider adapter.

### Telephony

Durable call state lives in `CallLifecycleService`. Carrier adapters translate provider events into the shared lifecycle.

### Messaging, calendar and payments

WhatsApp, calendar providers and payment providers follow the same rule: they transport or reconcile external state, while Helvoca remains the authority for domain and commercial decisions.

A provider supported by the codebase is not automatically active for a tenant. Real use requires the concrete tenant integration to be configured and tested.

## Runtime shape

Helvoca remains a modular monolith while that shape keeps domain consistency and operational complexity lower than premature service decomposition.

```text
helvoca-api
  |- auth / tenant isolation
  |- business / configuration
  |- customer / identity
  |- catalog / knowledge
  |- conversation / channel adapters
  |- operations / policy / automation
  |- booking / calendar
  |- telephony / voice
  |- messaging
  |- billing / entitlements / metering
  |- audit / event log / observability
  `- jobs / outbox

PostgreSQL = durable source of truth
```

Do not split modules into microservices until production load, reliability boundaries or team ownership create a measurable reason.

## Durable platform milestones

The platform now includes the following architectural blocks:

- V29: append-only universal event log
- V30: automation-first policy
- V31: safe retry
- V32: strong handoff
- V33: omnichannel foundations
- V34: outbound operations
- V35: confirmation policy
- V36: exactly-once and concurrency controls
- V37: persistent jobs/outbox
- V38: provider-neutral calendar core
- V39: observability
- V40: PostgreSQL row-level security
- V41: usage and cost metering
- V42: PostgreSQL-backed commercial plans, generic entitlements and provider-neutral billing boundaries

A milestone being implemented in the core does not by itself certify every external provider for every tenant. Production certification always belongs to the exact deployed revision.

## V41 and V42 commercial truth

Commercial behavior has three separate sources of truth:

1. `business_subscription` records the tenant's contracted subscription/payment state;
2. V41 `usage_meter_event` is the append-only source of measured usage;
3. V42 `commercial_plan` and `commercial_plan_entitlement` define catalog metadata, quotas, overage policy and hard capacity.

Plan price, public code, display name, recommendation flags and entitlements are database data, not Java enum constants. Subscription plan codes are persisted as strings so future catalog changes do not require adding enum values.

`CommercialEntitlementService` evaluates subscription status and generic entitlements. `VOICE_SECONDS` usage is derived from the V41 ledger. `CONCURRENT_CALLS` is a hard capacity entitlement used by call admission. Missing subscription, plan or required hard entitlement fails closed.

Payment providers receive an immutable provider-neutral `PaymentPlan`. Mercado Pago can create/reconcile financial provider state, but it does not decide plan price, capacity, usage or service eligibility.

## Security and tenant isolation

Tenant-owned data remains protected by tenant context and PostgreSQL RLS where applicable. Global commercial reference tables are read-only to runtime/system application roles except through migration/administrative ownership paths.

Public APIs must not accept arbitrary caller-provided `business_id` values when tenant identity can be derived from authentication context.

## Release gate

No V42 production claim is valid until all of these refer to the same intended revision:

1. exact PR HEAD CI is green;
2. that exact HEAD is merged;
3. main CI is green on the merge SHA;
4. Railway deploys that merge SHA;
5. Flyway applies V42 successfully;
6. Spring starts successfully;
7. `/actuator/health` passes the Railway health gate.

Certification flags and real external-provider actions are not changed merely to satisfy this gate.

## Scaling decisions

Redis or additional infrastructure should be introduced only when a measured runtime need appears, for example shared short-lived state, distributed rate limiting or multi-replica coordination. PostgreSQL remains the durable source of business truth.

Avoid Kafka, Kubernetes, unnecessary independent microservices or a broad integration catalog until they solve an observed customer, reliability or scaling problem.
