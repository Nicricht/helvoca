# Helvoca Product Architecture

## Product focus

Helvoca V1 is an AI receptionist for appointment-based businesses. The first commercial promise is deliberately narrow:

- answer business calls
- answer business-specific questions
- search services and availability
- create, cancel and reschedule bookings
- transfer difficult or sensitive cases to a human
- persist call history, transcript and summary

Features outside that loop should not delay the first paying customer.

## Architectural rule

Helvoca owns the business rules. Providers are replaceable infrastructure.

```text
Caller
  |
Telephony adapter
  |
Helvoca call lifecycle
  |
Voice AI provider
  |
Helvoca tools / application services
  |
PostgreSQL
```

The AI may request an operation, but Helvoca decides whether it succeeds. A provider response can never override tenant isolation, booking availability, permissions or application validation.

## Provider ports

### Voice AI

The core contracts are:

- `VoiceAiProvider`
- `VoiceAiSession`
- `VoiceTransportSession`
- `VoiceAiProviderRegistry`

The first implementation is OpenAI Realtime. The telephony adapter no longer passes a Twilio WebSocket directly into the AI provider. It passes a `VoiceTransportSession` port.

This makes these future implementations possible without rewriting the booking/customer/knowledge modules:

```text
VoiceAiProvider
  |- openai
  |- stt-llm-tts
  `- future provider
```

Selection is controlled by:

```text
HELVOCA_VOICE_AI_PROVIDER=openai
```

### Telephony

Twilio remains the first carrier adapter, but durable call state now lives in provider-neutral `CallLifecycleService`. `TwilioCallService` is intentionally thin and translates Twilio events into that lifecycle.

Future carrier adapters can delegate to the same lifecycle:

```text
CallLifecycleService
  ^
  |- Twilio adapter
  |- Telnyx adapter
  `- Generic SIP adapter
```

The currently selected carrier is declared with:

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
```

## Provider observability

Every `call_session` stores:

- `telephony_provider`
- `ai_provider`

These fields are exposed in the call API. They are the foundation for later per-call cost accounting, provider comparison, fallback analysis and margins by tenant.

## Runtime shape

Helvoca remains a modular monolith for V1:

```text
helvoca-api
  |- auth
  |- business
  |- customer
  |- servicecatalog
  |- booking
  |- knowledge
  |- call
  |- telephony
  |- voice
  `- ai

PostgreSQL = durable source of truth
```

Do not split these modules into microservices until production load or organizational boundaries create a measurable need.

## Redis decision

Redis is planned only when the realtime production path needs shared ephemeral state across multiple application replicas. Intended uses:

- active call state
- short-lived conversation state
- distributed locks
- rate limiting
- cache

PostgreSQL remains the durable source of truth. Redis must never become the only copy of a booking, customer, call history or business configuration.

## Commercial sequence

The implementation order from this point is:

1. stable real phone call
2. stable realtime conversation
3. business knowledge
4. create/cancel/reschedule booking
5. human transfer
6. call history and summary
7. minimal business dashboard
8. self-service onboarding
9. usage and cost metering
10. plans, limits and billing
11. first paying customers
12. add Telnyx/SIP only when economics or geography justify it

## Non-goals for V1

Avoid adding Kafka, Kubernetes, independent microservices, complex event buses, dozens of integrations or an oversized analytics suite before they solve a measured customer or scaling problem.
