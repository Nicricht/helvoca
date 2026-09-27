# Inventory V1

Status: **Block 5 integration candidate on `feat/inventory-admin-alerts-restock-v1` / PR #495**, layered on the certified omnichannel + exact-variant inventory integration. The original full Inventory V1 remains isolated in PR #465.

Inventory V1 is intentionally isolated from `main` until explicitly approved for merge. The implementation is backend-authoritative, tenant-scoped and designed to support voice, WhatsApp, orders and payments without allowing the AI or browser to invent stock.

## Scope

Inventory V1 provides:

- product-level stock;
- optional product variants with independent SKU and stock;
- on-hand, reserved and available quantities;
- reorder thresholds;
- manual stock adjustments;
- immutable movement history;
- stock reservations, release, consumption and expiry;
- order/payment integration;
- low-stock, out-of-stock and restocked alerts;
- opt-in customer restock subscriptions;
- a provider-neutral pending restock notification queue;
- an inventory workspace for BUSINESS_ADMIN and read-only operational visibility for OPERATOR;
- authoritative stock lookup for AI tools.

It is not a warehouse-management suite or ERP.

## Stock model and invariants

For every tracked product or variant:

```text
available = on_hand - reserved
on_hand >= 0
reserved >= 0
reserved <= on_hand
reorder_threshold >= 0
```

Additional guarantees:

- stock belongs to exactly one business;
- inventory can only reference catalog products belonging to the same tenant;
- variant references must match the exact product and tenant;
- SKU uniqueness is tenant-scoped;
- one product has at most one base inventory row;
- reservations use positive quantities only;
- an ACTIVE reservation cannot exceed authoritative available stock;
- consumption decrements both `on_hand` and `reserved`;
- release/expiry decrement only `reserved`;
- all stock mutations write a movement record;
- exact variant identity is persisted through draft lines, final order lines and reservations;
- base-product stock and variant stock remain independent.

V72 adds composite database foreign keys so cross-tenant product/variant references are rejected by PostgreSQL even for privileged writers. PostgreSQL RLS remains enabled and forced on inventory-owned tenant tables.

## Concurrency and idempotency

Inventory reservation and settlement use pessimistic row locking with deterministic lock ordering.

Certified behaviors include:

- two simultaneous purchases cannot oversell physical stock;
- concurrent reservations cannot make `reserved > on_hand`;
- base and variant inventories keep independent identities;
- tenant context cannot mutate another tenant;
- payment webhook replay cannot consume inventory twice;
- semantic replay with a different webhook event id still cannot double-decrement because no ACTIVE reservation remains after settlement.

## Reservation lifecycle

1. **Configure**: BUSINESS_ADMIN enables authoritative tracking and defines SKU/threshold.
2. **Adjust**: BUSINESS_ADMIN changes physical on-hand stock.
3. **Reserve**: an operational order reserves exact base or variant stock.
4. **Release**: cancellation/failure returns reserved capacity without changing physical stock.
5. **Consume**: successful payment/order settlement removes physical stock.
6. **Expire**: abandoned ACTIVE holds are released by the expiry worker.
7. **Repair paid stale hold**: if a stale ACTIVE hold belongs to an already SUCCEEDED payment, expiry recovery consumes it instead of releasing it.

Pending or `REQUIRES_ACTION` payments protect their inventory hold.

## Variants

Variants are optional and have:

- backend-owned `variantId`;
- name and structured option values;
- independent SKU;
- independent on-hand/reserved/available values;
- independent reorder threshold;
- active/inactive lifecycle.

A variant with reserved units cannot be deactivated.

## Alerts

The in-app state machine is:

```text
NORMAL -> LOW_STOCK -> OUT_OF_STOCK -> RESTOCKED
```

Rules:

- LOW_STOCK means available is positive and at/below threshold;
- OUT_OF_STOCK means available is zero;
- RESTOCKED is emitted only when a previously low/out subject returns above threshold;
- one open alert exists per product/variant subject;
- acknowledgement does not create duplicate same-state alerts;
- BUSINESS_ADMIN may acknowledge;
- OPERATOR may view only.

## Restock subscriptions

Customers can request notification when an unavailable product or exact variant returns.

Safety rules:

- explicit consent is mandatory;
- the target must be an active product/variant of the same tenant;
- tracking must be authoritative;
- subscription is rejected when the target is already available;
- contact is normalized;
- duplicate ACTIVE watches are deduplicated;
- an advisory subject lock closes subscribe/restock races;
- one idempotent pending notification is created per subscription;
- subscription becomes NOTIFIED after a real RESTOCKED transition;
- cancellation is idempotent and cancels a pending notification when present;
- cancellation through the administration API requires BUSINESS_ADMIN.

Inventory V1 does **not** automatically send WhatsApp, SMS or email. It only creates a provider-neutral PENDING notification for a separately authorized delivery workflow.

