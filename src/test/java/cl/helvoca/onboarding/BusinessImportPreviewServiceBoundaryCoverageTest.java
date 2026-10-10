package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Provider-side defense in depth: a future caller must not bypass the
 * already-enforced preview limits. All checks stay local (no paid requests).
 */
class BusinessImportPreviewServiceBoundaryCoverageTest {
    private static BusinessImportPreviewService service(HttpClient http, OpenAiRealtimeProperties props) {
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        return new BusinessImportPreviewService(
                new BusinessImportSpreadsheetParser(), props, tenant, null, http);
    }

    private static OpenAiRealtimeProperties configuredAi() {
        OpenAiRealtimeProperties props = new OpenAiRealtimeProperties();
        props.setApiKey("offline-test-key");
        props.setResponsesUrl("https://example.invalid/v1/responses");
        return props;
    }

    private static MockMultipartFile file(String filename, String mime, byte value) {
        return new MockMultipartFile("files", filename, mime, new byte[]{value});
    }

    @Test
    void anOptedInTenantWithoutBudgetNeverReachesProvider() {
        HttpClient http = mock(HttpClient.class);
        BusinessImportPreviewService preview = service(http, configuredAi());
        ReflectionTestUtils.setField(preview, "paidAiImportEnabled", true);

        var result = preview.preview("Tienda", List.of(file("photo.jpg", "image/jpeg", (byte) 1)));

        assertFalse(result.aiUsed());
        assertEquals("AI_BUDGET_EXCEEDED", result.sources().getFirst().method());
        verifyNoInteractions(http);
    }

    @Test
    void semanticMimeHeaderFallbackRecognizesOnlyFourAllowedTypesWithoutProviderCalls() {
        HttpClient http = mock(HttpClient.class);
        BusinessImportPreviewService preview = service(http, new OpenAiRealtimeProperties());
        var result = preview.preview("Tienda", List.of(
                file("opaque-a.bin", "application/pdf", (byte) 1),
                file("opaque-b.bin", "image/jpeg", (byte) 2),
                file("opaque-c.bin", "image/png", (byte) 3),
                file("opaque-d.bin", "image/webp", (byte) 4),
                file("opaque-e.bin", "application/octet-stream", (byte) 5)));

        assertEquals(4, result.sources().stream().filter(source -> "AI_DISABLED".equals(source.method())).count());
        assertEquals(1, result.sources().stream().filter(source -> "UNSUPPORTED".equals(source.method())).count());
        assertFalse(result.aiUsed());
        verifyNoInteractions(http);
    }

    @Test
    void normalizedMimeUsesOnlyApprovedHeadersWhenExtensionIsUnknown() throws Exception {
        Method normalized = BusinessImportPreviewService.class.getDeclaredMethod("normalizedMime", MultipartFile.class);
        normalized.setAccessible(true);

        for (String mime : List.of("application/pdf", "image/png", "image/webp", "image/jpeg")) {
            assertEquals(mime, normalized.invoke(null, file("blob.unknown", mime, (byte) 8)));
        }

        for (String invalid : List.of("image/gif", "text/plain", "application/octet-stream")) {
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                    () -> normalized.invoke(null, file("blob.unknown", invalid, (byte) 8)));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
        InvocationTargetException absent = assertThrows(InvocationTargetException.class,
                () -> normalized.invoke(null, file("blob.unknown", null, (byte) 8)));
        assertInstanceOf(IllegalArgumentException.class, absent.getCause());

        // An explicit trusted suffix wins over a forged content-type header.
        assertEquals("image/png", normalized.invoke(null, file("menu.png", "image/gif", (byte) 9)));
        assertEquals("image/jpeg", normalized.invoke(null, file("menu.jpeg", "text/plain", (byte) 9)));
        assertEquals("image/jpeg", normalized.invoke(null, file("menu.jpg", null, (byte) 9)));
    }

    @Test
    void semanticBuilderRejectsTooManyFilesAndExcessBytesBeforeHttp() throws Exception {
        HttpClient http = mock(HttpClient.class);
        BusinessImportPreviewService preview = service(http, configuredAi());
        Method analyze = BusinessImportPreviewService.class.getDeclaredMethod(
                "analyzeSemantic", String.class, List.class);
        analyze.setAccessible(true);

        List<MultipartFile> four = List.of(
                file("one.png", "image/png", (byte) 1),
                file("two.png", "image/png", (byte) 2),
                file("three.png", "image/png", (byte) 3),
                file("four.png", "image/png", (byte) 4));
        InvocationTargetException count = assertThrows(InvocationTargetException.class,
                () -> analyze.invoke(preview, "Negocio", four));
        assertInstanceOf(IllegalArgumentException.class, count.getCause());
        assertTrue(count.getCause().getMessage().contains("cantidad"));

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(4L * 1024 * 1024 + 1);
        InvocationTargetException bytes = assertThrows(InvocationTargetException.class,
                () -> analyze.invoke(preview, "Negocio", List.of(oversized)));
        assertInstanceOf(IllegalArgumentException.class, bytes.getCause());
        assertTrue(bytes.getCause().getMessage().contains("límite"));
        verify(oversized, never()).getBytes();
        verifyNoInteractions(http);
    }
}
