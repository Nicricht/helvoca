# Inventory commercial depletion certification

This certification proves the sellable inventory path against a real PostgreSQL Testcontainers database while keeping all external effects sandboxed.

## Scenario

1. Start with authoritative stock of 2 units.
2. First order reserves 1 unit.
3. A verified sandbox payment webhook consumes the first reservation.
4. Inventory becomes 1 on-hand, 0 reserved.
5. Second order reserves the remaining unit.
6. A second verified sandbox payment webhook consumes it.
7. Inventory becomes 0 on-hand, 0 reserved.
8. A third order attempts to reserve 1 unit and must fail with `INSUFFICIENT_STOCK`.
9. Database invariants are asserted: no negative stock, no reserved quantity above on-hand, exactly two consumed reservations, and exactly two consumption movements.

## Safety

The certification uses PostgreSQL Testcontainers and the existing fake `concurrency-test` payment provider. It performs no real payment, phone call, WhatsApp delivery, deploy, or production mutation.

## Run

```bash
bash scripts/ci/inventory-commercial-certification.sh
```

The test is also included in the normal Maven test suite and therefore in the pull-request full CI gate.
