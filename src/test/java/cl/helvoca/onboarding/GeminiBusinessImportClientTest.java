package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeminiBusinessImportClientTest {
    private static final String RESPONSE_TEXT = """
            {
              "products": [
                {"name":"Lasaña vegetariana","price":7800,"previousPrice":7200,"category":"Fondos","availability":"unknown"},
                {"name":"Bagel salmón","price":6900,"availability":"unavailable"},
                {"name":"Americano","price":2400,"availability":"unknown"}
              ],
              "extras":[{"name":"Palta","price":1200}],
              "promotions":[{"name":"PROMO BRUNCH 2x1","conditions":null}],
              "businessHours":[{"days":["lunes","martes"],"open":"08:00","close":"20:30"}],
              "faq":[{"question":"¿Tienen terraza?","answer":"Sí, pet friendly en terraza."}],
              "warnings":["Revisar alérgenos."]
            }
            """;

    private static MockMultipartFile picture() {
        return new MockMultipartFile("files", "menu.png", "image/png", new byte[]{1, 2, 3, 4});
    }

    @Test
    void parsesRealisticMenuAndFlagsPromotionsInsteadOfPublishingThem() {
        var result = GeminiBusinessImportClient.parse(RESPONSE_TEXT, "menu.png");
        assertEquals(3, result.products().size());
        assertEquals("7800", result.products().getFirst().price().toPlainString());
        assertNull(result.products().getFirst().onHand());
        assertEquals(0, result.products().get(1).onHand());
        assertEquals(3, result.setupSuggestions().size());
        assertEquals("FAQ", result.setupSuggestions().getFirst().kind());
        assertEquals("MONDAY", result.setupSuggestions().get(1).key());
        assertEquals("08:00-20:30", result.setupSuggestions().get(1).value());
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("Promoción pendiente")));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("agregados detectados")));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("AGOTADO")));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("Precio manuscrito")));
    }

    @Test
    void unknownAvailabilityMustNotBecomeStockEvenWithModelOnHand() {
        var result = GeminiBusinessImportClient.parse("""
                {"products":[{"name":"Café","price":2500,"availability":"unknown","onHand":99}]}
                """, "carta.jpg");
        assertNull(result.products().getFirst().onHand());
    }

    @Test
    void invalidSetupDoesNotBecomeLiveScheduleOrKnowledge() {
        var result = GeminiBusinessImportClient.parse("""
                {"products":[],"businessHours":[
                   {"days":["lunes"],"open":"20:00","close":"08:00"},
                   {"days":["feriados"],"open":"09:00","close":"18:00"}],
                 "faq":[{"question":"¿Abren?","answer":""}]}
                """, "menu.png");
        assertTrue(result.setupSuggestions().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void malformedOrOversizedProviderOutputFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> GeminiBusinessImportClient.parse("{}", "x.png"));
        assertThrows(IllegalArgumentException.class,
                () -> GeminiBusinessImportClient.parse("x".repeat(150_001), "x.png"));
        assertThrows(IllegalArgumentException.class,
                () -> GeminiBusinessImportClient.parse("{\"products\":[]}".repeat(15000), "x.png"));
    }

    @Test
    void missingDedicatedKeyBlocksBeforeAnyNetworkOrUsageReservation() {
        HttpClient http = mock(HttpClient.class);
        var client = new GeminiBusinessImportClient(http);
        var ledger = mock(BusinessImportAiUsageLedger.class);
        assertFalse(client.hasApiKey());
        assertThrows(IllegalStateException.class, () -> client.analyze(List.of(picture()), ledger));
        verifyNoInteractions(http, ledger);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realApiShapeUsesDedicatedHeaderAndPersistsUsageEvidence() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":%s}]}}],
                 "usageMetadata":{"promptTokenCount":1384,"candidatesTokenCount":1021}}
                """.formatted(org.json.JSONObject.quote(RESPONSE_TEXT)));
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var client = new GeminiBusinessImportClient(http);
        ReflectionTestUtils.setField(client, "apiKey", "synthetic-free-tier-key-only-0123456789");
        var ledger = mock(BusinessImportAiUsageLedger.class);
        var result = client.analyze(List.of(picture()), ledger);
        assertEquals(3, result.products().size());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertEquals("POST", request.getValue().method());
        assertTrue(request.getValue().uri().toString().endsWith("gemini-3.5-flash-lite:generateContent"));
        assertEquals("synthetic-free-tier-key-only-0123456789",
                request.getValue().headers().firstValue("x-goog-api-key").orElse(""));
        verify(ledger).started(any(UUID.class), eq("gemini-3.5-flash-lite"));
        verify(ledger).receivedGemini(any(UUID.class), eq("gemini-3.5-flash-lite"), same(response));
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerHttpFailureIsNotAProductSuccess() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"error\":\"quota\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var client = new GeminiBusinessImportClient(http);
        ReflectionTestUtils.setField(client, "apiKey", "synthetic-free-tier-key-only-0123456789");
        var ledger = mock(BusinessImportAiUsageLedger.class);
        var error = assertThrows(IllegalStateException.class,
                () -> client.analyze(List.of(picture()), ledger));
        assertTrue(error.getMessage().contains("429"));
        verify(ledger).receivedGemini(any(UUID.class), anyString(), same(response));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uncertainNetworkFailureIsRecordedAndNeverRetried() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("temporary failure"));
        var client = new GeminiBusinessImportClient(http);
        ReflectionTestUtils.setField(client, "apiKey", "synthetic-free-tier-key-only-0123456789");
        var ledger = mock(BusinessImportAiUsageLedger.class);
        assertThrows(IOException.class, () -> client.analyze(List.of(picture()), ledger));
        verify(http, times(1)).send(any(HttpRequest.class), any());
        verify(ledger).uncertain(any(UUID.class), eq("gemini-3.5-flash-lite"));
    }

    @Test
    void nonImageAndOversizeInputFailBeforeLedgerOrProvider() {
        var client = new GeminiBusinessImportClient(mock(HttpClient.class));
        ReflectionTestUtils.setField(client, "apiKey", "synthetic-free-tier-key-only-0123456789");
        var ledger = mock(BusinessImportAiUsageLedger.class);
        assertThrows(IllegalArgumentException.class,
                () -> client.analyze(List.of(new MockMultipartFile(
                    "files","menu.exe","application/octet-stream",new byte[]{1,2})), ledger));
        assertThrows(IllegalArgumentException.class,
                () -> client.analyze(List.of(new MockMultipartFile(
                    "files","large.png","image/png",new byte[4 * 1024 * 1024 + 1])), ledger));
        verifyNoInteractions(ledger);
    }

    @Test
    void serviceCanRouteGeminiThroughEstablishedPreviewAndPaidBudgetGate() throws Exception {
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        GeminiBusinessImportClient provider = mock(GeminiBusinessImportClient.class);
        when(provider.hasApiKey()).thenReturn(true);
        var extraction = GeminiBusinessImportClient.parse(RESPONSE_TEXT, "menu.png");
        when(provider.analyze(anyList(), any())).thenReturn(extraction);
        var budget = mock(BusinessImportAiBudget.class);
        when(budget.reserve()).thenReturn(true);
        HttpClient http = mock(HttpClient.class);
        var preview = new BusinessImportPreviewService(
                new BusinessImportSpreadsheetParser(), new OpenAiRealtimeProperties(),
                tenant, budget, mock(BusinessImportAiUsageLedger.class), http, provider);
        ReflectionTestUtils.setField(preview, "paidAiImportEnabled", true);
        ReflectionTestUtils.setField(preview, "importProvider", "gemini");

        var output = preview.preview("Restaurante", List.of(picture()));
        assertTrue(output.aiUsed());
        assertEquals(3, output.products().size());
        assertEquals(3, output.setupSuggestions().size());
        assertEquals("AI", output.sources().getFirst().method());
        verify(budget).reserve();
        verify(provider).analyze(anyList(), any());
        verifyNoInteractions(http);
    }

    @Test
    void geminiNeverUsesLiveVoiceKeyOrCallsBudgetWhenDedicatedKeyIsAbsent() {
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        GeminiBusinessImportClient provider = mock(GeminiBusinessImportClient.class);
        when(provider.hasApiKey()).thenReturn(false);
        var budget = mock(BusinessImportAiBudget.class);
        var preview = new BusinessImportPreviewService(
                new BusinessImportSpreadsheetParser(), new OpenAiRealtimeProperties(),
                tenant, budget, mock(BusinessImportAiUsageLedger.class),
                mock(HttpClient.class), provider);
        ReflectionTestUtils.setField(preview, "paidAiImportEnabled", true);
        ReflectionTestUtils.setField(preview, "importProvider", "gemini");
        var output = preview.preview("Restaurante", List.of(picture()));
        assertFalse(output.aiUsed());
        assertEquals("AI_UNAVAILABLE", output.sources().getFirst().method());
        verifyNoInteractions(budget);
        verify(provider, never()).analyze(anyList(), any());
    }
}
