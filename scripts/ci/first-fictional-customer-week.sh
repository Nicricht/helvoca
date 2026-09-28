#!/usr/bin/env bash
set -euo pipefail

echo "== RecepVoz First Fictional Customer Week =="
echo "Tenant: Barbería Norte Demo (fictitious only)"
echo "Safety: sandbox/simulation only; no real calls, no real WhatsApp, no real payments, no live credentials, no production mutation."

SCENARIOS=(
  "day-1:onboarding-and-business-configuration"
  "day-2:price-and-faq-questions"
  "day-2:availability-book-reschedule-cancel"
  "day-3:duplicates-and-repeated-conversation"
  "day-3:closed-hours-and-missing-service"
  "day-4:change-of-mind-and-interruption"
  "day-4:human-handoff"
  "day-5:tool-error-and-bounded-retry"
  "day-6:multi-tenant-isolation"
  "day-6:metrics-timeline-entitlements-dashboard"
  "day-7:replay-and-observability"
)

echo "Operational week scenarios:"
printf ' - %s\n' "${SCENARIOS[@]}"

BACKEND_TESTS=(
  DevDataInitializerTest
  OnboardingServiceTest
  DemoConversationScenariosTest
  SimulatorToolExecutorTest
  RealtimeToolServiceTest
  GoldenJourneyCommercialV1IntegrationTest
  BookingConversationCertificationPackTest
  ClosingConversationCertificationPackTest
  RealtimeHumanTransferToolTest
  GeminiLiveVoiceSessionTest
  TwilioMediaStreamHandlerTest
  SafeOperationRetryChaosCertificationTest
  ConversationQualityGoldenScenarioTest
  PostgresRowLevelSecurityIntegrationTest
  PilotMetricsServiceTest
  CustomerCommercialTimelineServiceTest
  CommercialEntitlementServiceTest
  JourneyTraceServiceIntegrationTest
  ConversationReplayFixtureSuiteTest
  CommercialSandboxE2eCertificationStartupRunnerTest
)

TEST_CSV="$(IFS=,; echo "${BACKEND_TESTS[*]}")"

echo "Running focused backend contracts:"
printf ' - %s\n' "${BACKEND_TESTS[@]}"

mvn --batch-mode --no-transfer-progress \
  -Dtest="$TEST_CSV" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "Installing browser dependencies for the non-delivery console simulation..."
npm install --no-audit --no-fund
npx playwright install --with-deps chromium

echo "Running first-user and owner-operation browser journeys..."
npx playwright test \
  e2e/first-user-ux-v2.spec.js \
  e2e/home-operational.spec.js

echo "FIRST FICTIONAL CUSTOMER WEEK: PASS"
