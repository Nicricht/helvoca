# WhatsApp Reliability Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Meta WhatsApp text and audio processing off the webhook request thread into Helvoca's existing PostgreSQL-backed durable job engine, add provider-neutral and failure-tolerant speech-to-text routing, guarantee exactly-once recovery messaging for exhausted audio, and certify the new flow in production without duplicate bookings.

**Architecture:** Reuse `PersistentJob` as the only queue. The Meta webhook validates, resolves the tenant, enqueues a deterministic tenant-scoped job keyed by the Meta `wamid`, and returns quickly. Workers execute text or audio work inside the existing tenant context. Audio processing downloads Meta media in the worker, routes it through ordered STT providers with bounded retry and circuit breaking, then calls the existing `WhatsAppReceptionistService`. Terminal audio failure persists one recovery reply in a `REQUIRES_NEW` transaction and dispatches it through a dedicated durable Meta recovery job. Feature flags keep the synchronous path available until staged production certification succeeds.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven, JUnit 5, Mockito, PostgreSQL 16, Testcontainers, Spring JDBC/JPA transactions, Java `HttpClient`, Micrometer/Prometheus, GitHub Actions, Railway, Meta WhatsApp Cloud API.

**Spec:** `docs/superpowers/specs/2026-09-23-whatsapp-reliability-architecture-design.md`

## Global Constraints

- Never persist or log Meta, Deepgram, Gemini, OpenAI, database, JWT, or Twilio secrets.
- Do not add RabbitMQ, Kafka, Redis, or another queue in this phase.
- Preserve the existing two-phase booking confirmation contract and booking idempotency protections.
- Preserve the synchronous Meta inbound path behind feature flags until the corresponding async path passes production certification.
- A webhook may acknowledge an inbound text or audio event only after its durable job has been accepted. If async processing is enabled while durable workers are disabled, return a retryable HTTP error instead of silently accepting work that cannot run.
- Delivery-status callbacks remain synchronous in this phase because they are lightweight and already have explicit retry behavior.
- Every side-effecting worker remains safe under at-least-once execution.
- Merge only green PR heads. Deploy and certify the exact merged SHA.
- Provider integration tests must use local fake HTTP servers. CI must never send real audio, messages, or provider requests.
- Production provider secrets are configured only through Railway environment variables, never through commits or PR text.

## Review Focus

Before each PR is merged, explicitly review these failure modes and the named regression that covers them:

1. **Concurrent duplicate `wamid` delivery:** deterministic correlation ID plus `(business_id, idempotency_key)` must produce one inbound job. Covered by `MetaWhatsAppInboundJobServiceTest` and `PersistentJobStoreIntegrationTest`.
2. **Async flag enabled while jobs are disabled:** webhook must not return a false-success 200 for newly accepted inbound work. Covered by `MetaWhatsAppWebhookTenantRoutingTest`.
3. **Worker crash or retry after a booking side effect:** existing receptionist advisory lock and `external_message_id` replay behavior must prevent a second booking. Covered by `MetaWhatsAppInboundTextJobHandlerTest`, `MetaWhatsAppInboundAudioJobHandlerTest`, and the existing booking confirmation regressions.
4. **All audio providers fail on the final durable attempt:** exactly one recovery reply must survive the inbound job's terminal failure. Covered by `WhatsAppAudioRecoveryTransactionIntegrationTest` and `MetaWhatsAppInboundAudioJobHandlerTest`.
5. **Provider 429 or repeated 503:** 429 must switch provider without immediately hammering the same provider, repeated retryable errors must open the circuit, and webhook HTTP status must remain independent of STT availability. Covered by `RoutedAudioTranscriberTest` and async webhook tests.

---

### Task 0: Merge the approved architecture documentation before product-code work

**Files:**
- Verify: `docs/superpowers/specs/2026-09-23-whatsapp-reliability-architecture-design.md`
- Verify: `docs/superpowers/plans/2026-09-23-whatsapp-reliability-architecture.md`

**Interfaces:**
- Consumes: the user-approved design and implementation plan.
- Produces: a documentation-only `main` revision that every implementation PR can reference.

- [ ] **Step 1: Open a documentation-only PR**

Create a PR from `docs/whatsapp-reliability-architecture` to `main` titled:

```text
docs: define WhatsApp reliability architecture
```

The PR body must state that it changes documentation only and does not alter runtime behavior.

- [ ] **Step 2: Require the exact PR-head CI run to be green**

Check `RecepVoz CI`. Do not merge while any required job is failed, cancelled, or still running.

- [ ] **Step 3: Merge the documentation PR**

Use squash merge only after the exact PR head is green. Record the merged SHA for the first implementation branch.

---

### Task 1: Add durable inbound job primitives, deterministic keys, and safe rollout configuration

**Files:**
- Modify: `src/main/java/cl/helvoca/jobs/PersistentJob.java`
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundProperties.java`
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppJobKeys.java`
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundJobService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Create: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundJobServiceTest.java`
- Modify: `src/test/java/cl/helvoca/jobs/PersistentJobStoreIntegrationTest.java`

**Interfaces:**
- Consumes: `MetaWhatsAppTenantRoute`, `MetaWhatsAppInboundMessage`, `MetaWhatsAppInboundAudio`, `PersistentJobService`.
- Produces: `enqueueText(...)`, `enqueueAudio(...)`, deterministic `correlationId(...)`, tenant-scoped job keys, and feature/config properties.

- [ ] **Step 1: Write the failing inbound enqueue tests**

Create tests asserting all of the following:

```java
PersistentJob first = service.enqueueText(route, message);
PersistentJob replay = service.enqueueText(route, message);

assertEquals(first.id(), replay.id());
assertEquals("wa-in-text:wamid.TEST-1", capturedKey);
assertEquals(expectedCorrelationId, UUID.fromString(new JSONObject(capturedPayload)
        .getString("correlationId")));
