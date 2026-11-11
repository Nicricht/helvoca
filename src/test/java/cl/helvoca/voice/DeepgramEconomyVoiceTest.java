package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepgramEconomyVoiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PHONE = "+56911112222";

    @Test
    void optInRequiresPinnedTenantPhoneAndNonEmptyCredential() {
        var settings = new DeepgramEconomyVoiceProperties();
        var context = context(TENANT);
        assertFalse(settings.configuredFor(PHONE));
        assertFalse(settings.permits(context));
        settings.setEnabled(true);
        settings.setApiKey("test-only");
        settings.setPinnedBusinessId(TENANT.toString());
        settings.setPinnedDestinationNumber(PHONE);
        assertTrue(settings.configuredFor(PHONE));
        assertTrue(settings.permits(context));
        assertFalse(settings.permits(context(UUID.randomUUID())));
        assertFalse(settings.configuredFor("+56999998888"));
        assertFalse(settings.permits(context.withVoiceOverride("Leda")));
        settings.setPinnedBusinessId("not-a-uuid");
        assertFalse(settings.configuredFor(PHONE));
    }

    @Test
    void economicGateRejectsUnknownRatesAndOverlongReplies() {
        var settings = new DeepgramEconomyVoiceProperties();
        assertTrue(settings.economicalForCharacters(150));
        assertFalse(settings.economicalForCharacters(250));
        assertFalse(settings.economicalForCharacters(-1));
        settings.setGeminiReferenceUsdPerMinute(0);
        assertFalse(settings.economicalForCharacters(100));
        settings.setGeminiReferenceUsdPerMinute(Double.NaN);
        assertFalse(settings.economicalForCharacters(100));
    }

    @Test
    void exactTenantCataloguePriceIsReadWithoutBookingOrInventedAnswer() {
        RealtimeToolService tools = mock(RealtimeToolService.class);
        var context = context(TENANT);
        when(tools.execute(eq(context), eq("list_services"), eq("{}"))).thenReturn(
                new JSONObject().put("success", true).put("data",
                        new JSONObject().put("services",
                                new org.json.JSONArray().put(new JSONObject()
                                        .put("name", "Psicología").put("price", 35000)))).toString());
        var responder = new DeepgramEconomyPublicResponder(tools);
        Optional<String> reply = responder.answer(context, "¿Cuánto cuesta la consulta de psicología?");
        assertTrue(reply.isPresent());
        assertTrue(reply.orElseThrow().contains("Psicología"));
        assertTrue(reply.orElseThrow().contains("35"));
        assertTrue(responder.answer(context, "Quiero reservar psicología").isEmpty());
        assertTrue(responder.answer(context, "¿Dónde quedan?").isEmpty());
        verify(tools, times(1)).execute(eq(context), eq("list_services"), eq("{}"));
        verifyNoMoreInteractions(tools);
    }

    @Test
    void unavailableOrAmbiguousTenantDataFailsClosed() {
        RealtimeToolService tools = mock(RealtimeToolService.class);
        var context = context(TENANT);
        when(tools.execute(eq(context), eq("list_services"), eq("{}")))
                .thenReturn("{\"success\":false}")
                .thenReturn("{\"success\":true,\"data\":{\"services\":[{\"name\":\"Otro servicio\",\"price\":20000}]}}")
                .thenThrow(new IllegalStateException("no access"));
        var responder = new DeepgramEconomyPublicResponder(tools);
        for (int i = 0; i < 3; i++) {
            assertTrue(responder.answer(context, "¿Cuánto cuesta psicología?").isEmpty());
        }
    }

    private static RealtimeCallContext context(UUID tenant) {
        return new RealtimeCallContext(UUID.randomUUID(), tenant, null,
                "+56933334444", PHONE, "MZ_test");
    }
}
