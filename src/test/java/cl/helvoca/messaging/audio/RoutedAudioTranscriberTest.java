package cl.helvoca.messaging.audio;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutedAudioTranscriberTest {

    @Test
    void skipsUnconfiguredProvidersAndUsesNextConfiguredProvider() {
        AtomicInteger skippedCalls = new AtomicInteger();
        AtomicInteger configuredCalls = new AtomicInteger();

        AudioTranscriptionProvider skipped = provider("gemini", false, skippedCalls, result("never", "gemini"));
        AudioTranscriptionProvider configured = provider("openai", true, configuredCalls, result("Hola desde OpenAI", "openai"));

        TranscriptionResult response = new RoutedAudioTranscriber(List.of(skipped, configured)).transcribe(input());

        assertEquals("Hola desde OpenAI", response.text());
        assertEquals(0, skippedCalls.get());
        assertEquals(1, configuredCalls.get());
    }

    @Test
    void selectsConfiguredProvidersInDeclaredOrder() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        RoutedAudioTranscriber transcriber = new RoutedAudioTranscriber(List.of(
                provider("gemini", true, firstCalls, result("Primero", "gemini")),
                provider("openai", true, secondCalls, result("Segundo", "openai"))));

        TranscriptionResult response = transcriber.transcribe(input());

        assertEquals("Primero", response.text());
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
    }

    @Test
    void retries503OnceThenReturnsSameProviderSuccess() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();
        SequenceProvider first = new SequenceProvider("deepgram", firstCalls,
                retryable("deepgram", 503), result("Recuperado", "deepgram"));
        SequenceProvider second = new SequenceProvider("gemini", new AtomicInteger(), result("No usado", "gemini"));

        RoutedAudioTranscriber transcriber = testRouter(List.of(first, second), millis -> sleeps.incrementAndGet(), 300L);
        TranscriptionResult response = transcriber.transcribe(input());

        assertEquals("Recuperado", response.text());
        assertEquals(2, firstCalls.get());
        assertEquals(0, second.calls.get());
        assertEquals(1, sleeps.get());
    }

    @Test
    void retries503OnceThenSwitchesProvider() {
        SequenceProvider first = new SequenceProvider("deepgram", new AtomicInteger(),
                retryable("deepgram", 503), retryable("deepgram", 503));
        SequenceProvider second = new SequenceProvider("gemini", new AtomicInteger(), result("Fallback", "gemini"));

        TranscriptionResult response = testRouter(List.of(first, second), ignored -> {}, 250L).transcribe(input());

        assertEquals("Fallback", response.text());
        assertEquals(2, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void switchesImmediatelyOn429WithoutSameProviderRetry() {
        SequenceProvider first = new SequenceProvider("deepgram", new AtomicInteger(), retryable("deepgram", 429));
        SequenceProvider second = new SequenceProvider("gemini", new AtomicInteger(), result("Fallback", "gemini"));
        AtomicInteger sleeps = new AtomicInteger();

        TranscriptionResult response = testRouter(List.of(first, second), ignored -> sleeps.incrementAndGet(), 300L)
                .transcribe(input());

        assertEquals("Fallback", response.text());
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
        assertEquals(0, sleeps.get());
    }

    @Test
    void rejectsBlankTranscriptFromProvider() {
        AudioTranscriptionProvider provider = provider(
                "gemini", true, new AtomicInteger(), result("   ", "gemini"));

        assertThrows(AudioTranscriptionException.class,
                () -> new RoutedAudioTranscriber(List.of(provider)).transcribe(input()));
    }

    private static RoutedAudioTranscriber testRouter(
            List<AudioTranscriptionProvider> providers,
            java.util.function.LongConsumer sleeper,
            long jitterMillis) {
        WhatsAppAudioTranscriptionProperties properties = new WhatsAppAudioTranscriptionProperties();
        properties.setImmediateRetryMinMillis(250);
        properties.setImmediateRetryMaxMillis(500);
        properties.setCircuitFailureThreshold(3);
        properties.setCircuitCooldownSeconds(60);
        return new RoutedAudioTranscriber(
                providers,
                properties,
                Clock.systemUTC(),
                sleeper,
                () -> jitterMillis);
    }

    private static AudioInput input() {
        return new AudioInput(
                new byte[]{1, 2, 3},
                "audio/ogg",
                "es",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "wamid.AUDIO-ROUTER",
                "22222222-2222-2222-2222-222222222222");
    }

    private static AudioTranscriptionProvider provider(
            String id,
            boolean configured,
            AtomicInteger calls,
            TranscriptionResult response) {
        return new AudioTranscriptionProvider() {
            @Override public String id() { return id; }
            @Override public boolean configured() { return configured; }
            @Override public TranscriptionResult transcribe(AudioInput input) {
                calls.incrementAndGet();
                return response;
            }
        };
    }

    private static TranscriptionResult result(String text, String providerId) {
        return new TranscriptionResult(text, providerId, "test-model", Duration.ofMillis(5), 1);
    }

    private static AudioTranscriptionException retryable(String provider, int status) {
        return new AudioTranscriptionException(
                "TEST_HTTP_" + status,
                provider,
                status,
                true,
                "sanitized test failure");
    }

    private static final class SequenceProvider implements AudioTranscriptionProvider {
        private final String id;
        private final AtomicInteger calls;
        private final Deque<Object> outcomes = new ArrayDeque<>();

        private SequenceProvider(String id, AtomicInteger calls, Object... outcomes) {
            this.id = id;
            this.calls = calls;
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override public String id() { return id; }
        @Override public boolean configured() { return true; }

        @Override
        public TranscriptionResult transcribe(AudioInput input) {
            calls.incrementAndGet();
            Object outcome = outcomes.removeFirst();
            if (outcome instanceof AudioTranscriptionException failure) throw failure;
            return (TranscriptionResult) outcome;
        }
    }
}