```

Also assert audio uses `wa-in-audio:` and that the JSON payload contains `phoneNumberId`, `from`, `mediaId`, and `correlationId`, but contains no access token, API key, or credential reference.

Use two identical calls created independently to prove correlation IDs are deterministic rather than generated per delivery.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundJobServiceTest,PersistentJobStoreIntegrationTest test
```

Expected: compilation failure for the missing inbound service/job types, or failing assertions if a partial implementation already exists.

- [ ] **Step 3: Add the two inbound job types**

Extend `PersistentJob.Type` to exactly:

```java
public enum Type {
    OUTBOUND_MESSAGE_DISPATCH,
    META_WHATSAPP_AI_REPLY,
    CALENDAR_EVENT_SYNC,
    WHATSAPP_INBOUND_TEXT_PROCESS,
    WHATSAPP_INBOUND_AUDIO_PROCESS
}
```

Do not add a Flyway migration for `job_type`; `persistent_job.job_type` is already `VARCHAR(80)` without a type check constraint.

- [ ] **Step 4: Implement deterministic Meta job keys and correlation IDs**

Create `MetaWhatsAppJobKeys` with these contracts:

```java
public static String text(String wamid) {
    return "wa-in-text:" + safeWamid(wamid);
}

public static String audio(String wamid) {
    return "wa-in-audio:" + safeWamid(wamid);
}

public static String recovery(String wamid) {
    return "wa-audio-recovery:" + safeWamid(wamid);
}

public static UUID correlationId(UUID businessId, String wamid) {
    String material = "wa:" + businessId + ":" + requireWamid(wamid);
    return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
}
```

`safeWamid` must trim, replace characters outside `[A-Za-z0-9._:-]` with `_`, reject blank input, and cap the sanitized value at 120 characters.

- [ ] **Step 5: Implement inbound properties with safe defaults**

Create `MetaWhatsAppInboundProperties` with prefix `app.meta.whatsapp.inbound` and defaults:

```java
private boolean asyncTextEnabled = false;
private boolean asyncAudioEnabled = false;
private int textMaxAttempts = 5;
private int audioMaxAttempts = 5;
```

Clamp max attempts to `1..20`.

Map environment controls in `application.yml`:

```yaml
app:
  meta:
    whatsapp:
      inbound:
        async-text-enabled: ${HELVOCA_META_WHATSAPP_ASYNC_TEXT_ENABLED:false}
        async-audio-enabled: ${HELVOCA_META_WHATSAPP_ASYNC_AUDIO_ENABLED:false}
        text-max-attempts: ${HELVOCA_META_WHATSAPP_TEXT_MAX_ATTEMPTS:5}
        audio-max-attempts: ${HELVOCA_META_WHATSAPP_AUDIO_MAX_ATTEMPTS:5}
```

Add the same four variables with safe disabled defaults to `.env.example`.

- [ ] **Step 6: Implement `MetaWhatsAppInboundJobService`**

The public API is:

```java
public PersistentJob enqueueText(MetaWhatsAppTenantRoute route,
                                 MetaWhatsAppInboundMessage message)

public PersistentJob enqueueAudio(MetaWhatsAppTenantRoute route,
                                  MetaWhatsAppInboundAudio message)
```

For text, build this payload exactly:

```java
UUID correlationId = MetaWhatsAppJobKeys.correlationId(route.businessId(), message.messageId());
String payload = new JSONObject()
        .put("messageId", message.messageId())
        .put("phoneNumberId", route.phoneNumberId().toString())
        .put("from", message.from())
        .put("text", message.text())
        .put("correlationId", correlationId.toString())
        .toString();
```

For audio, replace `text` with `mediaId` and include `mimeType` when Meta supplied it. Call the six-argument `jobs.enqueue(...)` overload so text and audio use their own configured max-attempt values.

Because the correlation ID is derived from tenant plus `wamid`, two concurrent duplicate deliveries create byte-equivalent JSON payloads and the existing store can safely return the same job rather than raising a payload conflict.

- [ ] **Step 7: Extend the PostgreSQL integration regression**

In `PersistentJobStoreIntegrationTest`, enqueue the same new inbound job twice with equivalent JSON key order and assert one row exists for the tenant and key. Then enqueue the same safe `wamid` for a second business and assert that business receives a distinct job.

- [ ] **Step 8: Verify GREEN**

Run the focused Maven command from Step 2.

Expected: all focused tests pass.

- [ ] **Step 9: Commit and publish PR 1**

Commit:

```text
feat: add durable WhatsApp inbound job primitives
```

Open a PR to `main`, require full PR CI, and merge only when green.

---

### Task 2: Move text processing behind a durable worker and a reversible webhook flag

**Files:**
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundTextJobHandler.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookController.java`
- Create: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundTextJobHandlerTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookTenantRoutingTest.java`

**Interfaces:**
- Consumes: `PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS`, `WhatsAppReceptionistService.handleResolved(...)`, async-text flag, and `PersistentJobProperties.isEnabled()`.
- Produces: background text execution with synchronous fallback when the flag is false.

- [ ] **Step 1: Add failing handler and webhook-cutover tests**

Add `MetaWhatsAppInboundTextJobHandlerTest` proving a valid payload calls:

```java
receptionist.handleResolved(
        "wamid.TEXT-ASYNC",
        businessId,
        phoneNumberId,
        "56911111111",
        "Hola");
```

Add an invalid-payload test that expects `PersistentJobHandler.PermanentJobException` and verifies no receptionist interaction.

Update `MetaWhatsAppWebhookTenantRoutingTest` with an async-text case asserting:

```java
assertEquals(200, response.getStatusCode().value());
verify(inboundJobs).enqueueText(route, parsedMessage);
verifyNoInteractions(receptionist);
```

Add a separate case where async text is enabled but `app.jobs.enabled=false`; expected HTTP status is `503`, with no receptionist execution and no false-success enqueue path.

