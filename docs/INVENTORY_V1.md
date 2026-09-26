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
- stock reservation and consumption use pessimistic row locking;
- an active reservation cannot oversell available stock;
- consuming a reservation decrements both `on_hand` and `reserved`;
- releasing a reservation decrements only `reserved`;
- every stock mutation creates a movement record;
- SKU is unique inside a business;
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

## Next isolated steps

1. add unit/integration tests, including concurrency and tenant isolation;
2. add reservation expiry;
3. connect order confirmation to stock reservation;
4. connect payment success to reservation consumption and cancellation/failure to release;
5. expose safe AI tools: `get_stock`, `reserve_stock`, `release_stock`;
6. add inventory management UI and low-stock dashboard;
7. add product variants (size/color) after the base stock model is certified.
