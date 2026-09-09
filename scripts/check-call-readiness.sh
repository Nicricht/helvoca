#!/usr/bin/env bash
set -euo pipefail

required=(TWILIO_AUTH_TOKEN TWILIO_PUBLIC_BASE_URL TWILIO_MEDIA_STREAM_URL OPENAI_API_KEY)
missing=0

for key in "${required[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "MISSING: $key"
    missing=1
  else
    echo "OK: $key configured"
  fi
done

if [[ -n "${TWILIO_PUBLIC_BASE_URL:-}" && ! "${TWILIO_PUBLIC_BASE_URL}" =~ ^https:// ]]; then
  echo "INVALID: TWILIO_PUBLIC_BASE_URL must use https://"
  missing=1
fi

if [[ -n "${TWILIO_MEDIA_STREAM_URL:-}" && ! "${TWILIO_MEDIA_STREAM_URL}" =~ ^wss:// ]]; then
  echo "INVALID: TWILIO_MEDIA_STREAM_URL must use wss://"
  missing=1
fi

if [[ $missing -ne 0 ]]; then
  echo "Helvoca is not ready for a real end-to-end phone call."
  exit 1
fi

echo "Environment is ready for a real Twilio + OpenAI call test."