- [ ] **Step 2: Run the focused tests and confirm RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundTextJobHandlerTest,MetaWhatsAppWebhookTenantRoutingTest test
```

Expected: missing handler/async wiring failures.

- [ ] **Step 3: Implement the text job handler**

Create a component whose type is `WHATSAPP_INBOUND_TEXT_PROCESS`. Parse required payload fields strictly:

```java
JSONObject payload = new JSONObject(job.payloadJson());
String messageId = payload.getString("messageId");
UUID phoneNumberId = UUID.fromString(payload.getString("phoneNumberId"));
String from = payload.getString("from");
String text = payload.getString("text");
```

Reject blank fields and malformed UUIDs with `PermanentJobException`. Call `receptionist.handleResolved(...)` exactly once. Convert `IllegalArgumentException` to permanent failure. Convert unexpected `IllegalStateException`/runtime infrastructure failures to `RetryableJobException`; the receptionist's existing application-level fallback reply behavior remains unchanged.

- [ ] **Step 4: Add reversible async text routing to the webhook**

Inject the inbound job service, inbound properties, and `PersistentJobProperties` without removing the existing synchronous dependencies.

For each resolved text message:

```java
if (inboundProperties.isAsyncTextEnabled()) {
    if (!jobProperties.isEnabled()) {
        failed++;
        continue;
    }
    inboundJobs.enqueueText(route, message);
    processed++;
    continue;
}

databaseContext.callAsTenant(route.businessId(), () ->
        receptionist.handleResolved(
                message.messageId(),
                route.businessId(),
                route.phoneNumberId(),
                message.from(),
                message.text()));
processed++;
```

An enqueue exception means the event was not durably accepted, so preserve webhook 5xx behavior for that failure. Do not catch and convert it to 200.

- [ ] **Step 5: Preserve existing sync-path regressions**

Keep the original synchronous tests with `asyncTextEnabled=false`. They must still assert direct receptionist execution so rollback remains proven.

- [ ] **Step 6: Verify GREEN plus the existing WhatsApp receptionist suite**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundTextJobHandlerTest,MetaWhatsAppWebhookTenantRoutingTest,WhatsAppReceptionistServiceTest test
```

Expected: all focused tests pass.

- [ ] **Step 7: Commit and publish PR 2**

Commit:

```text
feat: process Meta WhatsApp text through durable jobs
```

Merge only after full PR CI is green. Leave the production async-text flag false after deployment.

---

### Task 3: Separate audio media download from provider-neutral transcription

**Files:**
- Create: `src/main/java/cl/helvoca/messaging/audio/AudioInput.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/TranscriptionResult.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/AudioTranscriber.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/AudioTranscriptionProvider.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/AudioTranscriptionException.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/GeminiAudioTranscriptionProvider.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/OpenAiAudioTranscriptionProvider.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/RoutedAudioTranscriber.java`
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppAudioMediaService.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppAudioTranscriptionService.java`
- Create: `src/test/java/cl/helvoca/messaging/audio/RoutedAudioTranscriberTest.java`
- Create: `src/test/java/cl/helvoca/messaging/audio/GeminiAudioTranscriptionProviderTest.java`
- Create: `src/test/java/cl/helvoca/messaging/audio/OpenAiAudioTranscriptionProviderTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppAudioTranscriptionServiceTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppAudioPrimaryPreferenceTest.java`

**Interfaces:**
- Consumes: raw audio bytes and metadata, existing Gemini/OpenAI configuration, tenant-aware Meta credentials.
- Produces: provider-neutral `AudioTranscriber.transcribe(AudioInput)` and a Meta media download service that contains no STT logic.

- [ ] **Step 1: Write the provider-neutral contracts and failing tests first**

The exact records/interfaces are:

```java
public record AudioInput(
        byte[] bytes,
        String mimeType,
        String languageHint,
        UUID businessId,
        String messageId,
        String correlationId) {}

public record TranscriptionResult(
        String text,
        String providerId,
        String modelId,
        Duration providerLatency,
        int attemptCount) {}

public interface AudioTranscriber {
    TranscriptionResult transcribe(AudioInput input);
}

public interface AudioTranscriptionProvider {
    String id();
    boolean configured();
    TranscriptionResult transcribe(AudioInput input);
}
```

`AudioTranscriptionException` must expose a stable `code`, `providerId`, `httpStatus` when known, and `retryable` boolean without storing raw provider bodies or secrets.

Test that the routed transcriber skips unconfigured providers, selects providers in configured order, and rejects an empty/blank transcript.

- [ ] **Step 2: Run the new audio tests and confirm RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=RoutedAudioTranscriberTest,GeminiAudioTranscriptionProviderTest,OpenAiAudioTranscriptionProviderTest,MetaWhatsAppAudioTranscriptionServiceTest,MetaWhatsAppAudioPrimaryPreferenceTest test
```

Expected: compilation failures for the new abstraction.

- [ ] **Step 3: Extract Meta media download**

Create `MetaWhatsAppAudioMediaService` that does only:

```java
public DownloadedAudio download(UUID businessId, String mediaId) {
    String token = accessTokens.resolve(businessId)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new AudioMediaException(
                    "META_CREDENTIAL_MISSING", false));
    MetaWhatsAppCloudClient.DownloadedMedia media = meta.downloadMedia(mediaId, token);
    return new DownloadedAudio(media.bytes(), normalizeMime(media.contentType()));
}
```

Translate `MetaWhatsAppApiException.retryable()` into a typed media exception so the later durable handler can distinguish retryable from permanent failures.

- [ ] **Step 4: Extract Gemini and OpenAI provider adapters**

Move provider-specific request building/parsing from `MetaWhatsAppAudioTranscriptionService` into `GeminiAudioTranscriptionProvider` and `OpenAiAudioTranscriptionProvider`.

Each adapter must:

- use Java `HttpClient`;
- validate configured HTTPS endpoint and API key before request creation;
- send only the required audio/request payload;
- return a nonblank `TranscriptionResult`;
- map 408, 429, and 5xx to retryable `AudioTranscriptionException`;
- map 400/401/403/404 and malformed successful responses to permanent provider failure unless the response explicitly represents transient unavailability;
- never include a provider response body or key in the exception message.

