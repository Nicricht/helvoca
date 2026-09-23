# WhatsApp Reliability Architecture

Date: 2026-09-23
Status: Approved design, pending implementation plan
Repository: `Nicricht/helvoca`

## 1. Purpose

Make RecepVoz reliable enough that inbound WhatsApp text and audio no longer depend on long-running AI or transcription work inside Meta's webhook request.

The target behavior is simple:

- Meta receives an HTTP 200 quickly after a valid inbound event is accepted.
- Text and audio processing continue durably after the webhook returns.
- A duplicate Meta event cannot create duplicate conversations, replies, bookings, or outbound messages.
- Transient provider failures are retried with bounded backoff.
- Provider outages do not make the assistant silently disappear.
- A human-readable recovery message is sent when audio cannot be transcribed after the full retry/fallback policy is exhausted.
- Every stage is observable using one correlation path from inbound webhook to outbound reply.

This design intentionally reuses the existing PostgreSQL-backed `PersistentJob` infrastructure. RabbitMQ, Kafka, and other new brokers are not introduced in this phase.

## 2. Scope

### In scope

1. Meta WhatsApp webhook ingestion for text and audio.
2. Durable enqueue of inbound work using `PersistentJob`.
3. Asynchronous worker processing under the correct tenant context.
4. Idempotency based on Meta `wamid` plus tenant identity.
5. Dedicated transcription abstraction with ordered provider fallback.
6. Retry classification for HTTP 429, 5xx, timeouts, malformed media, and permanent configuration errors.
7. Provider circuit-breaker behavior.
8. User-facing recovery when audio cannot be processed.
9. Correlation IDs and timing metrics across webhook, queue, transcription, AI, tools, and outbound dispatch.
10. Production certification scenarios for text, audio, duplicate delivery, provider failure, and booking creation.

### Out of scope

- Payment-provider redesign.
- New sales modules.
- RabbitMQ/Kafka adoption.
- Replacing the existing booking two-phase confirmation model.
- Replacing the existing durable outbound WhatsApp job flow.
- Voice-call architecture changes unrelated to shared observability or idempotency helpers.

## 3. Existing foundation to preserve

RecepVoz already has the pieces needed for a durable asynchronous design:

- `PersistentJob`
- `PersistentJobStore`
- `PersistentJobService`
- `PersistentJobWorker`
- handler registry and typed handlers
- bounded retry/backoff
- lease ownership
- dead-letter state
- tenant-scoped execution
- unique idempotency keys
- durable outbound WhatsApp reply jobs

The implementation must extend this infrastructure instead of building a parallel queue.

## 4. Architectural decisions

### 4.1 Webhook becomes an ingestion boundary only

`POST /webhooks/v1/meta/whatsapp` must perform only work required to safely accept the event:

1. validate Meta signature;
2. parse payload;
3. resolve tenant route;
4. persist or enqueue the inbound unit of work idempotently;
5. process delivery-status callbacks that are already lightweight, or enqueue them if later evidence shows they block materially;
6. return HTTP 200.

The webhook must not call Gemini, OpenAI, a speech-to-text provider, booking tools, or outbound Meta send APIs synchronously.

Target webhook response time under normal database conditions: less than 750 ms p95.

### 4.2 New durable job types

Add two job types:

- `WHATSAPP_INBOUND_TEXT_PROCESS`
- `WHATSAPP_INBOUND_AUDIO_PROCESS`

The idempotency key must be deterministic:

- text: `wa-in-text:{safeWamid}`
- audio: `wa-in-audio:{safeWamid}`

Uniqueness remains tenant-scoped through the existing `(business_id, idempotency_key)` constraint.

A duplicate Meta delivery therefore resolves to the same durable job and cannot create a second processing path.

### 4.3 Job payloads

Text job payload:

```json
{
  "messageId": "wamid...",
  "phoneNumberId": "uuid",
  "from": "+569...",
  "text": "...",
  "correlationId": "uuid"
}
```

Audio job payload:

