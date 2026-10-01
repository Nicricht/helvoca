# Universal Workspaces V1 Design

**Date:** 2026-10-01  
**Status:** Proposed design for implementation  
**Scope:** first implementation block after Team & Access V2  
**Risk:** HIGH  
**Frontend:** FRAME CHANGE: NO

## 1. Intent

RecepVoz must let each person in a business sign in with their own account and immediately see the work they are allowed to perform, without turning the product into a separate application per industry.

Success means:

- the owner keeps the current business dashboard;
- employees land in a role-appropriate operational workspace;
- visible modules are derived from permissions, not hardcoded industry checks;
- work shown in the workspace comes from real persisted business operations;
- backend authorization remains authoritative even when a user guesses an API URL;
- restaurants, salons, clinics, veterinary businesses, stores, workshops and future verticals share the same operational core.

The first implementation block is **Workspaces V1 + Universal Operations Queue**. It does not attempt to finish branches, individual assignment, push notifications or a visual workflow builder.

## 2. Existing architecture to preserve

The repository already contains the core that this feature needs:

- `BusinessOperation` is the universal operation envelope.
- Current operation types include `ORDER`, `QUOTE`, `LEAD`, `DELIVERY`, `REQUEST`, `BOOKING` and `PAYMENT`.
- Typed projections already exist for orders, bookings, quotes, leads, requests, deliveries and payments.
- `BusinessOperationEvent` is the immutable operational event log.
- `BusinessOperationItem` stores structured line/item snapshots.
- `ConversationOperationState` preserves conversational state independently of model text history.
- Team & Access V2 provides `BUSINESS_OWNER`, `BUSINESS_ADMIN`, `MANAGER`, `RECEPTION`, `STAFF`, `KITCHEN`, `DISPATCH`, `PROFESSIONAL`, `WAREHOUSE`, `SALES`, legacy `OPERATOR`, and granular `PERM_*` authorities.
- Existing typed mutation endpoints already enforce important business rules such as booking lifecycle and order preparation transitions.

Therefore V1 must **not** introduce a second generic entity such as `WorkItem`. `BusinessOperation` remains the source operation envelope, while typed projections remain authoritative for type-specific facts.

## 3. Architectural decision

The product flow becomes:

```text
Customer / human input
        |
        v
Conversation + business rules
        |
        v
BusinessOperation
        |
        +---- typed projection (order / booking / request / ...)
        |
        +---- immutable operation events
        |
        v
Workspace query adapter
        |
        v
Permission-filtered work queue
        |
        v
Employee action through existing typed endpoint
        |
        v
BusinessOperation / projection / event update
        |
        v
Owner dashboard + employee workspace
```

The new workspace layer is a **read/projection layer plus navigation/orchestration**, not a second transaction engine.

## 4. Workspace modules

The UI exposes capability-oriented modules. A module is shown when the authenticated user has at least the required permission. Industry names never decide visibility.

Initial modules:

| Module | Primary permissions | Typical use |
| --- | --- | --- |
| Attention | `CONVERSATIONS_READ`, `CONVERSATIONS_RESPOND`, `CUSTOMERS_READ` | reception, front desk, customer service |
| Agenda | `BOOKINGS_READ`, `BOOKINGS_MANAGE` | salons, clinics, veterinary, workshops, professional services |
| Orders | `ORDERS_READ`, `ORDERS_MANAGE` | stores, restaurants, commerce |
| Preparation | `ORDERS_PREPARE` | kitchen, packing, production/preparation teams |
| Dispatch | `DELIVERIES_READ`, `DELIVERIES_MANAGE` | pickup/delivery/dispatch teams |
| Inventory | `INVENTORY_READ`, `INVENTORY_MANAGE` | warehouse and stock teams |
| Sales | `LEADS_READ`, `LEADS_MANAGE`, `QUOTES_READ`, `QUOTES_MANAGE` | sales and quoting |
| Operations | `OPERATIONS_READ` | managers and broad operational users |

A user can have multiple modules. Multi-role users do not receive a separate hardcoded experience; their module set is the union of their permissions.

## 5. Landing behavior

### Owner and administrator

Users with business ownership/administration and broad analytics access continue to land on the existing owner dashboard at `/`.

The owner dashboard remains the place for:

- confirmed revenue;
- analytics;
- attention items;
- recent business activity;
- business-wide configuration;
- team;
- billing.

### Operational employee

Users whose primary access is operational land on `/workspace.html`.

The route resolves modules from `/api/v1/auth/me` permissions. The frontend must not infer access only from role labels.

Default module resolution:

1. if exactly one operational module is available, open it;
2. if multiple modules are available, open **My work**, an aggregate of active items the user is permitted to read;
3. if the user has no operational module, show a safe empty state and no protected data.

A user may still navigate to any module allowed by their permissions.

## 6. Universal operations queue

### API shape

Introduce a tenant-scoped workspace query API:

```text
GET /api/v1/workspace/profile
GET /api/v1/workspace/items?module=<module>&bucket=<bucket>&limit=<n>
GET /api/v1/workspace/items/{operationId}
```