- [ ] **Step 5: Implement the first ordered `RoutedAudioTranscriber`**

At this task boundary, routing is one call per configured provider in order. Circuit breaking and immediate retry are added in Task 4. If every configured provider fails, throw one aggregate `AudioTranscriptionException` whose retryable flag is true if at least one failure was retryable and no provider succeeded.

- [ ] **Step 6: Reduce `MetaWhatsAppAudioTranscriptionService` to compatibility orchestration**

Keep its existing public `transcribe(UUID businessId, String mediaId)` method while synchronous rollback exists, but implement it through:

```java
DownloadedAudio media = mediaService.download(businessId, mediaId);
String correlationId = Optional.ofNullable(MDC.get("correlationId"))
        .filter(value -> !value.isBlank())
        .orElseGet(() -> MetaWhatsAppJobKeys.correlationId(businessId, mediaId).toString());
return transcriber.transcribe(new AudioInput(
        media.bytes(),
        media.mimeType(),
        "",
        businessId,
        mediaId,
        correlationId)).text();
```

This preserves rollback behavior without keeping provider logic in the Meta service.

- [ ] **Step 7: Verify GREEN and legacy behavior**

Run the focused Maven command from Step 2. The existing Gemini-primary and OpenAI/Gemini fallback tests must continue passing through the new abstraction.

- [ ] **Step 8: Commit and publish PR 3**

Commit:

```text
refactor: isolate WhatsApp audio transcription providers
```

Merge only after full PR CI is green.

---

### Task 4: Add a dedicated STT provider, bounded retry routing, and circuit breaking

**Files:**
- Create: `src/main/java/cl/helvoca/messaging/audio/WhatsAppAudioTranscriptionProperties.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/DeepgramAudioTranscriptionProvider.java`
- Create: `src/main/java/cl/helvoca/messaging/audio/TranscriptionCircuitBreaker.java`
- Modify: `src/main/java/cl/helvoca/messaging/audio/RoutedAudioTranscriber.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Create: `src/test/java/cl/helvoca/messaging/audio/DeepgramAudioTranscriptionProviderTest.java`
- Create: `src/test/java/cl/helvoca/messaging/audio/TranscriptionCircuitBreakerTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/audio/RoutedAudioTranscriberTest.java`

**Interfaces:**
- Consumes: configured provider order and provider health, Deepgram prerecorded STT HTTP API, existing Gemini/OpenAI adapters.
- Produces: ordered STT routing `deepgram -> gemini -> openai` by default, bounded same-provider retry, 429 fast-switch, and in-memory circuit state.

- [ ] **Step 1: Add RED routing and circuit tests**

Cover these exact scenarios:

```text
503 -> one jittered retry -> same provider succeeds
503 -> retry fails -> next configured provider succeeds
429 -> no same-provider immediate retry -> next provider succeeds
3 consecutive retryable failures -> circuit OPEN
OPEN circuit -> provider is not invoked
cooldown elapsed -> one half-open probe
successful half-open probe -> circuit CLOSED
```

Use a package-private constructor accepting `Clock`, a no-op sleeper, and a deterministic jitter supplier so unit tests never sleep.

- [ ] **Step 2: Add RED Deepgram adapter tests with a local HTTP server**

For a successful response, return JSON containing:

```json
{
  "results": {
    "channels": [{
      "alternatives": [{"transcript": "Quiero reservar mañana"}]
    }]
  }
}
```

Assert the request uses:

```text
POST /v1/listen?model=nova-3&smart_format=true&language=multi
Authorization: Token test-key
Content-Type: audio/ogg
```

Add 429, 503, 401, empty transcript, and timeout regressions.

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=DeepgramAudioTranscriptionProviderTest,TranscriptionCircuitBreakerTest,RoutedAudioTranscriberTest test
```

Expected: missing provider/circuit/config classes or failing resilience assertions.

- [ ] **Step 4: Implement `WhatsAppAudioTranscriptionProperties`**

Use prefix `app.whatsapp.audio-transcription` with defaults:

```java
private List<String> providerOrder = List.of("deepgram", "gemini", "openai");
private int immediateRetryMinMillis = 250;
private int immediateRetryMaxMillis = 500;
private int circuitFailureThreshold = 3;
private int circuitCooldownSeconds = 60;
private boolean deepgramEnabled = false;
private String deepgramApiKey = "";
private String deepgramEndpoint = "https://api.deepgram.com/v1/listen";
private String deepgramModel = "nova-3";
private String deepgramLanguage = "multi";
private int deepgramTimeoutSeconds = 15;
```

Map the following environment variables in `application.yml` and document them in `.env.example`:

```text
HELVOCA_AUDIO_TRANSCRIPTION_PROVIDER_ORDER=deepgram,gemini,openai
HELVOCA_AUDIO_TRANSCRIPTION_RETRY_MIN_MS=250
HELVOCA_AUDIO_TRANSCRIPTION_RETRY_MAX_MS=500
HELVOCA_AUDIO_TRANSCRIPTION_CIRCUIT_FAILURE_THRESHOLD=3
HELVOCA_AUDIO_TRANSCRIPTION_CIRCUIT_COOLDOWN_SECONDS=60
DEEPGRAM_TRANSCRIPTION_ENABLED=false
DEEPGRAM_API_KEY=
DEEPGRAM_TRANSCRIPTION_ENDPOINT=https://api.deepgram.com/v1/listen
DEEPGRAM_TRANSCRIPTION_MODEL=nova-3
DEEPGRAM_TRANSCRIPTION_LANGUAGE=multi
DEEPGRAM_TRANSCRIPTION_TIMEOUT_SECONDS=15
```

- [ ] **Step 5: Implement the Deepgram adapter without adding an SDK dependency**

Build the URI using the configured endpoint and encoded query parameters. Send the original audio bytes as the request body. Parse `results.channels[0].alternatives[0].transcript` and reject blank text.

Provider ID must be exactly `deepgram`.

- [ ] **Step 6: Implement the circuit breaker**

