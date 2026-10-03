#!/usr/bin/env bash
set -euo pipefail

BACKEND_LOG="${SYSTEM_E2E_BACKEND_LOG:-system-e2e-backend.log}"
BASE_URL="${SYSTEM_E2E_BASE_URL:-http://127.0.0.1:8080}"

cleanup() {
  if [ -n "${BACKEND_PID:-}" ] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    kill "${BACKEND_PID}" || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

export DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5432/helvoca}"
export DB_USERNAME="${DB_USERNAME:-helvoca}"
export DB_PASSWORD="${DB_PASSWORD:-helvoca}"
export JWT_ISSUER="${JWT_ISSUER:-helvoca-system-e2e}"
export JWT_SECRET="${JWT_SECRET:-Y2FsbGFpLXN5c3RlbS1lMmUtc2VjcmV0LWtleS0zMi1ieXRlcyEhIQ==}"
export SEED_ENABLED=true
export SEED_ADMIN_EMAIL="${SYSTEM_E2E_ADMIN_EMAIL:-admin@helvoca.local}"
export SEED_ADMIN_PASSWORD="${SYSTEM_E2E_ADMIN_PASSWORD:-ChangeMe123!}"
export SEED_PRESET=barbershop
export HELVOCA_RATE_LIMIT_ENABLED=false
export APP_JOBS_ENABLED=false
export HELVOCA_OUTBOUND_DELIVERY_ENABLED=false
export HELVOCA_OUTBOUND_PROVIDER=NONE
export TWILIO_WHATSAPP_ENABLED=false
export TWILIO_PROVISIONING_ENABLED=false
export META_WHATSAPP_ENABLED=false
export META_WHATSAPP_EMBEDDED_SIGNUP_ENABLED=false
export MERCADOPAGO_ENABLED=false
export OPENAI_LIVE_ENABLED=false
export GEMINI_LIVE_ENABLED=false

echo "== System E2E: React -> Spring Boot -> PostgreSQL =="
echo "Safety: ephemeral CI database, seed tenant only, all external providers disabled."

npm install --no-audit --no-fund
npm run frontend:build
npx playwright install --with-deps chromium

mvn --batch-mode --no-transfer-progress -DskipTests clean package

JAR_PATH="$(find target -maxdepth 1 -type f -name 'helvoca-*.jar' ! -name '*.original' | head -n 1)"
if [ -z "$JAR_PATH" ]; then
  echo "System E2E could not find the packaged backend jar."
  exit 1
fi

java -jar "$JAR_PATH" >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

READY=false
for attempt in $(seq 1 60); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Backend exited before becoming healthy."
    tail -n 200 "$BACKEND_LOG" || true
    exit 1
  fi

  if curl --connect-timeout 2 --max-time 5 -fsS "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
    READY=true
    break
  fi
  sleep 2
done

if [ "$READY" != "true" ]; then
  echo "Backend did not become healthy in time."
  tail -n 200 "$BACKEND_LOG" || true
  exit 1
fi

SYSTEM_E2E_BASE_URL="$BASE_URL" npm run test:e2e:system

echo "SYSTEM E2E: PASS"
