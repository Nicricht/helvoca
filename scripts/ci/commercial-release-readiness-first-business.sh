#!/usr/bin/env bash
set -euo pipefail

echo "== RecepVoz Commercial Release Readiness — First Business V1 =="
echo "Safety: local/sandbox verification only; no deploy, real calls, real WhatsApp, real payments, provisioning or production mutation."

BASE_MAIN="${COMMERCIAL_READINESS_MAIN_SHA:-}"
if [[ -z "$BASE_MAIN" ]]; then
  git fetch --no-tags origin main >/dev/null 2>&1 || true
  BASE_MAIN="$(git rev-parse origin/main)"
fi

echo "Fetching current main for release freshness verification..."
git fetch --no-tags origin main:refs/remotes/origin/main
CURRENT_MAIN="$(git rev-parse refs/remotes/origin/main)"
echo "Audited main: $BASE_MAIN"
echo "Fetched main: $CURRENT_MAIN"
if [[ -n "$BASE_MAIN" && "$CURRENT_MAIN" != "$BASE_MAIN" ]]; then
  echo "main moved after the commercial-readiness audit; refresh the candidate and recertify."
  exit 1
fi

if [[ -z "${GITHUB_REPOSITORY:-}" || -z "${GITHUB_SHA:-}" || -z "${GH_TOKEN:-}" ]]; then
  echo "GitHub compare context is required to prove the release ancestry."
  exit 1
fi

COMPARE_JSON="$(mktemp)"
curl --fail-with-body -sS -L \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/compare/$CURRENT_MAIN...$GITHUB_SHA" > "$COMPARE_JSON"

python3 - "$COMPARE_JSON" "$CURRENT_MAIN" <<'PY'
import json
import sys

path, expected_base = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    data = json.load(fh)

status = data.get("status")
behind = data.get("behind_by")
merge_base = (data.get("merge_base_commit") or {}).get("sha")

if status not in {"ahead", "identical"}:
    raise SystemExit(f"candidate is not ahead/identical to audited main: status={status}")
if behind != 0:
    raise SystemExit(f"candidate is behind audited main by {behind} commits")
if merge_base != expected_base:
    raise SystemExit(f"unexpected merge base: {merge_base} != {expected_base}")

print(f"GitHub compare ancestry PASS: status={status} behind_by={behind} merge_base={merge_base}")
PY

echo "Verifying required release-readiness evidence..."
for required in   docs/COMMERCIAL_EXTERNAL_GATES_V1.md   docs/CONTROLLED_REAL_BUSINESS_PILOT_V1.md   docs/FIRST_CUSTOMER_ONBOARDING_FORM.md   docs/FIRST_CUSTOMER_OPERATION.md   docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md   docs/SAAS_BILLING_COMMERCIAL_READINESS.md   docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md; do
  if [[ ! -s "$required" ]]; then
    echo "Missing required readiness evidence: $required" >&2
    exit 1
  fi
done

READINESS_DOC="docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md"
grep -Fq "PROVIDER TEST ROUND-TRIP PENDING" "$READINESS_DOC"
grep -Fq "CUSTOMER-SPECIFIC ACTIVATION PENDING" "$READINESS_DOC"
grep -Fq "**Real-customer activation:** NOT AUTHORIZED" "$READINESS_DOC"

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
