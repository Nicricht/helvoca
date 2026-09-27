#!/usr/bin/env bash
set -euo pipefail

TESTS=(
  FailureChaosLabContractTest
  FailureChaosMatrixTest
  SafeOperationRetryChaosCertificationTest
  PaymentWebhookChaosCertificationTest
  MetaWhatsAppInboundJobServiceTest
  PersistentJobStoreIntegrationTest
  DeepgramAudioTranscriptionProviderTest
  TranscriptionCircuitBreakerTest
  RoutedAudioTranscriberTest
  ConversationReplayFixtureSuiteTest
)

TEST_CSV="$(IFS=,; echo "${TESTS[*]}")"

echo "Failure/Chaos Lab V3"
printf ' - %s\n' "${TESTS[@]}"

mvn --batch-mode --no-transfer-progress \
  -Dtest="$TEST_CSV" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "Failure/Chaos Lab V3 passed."