```json
{
  "messageId": "wamid...",
  "phoneNumberId": "uuid",
  "from": "+569...",
  "mediaId": "meta-media-id",
  "correlationId": "uuid"
}
```

The Meta access token is never persisted in the job payload. The worker resolves credentials at execution time using the tenant-aware credential resolver.

### 4.4 Correlation ID

Every accepted inbound message gets one correlation ID.

Rules:

- If a correlation ID is already associated with a previously accepted duplicate `wamid`, reuse it.
- The worker places it in MDC while processing.
- Logs for transcription, AI, tools, bookings, and outbound scheduling include the same correlation ID.
- Durable outbound reply jobs should include or be able to resolve the same correlation ID.

## 5. Processing flow

### 5.1 Text

```text
Meta
  -> webhook validation
  -> tenant resolution
  -> enqueue WHATSAPP_INBOUND_TEXT_PROCESS
  -> HTTP 200

Worker
  -> tenant context
  -> WhatsAppReceptionistService.handleResolved(...)
  -> AI/tools/booking
  -> persist reply
  -> existing durable outbound Meta reply job
  -> Meta send
```

### 5.2 Audio

```text
Meta
  -> webhook validation
  -> tenant resolution
  -> enqueue WHATSAPP_INBOUND_AUDIO_PROCESS
  -> HTTP 200

Worker
  -> tenant context
  -> download Meta media
  -> AudioTranscriber.transcribe(...)
  -> WhatsAppReceptionistService.handleResolved(... transcript ...)
  -> AI/tools/booking
  -> persist reply
  -> existing durable outbound Meta reply job
  -> Meta send
```

The audio worker owns retry semantics. Meta does not.

## 6. Audio transcription subsystem

### 6.1 Interface boundary

Introduce a provider-neutral interface:

```java
public interface AudioTranscriber {
    TranscriptionResult transcribe(AudioInput input);
}
```

`AudioInput` contains bytes, MIME type, language hint when available, business ID, message ID, and correlation ID.

`TranscriptionResult` contains transcript text, provider ID, model ID, provider latency, and attempt count.

`MetaWhatsAppAudioTranscriptionService` should become orchestration around media download plus `AudioTranscriber`, not a hard-coded OpenAI/Gemini implementation.

### 6.2 Provider order

Production policy:

1. dedicated speech-to-text provider;
2. secondary independent speech-to-text provider when configured;
3. Gemini transcription fallback;
4. OpenAI transcription fallback.

The first implementation may ship before both dedicated providers are connected, but the interface and routing policy must support them without modifying webhook or receptionist code.

No provider is allowed to be a single point of failure.

### 6.3 Error classification

Retryable:

- HTTP 408
- HTTP 429
- HTTP 500, 502, 503, 504
- network timeout
- temporary DNS/connectivity failure
- provider unavailable response

Permanent:

- missing tenant credential
- unsupported or corrupt media confirmed by provider
- malformed provider request caused by local validation
- invalid API credential after one provider-specific validation attempt
- missing media that Meta reports as permanently unavailable

Unknown runtime failures are retried within the durable job attempt budget and eventually dead-lettered.

### 6.4 Circuit breaker

Each transcription provider has a small in-process circuit state keyed by provider ID.

Default policy:

- open after 3 consecutive retryable failures;
- remain open for 60 seconds;
- during open state, skip that provider immediately;
- half-open with one probe after cooldown;
- close after a successful probe.

The circuit breaker optimizes latency only. Durability remains the responsibility of `PersistentJob`.

A process restart may reset circuit state. Persistent circuit state is deliberately out of scope until production volume justifies it.

### 6.5 Provider retry policy within one job attempt

Within one durable job execution:

- a provider may receive at most 2 immediate calls;
- only retryable failures get a second immediate call;
- wait approximately 250-500 ms with jitter before that retry;
- after the second failure, move to the next provider;
- do not repeatedly hammer a provider that returned 429.

If all providers fail retryably, the job throws `RetryableJobException` so the existing durable backoff schedules another attempt.

## 7. Failure behavior and user recovery

### 7.1 No silent failure

