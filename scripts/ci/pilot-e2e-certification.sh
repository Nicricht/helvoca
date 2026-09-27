#!/usr/bin/env bash
set -euo pipefail

echo "== RecepVoz Pilot End-to-End Certification V1 =="
echo "Safety: sandbox only; no real provider delivery, real calls, real payments, or production mutation."

TESTS=(
  GoldenJourneyCommercialV1IntegrationTest
  OmnichannelCommerceJourneyIntegrationTest
  CommercialSandboxE2eCertificationStartupRunnerTest
  ConversationQualityGoldenScenarioTest
  ConversationReplayFixtureSuiteTest
  PaymentWebhookChaosCertificationTest
  SafeOperationRetryChaosCertificationTest
  PersistentJobStoreIntegrationTest
  InventoryServiceTest
  ReconciliationServiceTest
  ReconciliationRepositoryPostgresIntegrationTest
  JourneyTraceServiceIntegrationTest
)

TEST_CSV="$(IFS=,; echo "${TESTS[*]}")"

echo "Certification suites:"
printf ' - %s\\n' "${TESTS[@]}"

mvn --batch-mode --no-transfer-progress \\
  -Dtest="$TEST_CSV" \\
  -Dsurefire.failIfNoSpecifiedTests=false \\
  test

echo "Pilot End-to-End Certification V1 passed."