Use a `ConcurrentHashMap<String, State>` keyed only by controlled provider IDs. The default policy is:

```text
CLOSED -> open after 3 consecutive retryable failures
OPEN -> skip for 60 seconds
OPEN after cooldown -> HALF_OPEN allowing one probe
HALF_OPEN success -> CLOSED and zero failures
HALF_OPEN retryable failure -> OPEN with a new cooldown
```

A permanent provider failure must not increment the transient outage counter.

- [ ] **Step 7: Upgrade `RoutedAudioTranscriber` resilience**

For each configured provider:

1. skip when not configured;
2. skip when circuit is open;
3. call once;
4. on HTTP 429, record retryable failure and move immediately to the next provider;
5. on another retryable failure, wait one jittered 250-500 ms delay and call the same provider once more;
6. after the second retryable failure, move to the next provider;
7. on permanent provider failure, move to the next provider without retry;
8. on success, close/reset the provider circuit and return the result.

If no provider succeeds, throw a sanitized aggregate exception. Do not concatenate raw provider messages.

- [ ] **Step 8: Verify GREEN**

Run the focused Maven command from Step 3, then:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppAudioTranscriptionServiceTest,MetaWhatsAppAudioPrimaryPreferenceTest test
```

Expected: all tests pass and no real provider network request occurs.

- [ ] **Step 9: Commit and publish PR 4**

Commit:

```text
feat: add resilient dedicated WhatsApp speech routing
```

Merge only after full PR CI is green. Keep `DEEPGRAM_TRANSCRIPTION_ENABLED=false` in production until a secret is securely configured.

---

### Task 5: Build exactly-once durable audio recovery that survives terminal job failure

**Files:**
- Modify: `src/main/java/cl/helvoca/jobs/PersistentJob.java`
- Modify: `src/main/java/cl/helvoca/messaging/WhatsAppReceptionistService.java`
- Create: `src/main/java/cl/helvoca/messaging/meta/WhatsAppAudioRecoveryService.java`
- Create: `src/main/java/cl/helvoca/messaging/outbound/WhatsAppRecoveryReplyDeliveryService.java`
- Create: `src/main/java/cl/helvoca/messaging/outbound/MetaWhatsAppPersistedReplySender.java`
- Modify: `src/main/java/cl/helvoca/messaging/outbound/MetaWhatsAppAssistantReplyJobHandler.java`
- Create: `src/main/java/cl/helvoca/messaging/outbound/MetaWhatsAppRecoveryReplyJobHandler.java`
- Create: `src/test/java/cl/helvoca/messaging/meta/WhatsAppAudioRecoveryServiceTest.java`
- Create: `src/test/java/cl/helvoca/messaging/meta/WhatsAppAudioRecoveryTransactionIntegrationTest.java`
- Create: `src/test/java/cl/helvoca/messaging/outbound/MetaWhatsAppRecoveryReplyJobHandlerTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/outbound/WhatsAppAssistantReplyDeliveryServiceTest.java`

**Interfaces:**
- Consumes: tenant/business/phone identity, failed audio `wamid`, sender, existing conversation/message repositories, existing Meta provider.
- Produces: one persisted recovery reply plus one `META_WHATSAPP_RECOVERY_REPLY` durable job keyed `wa-audio-recovery:{safeWamid}` in a transaction that survives inbound job failure.

- [ ] **Step 1: Add the recovery job type and RED tests**

Add:

```java
META_WHATSAPP_RECOVERY_REPLY
```

to `PersistentJob.Type`.

The recovery service unit test must call recovery twice with the same `wamid` and assert the delivery enqueue key is exactly:

```text
wa-audio-recovery:wamid.AUDIO-FAIL
```

The recovery handler test must reject a normal AI key and accept only the recovery prefix matching the persisted source message's external ID.

- [ ] **Step 2: Add the rollback-survival integration test and confirm RED**

Create `WhatsAppAudioRecoveryTransactionIntegrationTest` with PostgreSQL Testcontainers. Start an outer transaction, call the recovery service, then deliberately throw to roll back the caller. In a new transaction, assert both the persisted recovery source/reply state and the recovery durable job still exist.

Run:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=WhatsAppAudioRecoveryServiceTest,WhatsAppAudioRecoveryTransactionIntegrationTest,MetaWhatsAppRecoveryReplyJobHandlerTest test
```

Expected before implementation: missing classes or rollback-survival assertion failure.

- [ ] **Step 3: Add a no-AI system reply persistence method to the receptionist**

Add a public method whose transaction joins the caller's current transaction:

```java
public ResolvedSystemReply recordResolvedSystemReply(
        String messageId,
        UUID businessId,
        UUID phoneNumberId,
        String rawFrom,
        String sourceContent,
        String reply,
        String failureCode,
        String replyProviderId)
```

It must reuse the existing advisory message lock, tenant phone validation, conversation lookup/creation, customer attachment, and duplicate external-message lookup. It must not invoke `MessagingAiClient` or tools.

For a new audio recovery, persist:

```text
INBOUND content: [audio no transcrito]
INBOUND failureCode: AUDIO_TRANSCRIPTION_EXHAUSTED
INBOUND replyText: No pude escuchar bien ese audio en este momento. ¿Puedes escribir el mensaje o enviarlo nuevamente?
OUTBOUND assistant content: same recovery text
```

Return the persisted inbound `messageId`, normalized recipient, and reply provider ID. If the same external message already has the same recovery reply, return the existing persisted record rather than inserting another one.

- [ ] **Step 4: Implement `WhatsAppAudioRecoveryService` with `REQUIRES_NEW`**

The method is:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recover(UUID businessId,
                    UUID phoneNumberId,
                    String wamid,
                    String from)
