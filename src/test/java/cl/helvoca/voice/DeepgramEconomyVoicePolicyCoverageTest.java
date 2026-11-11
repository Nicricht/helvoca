package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepgramEconomyVoicePolicyCoverageTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PHONE = "+56911112222";

    private static RealtimeCallContext context(UUID businessId) {
        return new RealtimeCallContext(UUID.randomUUID(), businessId, null,
                "+56933334444", PHONE, "MZ-pcmu-test");
    }

    private static DeepgramEconomyVoiceProperties permitted() {
        var p = new DeepgramEconomyVoiceProperties();
        p.setEnabled(true);
        p.setApiKey("test-token-never-sent");
        p.setPinnedBusinessId(TENANT.toString());
        p.setPinnedDestinationNumber(PHONE);
        return p;
    }

    @Test
    void allPublicConfigurationAccessorsAndPinnedIdentityAreHonored() {
        var p = permitted();
        assertTrue(p.isEnabled());
        assertEquals("test-token-never-sent", p.getApiKey());
        assertEquals(TENANT.toString(), p.getPinnedBusinessId());
        assertEquals(PHONE, p.getPinnedDestinationNumber());
        p.setNovaUsdPerMinute(0.005);
        p.setAuraUsdPerThousandChars(0.01);
        p.setGeminiReferenceUsdPerMinute(0.02);
        assertEquals(0.005, p.getNovaUsdPerMinute());
        assertEquals(0.01, p.getAuraUsdPerThousandChars());
        assertEquals(0.02, p.getGeminiReferenceUsdPerMinute());
        assertTrue(p.configuredFor(PHONE));
        assertTrue(p.permits(context(TENANT)));
        assertFalse(p.permits(null));
        assertFalse(p.permits(context(null)));
        assertFalse(p.permits(context(UUID.randomUUID())));
        assertFalse(p.permits(context(TENANT).withVoiceOverride("Aoede")));
        p.setEnabled(false);
        assertFalse(p.isEnabled());
        assertFalse(p.configuredFor(PHONE));
        assertFalse(p.permits(context(TENANT)));
    }

    @Test
    void missingCredentialTenantPhoneAndInvalidUuidAllFailClosed() {
        var p = permitted();
        p.setApiKey(null);
        assertFalse(p.configuredFor(PHONE));
        p.setApiKey(" ");
        assertFalse(p.configuredFor(PHONE));
        p.setApiKey("test");
        p.setPinnedBusinessId(null);
        assertFalse(p.configuredFor(PHONE));
        p.setPinnedBusinessId(" ");
        assertFalse(p.configuredFor(PHONE));
        p.setPinnedBusinessId(TENANT.toString());
        p.setPinnedDestinationNumber(null);
        assertFalse(p.configuredFor(PHONE));
        p.setPinnedDestinationNumber(" ");
        assertFalse(p.configuredFor(PHONE));
        p.setPinnedDestinationNumber(PHONE);
        assertFalse(p.configuredFor("different-phone"));
        p.setPinnedBusinessId("invalid-not-a-uuid");
        assertFalse(p.configuredFor(PHONE));
        p.setPinnedBusinessId(TENANT.toString());
        assertTrue(p.configuredFor(PHONE));
        p.setGeminiReferenceUsdPerMinute(0.0001);
        assertFalse(p.configuredFor(PHONE));
        assertFalse(p.permits(context(TENANT)));
    }

    @Test
    void malformedZeroNonFiniteOrExpensiveRatesNeverAllowEconomy() {
        var p = permitted();
        assertTrue(p.economicalForCharacters(0));
        assertTrue(p.economicalForCharacters(220));
        assertFalse(p.economicalForCharacters(-1));
        assertFalse(p.economicalForCharacters(221));
        p.setNovaUsdPerMinute(Double.NaN);
        assertFalse(p.economicalForCharacters(100));
        p.setNovaUsdPerMinute(0.0092);
        p.setAuraUsdPerThousandChars(Double.POSITIVE_INFINITY);
        assertFalse(p.economicalForCharacters(100));
        p.setAuraUsdPerThousandChars(0.03);
        p.setGeminiReferenceUsdPerMinute(Double.NaN);
        assertFalse(p.economicalForCharacters(100));
        p.setGeminiReferenceUsdPerMinute(0.023);
        p.setNovaUsdPerMinute(0);
        assertFalse(p.economicalForCharacters(100));
        p.setNovaUsdPerMinute(0.0092);
        p.setAuraUsdPerThousandChars(-1);
        assertFalse(p.economicalForCharacters(100));
        p.setAuraUsdPerThousandChars(0.03);
        p.setGeminiReferenceUsdPerMinute(0);
        assertFalse(p.economicalForCharacters(100));
        p.setGeminiReferenceUsdPerMinute(0.0092);
        assertFalse(p.economicalForCharacters(100));
    }

    @Test
    void routerRejectsPunctuationOnlyMultientityContextAndLongSentences() {
        assertEquals(VoiceEconomyIntentRouter.Reason.AMBIGUOUS,
                VoiceEconomyIntentRouter.classify("???").reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.AMBIGUOUS,
                VoiceEconomyIntentRouter.classify("x".repeat(141)).reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.AMBIGUOUS,
                VoiceEconomyIntentRouter.classify("x".repeat(161)).reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.AMBIGUOUS,
                VoiceEconomyIntentRouter.classify(null).reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.AMBIGUOUS,
                VoiceEconomyIntentRouter.classify(" ").reason());
        for (String utterance : new String[]{
                "¿Cuánto cuesta psicología y nutrición?",
                "¿Cuánto cuesta psicología pero nutrición?",
                "¿Cuánto cuesta psicología también nutrición?",
                "Quiero reservar para mañana"}) {
            assertEquals(VoiceEconomyIntentRouter.Path.GEMINI_LIVE,
                    VoiceEconomyIntentRouter.classify(utterance).path());
        }
        assertEquals(VoiceEconomyIntentRouter.Reason.BUSINESS_HOURS,
                VoiceEconomyIntentRouter.classify("¿Qué horario tienen?").reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.BUSINESS_ADDRESS,
                VoiceEconomyIntentRouter.classify("¿Dónde quedan?").reason());
        assertEquals(VoiceEconomyIntentRouter.Reason.SERVICE_PRICE,
                VoiceEconomyIntentRouter.classify("¿Cuánto cuesta psicología?").reason());
        assertFalse(VoiceEconomyIntentRouter.classify("hola").cheaperPathCandidate());
    }

    @Test
    void catalogWithoutTenantSuccessOrReadableServicesReturnsNoAnswer() {
        var tool = mock(RealtimeToolService.class);
        var r = new DeepgramEconomyPublicResponder(tool);
        var context = context(TENANT);
        assertTrue(r.answer(null, "¿Cuánto cuesta psicología?").isEmpty());
        assertTrue(r.answer(context(null), "¿Cuánto cuesta psicología?").isEmpty());
        assertTrue(r.answer(context, "¿Dónde quedan?").isEmpty());
        assertTrue(r.answer(context, "¿Qué horario tienen?").isEmpty());
        assertTrue(r.answer(context, "¿Cuánto cuesta medicina desconocida?").isEmpty());
        assertTrue(r.answer(context, null).isEmpty());
        verifyNoInteractions(tool);
        when(tool.execute(eq(context), eq("list_services"), eq("{}"))).thenReturn(
                "{\"success\":true}",
                "{\"success\":true,\"data\":{}}",
                "{\"success\":false}",
                "{\"success\":true,\"data\":{\"services\":[]}}");
        for (int i = 0; i < 4; i++) {
            assertTrue(r.answer(context, "¿Cuánto cuesta psicología?").isEmpty());
        }
    }

    @Test
    void catalogSkipsNullWrongServiceAndMissingPriceButAnswersExactTenantMatch() {
        var tool = mock(RealtimeToolService.class);
        var context = context(TENANT);
        var services = new JSONArray()
                .put(JSONObject.NULL)
                .put(new JSONObject().put("name", "Otra consulta").put("price", 10000))
                .put(new JSONObject().put("name", "Psicología"))
                .put(new JSONObject().put("name", "Psicología").put("price", 35000));
        when(tool.execute(eq(context), eq("list_services"), eq("{}"))).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("services", services)).toString());
        var response = new DeepgramEconomyPublicResponder(tool).answer(
                context, "¿Cuánto cuesta la consulta de psicología por favor?");
        assertTrue(response.isPresent());
        assertTrue(response.orElseThrow().contains("35"));
        verify(tool).execute(eq(context), eq("list_services"), eq("{}"));
    }

    @Test
    void nonPositiveOverspecifiedAndInvalidPricesAreNotInvented() {
        for (Object bad : new Object[]{0, -100, "100.123", "not-money"}) {
            var tool = mock(RealtimeToolService.class);
            var context = context(TENANT);
            when(tool.execute(eq(context), eq("list_services"), eq("{}"))).thenReturn(
                    new JSONObject().put("success", true).put("data",
                            new JSONObject().put("services", new JSONArray()
                                    .put(new JSONObject().put("name", "Psicología")
                                            .put("price", bad)))).toString());
            assertTrue(new DeepgramEconomyPublicResponder(tool)
                    .answer(context, "¿Cuánto cuesta psicología?").isEmpty());
        }
    }


    @Test
    void unsupportedServiceAndNonPriceRequestFailClosedBeforeAnyCatalogQuery() throws Exception {
        var tool = mock(RealtimeToolService.class);
        var responder = new DeepgramEconomyPublicResponder(tool);
        var context = context(TENANT);
        assertTrue(responder.answer(context, "¿Cuánto cuesta cardiología?").isEmpty());
        assertTrue(responder.answer(context, "Hola, ¿en qué horario atienden?").isEmpty());
        assertTrue(responder.answer(context, "¿Cuánto cuesta una consulta de psiquiatría?").isEmpty());
        verifyNoInteractions(tool);

        Method parser = DeepgramEconomyPublicResponder.class
                .getDeclaredMethod("requestedService", String.class);
        parser.setAccessible(true);
        assertNull(parser.invoke(null, "¿Cuánto cuesta cardiología?"),
                "Unsupported services must never receive invented prices");
    }

    @Test
    void allFourAllowedServicesRequireExactAuthorizedCatalogueRows() {
        var context = context(TENANT);
        for (String service : new String[]{"Medicina General", "Psicología",
                "Dermatología", "Nutrición"}) {
            var tool = mock(RealtimeToolService.class);
            when(tool.execute(eq(context), eq("list_services"), eq("{}"))).thenReturn(
                    new JSONObject().put("success", true)
                            .put("data", new JSONObject().put("services", new JSONArray()
                                    .put(new JSONObject().put("name", service)
                                            .put("price", 25000)))).toString());
            var result = new DeepgramEconomyPublicResponder(tool).answer(
                    context, "¿Cuánto cuesta " + service + " por favor?");
            assertTrue(result.isPresent(), "Exact supported service must be readable: " + service);
            assertTrue(result.orElseThrow().contains("25"));
            verify(tool).execute(eq(context), eq("list_services"), eq("{}"));
        }
    }

    @Test
    void nullNormalizerInputIsDefensivelyEmpty() throws Exception {
        Method m = DeepgramEconomyPublicResponder.class.getDeclaredMethod("normalize", String.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(null, new Object[]{null}));
    }
}
