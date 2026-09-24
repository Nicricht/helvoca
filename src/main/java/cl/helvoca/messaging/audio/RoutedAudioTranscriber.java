package cl.helvoca.messaging.audio;

import java.util.List;
import java.util.Objects;

public class RoutedAudioTranscriber implements AudioTranscriber {
    private final List<AudioTranscriptionProvider> providers;

    public RoutedAudioTranscriber(List<AudioTranscriptionProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        Objects.requireNonNull(input, "input");

        boolean configuredProviderFound = false;
        AudioTranscriptionException lastFailure = null;
        boolean retryableFailureSeen = false;

        for (AudioTranscriptionProvider provider : providers) {
            if (!provider.configured()) {
                continue;
            }
            configuredProviderFound = true;

            try {
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
            } catch (AudioTranscriptionException failure) {
                lastFailure = failure;
                retryableFailureSeen = retryableFailureSeen || failure.retryable();
            } catch (RuntimeException failure) {
                lastFailure = new AudioTranscriptionException(
                        "AUDIO_PROVIDER_FAILURE",
                        provider.id(),
                        null,
                        true,
                        "Audio transcription provider failed",
                        failure);
                retryableFailureSeen = true;
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
                retryableFailureSeen,
                "All configured audio transcription providers failed",
                lastFailure);
    }
}
