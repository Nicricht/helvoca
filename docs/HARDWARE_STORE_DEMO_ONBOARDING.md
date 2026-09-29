# Ferretería San Martín Demo onboarding

## Purpose

This demo preset configures **Ferretería San Martín Demo** through the same development seed path already used by RecepVoz.

It is intentionally fictional and must not be used as a production customer configuration.

## Start the hardware-store demo tenant

Use a local or disposable demo database.

```bash
export SEED_ENABLED=true
export SEED_PRESET=hardware_store
export SEED_ADMIN_EMAIL=ferreteria.demo@helvoca.local
export SEED_ADMIN_PASSWORD='choose-a-local-demo-password'
```

Start the application through the normal local development path.

The seed is idempotent and will create or reconcile the demo configuration without manual SQL.

## Business identity loaded

- Business: **Ferretería San Martín Demo**
- Owner reference: **Mauricio San Martín Demo**
- Timezone: `America/Santiago`
- Language: Spanish
- Currency: CLP
- Address: **Pasaje Tuerca Demo 742, Providencia, Santiago**
- Products: enabled
- Services: disabled
- Reservations: disabled
- Real phone number: not configured
- Real WhatsApp: not configured
- Real payments: not configured
- Real delivery provider: not configured

## Receptionist behavior

Greeting:

> Hola, te comunicaste con Ferretería San Martín Demo. ¿Qué necesitas cotizar o comprar?

The agent is configured to:

- use only configured catalog, stock, prices, schedules, delivery zones and policies;
- never invent stock, price, measure, compatibility, brand, discount or delivery time;
- clarify the selling unit when ambiguous;
- query authoritative inventory before promising availability;
- revalidate stock before order confirmation;
- repeat product, measure/variant, quantity, unit, fulfillment and total before confirmation;
- replace previous intent when the customer corrects quantity, product, measure or fulfillment;
- never silently substitute another product;
- record unknown questions instead of inventing answers;
- escalate gas, electrical-network and structural-risk questions;
- never claim that a real payment was completed.

## Catalog

The preset loads 34 products across:

- fasteners;
- PVC;
- construction materials;
- paint;
- tools;
- electricity;
- gas reference product;
- plumbing;
- wood;
- garden;
- safety.

Prices and selling units are stored in the product catalog.

## Inventory

Every product receives an inventory record with SKU and tracking enabled.

The dataset intentionally includes:

- out-of-stock items;
- low-stock items;
- one SKU with exactly one unit available;
- products sold by unit, box, bag, meter, tube, sack, gallon, set, pair and roll.

This supports realistic stock checks and adversarial order scenarios.

## Delivery

Three fictional delivery zones are configured:

- Providencia Demo: CLP 3,990
- Ñuñoa Demo: CLP 4,990
- Santiago Centro Demo: CLP 5,990

Configured delivery estimates:

- Providencia Demo: same day, subject to 13:00 cutoff
- Ñuñoa Demo: next business day
- Santiago Centro Demo: next business day

Coverage is resolved from the configured address terms. No physical delivery is created by simply loading this fixture.

## Knowledge and policies

The tenant loads policies for:

- prices and VAT;
- authoritative stock;
- quotes;
- order confirmation;
- intent corrections;
- pickup;
- delivery;
- returns;
- cut-to-length products;
- compatibility;
- electrical safety;
- gas safety;
- structural safety;
- unknown products;
- payments.

## Commercial capabilities

Enabled for the receptionist:

- business information;
- knowledge search;
- customer identification;
- request/unanswered-question capture;
- human handoff capability for safe simulator behavior;
- catalog;
- stock lookup derived from catalog;
- delivery zones and address validation;
- delivery quote/update/create/status/cancel;
- order quote/update/create/status/cancel;
- structured quote creation.

Payment capabilities are deliberately not enabled.

## Safety

This preset does not activate:

- Twilio or any real telephony provider;
- Meta/WhatsApp delivery;
- payment provider credentials;
- a real human-transfer number;
- a real courier or delivery provider.

Use the RecepVoz simulator for conversational testing until provider activation is explicitly authorized.
