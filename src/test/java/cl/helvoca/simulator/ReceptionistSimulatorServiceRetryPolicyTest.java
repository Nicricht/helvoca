package cl.helvoca.simulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceptionistSimulatorServiceRetryPolicyTest {

    @Test
    void retriesOrdinaryRateLimitResponses() {
        String body = """
                {"error":{"type":"requests","code":"rate_limit_exceeded","message":"details not logged"}}
                """;

        assertTrue(ReceptionistSimulatorService.retryableOpenAi429(body));
        assertEquals(
                "OpenAI simulator request failed with HTTP 429 type=requests code=rate_limit_exceeded",
                ReceptionistSimulatorService.openAiFailureSummary(429, body));
    }

    @Test
    void doesNotRetryInsufficientQuota() {
        String body = """
                {"error":{"type":"insufficient_quota","code":"insufficient_quota","message":"billing details"}}
                """;

        assertFalse(ReceptionistSimulatorService.retryableOpenAi429(body));
    }

    @Test
    void recognizesOpenAiProviderFailureForGeminiFallback() {
        assertTrue(ReceptionistSimulatorService.openAiProviderFailure(
                new IllegalStateException(
                        "OpenAI simulator request failed with HTTP 429 type=insufficient_quota code=credit_balance_exhausted")));
        assertFalse(ReceptionistSimulatorService.openAiProviderFailure(
                new IllegalStateException("unrelated simulator error")));
    }

    @Test
    void providerFailureSummaryDoesNotExposeProviderMessage() {
        String body = """
                {"error":{"type":"requests","code":"rate_limit_exceeded","message":"secret-ish provider detail"}}
                """;

        String summary = ReceptionistSimulatorService.openAiFailureSummary(429, body);

        assertFalse(summary.contains("secret-ish"));
        assertTrue(summary.contains("type=requests"));
        assertTrue(summary.contains("code=rate_limit_exceeded"));
    }
}
