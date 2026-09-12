package cl.helvoca.ai.live;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiLiveWebhookControllerTest {
    private static final String BODY = "{\"type\":\"live.transport.incoming\",\"data\":{\"session_id\":\"live_test\",\"type\":\"sip\"}}";

    @Test
    void terminalBillingFailureIsAcknowledgedToStopRedelivery() {
        OpenAiWebhookVerifier verifier = mock(OpenAiWebhookVerifier.class);
        OpenAiLiveSipService service = mock(OpenAiLiveSipService.class);
        when(verifier.verify(anyString(), anyString(), anyString(), eq(BODY))).thenReturn(true);
        when(service.isReady()).thenReturn(true);
        doThrow(new OpenAiLiveProviderException(
                "credit exhausted", 429, "credit_balance_exhausted", true))
                .when(service).handleIncoming(eq("wh_test"), any(JSONObject.class));

        OpenAiLiveWebhookController controller = new OpenAiLiveWebhookController(verifier, service);
        var response = controller.webhook("wh_test", "123", "sig", BODY);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void transientProviderFailureRequestsWebhookRetry() {
        OpenAiWebhookVerifier verifier = mock(OpenAiWebhookVerifier.class);
        OpenAiLiveSipService service = mock(OpenAiLiveSipService.class);
        when(verifier.verify(anyString(), anyString(), anyString(), eq(BODY))).thenReturn(true);
        when(service.isReady()).thenReturn(true);
        doThrow(new OpenAiLiveProviderException(
                "upstream unavailable", 503, "upstream_error", false))
                .when(service).handleIncoming(eq("wh_test"), any(JSONObject.class));

        OpenAiLiveWebhookController controller = new OpenAiLiveWebhookController(verifier, service);
        var response = controller.webhook("wh_test", "123", "sig", BODY);

        assertEquals(503, response.getStatusCode().value());
    }
}
