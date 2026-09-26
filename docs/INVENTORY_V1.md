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
- no AI provider is allowed to invent stock.

## Initial API

- `GET /api/v1/inventory`
- `GET /api/v1/inventory/{catalogItemId}`
- `PUT /api/v1/inventory/{catalogItemId}`
- `POST /api/v1/inventory/{catalogItemId}/adjustments`
- `POST /api/v1/inventory/{catalogItemId}/reservations`
- `POST /api/v1/inventory/reservations/{reservationId}/release`
- `POST /api/v1/inventory/reservations/{reservationId}/consume`
- `GET /api/v1/inventory/{catalogItemId}/movements`

## Reservation expiry

A scheduled worker scans expired ACTIVE holds in batches of 100. Discovery runs with system database scope, while every mutation is re-entered under the reservation's tenant scope.

Default schedule:

- enabled: `HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED=true`;
- initial delay: 15 seconds;
- poll delay: 30 seconds.

This worker is intentionally independent from `APP_JOBS_ENABLED` because releasing abandoned database stock is an internal consistency action, not an outbound delivery action.

## Next isolated steps

1. add PostgreSQL concurrency and tenant-isolation integration certification;
2. add inventory management UI and low-stock dashboard;
3. connect variant selection to order lines and AI stock lookup;
4. add variant management to the inventory UI.