## Permissions

### BUSINESS_ADMIN

Can:

- configure stock;
- make manual adjustments;
- create/edit/deactivate variants;
- acknowledge inventory alerts;
- cancel restock subscriptions;
- use all read views.

### OPERATOR

Can:

- read inventory, variants, movements, alerts and restock queues;
- participate in operational reservation/release/consume flows;
- register a consented restock request during an operational interaction.

The inventory administration UI is read-only for OPERATOR. Sensitive administration mutations are enforced in the backend, not only hidden in the browser.

## Tenant security

Inventory uses the authenticated tenant context. Browser and AI payloads are not trusted to select a business.

Protection layers:

- `TenantProvider` for tenant-bound administration services;
- business-scoped repository queries;
- ENABLE + FORCE PostgreSQL RLS;
- runtime/system role grants;
- tenant-safe composite foreign keys;
- exact product/variant identity checks;
- PostgreSQL integration tests proving tenant isolation and cross-tenant write rejection.

## AI integration

Commercial AI tools receive authoritative inventory data through backend services.

- `list_catalog` exposes active variants and backend ids;
- `get_stock` resolves exact product or variant stock by backend identity/SKU;
- AI providers do not calculate or invent availability;
- order confirmation revalidates authoritative stock before persistence.

## Orders and payments

- order confirmation reserves exact stock;
- draft and immutable order lines persist `variant_id`;
- payment retry ensures stock is reserved again when needed;
- SUCCEEDED consumes;
- FAILED/CANCELLED/EXPIRED releases;
- PENDING/REQUIRES_ACTION preserves the hold;
- REFUNDED does not automatically restock a physical item;
- verified webhook replay is idempotent.

## API summary

Base inventory:

- `GET /api/v1/inventory`
- `GET /api/v1/inventory/{catalogItemId}`
- `PUT /api/v1/inventory/{catalogItemId}`
- `POST /api/v1/inventory/{catalogItemId}/adjustments`
- `POST /api/v1/inventory/{catalogItemId}/reservations`
- `POST /api/v1/inventory/reservations/{reservationId}/release`
- `POST /api/v1/inventory/reservations/{reservationId}/consume`
- `GET /api/v1/inventory/{catalogItemId}/movements`

Variants:

- `GET /api/v1/inventory/{catalogItemId}/variants`
- create/update/adjust/deactivate endpoints under the same resource;
- variant movement history.

Alerts:

- `GET /api/v1/inventory/alerts`
- `GET /api/v1/inventory/alerts/history`
- `POST /api/v1/inventory/alerts/{alertId}/acknowledge`

Restock queue:

- `GET /api/v1/inventory/restock-subscriptions`
- `POST /api/v1/inventory/restock-subscriptions`
- `POST /api/v1/inventory/restock-subscriptions/{id}/cancel`
- `GET /api/v1/inventory/restock-subscriptions/notifications`

## UI

`/inventory.html` includes:

- product/SKU search;
- tracked/low/out/restocked filters;
- explicit Agotado state;
- Reponer stock action;
- product and variant adjustments;
- movement history;
- inventory alert actions;
- customers waiting for restock;
- exact variant context;
- provider-neutral “Aviso listo / Pendiente de envío” state;
- admin cancellation;
- operator read-only behavior.

The browser does not submit a tenant/business id to select inventory ownership.

## Migrations

- V67: inventory stock, reservations, movements and RLS;
- V68: product variants;
- V69: variant identity through order/reservation lifecycle;
- V70: inventory alert state machine;
- V71: consented restock subscriptions + pending notification queue;
- V72: final tenant/product/variant referential-integrity hardening.

## Certification evidence

The branch contains focused tests for:

- `InventoryService`;
- `InventoryVariantService`;
- `InventoryAlertService`;
- `InventoryRestockSubscriptionService`;
- reservation expiry;
- PostgreSQL concurrency;
- PostgreSQL RLS and tenant referential integrity;
- payment workflow/webhooks;
- order workflow;
- controller authorization contracts;
- Playwright inventory admin/operator workflows.

Block 5 certification is tied to PR #495. The integration uses the same Inventory V1 alert, restock, concurrency, RLS and Playwright coverage while preserving the already-certified omnichannel/order/payment/variant flow. The first combined GREEN run was RecepVoz CI `36291486817`; a final run is required after syncing the latest `main`.

## Intentional V1 limitations

Inventory V1 deliberately does not include:

- multiple warehouses/locations;
- suppliers or purchase orders;
- costing/accounting;
- batch/lot/serial tracking;
- automated physical-return restocking on refunds;
- automatic outbound WhatsApp/SMS/email delivery;
- a large CRM/ERP layer.

Those are separate product decisions and must not block the sellable pilot.