No request accepts a caller-provided `businessId`. Tenant identity comes from the authenticated `TenantProvider`.

### Workspace profile

`WorkspaceProfileService` returns:

- current user identity;
- human-readable role labels;
- effective permissions;
- allowed workspace modules;
- recommended/default module;
- whether the owner dashboard is appropriate.

This service is the one place that translates permission sets into workspace composition.

### Workspace item view

A queue item is a projection, not a new persistence entity.

Suggested shape:

```json
{
  "operationId": "uuid",
  "type": "ORDER",
  "bucket": "IN_PROGRESS",
  "status": "PREPARING",
  "title": "Pedido #1042",
  "summary": "Promo 40 piezas · sin cebollín",
  "customer": {
    "id": "uuid-or-null",
    "name": "Cliente"
  },
  "source": "WHATSAPP",
  "createdAt": "...",
  "updatedAt": "...",
  "dueAt": null,
  "total": 24990,
  "currency": "CLP",
  "allowedActions": [
    "MARK_READY"
  ],
  "deepLink": "/...?tab=orders&operationId=..."
}
```

The workspace never fabricates missing facts. Type-specific data is loaded from the corresponding projection.

### Normalized buckets

V1 uses a small presentation-only state vocabulary:

- `NEW`
- `IN_PROGRESS`
- `WAITING`
- `READY`
- `DONE`

These values are **derived for display**. They are not persisted and do not replace domain statuses.

Examples:

- confirmed order awaiting preparation -> `NEW`;
- order `PREPARING` -> `IN_PROGRESS`;
- order `READY` -> `READY`;
- completed/cancelled terminal operation -> `DONE`;
- open request -> `NEW` or `IN_PROGRESS` depending on projection state;
- future provider/customer dependency -> `WAITING`.

Each operation type has a small adapter that maps domain state to the normalized bucket.

## 7. Type adapters

Create focused query adapters rather than one giant switch-heavy service.

Initial adapters:

- `OrderWorkspaceAdapter`
- `BookingWorkspaceAdapter`
- `RequestWorkspaceAdapter`
- `QuoteWorkspaceAdapter`
- `LeadWorkspaceAdapter`
- `DeliveryWorkspaceAdapter`

PAYMENT is intentionally excluded from employee work queues in V1. Payment truth remains in the billing/payment domain until a safe operational payment console is explicitly designed.

Each adapter must answer:

1. can this module expose this operation type?
2. what typed projection belongs to the operation?
3. what title/summary/status/bucket should be shown?
4. which actions are allowed given both operation state and effective permissions?
5. what deep link or existing detail surface should open?

Adapters may not bypass existing service/domain rules.

## 8. Mutations

V1 does **not** add a universal `PATCH /operation/action` endpoint.

The workspace invokes existing typed endpoints:

- order preparation uses the already restricted preparation-status endpoint;
- booking changes use booking lifecycle endpoints;
- delivery changes use delivery endpoints;
- customer/conversation actions use their existing controllers;
- inventory operations remain in inventory APIs.

This prevents the workspace from becoming a parallel business-rule engine.

For operation types without a safe existing mutation, V1 shows detail/navigation only.

## 9. Security model

Security remains server-side and permission-based.

Requirements:

- every workspace endpoint requires authentication;
- module access is checked against `PERM_*` authorities server-side;
- item queries filter by the authenticated tenant before any projection is loaded;
- an operation from another tenant must resolve as inaccessible/not found, never as a partially redacted item;
- allowed actions are computed from effective permissions and domain state;
- frontend hiding is convenience only;
- unknown role/permission combinations fail closed;
- `BusinessOperationEventController` must migrate from legacy ADMIN/OPERATOR role gating to the appropriate operation permission as part of implementation.

No workspace endpoint may accept a user-controlled tenant identifier.

## 10. Frontend composition

`FRAME CHANGE: NO`.

Add a principal operational screen `/workspace.html` using the existing protected shell and frame primitives. Update the frame screen map/manifest in the same implementation PR without changing frame geometry or tokens.

Editable slots:

- `workspace-summary`
- `workspace-module-tabs`
- `workspace-queue`
- `workspace-detail`
- `workspace-actions`

The page contains:

1. **Header:** “Tu trabajo” plus business name and compact status counts.
2. **Module tabs:** only modules authorized by permissions.
3. **Queue:** active work grouped by normalized bucket.
4. **Detail drawer/panel:** typed details and operation event timeline.
5. **Primary action:** only an action the backend already permits.
6. **Empty/error states:** explicit and non-technical.

The UI must not mention restaurant, clinic, salon, veterinary, workshop, etc. unless that text comes from tenant data. Modules are capability names, not industry branches.

## 11. Cross-vertical behavior examples

### Sushi / pizzeria / store

```text
ORDER
  -> Orders
  -> Preparation when ORDERS_PREPARE
  -> Dispatch when delivery permissions apply
```

### Salon / clinic / veterinary / professional office

```text
BOOKING
  -> Agenda
REQUEST
  -> Attention / Operations
```

Clinical/veterinary workspaces remain administrative. No diagnostic or treatment behavior is introduced.

