#!/usr/bin/env bash
set -euo pipefail

echo "== RecepVoz Commercial Pilot Rehearsal V1 =="
echo "Safety: sandbox/simulation only; no real calls, no real WhatsApp, no real payments, no production mutation."

FOCUSED_TESTS=(
  DevDataInitializerTest
  GoldenJourneyCommercialV1IntegrationTest
  PilotGoNoGoServiceTest
  PilotGoNoGoControllerContractTest
  BillingSubscriptionServiceTest
  CommercialEntitlementServiceTest
  MercadoPagoWebhookControllerTest
  SaasBillingSandboxCertificationStartupRunnerTest
  PostgresRowLevelSecurityIntegrationTest
  JourneyTraceServiceIntegrationTest
  ConversationReplayFixtureSuiteTest
  TwilioVoiceTransportSessionTest
  ClosingConversationCertificationPackTest
  BookingConversationCertificationPackTest
  PilotMetricsServiceTest
  CommercialSandboxE2eCertificationStartupRunnerTest
)

TEST_CSV="$(IFS=,; echo "${FOCUSED_TESTS[*]}")"

echo "Focused rehearsal contracts:"
printf ' - %s\n' "${FOCUSED_TESTS[@]}"

mvn --batch-mode --no-transfer-progress \
  -Dtest="$TEST_CSV" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "Running existing Pilot End-to-End Certification..."
bash scripts/ci/pilot-e2e-certification.sh

if [[ "${COMMERCIAL_PILOT_REHEARSAL_FULL:-false}" == "true" ]]; then
  echo "Running exhaustive backend suite with JaCoCo..."
  mvn --batch-mode --no-transfer-progress -Pcoverage test

  BASE_SHA="${BASE_SHA:-}"
  if [[ -z "$BASE_SHA" ]]; then
    git fetch --no-tags origin main >/dev/null 2>&1 || true
    BASE_SHA="$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse HEAD^)"
  fi

  echo "Enforcing differential Java coverage against $BASE_SHA..."
  DIFF_LINE_COVERAGE="${DIFF_LINE_COVERAGE:-80}" \
  DIFF_BRANCH_COVERAGE="${DIFF_BRANCH_COVERAGE:-70}" \
    python3 scripts/ci/check_diff_coverage.py "$BASE_SHA"

  echo "Validating all console/e2e JavaScript syntax..."
  while IFS= read -r file; do
    node --check "$file"
  done < <(find src/main/resources/static e2e -type f -name '*.js' | sort)

  echo "Installing browser dependencies..."
  npm install --no-audit --no-fund
  npx playwright install --with-deps chromium

  echo "Running complete Playwright suite..."
  npm run test:e2e
fi

echo "PILOT REHEARSAL: PASS"
