# Project Invariants

Document truths that **must never** be broken by a change.

Each invariant should be:
- specific;
- observable;
- **testable**;
- linked to automated evidence whenever practical.

Examples:
- Tenant A must never read or mutate Tenant B data.
- A confirmed payment must never execute twice for the same idempotency key.
- Inventory must never become negative unless the domain explicitly models backorders.

For each invariant, record the owning subsystem and the test or gate that protects it.