An audio message must never simply disappear after being acknowledged by the webhook.

If the durable audio job reaches its final attempt without a transcript, schedule exactly one recovery reply to the customer:

> No pude escuchar bien ese audio en este momento. ¿Puedes escribir el mensaje o enviarlo nuevamente?

This recovery message is itself sent through the existing durable outbound WhatsApp mechanism.

### 7.2 Terminal audio failure semantics

The current job service marks permanent or exhausted failures as `DEAD_LETTER`. The audio handler must preserve that terminal state while guaranteeing that recovery messaging survives the handler failure.

Concrete design:

1. while `attemptCount < maxAttempts`, an all-provider retryable failure throws `RetryableJobException`;
2. on the final allowed attempt, the handler calls a dedicated audio-recovery service;
3. that recovery service persists/schedules the outbound recovery reply in an independent `REQUIRES_NEW` transaction using an idempotency key `wa-audio-recovery:{safeWamid}`;
4. after that transaction commits, the audio handler throws `PermanentJobException` with a sanitized terminal error;
5. `PersistentJobService` marks the inbound audio job `DEAD_LETTER`;
6. if the final worker execution is repeated because of a lease race or process crash, the recovery idempotency key returns the existing recovery job and cannot create a duplicate message.

This makes the state explicit: the inbound audio job truthfully records terminal failure, while the customer still receives exactly one durable recovery message.

## 8. Idempotency and consistency

### 8.1 Inbound event

The Meta `wamid` is the canonical external idempotency identifier for a WhatsApp message.

The system must guarantee:

- one durable inbound job per tenant + `wamid`;
- one persisted inbound `MessagingMessage` per external message ID;
- one AI processing result per inbound message;
- one booking transition for an explicitly confirmed booking operation;
- one outbound assistant reply per inbound message result.

### 8.2 Existing advisory lock

`WhatsAppReceptionistService` already uses a transaction advisory lock and external-message lookup. Preserve this as a second line of defense.

Queue idempotency prevents duplicate workers from being created. Receptionist idempotency prevents duplicate domain work if a worker is reclaimed after a lease expiry or crashes after partial progress.

### 8.3 Crash boundaries

The following cases must be safe:

- crash after job claim, before processing;
- crash after transcript, before receptionist persistence;
- crash after booking commit, before outbound reply scheduling;
- crash after outbound job creation, before provider send;
- crash after provider accepted outbound message, before local status update.

Existing leases and idempotency checks should be extended rather than bypassed.

## 9. Observability

### 9.1 Structured stage logs

Use stable event names:

- `WA_INBOUND_ACCEPTED`
- `WA_JOB_ENQUEUED`
- `WA_JOB_STARTED`
- `WA_MEDIA_DOWNLOADED`
- `WA_STT_ATTEMPT`
- `WA_STT_SUCCESS`
- `WA_STT_FAILURE`
- `WA_AI_STARTED`
- `WA_AI_COMPLETED`
- `WA_TOOL_COMPLETED`
- `WA_REPLY_SCHEDULED`
- `WA_REPLY_ACCEPTED_BY_META`
- `WA_AUDIO_RECOVERY_SCHEDULED`

Every event includes business ID, safe message ID, correlation ID, job ID when applicable, provider/model where applicable, and duration.

Secrets, API keys, raw access tokens, and full audio payloads must never be logged.

### 9.2 Metrics

Add or extend Micrometer metrics for:

- webhook acceptance latency;
- queue wait duration;
- job execution duration by type/outcome;
- STT duration by provider/outcome;
- STT retry count;
- circuit-open count by provider;
- audio recovery count;
- AI duration;
- outbound provider duration;
- end-to-end inbound-to-provider-accepted duration.

## 10. Configuration

Use environment-backed properties with safe defaults.

Required controls:

- durable jobs enabled (`APP_JOBS_ENABLED=true` before production cutover);
- inbound async processing feature flag, default false until deployment verification;
- transcription provider order;
- per-provider enabled flag;
- per-provider timeout;
- circuit-breaker threshold/cooldown;
- maximum durable attempts for inbound text/audio jobs.

