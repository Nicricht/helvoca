#!/usr/bin/env bash
set -euo pipefail

echo "Inventory commercial depletion certification"
echo "Safety: PostgreSQL Testcontainers + fake payment provider only; no real payments, calls, WhatsApp, deploy, or production mutation."

mvn --batch-mode --no-transfer-progress \
  -Dtest=InventoryPostgresConcurrencyIntegrationTest#twoSequentialPaidPurchasesDepleteStockAndThirdReservationFails \
  test

echo "PASS: stock 2 -> reserve 1 -> paid/consume -> stock 1 -> reserve 1 -> paid/consume -> stock 0 -> third reservation rejected."
