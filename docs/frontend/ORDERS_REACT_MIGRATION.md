# React Orders / Operations migration

Route: `/app/orders`

Branch: `feat/react-orders-operations-migration`

PR: #710

## Product model

Orders / Operations is a primary operational surface. Customers and conversations are not standalone React destinations in the target information architecture; they are contextual views inside Agenda and Operations.

The Orders screen is list-first on desktop and card-first on mobile. Order state mutations live only inside the order detail.

## Backend authority

The browser never supplies `businessId`. Tenant scope, order totals and valid state transitions remain backend-authoritative.

Supported order transitions:

- `CONFIRMED -> PREPARING | CANCELLED`
- `PREPARING -> READY | CANCELLED`
- `READY + DELIVERY -> DISPATCHED`
- `READY + PICKUP -> COMPLETED`
- `DISPATCHED -> COMPLETED`

General mutations use `ORDERS_MANAGE`. Preparation-only roles use the dedicated preparation endpoint with `ORDERS_PREPARE`.

## Context

Order detail may read operation history and the source conversation/call. Those reads are optional context: failure must not hide the authoritative order itself and must never trigger messaging or call writes.

## UI contract

- dark-first Helvoca frame
- search, status, source and sort controls
- embedded Orders / Customers / Conversations views
- responsive mobile list
- full-height order drawer
- duplicate mutation protection
- refetch after successful mutation
- explicit loading, empty, partial and error states
- reduced-motion support

Permanent browser contract: `e2e/react-orders.spec.js`.
