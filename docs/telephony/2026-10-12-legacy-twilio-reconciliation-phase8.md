# Phase 8/10: Gemini Live model identity and offline readiness

**Classification:** code-only technical certification, production/commercial voice **NO-GO**.
No Gemini/Twilio HTTP or WebSocket requests, no Google project quota checks, no API calls, no paid inference, no production deploys, no credential reads from Railway and no edits to live tenant data.

## Source-of-truth audit (2026-10-12)

- `GeminiLiveProperties` originally defaulted to `gemini-3.8-live` while `application.yml` overrode it with `gemini-3.1-flash-live-preview`; the effective deployed value additionally depends on `GEMINI_LIVE_MODEL`.
- Google model documentation lists `gemini-3.8-live` as stable and recommended for low-latency live agents; the 3.1 Flash Live preview remains a legacy option. References:
  - https://ai.google.dev/gemini-api/docs/models
  - https://ai.google.dev/gemini-api/docs/models/gemini-3.8-live
  - https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-live-preview
- `GEMINI_LIVE_ENABLED` defaults to **false**. `GEMINI_API_KEY` must be present. Voice routing also considers `HELVOCA_VOICE_PROVIDER_ORDER`, circuit health and other configured providers; merely seeing a key or default model never proves Gemini is used in a live call.
- Existing `GeminiLiveStartupProbe` can make a **billable or quota-affecting WebSocket connection** only if explicitly enabled by `GEMINI_LIVE_PROBE_ON_STARTUP=true`. **Do not enable it in this phase.**
- Live model/setup identity is included in `GeminiLiveVoiceSession.buildSetup()`; a separate authenticated provider handshake would be required to prove model/account access.

## Implementation

1. Change the default YAML fallback to `gemini-3.8-live`, matching the Java default, without changing any existing Railway environment variables or deployed model selection.
2. Add `GeminiLiveModelPolicy`: allow `gemini-3.8-live` (STABLE) and explicit `gemini-3.1-flash-live-preview` (LEGACY_PREVIEW); reject non-Live models, arbitrary model strings and extended-thinking mode (current transactional tool declarations require BLOCKING). Constrain the key-bearing WebSocket to the exact official Bidi endpoint, without query/fragment, alternative host or scheme.
3. Add `GeminiLiveRuntimeReadiness` to log **only** safe local metadata: allowlisted model ID, tier, enabled, presence of key, endpoint policy, local-config readiness, and router-selected-provider class (`GEMINI`, `OTHER` or `NONE`). Never log key values, arbitrary model inputs, arbitrary endpoint URLs or tenant data. No new public endpoint and no network I/O.
4. Cover safe/legacy/invalid model selection, invalid URLs, missing/disabled inputs, runtime selection, secret redaction and YAML fallback using deterministic tests.

## Interpretation

- `localConfigurationReady=true` means only syntactically acceptable local configuration. **It does not prove live connectivity, entitlement, quota, balance, actual production model usage, latency, billing, or Twilio readiness**.
- `routerSelection=GEMINI` means the existing in-process router currently prefers Gemini according to configuration and health; it is **not evidence that a real phone call occurred**.
- Existing `VERIFIED_EXISTING` Twilio routing remains intentionally disallowed pending explicit future production authorization.
- Phase 9 is **not started**.

## Exit criteria

On final PR #791 HEAD: all seven mandatory GitHub Actions jobs SUCCESS; full backend + E2E suites green; **100% differential executable Java lines, branches and methods**, no real provider activity. Document the exact run and SHA in the PR.