```

It calls `recordResolvedSystemReply(...)`, then `WhatsAppRecoveryReplyDeliveryService.schedule(...)`. Because both persistence and enqueue happen inside this independent transaction, the caller can throw afterward without erasing recovery work.

- [ ] **Step 5: Implement a dedicated recovery delivery job**

`WhatsAppRecoveryReplyDeliveryService` enqueues `META_WHATSAPP_RECOVERY_REPLY` with:

```java
String key = MetaWhatsAppJobKeys.recovery(inboundMessageId);
String payload = new JSONObject()
        .put("messageId", messageId.toString())
        .toString();
```

Do not weaken the existing `meta-ai-reply:` validation in `MetaWhatsAppAssistantReplyJobHandler`.

- [ ] **Step 6: Extract common persisted-reply sending without weakening key validation**

Create `MetaWhatsAppPersistedReplySender` that receives the already-validated job/message pair, loads the conversation, sends through `MetaWhatsAppMessagingProvider`, and persists provider message ID/status.

`MetaWhatsAppAssistantReplyJobHandler` keeps validating `meta-ai-reply:{wamid}` then delegates to the sender.

`MetaWhatsAppRecoveryReplyJobHandler` validates `wa-audio-recovery:{wamid}`, requires `failureCode=AUDIO_TRANSCRIPTION_EXHAUSTED`, then delegates to the same sender.

- [ ] **Step 7: Verify recovery is exactly once and independent of AI**

Run the focused command from Step 2 plus:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=WhatsAppReceptionistServiceTest,WhatsAppAssistantReplyDeliveryServiceTest test
```

Expected: rollback-survival passes, duplicate recovery creates no second durable recovery job, and recovery persistence performs zero AI interactions.

- [ ] **Step 8: Commit and publish PR 5**

Commit:

```text
feat: add durable WhatsApp audio recovery replies
```

Merge only after full PR CI is green.

---

### Task 6: Move audio processing behind the durable worker and keep synchronous rollback available

**Files:**
- Create: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundAudioJobHandler.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookController.java`
- Create: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundAudioJobHandlerTest.java`
- Modify: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookTenantRoutingTest.java`

**Interfaces:**
- Consumes: `WHATSAPP_INBOUND_AUDIO_PROCESS`, `MetaWhatsAppAudioMediaService`, `AudioTranscriber`, `WhatsAppReceptionistService`, `WhatsAppAudioRecoveryService`.
- Produces: durable audio execution, correct retry/permanent classification, and async-audio webhook cutover.

- [ ] **Step 1: Write RED handler tests for success, retry, permanent failure, and final exhaustion**

Success test:

```java
when(media.download(businessId, mediaId))
        .thenReturn(new DownloadedAudio(bytes, "audio/ogg"));
when(transcriber.transcribe(any()))
        .thenReturn(new TranscriptionResult(
                "Quiero reservar mañana",
                "deepgram",
                "nova-3",
                Duration.ofMillis(320),
                1));

handler.handle(job);

verify(receptionist).handleResolved(
        wamid,
        businessId,
        phoneNumberId,
        from,
        "Quiero reservar mañana");
verifyNoInteractions(recovery);
```

Retryable provider failure with `attemptCount < maxAttempts` must throw `RetryableJobException` and not schedule recovery.

Permanent media/transcription failure must schedule recovery once and throw `PermanentJobException`.

Retryable provider failure on `attemptCount == maxAttempts` must schedule recovery once and then throw `PermanentJobException` so the durable inbound job records terminal failure.

- [ ] **Step 2: Add RED webhook tests proving audio HTTP is decoupled from STT**

With async audio enabled and durable jobs enabled, assert:

```java
assertEquals(200, response.getStatusCode().value());
verify(inboundJobs).enqueueAudio(route, parsedAudio);
verifyNoInteractions(audioTranscription, receptionist);
```

With async audio enabled and durable jobs disabled, assert HTTP `503` and no STT/receptionist execution.

With the flag false, preserve the current synchronous audio regression.

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundAudioJobHandlerTest,MetaWhatsAppWebhookTenantRoutingTest test
```

- [ ] **Step 4: Implement `MetaWhatsAppInboundAudioJobHandler`**

Parse required fields strictly. Download media inside the worker, not the webhook. Build `AudioInput` with the persisted correlation ID from payload. Call the routed transcriber, then `receptionist.handleResolved(...)` only after a nonblank transcript.

Error policy:

```text
retryable && attemptCount < maxAttempts -> RetryableJobException
retryable && attemptCount >= maxAttempts -> recover(); PermanentJobException
permanent provider/media failure -> recover(); PermanentJobException
malformed durable payload -> PermanentJobException without provider call
unknown runtime failure before final attempt -> RetryableJobException
unknown runtime failure on final attempt -> recover(); PermanentJobException
```

Never send the provider's raw exception body to the customer or logs.

- [ ] **Step 5: Add async audio routing to the controller**

Mirror Task 2's text behavior using `inboundProperties.isAsyncAudioEnabled()` and `jobProperties.isEnabled()`.

When async audio is enabled and enqueue succeeds, return the normal 200 response without downloading media or invoking STT.

When the flag is false, keep the synchronous compatibility path for rollback.

