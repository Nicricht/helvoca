package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opt-in Twilio PCMU 8k -> Deepgram Nova-3 multilingual -> verified catalogue
 * answer -> Aura-2 PCMU 8k. All complex turns go to the established Gemini
 * provider. Provider credentials and raw transcript are never logged.
 */
final class DeepgramEconomyVoiceSession implements VoiceAiSession, WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(DeepgramEconomyVoiceSession.class);
    private static final String STT_URL = "wss://api.deepgram.com/v1/listen"
            + "?model=nova-3&language=multi&language_hint=es"
            + "&encoding=mulaw&sample_rate=8000&channels=1"
            + "&interim_results=true&endpointing=500&punctuate=true";
    private static final String TTS_URL = "https://api.deepgram.com/v1/speak"
            + "?model=aura-2-selena-es&encoding=mulaw&sample_rate=8000&container=none";
    private static final int MAX_FRAME_BYTES = 4096;
    private static final int MAX_PENDING_FRAMES = 200;
    private static final int MAX_TTS_BYTES = 512000;
    private static final int MAX_TTS_CHARACTERS = 220;

    private final RealtimeCallContext context;
    private final VoiceTransportSession transport;
    private final DeepgramEconomyVoiceProperties properties;
    private final DeepgramEconomyPublicResponder responder;
    private final RealtimeToolService tools;
    private final GeminiLiveVoiceProvider gemini;
    private final HttpClient http;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean handedToGemini = new AtomicBoolean();
    private final AtomicBoolean responding = new AtomicBoolean();
    private final AtomicInteger sentCharacters = new AtomicInteger();
    private final Object audioLock = new Object();
    private final Deque<byte[]> pendingAudio = new ArrayDeque<>();
    private final Deque<String> replayAudio = new ArrayDeque<>();
    private final StringBuilder frame = new StringBuilder();
    private final StringBuilder finalWords = new StringBuilder();
    private volatile WebSocket socket;
    private volatile VoiceAiSession geminiSession;
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
    private int pendingSendCount;

    DeepgramEconomyVoiceSession(RealtimeCallContext context,
                                VoiceTransportSession transport,
                                DeepgramEconomyVoiceProperties properties,
                                DeepgramEconomyPublicResponder responder,
                                RealtimeToolService tools,
                                GeminiLiveVoiceProvider gemini,
                                HttpClient http) {
        this.context = context;
        this.transport = transport;
        this.properties = properties;
        this.responder = responder;
        this.tools = tools;
        this.gemini = gemini;
        this.http = http;
    }

    @Override
    public void start() {
        if (!properties.permits(context)) throw new IllegalStateException("Economy provider disabled");
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(6))
                .header("Authorization", "Token " + properties.getApiKey())
                .buildAsync(URI.create(STT_URL), this)
                .exceptionally(error -> {
                    fallback("stt_connect_failure");
                    return null;
                });
    }

    @Override
    public void acceptInboundAudio(String base64Audio) {
        if (closed.get() || base64Audio == null || base64Audio.isBlank()) return;
        VoiceAiSession active = geminiSession;
        if (active != null) { active.acceptInboundAudio(base64Audio); return; }
        if (handedToGemini.get()) return;
        byte[] bytes;
        try {
            if (base64Audio.length() > 8192) { fallback("oversized_frame"); return; }
            bytes = Base64.getDecoder().decode(base64Audio);
        } catch (IllegalArgumentException error) { fallback("invalid_frame"); return; }
        if (bytes.length == 0 || bytes.length > MAX_FRAME_BYTES) {
            fallback("invalid_frame_size"); return;
        }
        synchronized (audioLock) {
            if (replayAudio.size() >= 120) replayAudio.removeFirst();
            replayAudio.addLast(base64Audio);
            if (socket == null) {
                if (pendingAudio.size() >= MAX_PENDING_FRAMES) { fallback("stt_start_buffer_full"); return; }
                pendingAudio.addLast(bytes);
            } else {
                sendFrame(bytes);
            }
        }
    }

    private void sendFrame(byte[] bytes) {
        if (pendingSendCount >= MAX_PENDING_FRAMES) { fallback("stt_backpressure"); return; }
        WebSocket current = socket;
        if (current == null || handedToGemini.get() || closed.get()) return;
        pendingSendCount++;
        sendChain = sendChain.thenCompose(ignored ->
                current.sendBinary(ByteBuffer.wrap(bytes), true).thenApply(sent -> (Void) null))
                .whenComplete((done, error) -> {
                    synchronized (audioLock) { pendingSendCount--; }
                    if (error != null) fallback("stt_send_failure");
                });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
        if (closed.get() || handedToGemini.get()) { webSocket.abort(); return; }
        synchronized (audioLock) {
            socket = webSocket;
            while (!pendingAudio.isEmpty()) sendFrame(pendingAudio.removeFirst());
        }
        String configuredGreeting = tools.agentGreeting(context,
                "Hola, gracias por llamar. Cuéntame, ¿en qué te puedo ayudar?");
        if (configuredGreeting == null || configuredGreeting.isBlank()
                || configuredGreeting.length() > 160) {
            configuredGreeting = "Hola, gracias por llamar. ¿En qué te puedo ayudar?";
        }
        synthesize(configuredGreeting);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            synchronized (frame) {
                if (frame.length() + data.length() > 32768) {
                    frame.setLength(0);
                    fallback("oversized_stt_response");
                } else {
                    frame.append(data);
                    if (last) {
                        JSONObject json = new JSONObject(frame.toString());
                        frame.setLength(0);
                        consumeTranscript(json);
                    }
                }
            }
        } catch (RuntimeException error) {
            fallback("invalid_stt_response");
        } finally {
            webSocket.request(1);
        }
        return CompletableFuture.completedFuture(null);
    }

    void consumeTranscript(JSONObject json) {
        if (closed.get() || handedToGemini.get() || !"Results".equals(json.optString("type"))
                || !json.optBoolean("is_final")) return;
        JSONObject channel = json.optJSONObject("channel");
        JSONArray alternatives = channel == null ? null : channel.optJSONArray("alternatives");
        JSONObject first = alternatives == null ? null : alternatives.optJSONObject(0);
        String transcript = first == null ? "" : first.optString("transcript", "").trim();
        synchronized (finalWords) {
            if (!transcript.isEmpty()) {
                if (finalWords.length() > 0) finalWords.append(' ');
                finalWords.append(transcript);
            }
            if (finalWords.length() > 1024) { finalWords.setLength(0); fallback("long_utterance"); return; }
            if (json.optBoolean("speech_final") && finalWords.length() > 0) {
                String complete = finalWords.toString();
                finalWords.setLength(0);
                onUtterance(complete);
            }
        }
    }

    private void onUtterance(String transcript) {
        if (closed.get() || handedToGemini.get() || !responding.compareAndSet(false, true)) {
            // Do not silently discard a second user request while a reply is in flight.
            fallback("overlapping_utterance");
            return;
        }
        try {
            var reply = responder.answer(context, transcript);
            if (reply.isEmpty()) {
                responding.set(false);
                fallback("complex_or_unverified");
                return;
            }
            transport.clearPlayback(context.streamSid());
            synthesize(reply.get());
        } catch (RuntimeException error) {
            responding.set(false);
            fallback("answer_failure");
        }
    }

    private void synthesize(String text) {
        if (closed.get() || handedToGemini.get()) return;
        int total = sentCharacters.addAndGet(text.length());
        if (total > MAX_TTS_CHARACTERS || !properties.economicalForCharacters(total)) {
            responding.set(false); fallback("cost_gate"); return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(TTS_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Token " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new JSONObject().put("text", text).toString(),
                        StandardCharsets.UTF_8)).build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, error) -> {
                    if (error != null || response == null || response.statusCode() != 200
                            || response.body() == null || response.body().length == 0
                            || response.body().length > MAX_TTS_BYTES) {
                        responding.set(false); fallback("tts_failed"); return;
                    }
                    if (closed.get() || handedToGemini.get()) { responding.set(false); return; }
                    byte[] audio = response.body();
                    for (int offset = 0; offset < audio.length; offset += 1600) {
                        if (closed.get() || handedToGemini.get() || !transport.isOpen()) break;
                        int size = Math.min(1600, audio.length - offset);
                        byte[] chunk = new byte[size];
                        System.arraycopy(audio, offset, chunk, 0, size);
                        transport.sendAudio(context.streamSid(), Base64.getEncoder().encodeToString(chunk));
                    }
                    responding.set(false);
                });
    }

    private void fallback(String reason) {
        if (closed.get() || !handedToGemini.compareAndSet(false, true)) return;
        log.info("DEEPGRAM_ECONOMY_FALLBACK call_id={} reason={}", context.callId(), reason);
        WebSocket current = socket;
        socket = null;
        if (current != null) current.abort();
        try {
            if (!gemini.configuredFor(context.destinationNumber())) {
                transport.closeOnUpstreamFailure(); return;
            }
            VoiceAiSession session = gemini.createSession(context, transport);
            geminiSession = session;
            session.start();
            // Best effort replay of the final short speech segment, without text or PII logs.
            synchronized (audioLock) {
                for (String segment : replayAudio) session.acceptInboundAudio(segment);
                replayAudio.clear();
                pendingAudio.clear();
            }
        } catch (RuntimeException error) {
            log.warn("DEEPGRAM_ECONOMY_FALLBACK_FAILED call_id={}", context.callId());
            transport.closeOnUpstreamFailure();
        }
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        fallback("stt_network_error");
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        fallback("stt_disconnected");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        WebSocket current = socket;
        socket = null;
        if (current != null) current.abort();
        VoiceAiSession active = geminiSession;
        if (active != null) active.close();
        synchronized (audioLock) {
            replayAudio.clear();
            pendingAudio.clear();
        }
    }
}
