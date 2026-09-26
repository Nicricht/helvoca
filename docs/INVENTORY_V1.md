# Inventory V1

Status: **isolated development on `feat/inventory-v1`**

This work must not be merged into `main` while the sellable pilot is being certified unless it passes its own tests and is explicitly approved.

## Goal

Give RecepVoz an authoritative stock backend that voice, WhatsApp, orders and payments can use without guessing availability.

## V1 model

```text
Catalog product
   |
   +--> Inventory stock
          |- SKU
          |- tracking enabled
          |- on hand
          |- reserved
          |- available
          |- reorder threshold
          |
          +--> Product variants
          |      |- name (for example Negro / 42)
          |      |- option values JSON (color, size, etc.)
          |      |- unique SKU
          |      |- own on-hand / reserved / available
          |      `- own reorder threshold
          |
          +--> Reservations
          |      |- ACTIVE
          |      |- CONSUMED
          |      |- RELEASED
          |      `- EXPIRED
          |
          `--> Immutable movement history
```

## Invariants

- inventory belongs to exactly one tenant;
- inventory is allowed only for active catalog products;
- `on_hand >= 0`;
- `reserved >= 0`;
- `reserved <= on_hand`;
- `available = on_hand - reserved`;
- stock reservation, payment protection and expiry use pessimistic row locking;
- an active reservation cannot oversell available stock;
- consuming a reservation decrements both `on_hand` and `reserved`;
- releasing or expiring a reservation decrements only `reserved`;
- expired order reservations are not released while a payment remains `REQUIRES_ACTION` or `PENDING`;
- if an expired ACTIVE hold belongs to an already `SUCCEEDED` payment, the expiry worker repairs it by consuming the stock instead of releasing it;
- every stock mutation creates a movement record;
- base inventory SKU is unique inside a business;
- every product variant has its own required SKU and stock balance;
- variant creation refuses a SKU already used by base product inventory or another variant;
- a variant with reserved units cannot be deactivated;
- no AI provider is allowed to invent stock;
- inventory alerts are tenant-scoped and deduplicated per product or variant;
- alert transitions are state-based: normal → low stock → out of stock → restocked;
- acknowledging an alert does not cause the same state to notify again;
- external WhatsApp/provider delivery is not triggered by inventory alerts unless a separate authorized delivery path is explicitly enabled.

## Initial API

- `GET /api/v1/inventory`
- `GET /api/v1/inventory/{catalogItemId}`
- `PUT /api/v1/inventory/{catalogItemId}`
- `POST /api/v1/inventory/{catalogItemId}/adjustments`
- `POST /api/v1/inventory/{catalogItemId}/reservations`
- `POST /api/v1/inventory/reservations/{reservationId}/release`
- `POST /api/v1/inventory/reservations/{reservationId}/consume`
- `GET /api/v1/inventory/{catalogItemId}/movements`
- `GET /api/v1/inventory/alerts`
- `GET /api/v1/inventory/alerts/history`
- `POST /api/v1/inventory/alerts/{alertId}/acknowledge`

## Reservation expiry

A scheduled worker scans expired ACTIVE holds in batches of 100. Discovery runs with system database scope, while every mutation is re-entered under the reservation's tenant scope.

Default schedule:

- enabled: `HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED=true`;
- initial delay: 15 seconds;
- poll delay: 30 seconds.

This worker is intentionally independent from `APP_JOBS_ENABLED` because releasing abandoned database stock is an internal consistency action, not an outbound delivery action.

## Current variant integration

Product variants now carry an exact backend-owned identity through the full commercial lifecycle:

- `list_catalog` exposes active variants with `variantId`, SKU and structured options;
- `get_stock` can resolve exact variant stock by `variantId` or variant SKU;
- draft order lines persist `variant_id`;
- final immutable order lines persist `variant_id`;
- inventory reservations persist `variant_id`;
- order confirmation reserves the exact variant;
- payment retry re-reserves the exact variant;
- payment success consumes the exact variant;
- cancellation, failure and expiry release the exact variant;
- the visual inventory workspace can create, edit, adjust and inspect variant history;
- BUSINESS_ADMIN can mutate variants while OPERATOR remains read-only.

## Automatic inventory alerts

V70 adds an internal alert state machine for base products and variants.

- `LOW_STOCK`: available stock is positive and at or below the configured minimum;
- `OUT_OF_STOCK`: available stock reaches zero;
- `RESTOCKED`: a previously low/out-of-stock item returns above its threshold;
- one open alert exists per product/variant, preventing repeated duplicate notifications;
- BUSINESS_ADMIN can acknowledge alerts from `/inventory.html`;
- OPERATOR can view alerts but cannot acknowledge them;
- PostgreSQL RLS protects alert rows by tenant;
- the inventory console refreshes alerts after product and variant changes;
- this layer is deliberately in-app only. It does not create outbound WhatsApp traffic.

## Next isolated steps

1. add PostgreSQL concurrency certification for mixed base-product + variant orders;
2. add an opt-in customer restock-watch queue that can later connect to authorized outbound WhatsApp delivery.
