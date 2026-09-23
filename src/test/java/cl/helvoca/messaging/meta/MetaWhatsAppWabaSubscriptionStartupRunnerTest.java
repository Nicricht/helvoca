package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppWabaSubscriptionStartupRunnerTest {
    private static final String WABA_ID = "1388561203388953";

    @Test
    void subscribesConfiguredWabaUsingReferencedEnvironmentToken() {
        MetaWhatsAppCloudClient client = mock(MetaWhatsAppCloudClient.class);
        Function<String, String> env = name -> Map.of(
                "HELVOCA_META_WHATSAPP_PILOT_01_ACCESS_TOKEN", "secret-token"
        ).get(name);

        MetaWhatsAppWabaSubscriptionStartupRunner runner =
                new MetaWhatsAppWabaSubscriptionStartupRunner(
                        true, WABA_ID, "PILOT_01", client, env);

        runner.run(null);

        verify(client).subscribeWaba(WABA_ID, "secret-token");
    }

    @Test
    void doesNotCrashStartupWhenMetaRejectsSubscription() {
        MetaWhatsAppCloudClient client = mock(MetaWhatsAppCloudClient.class);
        Function<String, String> env = name -> "secret-token";
        doThrow(new MetaWhatsAppApiException(
                400,
                "200",
                null,
                false,
                "OAuthException",
                "trace-123"))
                .when(client).subscribeWaba(WABA_ID, "secret-token");
        when(client.diagnoseAccess(WABA_ID, "secret-token"))
                .thenReturn(new MetaWhatsAppCloudClient.AccessDiagnostic(
                        "GRANTED", "GRANTED", "NONE", false, "META_200"));

        MetaWhatsAppWabaSubscriptionStartupRunner runner =
                new MetaWhatsAppWabaSubscriptionStartupRunner(
                        true, WABA_ID, "PILOT_01", client, env);

        assertDoesNotThrow(() -> runner.run(null));
        verify(client).subscribeWaba(WABA_ID, "secret-token");
        verify(client).diagnoseAccess(WABA_ID, "secret-token");
    }

    @Test
    void doesNothingWhenDisabled() {
        MetaWhatsAppCloudClient client = mock(MetaWhatsAppCloudClient.class);

        new MetaWhatsAppWabaSubscriptionStartupRunner(
                false, WABA_ID, "PILOT_01", client, name -> "secret-token").run(null);

        verifyNoInteractions(client);
    }

    @Test
    void failsClosedWhenAccessTokenIsMissing() {
        MetaWhatsAppCloudClient client = mock(MetaWhatsAppCloudClient.class);

        MetaWhatsAppWabaSubscriptionStartupRunner runner =
                new MetaWhatsAppWabaSubscriptionStartupRunner(
                        true, WABA_ID, "PILOT_01", client, name -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals("Meta WhatsApp WABA subscription access token is missing", error.getMessage());
        verifyNoInteractions(client);
    }
}
