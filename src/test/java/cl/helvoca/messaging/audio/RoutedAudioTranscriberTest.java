package cl.helvoca.messaging.audio;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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

        AudioTranscriptionProvider skipped = new AudioTranscriptionProvider() {
            @Override
            public String id() {
                return "gemini";
            }

            @Override
            public boolean configured() {
                return false;
            }

            @Override
            public TranscriptionResult transcribe(AudioInput input) {
                skippedCalls.incrementAndGet();
                return result("should-not-run", "gemini");
            }
        };

        AudioTranscriptionProvider configured = new AudioTranscriptionProvider() {
            @Override
            public String id() {
                return "openai";
            }

            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public TranscriptionResult transcribe(AudioInput input) {
                configuredCalls.incrementAndGet();
                return result("Hola desde OpenAI", "openai");
            }
        };

        RoutedAudioTranscriber transcriber =
                new RoutedAudioTranscriber(List.of(skipped, configured));

        TranscriptionResult response = transcriber.transcribe(input());

        assertEquals("Hola desde OpenAI", response.text());
        assertEquals("openai", response.providerId());
        assertEquals(0, skippedCalls.get());
        assertEquals(1, configuredCalls.get());
    }

    @Test
    void selectsConfiguredProvidersInDeclaredOrder() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        AudioTranscriptionProvider first = provider(
                "gemini",
                firstCalls,
                result("Primero", "gemini"));
        AudioTranscriptionProvider second = provider(
                "openai",
                secondCalls,
                result("Segundo", "openai"));

        RoutedAudioTranscriber transcriber =
                new RoutedAudioTranscriber(List.of(first, second));

        TranscriptionResult response = transcriber.transcribe(input());

        assertEquals("Primero", response.text());
        assertEquals("gemini", response.providerId());
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
    }

    @Test
    void rejectsBlankTranscriptFromProvider() {
        AudioTranscriptionProvider provider = provider(
                "gemini",
                new AtomicInteger(),
                result("   ", "gemini"));
        RoutedAudioTranscriber transcriber =
                new RoutedAudioTranscriber(List.of(provider));

        assertThrows(AudioTranscriptionException.class,
                () -> transcriber.transcribe(input()));
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
            AtomicInteger calls,
            TranscriptionResult response) {
        return new AudioTranscriptionProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public TranscriptionResult transcribe(AudioInput input) {
                calls.incrementAndGet();
                return response;
            }
        };
    }

    private static TranscriptionResult result(String text, String providerId) {
        return new TranscriptionResult(
                text,
                providerId,
                "test-model",
                Duration.ofMillis(5),
                1);
    }
}
