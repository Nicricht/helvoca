package cl.helvoca.messaging.audio;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public class RoutedAudioTranscriber implements AudioTranscriber {
    private final List<AudioTranscriptionProvider> providers;
    private final TranscriptionCircuitBreaker circuitBreaker;
    private final int retryMinMillis;
    private final int retryMaxMillis;
    private final LongConsumer sleeper;
    private final LongSupplier jitterMillis;

    public RoutedAudioTranscriber(List<AudioTranscriptionProvider> providers) {
        this(
                providers,
                new WhatsAppAudioTranscriptionProperties(),
                Clock.systemUTC(),
                RoutedAudioTranscriber::sleep,
                () -> ThreadLocalRandom.current().nextLong(250, 501),
                false);
    }

    public RoutedAudioTranscriber(
            List<AudioTranscriptionProvider> providers,
            WhatsAppAudioTranscriptionProperties properties) {
        this(
                providers,
                properties,
                Clock.systemUTC(),
                RoutedAudioTranscriber::sleep,
                () -> ThreadLocalRandom.current().nextLong(
                        properties.getImmediateRetryMinMillis(),
                        (long) properties.getImmediateRetryMaxMillis() + 1L),
                true);
    }

    RoutedAudioTranscriber(
            List<AudioTranscriptionProvider> providers,
            WhatsAppAudioTranscriptionProperties properties,
            Clock clock,
            LongConsumer sleeper,
            LongSupplier jitterMillis) {
        this(providers, properties, clock, sleeper, jitterMillis, true);
    }

    private RoutedAudioTranscriber(
            List<AudioTranscriptionProvider> providers,
            WhatsAppAudioTranscriptionProperties properties,
            Clock clock,
            LongConsumer sleeper,
            LongSupplier jitterMillis,
            boolean configuredOrder) {
        Objects.requireNonNull(properties, "properties");
        List<AudioTranscriptionProvider> copy = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.providers = configuredOrder ? ordered(copy, properties.getProviderOrder()) : copy;
        this.retryMinMillis = properties.getImmediateRetryMinMillis();
        this.retryMaxMillis = properties.getImmediateRetryMaxMillis();
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.jitterMillis = Objects.requireNonNull(jitterMillis, "jitterMillis");
        this.circuitBreaker = new TranscriptionCircuitBreaker(
                properties.getCircuitFailureThreshold(),
                Duration.ofSeconds(properties.getCircuitCooldownSeconds()),
                Objects.requireNonNull(clock, "clock"));
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        Objects.requireNonNull(input, "input");

        boolean configuredProviderFound = false;
        boolean attemptedProvider = false;
        boolean retryableFailureSeen = false;
        AudioTranscriptionException lastFailure = null;

        for (AudioTranscriptionProvider provider : providers) {
            if (!provider.configured()) continue;
            configuredProviderFound = true;
            if (!circuitBreaker.allowRequest(provider.id())) continue;
            attemptedProvider = true;

            AudioTranscriptionException firstFailure;
            try {
                TranscriptionResult result = invoke(provider, input);
                circuitBreaker.onSuccess(provider.id());
                return result;
            } catch (AudioTranscriptionException failure) {
                firstFailure = failure;
            } catch (RuntimeException failure) {
                firstFailure = unexpected(provider, failure);
            }

            lastFailure = firstFailure;
            if (!firstFailure.retryable()) {
                circuitBreaker.onPermanentFailure(provider.id());
                continue;
            }

            retryableFailureSeen = true;
            circuitBreaker.onRetryableFailure(provider.id());
            if (isRateLimit(firstFailure) || !circuitBreaker.allowRequest(provider.id())) {
                continue;
            }

            sleeper.accept(retryDelayMillis());
            try {
                TranscriptionResult result = invoke(provider, input);
                circuitBreaker.onSuccess(provider.id());
                return result;
            } catch (AudioTranscriptionException failure) {
                lastFailure = failure;
                if (failure.retryable()) {
                    retryableFailureSeen = true;
                    circuitBreaker.onRetryableFailure(provider.id());
                } else {
                    circuitBreaker.onPermanentFailure(provider.id());
                }
            } catch (RuntimeException failure) {
                lastFailure = unexpected(provider, failure);
                retryableFailureSeen = true;
                circuitBreaker.onRetryableFailure(provider.id());
            }
        }

        if (!configuredProviderFound) {
            throw new AudioTranscriptionException(
                    "AUDIO_PROVIDER_NOT_CONFIGURED",
                    "router",
                    null,
                    false,
                    "No audio transcription provider is configured");
        }

        throw new AudioTranscriptionException(
                "AUDIO_TRANSCRIPTION_EXHAUSTED",
                "router",
                null,
                retryableFailureSeen || !attemptedProvider,
                "All configured audio transcription providers failed",
                lastFailure);
    }

    private TranscriptionResult invoke(AudioTranscriptionProvider provider, AudioInput input) {
        TranscriptionResult result = provider.transcribe(input);
        if (result == null || result.text() == null || result.text().isBlank()) {
            throw new AudioTranscriptionException(
                    "AUDIO_TRANSCRIPT_EMPTY",
                    provider.id(),
                    null,
                    false,
                    "Audio transcription provider returned no text");
        }
        return result;
    }

    private long retryDelayMillis() {
        long candidate = jitterMillis.getAsLong();
        return Math.max(retryMinMillis, Math.min(retryMaxMillis, candidate));
    }

    private static boolean isRateLimit(AudioTranscriptionException failure) {
        return failure.httpStatus() != null && failure.httpStatus() == 429;
    }

    private static AudioTranscriptionException unexpected(
            AudioTranscriptionProvider provider,
            RuntimeException failure) {
        return new AudioTranscriptionException(
                "AUDIO_PROVIDER_FAILURE",
                provider.id(),
                null,
                true,
                "Audio transcription provider failed",
                failure);
    }

    private static List<AudioTranscriptionProvider> ordered(
            List<AudioTranscriptionProvider> providers,
            List<String> providerOrder) {
        Map<String, AudioTranscriptionProvider> byId = new LinkedHashMap<>();
        for (AudioTranscriptionProvider provider : providers) {
            if (provider != null) byId.putIfAbsent(provider.id(), provider);
        }
        List<AudioTranscriptionProvider> out = new ArrayList<>();
        for (String id : providerOrder) {
            AudioTranscriptionProvider provider = byId.get(id);
            if (provider != null) out.add(provider);
        }
        return List.copyOf(out);
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException(
                    "AUDIO_RETRY_INTERRUPTED",
                    "router",
                    null,
                    false,
                    "Audio transcription retry was interrupted",
                    failure);
        }
    }
}