### Workshop

```text
REQUEST
  -> Attention / Operations
BOOKING
  -> Agenda
QUOTE
  -> Sales
```

### Real estate / sales office

```text
LEAD
  -> Sales
BOOKING
  -> Agenda
REQUEST
  -> Attention
```

The same backend contracts serve all examples.

## 12. Commercial demo separation

PR #671 and `/demo.html` remain a **commercial demonstration surface**.

Rules:

- demo data is illustrative/fictitious;
- the demo never becomes a source of production operations;
- accepting a demo does not create a tenant or employee account automatically;
- the real product begins when the prospect proceeds to a configured tenant/simulator/pilot;
- the workspace reads only real persisted tenant data.

The commercial demo and operational workspace may live in the same deployable artifact but are separate product surfaces and data paths.

## 13. Error and degraded-state behavior

- If `/auth/me` cannot be loaded, do not guess permissions; show authentication/session recovery.
- If a module is unauthorized, return 403 and do not render its data.
- If an operation references a missing/corrupt typed projection, omit sensitive partial data, record diagnostic context, and expose a safe “needs review” state to authorized managers where possible.
- A failure loading one selected module must not leak or substitute another module's data.
- Duplicate clicks on an existing typed idempotent action preserve current endpoint semantics.
- Network retries never convert queued/accepted work into a falsely completed outcome.

## 14. Testing and certification

### Backend unit tests

- permission set -> workspace modules;
- owner/admin dashboard recommendation;
- operational employee workspace recommendation;
- each type adapter bucket/status/action mapping;
- unknown permission combinations fail closed;
- payment excluded from V1 queue;
- missing typed projection safe behavior.

### Security/integration tests

- cross-tenant operation cannot be queried by ID;
- KITCHEN can read/prepare allowed orders but cannot cancel them;
- RECEPTION can access authorized booking/customer/conversation surfaces but not team/billing;
- WAREHOUSE does not receive booking/order-management powers;
- SALES sees leads/quotes without billing/team management;
- operation event timeline requires the correct permission;
- PostgreSQL RLS suite remains green.

### Browser E2E

- owner logs in and remains on owner dashboard;
- KITCHEN lands on workspace preparation queue;
- RECEPTION lands on authorized operational modules;
- multi-permission employee sees multiple tabs;
- unauthorized tabs are absent and direct API access returns 403;
- preparation action changes the persisted order and the queue reflects the new state;
- 1536, 1440, 1366, 1280, 768 and 390 widths have no body horizontal overflow.

### Release

Because the change touches authorization and operational data, treat implementation as HIGH risk:

```text
RED tests
-> implementation
-> targeted security tests
-> Fast Gate
-> adversarial cross-tenant/permission review
-> Full Gate exact HEAD
-> protected merge
-> exact main CI
-> Railway exact-SHA deploy
-> runtime health
```

## 15. Explicit non-goals for V1

Do not include:

- individual operation assignment;
- branch/sucursal hierarchy;
- custom role builder;
- WebSocket/SSE real-time notifications;
- push/mobile notifications;
- drag-and-drop workflow builder;
- new payment behavior;
- new provider integrations;
- autonomous clinical diagnosis/treatment;
- replacement of typed projection tables;
- microservice extraction;
- industry-specific conditionals.

These are future blocks after a real pilot demonstrates the need.

## 16. Follow-up phases

### Phase 2: Assignment

Add tenant-scoped operation assignment to a user or work pool only after V1 usage proves where ownership is needed. Preserve event history for claim/reassign/release.

### Phase 3: Realtime

Introduce event-driven client refresh/SSE only after polling/refresh behavior becomes an observed operational limitation.

### Phase 4: Branches

Add branch/sucursal scope after the first customer that actually needs multi-location ownership. Branch scope must compose with tenant RLS and permission checks.

### Phase 5: Custom roles

Expose custom permission bundles only after built-in roles are proven insufficient in real pilots.

## 17. Acceptance criteria for implementation

The implementation is complete only when all are true:

1. no new generic operation persistence model was introduced;
2. `BusinessOperation` remains the universal operation envelope;
3. owner/admin experience is preserved;
4. operational employees reach `/workspace.html` based on effective permissions;
5. module visibility is permission-driven, not industry-driven;
6. workspace items are backed by persisted tenant operations/projections;
7. server-side checks prevent unauthorized module/item access;
8. at least ORDER, BOOKING, REQUEST, QUOTE, LEAD and DELIVERY are represented through adapters;
9. KITCHEN can perform only the already permitted preparation transitions;
10. typed mutation endpoints remain the business-rule authority;
11. no real external call, WhatsApp message or payment is triggered by certification;
12. FRAME CHANGE remains NO and canonical viewport evidence passes;
13. Fast Gate and Full Gate are green on the exact final HEAD;
14. post-merge main CI and production deployment are verified before completion is claimed.

## 18. Product outcome

After V1, RecepVoz stops behaving as “the same dashboard for every employee.”

The owner sees the business.

The employee sees the work they are authorized to perform.

The underlying system remains one universal multi-tenant platform, and verticals remain configuration rather than separate products.