- [ ] **Step 6: Verify GREEN plus audio provider regressions**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundAudioJobHandlerTest,MetaWhatsAppWebhookTenantRoutingTest,MetaWhatsAppAudioTranscriptionServiceTest,MetaWhatsAppAudioPrimaryPreferenceTest,WhatsAppAudioRecoveryServiceTest test
```

Expected: all pass.

- [ ] **Step 7: Commit and publish PR 6**

Commit:

```text
feat: process Meta WhatsApp audio through durable jobs
```

Merge only after full PR CI is green. Leave production async-audio false after deployment.

---

### Task 7: Add correlation propagation and low-cardinality operational metrics

**Files:**
- Modify: `src/main/java/cl/helvoca/jobs/PersistentJobService.java`
- Modify: `src/main/java/cl/helvoca/observability/OperationalMetrics.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookController.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundTextJobHandler.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundAudioJobHandler.java`
- Modify: `src/main/java/cl/helvoca/messaging/audio/RoutedAudioTranscriber.java`
- Modify: `src/main/java/cl/helvoca/messaging/WhatsAppReceptionistService.java`
- Modify: `src/main/java/cl/helvoca/messaging/outbound/MetaWhatsAppPersistedReplySender.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/cl/helvoca/observability/WhatsAppOperationalMetricsTest.java`
- Create: `src/test/java/cl/helvoca/jobs/PersistentJobCorrelationTest.java`

**Interfaces:**
- Consumes: `correlationId` stored in durable JSON payload and controlled provider/job outcome names.
- Produces: one MDC correlation path plus low-cardinality counters/timers from webhook acceptance through Meta outbound acceptance.

- [ ] **Step 1: Add RED correlation and metrics tests**

`PersistentJobCorrelationTest` must create a claimed job payload containing:

```json
{"correlationId":"9b6571bd-b193-3ca4-8dd7-bcfe68c70615"}
```

and assert the handler observes that exact value in `MDC.get("correlationId")`. Assert MDC is cleared/restored after execution, including exception paths.

`WhatsAppOperationalMetricsTest` uses `SimpleMeterRegistry` and verifies counters/timers are tagged only by controlled fields such as `type`, `outcome`, and provider ID. Do not tag tenant ID, `wamid`, phone, booking ID, or correlation ID.

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=PersistentJobCorrelationTest,WhatsAppOperationalMetricsTest test
```

- [ ] **Step 3: Propagate correlation in `PersistentJobService`**

Before invoking a handler, read `correlationId` from the JSON payload. Accept only a valid UUID string. Save any pre-existing MDC value, install the job correlation, and restore/remove it in `finally` alongside `jobId` and `jobType`.

Also record queue wait as `Duration.between(job.createdAt(), Instant.now())` when `createdAt` is present.

- [ ] **Step 4: Add stable stage logs**

Emit the approved event names at these boundaries:

```text
WA_INBOUND_ACCEPTED        controller after durable enqueue
WA_JOB_ENQUEUED            inbound job service
WA_JOB_STARTED             inbound handlers
WA_MEDIA_DOWNLOADED        audio media service/handler
WA_STT_ATTEMPT             routed transcriber
WA_STT_SUCCESS             routed transcriber
WA_STT_FAILURE             routed transcriber
WA_AI_STARTED              receptionist immediately before ai.respond
WA_AI_COMPLETED            receptionist immediately after ai.respond
WA_TOOL_COMPLETED          receptionist tool callback after execute
WA_REPLY_SCHEDULED         AI/recovery delivery service after durable enqueue
WA_REPLY_ACCEPTED_BY_META  persisted reply sender after Meta returns provider ID
WA_AUDIO_RECOVERY_SCHEDULED recovery service after recovery enqueue commit path
```

Each log uses safe message ID, business ID, job ID when present, correlation ID through MDC, provider/model when relevant, and elapsed milliseconds. Never log text transcripts, audio bytes, access tokens, or raw provider response bodies in these structural logs.

- [ ] **Step 5: Add the approved Micrometer metrics**

Extend `OperationalMetrics` with controlled methods for:

```text
helvoca.whatsapp.webhook.accept.duration
helvoca.whatsapp.queue.wait.duration
helvoca.whatsapp.stt.duration
helvoca.whatsapp.stt.retries
helvoca.whatsapp.stt.circuit.opens
helvoca.whatsapp.audio.recoveries
helvoca.whatsapp.ai.duration
helvoca.whatsapp.outbound.duration
helvoca.whatsapp.end_to_end.duration
```

Record webhook timing in the controller, STT timing/retries/circuit opens in the router, AI and tool timing in the receptionist, outbound timing after Meta accepts a message, and end-to-end duration from the persisted inbound message creation time to Meta provider acceptance.

- [ ] **Step 6: Enable histograms only for bounded latency timers**

Extend `management.metrics.distribution.percentiles-histogram` in `application.yml` for:

```yaml
"helvoca.whatsapp.webhook.accept.duration": true
"helvoca.whatsapp.stt.duration": true
"helvoca.whatsapp.end_to_end.duration": true
```

- [ ] **Step 7: Verify GREEN and no high-cardinality tags**

Run the focused command from Step 2 and:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppInboundTextJobHandlerTest,MetaWhatsAppInboundAudioJobHandlerTest,MetaWhatsAppWebhookTenantRoutingTest test
```

Inspect metric tags in tests and code. Any tenant/message/correlation tag fails review.

- [ ] **Step 8: Commit and publish PR 7**

Commit:

```text
obs: trace durable WhatsApp processing end to end
```

Merge only after full PR CI is green.

---

### Task 8: Run full verification, deploy disabled-by-default code, and stage the production cutover

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `.env.example`
- Verify: `src/main/resources/application.yml`
- Verify: `docs/superpowers/specs/2026-09-23-whatsapp-reliability-architecture-design.md`
- No new product code unless verification exposes a reproducible defect.

**Interfaces:**
- Consumes: all merged WhatsApp reliability PRs, Railway production environment, existing Meta sandbox/test number.
- Produces: staged evidence that async text/audio operate durably without changing unrelated product behavior.

- [ ] **Step 1: Run the complete local/CI-equivalent backend verification**

```bash
mvn --batch-mode --no-transfer-progress test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run the complete browser suite because CI treats it as part of release health**

