#!/usr/bin/env bash
set -euo pipefail

CURRENT_SCENARIO="bootstrap"

report_failure() {
  local exit_code=$?
  echo "ADVERSARIAL HARDWARE STORE WEEK: FAIL"
  echo "Scenario: ${CURRENT_SCENARIO}"
  if [ -d target/surefire-reports ]; then
    grep -R -m 3 -B 1 '<failure\|<error' target/surefire-reports 2>/dev/null || true
  fi
  exit "${exit_code}"
}
trap report_failure ERR

echo "== RecepVoz Adversarial Hardware Store Week =="
echo "Tenant: Ferretería San Martín Demo (fictitious only)"
echo "Target: 180 simulated conversations across 7 days."
echo "Safety: sandbox/simulation only; no real calls, no real WhatsApp, no real payments, no live credentials, no real delivery, no production mutation."

SCENARIOS=(
  "day-1:catalog-price-unit-similar-products"
  "day-2:inventory-out-of-stock-low-stock-no-substitution"
  "day-3:quotes-intent-corrections-duplicate-confirmation"
  "day-4:orders-pickup-delivery-coverage"
  "day-5:non-technical-unknown-handoff-ambiguity"
  "day-6:tool-failure-bounded-retry-concurrency-tenant-isolation"
  "day-7:long-conversations-mixed-problems-full-regression"
)

echo "Adversarial week scenarios:"
printf ' - %s\n' "${SCENARIOS[@]}"

BACKEND_TESTS=(
  AdversarialHardwareStoreFixtureTest
  AdversarialHardwareStoreWeekCertificationTest
  CommercialOperationToolServiceTest
  ConfirmationAwareCommercialOperationToolServiceTest
  OrderWorkflowServiceTest
  InventoryServiceTest
  InventoryVariantServiceTest
  RealtimeToolServiceTest
  SimulatorToolExecutorTest
  SimulatorExternalSideEffectIsolationTest
  ReceptionistSimulatorAdversarialCapacityTest
  GoldenJourneyCommercialV1IntegrationTest
  OmnichannelCommerceJourneyIntegrationTest
  SafeOperationRetryChaosCertificationTest
  PostgresRowLevelSecurityIntegrationTest
  JourneyTraceServiceIntegrationTest
  ConversationReplayFixtureSuiteTest
)

TEST_CSV="$(IFS=,; echo "${BACKEND_TESTS[*]}")"

CURRENT_SCENARIO="backend:180-conversation-harness-and-critical-commerce-contracts"
echo "Running focused backend contracts:"
printf ' - %s\n' "${BACKEND_TESTS[@]}"

mvn --batch-mode --no-transfer-progress \
  -Dtest="$TEST_CSV" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

CURRENT_SCENARIO="browser:inventory-orders-owner-operation"
echo "Installing browser dependencies for relevant E2E..."
npm install --no-audit --no-fund
npx playwright install --with-deps chromium

echo "Running relevant browser E2E..."
npx playwright test \
  e2e/inventory.spec.js \
  e2e/home-operational.spec.js \
  e2e/operations-internal.spec.js

CURRENT_SCENARIO="complete"
trap - ERR
echo "ADVERSARIAL HARDWARE STORE WEEK: PASS"
