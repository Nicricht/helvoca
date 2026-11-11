package cl.helvoca.ai.gemini;

import cl.helvoca.ai.deepgram.DeepgramVoiceProperties;
import cl.helvoca.ai.deepgram.DeepgramVoiceProtocol;
import cl.helvoca.ai.deepgram.EconomicVoiceFactsService;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceEconomyIntentRouter;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composed voice session:
 * - Deepgram Nova-3 transcribes Twilio's native 8 kHz mu-law stream.
 * - Exact public facts are read from the authenticated tenant database and
 *   spoken with Aura-2 without invoking a generative model.
 * - Complex turns are sent to Gemini as text only; Gemini returns text to the
 *   same Deepgram TTS voice, preserving one audible voice throughout the call.
 * - If Deepgram STT fails, Gemini receives the raw audio in text-output mode.
 *
 * All Deepgram requests opt out of model improvement. The provider is disabled
 * unless credentials AND an explicit data-processing approval are configured.
 */
final class DeepgramHybridVoiceSession implements VoiceAiSession, WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(DeepgramHybridVoiceSession.class);
    private static final int TWILIO_MULAW_FRAME_BYTES = 160; // 20 ms at 8 kHz
    private static final int MAX_PENDING_AUDIO_FRAMES = 250;
    private static final int MAX_RECENT_AUDIO_FRAMES = 250;
    private static final int MAX_PENDING_STT_MESSAGES = 600;
    private static final ScheduledExecutorService KEEPALIVE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "deepgram-voice-keepalive");
        thread.setDaemon(true);
        return thread;
    });

    private final RealtimeCallContext context;
    private final VoiceTransportSession transport;
    private final DeepgramVoiceProperties properties;
    private final GeminiLiveVoiceProvider gemini;
    private final RealtimeToolService tools;
    private final EconomicVoiceFactsService facts;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final CallLifecycleService lifecycle;
    private final VoiceProviderHealthRegistry health;
    private final TenantDatabaseContext databaseContext;
    private final java.net.http.HttpClient http;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean greetingStarted = new AtomicBoolean(false);
    private final AtomicBoolean summaryFinalized = new AtomicBoolean(false);
    private final AtomicBoolean deepgramSttFailed = new AtomicBoolean(false);
    private final AtomicLong ttsGeneration = new AtomicLong();
    private final Queue<byte[]> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Object recentAudioLock = new Object();
    private final ArrayDeque<String> recentAudio = new ArrayDeque<>();
    private final Object utteranceLock = new Object();
    private final StringBuilder utterance = new StringBuilder();
    private final Object sttSendLock = new Object();

    private volatile WebSocket sttSocket;
    private volatile CompletableFuture<Void> sttSendChain = CompletableFuture.completedFuture(null);
    private final AtomicInteger pendingSttMessages = new AtomicInteger();
    private volatile ScheduledFuture<?> keepaliveTask;
    private volatile CompletableFuture<HttpResponse<byte[]>> currentTtsRequest;
    private volatile GeminiLiveVoiceSession geminiTextSession;
    private volatile GeminiLiveVoiceSession geminiAudioSession;
    private volatile List<String> lastUtteranceAudio = List.of();
    private final StringBuilder inboundJson = new StringBuilder();

    DeepgramHybridVoiceSession(RealtimeCallContext context,
                               VoiceTransportSession transport,
                               DeepgramVoiceProperties properties,
                               GeminiLiveVoiceProvider gemini,
                               RealtimeToolService tools,
                               EconomicVoiceFactsService facts,
                               CallTranscriptService transcripts,
                               CallSummaryService summaries,
                               CallLifecycleService lifecycle,
                               VoiceProviderHealthRegistry health,
                               TenantDatabaseContext databaseContext,
                               java.net.http.HttpClient http) {
        if (context == null || context.businessId() == null) {
            throw new IllegalArgumentException("Realtime call context requires businessId");
        }
        this.context = context;
        this.transport = transport;
        this.properties = properties;
        this.gemini = gemini;
        this.tools = tools;
        this.facts = facts;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
        this.health = health;
        this.databaseContext = databaseContext;
        this.http = http;
    }

    @Override
    public void start() {
        if (!properties.ready()) {
            throw new IllegalStateException("Deepgram voice is not configured or data-processing-approved");
        }
        URI uri = DeepgramVoiceProtocol.sttUri(properties);
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .header("Authorization", "Token " + properties.getApiKey())
                .buildAsync(uri, this)
                .whenComplete((socket, error) -> {
                    if (error != null && !closed.get()) {
                        failToGeminiTextAudio("deepgram_connect_failed");
                    }
                });
    }

    @Override
    public void acceptInboundAudio(String base64Audio) {
        if (closed.get() || base64Audio == null || base64Audio.isBlank()) return;
        if (geminiAudioSession != null) {
            geminiAudioSession.acceptInboundAudio(base64Audio);
            return;
        }
        if (deepgramSttFailed.get()) {
            ensureGeminiTextSession(null);
            GeminiLiveVoiceSession fallback = geminiTextSession;
            if (fallback != null) fallback.acceptInboundAudio(base64Audio);
            return;
        }

        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(base64Audio);
        } catch (IllegalArgumentException invalid) {
            log.warn("Dropping malformed Twilio mu-law frame call={}", context.callId());
            return;
        }
        if (audio.length == 0 || audio.length > 4096) return;
        rememberAudio(base64Audio);

        WebSocket socket = sttSocket;
        if (socket == null) {
            if (pendingAudio.size() >= MAX_PENDING_AUDIO_FRAMES) {
                failToGeminiTextAudio("deepgram_startup_audio_backlog");
                return;
            }
            pendingAudio.offer(audio);
            return;
        }
        sendSttAudio(socket, audio);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        sttSocket = webSocket;
        if (closed.get() || deepgramSttFailed.get()) {
            closeStt(webSocket);
            return;
        }
        health.success(DeepgramHybridVoiceProvider.ID);
        webSocket.request(1);
        keepaliveTask = KEEPALIVE.scheduleAtFixedRate(
                this::sendKeepAlive, 5, 5, TimeUnit.SECONDS);
        databaseContext.runAsTenant(context.businessId(),
                () -> lifecycle.markAiSetupCompleted(context.callId()));

        byte[] frame;
        while ((frame = pendingAudio.poll()) != null) sendSttAudio(webSocket, frame);
        sendGreeting();
        log.info("DEEPGRAM_HYBRID_STT_READY call={} model={} tts_model={} retention=OPT_OUT",
                context.callId(), properties.getSttModel(), properties.getTtsModel());
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            String payload;
            synchronized (inboundJson) {
                inboundJson.append(data);
                if (!last) return null;
                payload = inboundJson.toString();
                inboundJson.setLength(0);
            }
            handleSttMessage(payload);
        } catch (Exception failure) {
            failToGeminiTextAudio("deepgram_invalid_event");
        } finally {
            webSocket.request(1);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (!closed.get() && !deepgramSttFailed.get()) {
            failToGeminiTextAudio("deepgram_socket_closed");
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (!closed.get() && !deepgramSttFailed.get()) {
            failToGeminiTextAudio("deepgram_socket_error");
        }
    }

    private void handleSttMessage(String payload) {
        JSONObject event = new JSONObject(payload);
        String type = event.optString("type", "");
        if ("SpeechStarted".equals(type)) {
            onCallerBargeIn();
            return;
        }
        if ("UtteranceEnd".equals(type)) {
            dispatchFinalTranscript();
            return;
        }
        if ("Error".equals(type)) {
            failToGeminiTextAudio("deepgram_provider_error");
            return;
        }
        if (!"Results".equals(type)) return;

        // Deepgram Results uses channel.alternatives, not a flat transcript.
        JSONObject results = event.optJSONObject("channel");
        JSONArray alternatives = results == null ? null : results.optJSONArray("alternatives");
        JSONObject first = alternatives == null || alternatives.isEmpty()
                ? null : alternatives.optJSONObject(0);
        String transcript = first == null ? "" : first.optString("transcript", "").trim();
        boolean isFinal = event.optBoolean("is_final", false);
        boolean speechFinal = event.optBoolean("speech_final", false);
        if (isFinal && !transcript.isBlank()) {
            synchronized (utteranceLock) {
                if (!utterance.isEmpty()
                        && !Character.isWhitespace(utterance.charAt(utterance.length() - 1))) {
                    utterance.append(' ');
                }
                utterance.append(transcript);
            }
        }
        if (speechFinal) dispatchFinalTranscript();
    }

    private void dispatchFinalTranscript() {
        String text;
        synchronized (utteranceLock) {
            text = utterance.toString().trim();
            utterance.setLength(0);
        }
        if (text.isBlank() || closed.get()) return;
        lastUtteranceAudio = takeRecentAudio();

        GeminiLiveVoiceSession textFallback = geminiTextSession;
        if (textFallback != null) {
            textFallback.acceptInboundText(text);
            return;
        }

        VoiceEconomyIntentRouter.Decision decision = VoiceEconomyIntentRouter.classify(text);
        if (decision.cheaperPathCandidate()) {
            java.util.Optional<String> response = facts.answer(context, text);
            if (response.isPresent()) {
                String answer = response.get();
                speak(answer).whenComplete((spoken, failure) -> {
                    if (Boolean.TRUE.equals(spoken) && failure == null) {
                        persistSimpleTurn(text, answer);
                        log.info("VOICE_ECONOMY_ROUTE call={} path=DEEPGRAM_RULES reason={} mode=PUBLIC_FACT_ONLY",
                                context.callId(), decision.reason());
                    } else {
                        log.warn("DEEPGRAM_TTS_SIMPLE_FAILED call={} reason=PROVIDER_UNAVAILABLE",
                                context.callId());
                        startGeminiAudioFallback(lastUtteranceAudio, false);
                    }
                });
                return;
            }
        }
        ensureGeminiTextSession(text);
    }

    private void ensureGeminiTextSession(String firstTurn) {
        if (closed.get() || geminiAudioSession != null) return;
        synchronized (this) {
            if (geminiTextSession == null) {
                geminiTextSession = greetingStarted.get()
                        ? gemini.createTextContinuationSession(context, transport, this::speakGeminiText)
                        : gemini.createTextWelcomeSession(context, transport, this::speakGeminiText);
                geminiTextSession.start();
                log.info("VOICE_ECONOMY_ROUTE call={} path=GEMINI_TEXT mode=DEEPGRAM_STT_TTS",
                        context.callId());
            }
        }
        if (firstTurn != null && !firstTurn.isBlank()) {
            geminiTextSession.acceptInboundText(firstTurn);
        }
    }

    private void speakGeminiText(String text) {
        speak(text).whenComplete((spoken, failure) -> {
            if (!Boolean.TRUE.equals(spoken) || failure != null) {
                // The model may already have executed a read or mutation tool.
                // Never replay the same caller audio into a second model session.
                log.error("DEEPGRAM_TTS_FALLBACK_UNAVAILABLE call={} action=SAFE_CALL_CLOSE",
                        context.callId());
                transport.closeOnUpstreamFailure();
            }
        });
    }

    private void sendGreeting() {
        if (!greetingStarted.compareAndSet(false, true)) return;
        String businessName = facts.businessName(context).orElse("nuestro negocio");
        String fallback = "Hola, gracias por llamar a " + businessName
                + ". Ya, cuéntame, ¿en qué te puedo ayudar?";
        String greeting = tools.agentGreeting(context, fallback);
        speak(greeting).whenComplete((spoken, failure) -> {
            if (Boolean.TRUE.equals(spoken) && failure == null) {
                persistTranscript("ASSISTANT", greeting);
            } else {
                log.warn("DEEPGRAM_GREETING_TTS_FAILED call={} action=GEMINI_AUDIO_FALLBACK",
                        context.callId());
                startGeminiAudioFallback(takeRecentAudio(), true);
            }
        });
    }

    private CompletableFuture<Boolean> speak(String text) {
        if (closed.get() || !transport.isOpen() || text == null || text.isBlank()
                || text.length() > properties.getMaxTtsCharacters()) {
            return CompletableFuture.completedFuture(false);
        }
        long generation = ttsGeneration.get();
        JSONObject payload = new JSONObject().put("text", text);
        HttpRequest request = HttpRequest.newBuilder(DeepgramVoiceProtocol.ttsUri(properties))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Authorization", "Token " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        CompletableFuture<HttpResponse<byte[]>> future =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        currentTtsRequest = future;
        return future.handle((response, error) -> {
            if (error != null || response == null) return false;
            if (response.statusCode() < 200 || response.statusCode() >= 300) return false;
            byte[] audio = response.body();
            if (audio == null || audio.length == 0 || generation != ttsGeneration.get()
                    || closed.get() || !transport.isOpen()) return false;
            sendMuLawFrames(audio, generation);
            return generation == ttsGeneration.get() && !closed.get() && transport.isOpen();
        }).exceptionally(error -> false).whenComplete((ignored, error) -> {
            if (currentTtsRequest == future) currentTtsRequest = null;
        });
    }

    private void sendMuLawFrames(byte[] audio, long generation) {
        for (int offset = 0; offset < audio.length; offset += TWILIO_MULAW_FRAME_BYTES) {
            if (generation != ttsGeneration.get() || closed.get() || !transport.isOpen()) return;
            byte[] frame = new byte[TWILIO_MULAW_FRAME_BYTES];
            java.util.Arrays.fill(frame, (byte) 0xFF); // mu-law silence padding
            int count = Math.min(TWILIO_MULAW_FRAME_BYTES, audio.length - offset);
            System.arraycopy(audio, offset, frame, 0, count);
            transport.sendAudio(context.streamSid(), Base64.getEncoder().encodeToString(frame));
        }
    }

    private void onCallerBargeIn() {
        ttsGeneration.incrementAndGet();
        CompletableFuture<?> current = currentTtsRequest;
        if (current != null) current.cancel(true);
        transport.clearPlayback(context.streamSid());
    }

    private void persistSimpleTurn(String userText, String assistantText) {
        databaseContext.runAsTenant(context.businessId(), () -> {
            transcripts.append(context.callId(), "USER", userText);
            transcripts.append(context.callId(), "ASSISTANT", assistantText);
        });
    }

    private void persistTranscript(String speaker, String text) {
        databaseContext.runAsTenant(context.businessId(),
                () -> transcripts.append(context.callId(), speaker, text));
    }

    private void rememberAudio(String base64Audio) {
        synchronized (recentAudioLock) {
            recentAudio.addLast(base64Audio);
            while (recentAudio.size() > MAX_RECENT_AUDIO_FRAMES) recentAudio.removeFirst();
        }
    }

    private List<String> takeRecentAudio() {
        synchronized (recentAudioLock) {
            List<String> copy = new ArrayList<>(recentAudio);
            recentAudio.clear();
            return List.copyOf(copy);
        }
    }

    private void failToGeminiTextAudio(String reason) {
        if (closed.get() || !deepgramSttFailed.compareAndSet(false, true)) return;
        health.failure(DeepgramHybridVoiceProvider.ID,
                VoiceProviderHealthRegistry.FailureKind.UPSTREAM, reason);
        log.warn("DEEPGRAM_STT_FALLBACK call={} reason={} destination=GEMINI_TEXT_OUTPUT",
                context.callId(), reason);
        if (keepaliveTask != null) keepaliveTask.cancel(false);
        closeStt(sttSocket);
        List<String> replay = takeRecentAudio();
        pendingAudio.clear(); // replay already contains the same bounded pre-connect frames
        ensureGeminiTextSession(null);
        GeminiLiveVoiceSession fallback = geminiTextSession;
        if (fallback != null) {
            for (String frame : replay) fallback.acceptInboundAudio(frame);
        }
    }

    private void startGeminiAudioFallback(List<String> replayAudio, boolean greetingFailed) {
        if (closed.get() || geminiAudioSession != null) return;
        synchronized (this) {
            if (geminiAudioSession != null) return;
            if (geminiTextSession != null) {
                geminiTextSession.close();
                geminiTextSession = null;
            }
            deepgramSttFailed.set(true);
            if (keepaliveTask != null) keepaliveTask.cancel(false);
            closeStt(sttSocket);
            geminiAudioSession = greetingFailed
                    ? (GeminiLiveVoiceSession) gemini.createSession(context, transport)
                    : gemini.createAudioContinuationSession(context, transport);
            geminiAudioSession.start();
            if (replayAudio != null) {
                for (String frame : replayAudio) geminiAudioSession.acceptInboundAudio(frame);
            }
            pendingAudio.clear(); // replayAudio contains the same bounded pre-connect frames
        }
    }

    private void sendKeepAlive() {
        WebSocket socket = sttSocket;
        if (socket == null || closed.get() || deepgramSttFailed.get()) return;
        enqueueSttSend(socket, () -> socket.sendText("{\"type\":\"KeepAlive\"}", true));
    }

    private void sendSttAudio(WebSocket socket, byte[] audio) {
        if (closed.get() || deepgramSttFailed.get()) return;
        enqueueSttSend(socket, () -> socket.sendBinary(ByteBuffer.wrap(audio), true));
    }

    private void enqueueSttSend(WebSocket socket,
                                java.util.function.Supplier<CompletableFuture<WebSocket>> send) {
        synchronized (sttSendLock) {
            int pending = pendingSttMessages.incrementAndGet();
            if (pending > MAX_PENDING_STT_MESSAGES) {
                pendingSttMessages.decrementAndGet();
                failToGeminiTextAudio("deepgram_send_backlog");
                return;
            }
            sttSendChain = sttSendChain.handle((ignored, previousError) -> (Void) null)
                    .thenCompose(ignored -> {
                        if (closed.get() || deepgramSttFailed.get()) {
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        return send.get().thenApply(sent -> (Void) null);
                    })
                    .whenComplete((ignored, error) -> {
                        pendingSttMessages.updateAndGet(value -> Math.max(0, value - 1));
                        if (error != null && !closed.get() && !deepgramSttFailed.get()) {
                            failToGeminiTextAudio("deepgram_send_failed");
                        }
                    });
        }
    }

    private void closeStt(WebSocket socket) {
        if (socket == null) return;
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "RecepVoz session ended");
        } catch (RuntimeException ignored) {
            try { socket.abort(); } catch (RuntimeException ignoredAgain) { }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ttsGeneration.incrementAndGet();
        CompletableFuture<?> current = currentTtsRequest;
        if (current != null) current.cancel(true);
        if (keepaliveTask != null) keepaliveTask.cancel(false);
        closeStt(sttSocket);
        if (geminiTextSession != null) {
            geminiTextSession.close();
        } else if (geminiAudioSession != null) {
            geminiAudioSession.close();
        } else if (summaryFinalized.compareAndSet(false, true)) {
            try {
                databaseContext.runAsTenant(context.businessId(), () -> summaries.generate(context.callId()));
            } catch (RuntimeException failure) {
                log.warn("Could not summarize Deepgram call={}: {}", context.callId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