The rollout must allow reverting inbound processing to the current synchronous path without reverting the deployment while certification is in progress.

## 11. Migration and rollout

### Stage A: code deployed, feature disabled

- new job types and handlers exist;
- new transcription abstraction exists;
- old synchronous webhook path remains active;
- CI and integration tests green.

### Stage B: shadow-safe readiness

- verify `APP_JOBS_ENABLED=true` in production;
- verify worker polling is active;
- verify no stuck leases or unexpected dead-letter growth;
- verify outbound durable jobs continue to work.

### Stage C: enable async inbound text

- enable text only;
- certify duplicate delivery and booking idempotency;
- compare latency and correctness against previous flow.

### Stage D: enable async inbound audio

- enable audio;
- certify provider failure and recovery behavior;
- verify Meta webhook remains fast while transcription is slow or unavailable.

### Stage E: remove synchronous fallback

Only after certification passes, remove the legacy synchronous inbound execution path in a later cleanup change.

## 12. Testing strategy

### Unit tests

- deterministic idempotency keys;
- payload validation;
- provider routing order;
- 503 retry then fallback;
- 429 immediate provider switch;
- circuit opens after threshold;
- circuit skips calls while open;
- recovery reply scheduled exactly once;
- permanent errors dead-letter without repeated provider calls.

### Repository/integration tests

- duplicate enqueue returns same durable job;
- expired lease can be reclaimed safely;
- tenant isolation is preserved;
- text job calls receptionist once;
- audio job calls receptionist only after successful transcript;
- duplicate `wamid` cannot create duplicate booking;
- outbound reply remains idempotent after worker retry.

### Webhook tests

- valid text event returns 200 after enqueue without invoking AI;
- valid audio event returns 200 after enqueue without invoking STT;
- duplicate Meta event returns 200 and does not create a second job;
- invalid signature returns 403;
- unresolved tenant is logged and acknowledged according to current integration policy without domain side effects.

### Production certification

Before declaring the architecture complete:

- 20 consecutive real text booking flows succeed;
- 20 consecutive real audio booking flows succeed when at least one STT provider is healthy;
- repeated delivery of the same real/simulated Meta message causes one domain result;
- forced primary STT failure successfully uses fallback;
- forced all-STT failure sends one recovery message;
- OpenAI 429 does not block successful handling by another provider;
- Gemini 503 does not cause Meta webhook retries;
- no duplicate bookings are created during certification.

## 13. Success criteria

The change is complete only when all of the following are true:

1. Meta webhook p95 is below 750 ms for accepted text/audio events under normal DB conditions.
2. No AI or STT HTTP call occurs on the webhook request thread.
3. A duplicate `wamid` cannot create duplicate domain effects.
4. Audio provider outages are retried durably without relying on Meta redelivery.
5. An exhausted audio job produces one user-facing recovery message.
6. Provider 429/503 errors do not produce webhook 5xx responses.
7. Operators can trace one message from webhook to outbound delivery using one correlation ID.
8. Production certification scenarios pass without manual intervention between messages.

## 14. Security and tenant isolation

- Job payloads contain no access tokens or provider API keys.
- Provider credentials are resolved at execution time.
- Jobs execute inside the existing tenant database context.
- `businessId` is authoritative for job scope.
- `phoneNumberId` must belong to the same tenant before processing.
- Logs use safe/sanitized message identifiers and never dump audio bytes.

## 15. Implementation boundaries

To keep this change reviewable, implementation should be split into small PRs behind feature flags. The expected sequence is:

1. inbound job types + enqueue service + tests;
2. async text handler + webhook cutover flag;
3. `AudioTranscriber` abstraction + current Gemini/OpenAI adapters;
4. async audio handler + recovery behavior;
5. circuit breaker + provider routing;
6. observability/metrics;
7. production rollout and certification;
8. legacy synchronous-path cleanup after certification.

No PR should combine unrelated payments, frontend redesign, or new commercial modules with this reliability work.
