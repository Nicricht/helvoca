package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.security.TenantProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessImportPreviewServiceCoverageTest {

    @Test
    void validatesBusinessNameFileCountAndRequiredFiles() {
        TenantProvider tenant = tenant();
        BusinessImportPreviewService service = serviceWithoutAi(tenant);

        var file = new MockMultipartFile(
                "files", "productos.csv", "text/csv",
                "Producto,Precio\nPapas,3490\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.preview(null, List.of(file)));
        assertThrows(IllegalArgumentException.class, () -> service.preview("   ", List.of(file)));
        assertThrows(IllegalArgumentException.class, () -> service.preview("x".repeat(151), List.of(file)));
        assertThrows(IllegalArgumentException.class, () -> service.preview("Negocio", null));
        assertThrows(IllegalArgumentException.class, () -> service.preview("Negocio", List.of()));

        List<MultipartFile> thirteen = new ArrayList<>();
        for (int i = 0; i < 13; i++) thirteen.add(file);
        assertThrows(IllegalArgumentException.class, () -> service.preview("Negocio", thirteen));
        verify(tenant, atLeast(6)).requireBusinessId();
    }

    @Test
    void skipsEmptyOversizedUnsupportedAndSemanticBudgetOverflow() {
        BusinessImportPreviewService service = serviceWithoutAi(tenant());

        MultipartFile empty = mockFile("empty.csv", "text/csv", 0, true);
        MultipartFile huge = mockFile("huge.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                10L * 1024L * 1024L + 1, false);
        MultipartFile unsupported = mockFile("data.json", "application/json", 20, false);
        MultipartFile image1 = mockFile("uno.jpg", "image/jpeg", 8L * 1024L * 1024L, false);
        MultipartFile image2 = mockFile("dos.png", "image/png", 8L * 1024L * 1024L, false);
        MultipartFile image3 = mockFile("tres.webp", "image/webp", 8L * 1024L * 1024L, false);

        var preview = service.preview("Negocio", List.of(empty, huge, unsupported, image1, image2, image3));

        assertFalse(preview.aiUsed());
        assertTrue(preview.products().isEmpty());
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("vacío")));
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("10 MB")));
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("no soportado")));
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("20 MB")));
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("IA")));
        assertTrue(preview.sources().stream().anyMatch(s -> "UNSUPPORTED".equals(s.method())));
        assertTrue(preview.sources().stream().anyMatch(s -> "AI_UNAVAILABLE".equals(s.method())));
    }

    @Test
    void deduplicatesSpreadsheetProductsAndLimitsPreviewToFiveHundred() {
        BusinessImportPreviewService service = serviceWithoutAi(tenant());

        var first = new MockMultipartFile(
                "files", "a.csv", "text/csv",
                "SKU,Producto,Precio\nX-1,Papas,3490\n".getBytes(StandardCharsets.UTF_8));
        var duplicate = new MockMultipartFile(
                "files", "b.csv", "text/csv",
                "SKU,Producto,Precio\nX-1,Papas nuevas,3590\n".getBytes(StandardCharsets.UTF_8));

        StringBuilder many = new StringBuilder("SKU,Producto,Precio\n");
        for (int i = 0; i < 501; i++) {
            many.append("S").append(i).append(",Producto ").append(i).append(",1000\n");
        }
        var large = new MockMultipartFile(
                "files", "muchos.csv", "text/csv", many.toString().getBytes(StandardCharsets.UTF_8));

        var preview = service.preview("Negocio", List.of(first, duplicate, large));

        assertEquals(500, preview.products().size());
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("duplicado")));
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("limitada")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void semanticImageAndPdfUseAiAndReturnReviewedFacts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new JSONObject()
                .put("output_text", "{\"products\":[{\"name\":\"Doble Bacon\",\"price\":8490,\"currency\":\"CLP\",\"sku\":\"BAC-01\",\"onHand\":7,\"category\":\"Hamburguesas\",\"confidence\":0.9}],\"warnings\":[]}")
                .toString());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BusinessImportAiBudget budget = mock(BusinessImportAiBudget.class);
        when(budget.reserve()).thenReturn(true);
        BusinessImportPreviewService service =
                new BusinessImportPreviewService(new BusinessImportSpreadsheetParser(), aiProps(), tenant(), budget, mock(BusinessImportAiUsageLedger.class), http);
        ReflectionTestUtils.setField(service, "paidAiImportEnabled", true);

        var image = new MockMultipartFile(
                "files", "menu.png", "application/octet-stream", new byte[]{1, 2, 3});
        var pdf = new MockMultipartFile(
                "files", "menu.pdf", "application/octet-stream", new byte[]{4, 5, 6});

        var preview = service.preview("Don\nPepe", List.of(image, pdf));

        assertTrue(preview.aiUsed(), preview.warnings().toString());
        assertEquals(1, preview.products().size());
        assertEquals("Doble Bacon", preview.products().getFirst().name());
        assertEquals(7, preview.products().getFirst().onHand());
        assertEquals(2, preview.sources().size());
        assertTrue(preview.sources().stream().allMatch(s -> "AI".equals(s.method())));
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void semanticNestedOutputWithoutProductsIsUnknownSource() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("content", new JSONArray().put(new JSONObject()
                                .put("type", "output_text")
                                .put("text", "{\"products\":[],\"warnings\":[\"Solo horarios visibles\"]}")))))
                .toString());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BusinessImportAiBudget budget = mock(BusinessImportAiBudget.class);
        when(budget.reserve()).thenReturn(true);
        BusinessImportPreviewService service =
                new BusinessImportPreviewService(new BusinessImportSpreadsheetParser(), aiProps(), tenant(), budget, mock(BusinessImportAiUsageLedger.class), http);
        ReflectionTestUtils.setField(service, "paidAiImportEnabled", true);
        var jpg = new MockMultipartFile("files", "menu.jpg", "image/jpeg", new byte[]{1});

        var preview = service.preview("Negocio", List.of(jpg));

        assertTrue(preview.aiUsed(), preview.warnings().toString());
        assertTrue(preview.products().isEmpty());
        assertTrue(preview.warnings().contains("Solo horarios visibles"));
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN, preview.sources().getFirst().kind());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void semanticProviderFailureFailsClosedInsteadOfApplyingAnything() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("unavailable");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BusinessImportAiBudget budget = mock(BusinessImportAiBudget.class);
        when(budget.reserve()).thenReturn(true);
        BusinessImportPreviewService service =
                new BusinessImportPreviewService(new BusinessImportSpreadsheetParser(), aiProps(), tenant(), budget, mock(BusinessImportAiUsageLedger.class), http);
        ReflectionTestUtils.setField(service, "paidAiImportEnabled", true);
        var webp = new MockMultipartFile("files", "menu.webp", null, new byte[]{1});

        var preview = service.preview("Negocio", List.of(webp));

        assertFalse(preview.aiUsed());
        assertTrue(preview.products().isEmpty());
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("HTTP 503")));
        assertEquals("AI_ERROR", preview.sources().getFirst().method());
    }

    @Test
    void semanticParserHandlesInvalidFactsMissingFieldsAndLongValues() {
        String longDescription = "d".repeat(550);
        String json = "{"
                + "\"products\":["
                + "\"not-an-object\","
                + "{\"description\":\"sin nombre\"},"
                + "{\"name\":\"Producto\",\"description\":\"" + longDescription + "\",\"price\":\"abc\","
                + "\"currency\":\"PESOS\",\"onHand\":1.5,\"confidence\":\"x\",\"sourceName\":null}"
                + "],\"warnings\":[\"Aviso\",null]}";

        var result = BusinessImportPreviewService.parseSemanticResult(json, "fallback.pdf");

        assertEquals(1, result.products().size());
        var product = result.products().getFirst();
        assertEquals("Producto", product.name());
        assertEquals(500, product.description().length());
        assertNull(product.price());
        assertNull(product.onHand());
        assertEquals("CLP", product.currency());
        assertEquals(0.5, product.confidence());
        assertEquals("fallback.pdf", product.sourceName());
        assertEquals(List.of("Aviso"), result.warnings());

        assertThrows(IllegalArgumentException.class,
                () -> BusinessImportPreviewService.parseSemanticResult(null, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> BusinessImportPreviewService.parseSemanticResult("   ", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> BusinessImportPreviewService.parseSemanticResult("sin json", "x"));
    }

    @Test
    void previewWithoutProductsStillAddsActionableWarning() {
        BusinessImportPreviewService service = serviceWithoutAi(tenant());
        var sales = new MockMultipartFile(
                "files", "ventas.csv", "text/csv",
                "Fecha,Nro Boleta,Total\n2026-09-01,1,9990\n".getBytes(StandardCharsets.UTF_8));

        var preview = service.preview("Negocio", List.of(sales));

        assertTrue(preview.products().isEmpty());
        assertTrue(preview.warnings().stream().anyMatch(w -> w.contains("No encontré productos")));
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.SALES, preview.sources().getFirst().kind());
    }

    private static TenantProvider tenant() {
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        return tenant;
    }

    private static BusinessImportPreviewService serviceWithoutAi(TenantProvider tenant) {
        BusinessImportPreviewService service = new BusinessImportPreviewService(
                new BusinessImportSpreadsheetParser(), new OpenAiRealtimeProperties(), tenant);
        // Explicitly allow the provider branch so the missing-key fallback remains tested.
        ReflectionTestUtils.setField(service, "paidAiImportEnabled", true);
        return service;
    }

    private static OpenAiRealtimeProperties aiProps() {
        OpenAiRealtimeProperties props = new OpenAiRealtimeProperties();
        props.setApiKey("test-key");
        props.setResponsesUrl("https://example.invalid/v1/responses");
        props.setSummaryModel("test-model");
        return props;
    }

    private static MultipartFile mockFile(String name, String type, long size, boolean empty) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getContentType()).thenReturn(type);
        when(file.getSize()).thenReturn(size);
        when(file.isEmpty()).thenReturn(empty);
        return file;
    }
}
