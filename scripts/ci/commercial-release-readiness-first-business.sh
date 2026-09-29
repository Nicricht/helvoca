#!/usr/bin/env bash
set -euo pipefail

echo "== RecepVoz Commercial Release Readiness — First Business V1 =="
echo "Safety: local/sandbox verification only; no deploy, real calls, real WhatsApp, real payments, provisioning or production mutation."

BASE_MAIN="${COMMERCIAL_READINESS_MAIN_SHA:-}"
if [[ -z "$BASE_MAIN" ]]; then
  git fetch --no-tags origin main >/dev/null 2>&1 || true
  BASE_MAIN="$(git rev-parse origin/main)"
fi

echo "Fetching current main for ancestry verification..."
if [[ "$(git rev-parse --is-shallow-repository)" == "true" ]]; then
  git fetch --no-tags --unshallow origin
fi
git fetch --no-tags origin main:refs/remotes/origin/main
CURRENT_MAIN="$(git rev-parse refs/remotes/origin/main)"
echo "Audited main: $BASE_MAIN"
echo "Fetched main: $CURRENT_MAIN"
if [[ -n "$BASE_MAIN" && "$CURRENT_MAIN" != "$BASE_MAIN" ]]; then
  echo "main moved after the commercial-readiness audit; refresh the candidate and recertify."
  exit 1
fi
git merge-base --is-ancestor "$CURRENT_MAIN" HEAD

for required in   docs/COMMERCIAL_EXTERNAL_GATES_V1.md   docs/CONTROLLED_REAL_BUSINESS_PILOT_V1.md   docs/FIRST_CUSTOMER_ONBOARDING_FORM.md   docs/FIRST_CUSTOMER_OPERATION.md   docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md   docs/SAAS_BILLING_COMMERCIAL_READINESS.md   docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md; do
  test -s "$required"
done

grep -Fq "PROVIDER TEST ROUND-TRIP PENDING" docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md
grep -Fq "CUSTOMER-SPECIFIC ACTIVATION PENDING" docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md
grep -Fq "Real-customer activation: NOT AUTHORIZED" docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md

FOCUSED_TESTS=(
  CommercialReleaseReadinessGateContractTest
  PilotLaunchControlServiceTest
  PilotLaunchControlControllerContractTest
  ControlledPilotExternalEffectGuardTest
  PilotGoNoGoServiceTest
  PilotGoNoGoControllerContractTest
  PilotActivationChecklistServiceTest
  PostgresRowLevelSecurityIntegrationTest
  InventoryPostgresConcurrencyIntegrationTest
  BillingSubscriptionServiceTest
  BillingSubscriptionServiceEdgeCasesTest
  SaasBillingSandboxCertificationStartupRunnerTest
  CommercialEntitlementServiceTest
  ReconciliationServiceTest
  ReconciliationRepositoryPostgresIntegrationTest
  JourneyTraceServiceIntegrationTest
)

TEST_CSV="$(IFS=,; echo "${FOCUSED_TESTS[*]}")"
echo "Running commercial launch-cage contracts:"
printf ' - %s\n' "${FOCUSED_TESTS[@]}"

mvn --batch-mode --no-transfer-progress   -Dtest="$TEST_CSV"   -Dsurefire.failIfNoSpecifiedTests=false   test

echo "Running existing commercial pilot rehearsal..."
bash scripts/ci/commercial-pilot-rehearsal.sh

echo "Running launch-cage browser contracts..."
npm install --no-audit --no-fund
npx playwright install --with-deps chromium
npx playwright test   e2e/pilot-preflight.spec.js   e2e/operations-internal.spec.js   e2e/frontend-release-candidate.spec.js   e2e/frontend-release-candidate-hardening.spec.js

echo "COMMERCIAL RELEASE READINESS FIRST BUSINESS: PASS"
