package cl.helvoca.chaos;

import java.util.List;

public final class FailureChaosMatrix {
    private FailureChaosMatrix() {}

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario(
                        "operation-timeout-bounded-retry",
                        "Transient provider/backend timeouts retry only within configured bounds and succeed at most once.",
                        List.of("SafeOperationRetryChaosCertificationTest")),
                new Scenario(
                        "payment-webhook-failure-retry-duplicate",
                        "A failed verification may retry, a verified success applies once, later duplicate events do not query the provider again.",
                        List.of("PaymentWebhookChaosCertificationTest")),
                new Scenario(
                        "whatsapp-duplicate-delivery",
                        "Concurrent duplicate inbound WhatsApp delivery materializes one durable job.",
                        List.of("MetaWhatsAppInboundJobServiceTest", "PersistentJobStoreIntegrationTest")),
                new Scenario(
                        "durable-job-crash-retry",
                        "Leased durable jobs recover from retry/crash paths without unbounded execution.",
                        List.of("PersistentJobStoreIntegrationTest")),
                new Scenario(
                        "audio-provider-failure-routing",
                        "Transient transcription failures retry/fallback and circuit state remains bounded.",
                        List.of(
                                "DeepgramAudioTranscriptionProviderTest",
                                "TranscriptionCircuitBreakerTest",
                                "RoutedAudioTranscriberTest")),
                new Scenario(
                        "conversation-replay-invariants",
                        "Known conversational and duplicate-effect regressions retain their certified signature.",
                        List.of("ConversationReplayFixtureSuiteTest"))
        );
    }

    public record Scenario(String id, String invariant, List<String> tests) {
        public Scenario {
            tests = List.copyOf(tests);
        }
    }
}