```bash
npm install --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

Expected: all repository browser tests pass.

- [ ] **Step 3: Verify production deploys with both async flags disabled**

After the last green PR merges, require Railway `SUCCESS` for the exact merged SHA and verify startup readiness remains healthy.

Production must initially have:

```text
HELVOCA_META_WHATSAPP_ASYNC_TEXT_ENABLED=false
HELVOCA_META_WHATSAPP_ASYNC_AUDIO_ENABLED=false
```

This proves the release itself does not force a cutover.

- [ ] **Step 4: Verify durable worker readiness before accepting async work**

Read the production values/state for `APP_JOBS_ENABLED`, job queue health, active leases, and dead-letter count. Require `APP_JOBS_ENABLED=true` before either async flag is turned on.

If it is false, enable `APP_JOBS_ENABLED=true`, deploy, and prove existing durable outbound WhatsApp jobs continue to complete before continuing.

- [ ] **Step 5: Configure the dedicated STT provider securely**

Set the Deepgram secret only in Railway environment variables. Never display or copy the secret into chat, GitHub, logs, or PRs.

Enable:

```text
DEEPGRAM_TRANSCRIPTION_ENABLED=true
HELVOCA_AUDIO_TRANSCRIPTION_PROVIDER_ORDER=deepgram,gemini,openai
```

Deploy and verify startup health. A provider readiness check may report configured/not-configured and provider ID, but never the key.

- [ ] **Step 6: Enable async text only and certify it**

Set:

```text
HELVOCA_META_WHATSAPP_ASYNC_TEXT_ENABLED=true
HELVOCA_META_WHATSAPP_ASYNC_AUDIO_ENABLED=false
```

For each real inbound text certification flow verify:

```text
Meta POST -> 200
WA_INBOUND_ACCEPTED
WHATSAPP_INBOUND_TEXT_PROCESS job -> SUCCEEDED
one receptionist result
one booking transition when explicitly confirmed
one outbound durable reply
Meta provider message ID accepted
no duplicate booking
```

Complete 20 consecutive real text booking flows before advancing. If any flow fails, stop the count, diagnose the first failure, fix it under TDD in a new PR, and restart certification after deployment.

- [ ] **Step 7: Enable async audio and certify normal-provider behavior**

Set:

```text
HELVOCA_META_WHATSAPP_ASYNC_AUDIO_ENABLED=true
```

Complete 20 consecutive real audio booking flows while at least one configured STT provider is healthy. For every message require webhook HTTP 200 without waiting for STT, an eventual successful audio job, exactly one receptionist result, and no duplicate booking.

- [ ] **Step 8: Certify controlled provider failures**

Use test-only provider fakes/configuration or a controlled non-production route where possible. Prove all of the following before declaring the architecture complete:

```text
primary STT retryable failure -> fallback provider succeeds
OpenAI 429 -> another provider still succeeds
Gemini 503 -> webhook remains 200 because STT runs in worker
all STT providers unavailable -> inbound audio job DEAD_LETTER + exactly one recovery reply
duplicate Meta delivery of same wamid -> one inbound job + one domain result
```

Do not intentionally corrupt production credentials to force failures.

- [ ] **Step 9: Measure the webhook latency target**

Use the new timer and Railway HTTP logs for accepted text/audio requests. Require p95 below 750 ms under normal database conditions and confirm no Gemini/OpenAI/Deepgram request appears on the webhook correlation before HTTP completion.

- [ ] **Step 10: Record certification evidence**

Create a concise repository document under `docs/` containing:

```text
release SHA
Railway deployment ID
certification window
text passes / total
 audio passes / total
forced-failure cases and outcomes
webhook p95
observed dead-letter/recovery counts
confirmation that no duplicate bookings occurred
```

Do not include phone numbers, access tokens, customer message contents, or raw transcripts.

---

### Task 9: Remove the synchronous Meta inbound fallback only after certification passes

**Files:**
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookController.java`
- Modify: `src/test/java/cl/helvoca/messaging/meta/MetaWhatsAppWebhookTenantRoutingTest.java`
- Modify: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppInboundProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Remove only compatibility wiring from: `src/main/java/cl/helvoca/messaging/meta/MetaWhatsAppAudioTranscriptionService.java` when no other caller requires it.

**Interfaces:**
- Consumes: successful production certification from Task 8.
- Produces: one permanent webhook behavior: validate, resolve, enqueue, acknowledge.

- [ ] **Step 1: Add RED tests requiring no synchronous inbound execution path**

Update webhook tests so both text and audio always enqueue when Meta inbound is enabled and durable jobs are ready. Assert the controller has no path that invokes `receptionist.handleResolved(...)` or audio transcription directly.

- [ ] **Step 2: Run focused tests and confirm RED against compatibility code**

```bash
mvn --batch-mode --no-transfer-progress -Dtest=MetaWhatsAppWebhookTenantRoutingTest test
```

- [ ] **Step 3: Remove synchronous feature-flag branches**

Delete direct text receptionist execution and direct audio transcription from the webhook. Keep the readiness guard that refuses new inbound work if durable workers are disabled.

Remove the two async flags from configuration only after the permanent behavior is deployed and verified. Retain max-attempt and STT provider configuration.

- [ ] **Step 4: Remove compatibility-only audio wrapper code if unused**

Search all production references to `MetaWhatsAppAudioTranscriptionService`. Delete the compatibility wrapper only if no runtime caller remains. Keep `MetaWhatsAppAudioMediaService`, `AudioTranscriber`, and provider adapters.

- [ ] **Step 5: Run complete verification**

```bash
mvn --batch-mode --no-transfer-progress test
npm run test:e2e
```

Expected: all backend and browser tests pass.

- [ ] **Step 6: Commit and publish the cleanup PR**

Commit:

```text
refactor: make durable Meta WhatsApp inbound permanent
```

Merge only after full PR CI is green, deploy the exact SHA, and run one final real text and one final real audio smoke flow.

## Completion Criteria

The plan is complete only when:

- the webhook does no AI or STT network work;
- accepted text/audio events are durable before HTTP 200;
- `wamid` replay cannot duplicate jobs, replies, or bookings;
- audio provider outages retry in the durable worker rather than through Meta webhook redelivery;
- terminal audio failure records `DEAD_LETTER` and sends one recovery reply;
- Deepgram or another dedicated STT provider is configured as the first production audio provider;
- Gemini and OpenAI remain independent fallbacks rather than single points of failure;
- structured correlation and low-cardinality metrics trace the full path;
- 20 consecutive real text and 20 consecutive real audio booking certifications pass;
- forced 429/503/all-provider failure scenarios behave as specified;
- webhook p95 is below 750 ms under normal database conditions;
- the cleanup PR removes the synchronous inbound fallback only after all prior gates pass.
