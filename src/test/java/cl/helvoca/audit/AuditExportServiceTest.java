package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditExportServiceTest {

    @Test
    void exportsFilteredAuditAsSafeCsvAndAuditsDownload() {
        UUID businessId = UUID.randomUUID();
        AuditQueryService queryService = mock(AuditQueryService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        Instant from = Instant.parse("2026-09-18T00:00:00Z");
        Instant to = Instant.parse("2026-09-20T00:00:00Z");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(queryService.search("Carolina", "BOOKING_RESCHEDULE", "BOOKING", from, to))
                .thenReturn(List.of(sample("=SUM(1,1)")));

        AuditExportFile file = new AuditExportService(
                queryService, tenantProvider, auditService)
                .export("csv", "Carolina", "BOOKING_RESCHEDULE", "BOOKING", from, to);

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFFecha,Accion,Recurso,Recurso ID"));
        assertTrue(csv.contains("\"'=SUM(1,1)\""));
        assertTrue(file.filename().endsWith(".csv"));

        verify(queryService).search("Carolina", "BOOKING_RESCHEDULE", "BOOKING", from, to);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("AUDIT_EXPORT"),
                eq("AUDIT"),
                isNull(),
                isNull(),
                argThat(after -> "CSV".equals(after.get("format"))
                        && Integer.valueOf(1).equals(after.get("count"))
                        && "Carolina".equals(after.get("actor"))));
    }

    @Test
    void exportsAuditAsRealXlsxContainer() throws Exception {
        UUID businessId = UUID.randomUUID();
        AuditQueryService queryService = mock(AuditQueryService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(queryService.recent()).thenReturn(List.of(sample("Carolina Soto")));

        AuditExportFile file = new AuditExportService(
                queryService, tenantProvider, auditService)
                .export("xlsx", null, null, null, null, null);

        assertTrue(file.filename().endsWith(".xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.contentType());

        String sheet = unzipEntry(file.content(), "xl/worksheets/sheet1.xml");
        assertNotNull(sheet);
        assertTrue(sheet.contains("Carolina Soto"));
        assertTrue(sheet.contains("BOOKING_RESCHEDULE"));
        assertTrue(sheet.contains("autoFilter"));
        assertNotNull(unzipEntry(file.content(), "xl/workbook.xml"));

        verify(queryService).recent();
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("AUDIT_EXPORT"),
                eq("AUDIT"),
                isNull(),
                isNull(),
                argThat(after -> "XLSX".equals(after.get("format"))
                        && Integer.valueOf(1).equals(after.get("count"))));
    }

    @Test
    void rejectsUnknownFormatBeforeQueryingAuditRows() {
        AuditQueryService queryService = mock(AuditQueryService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        when(tenantProvider.requireBusinessId()).thenReturn(UUID.randomUUID());

        AuditExportService service = new AuditExportService(
                queryService, tenantProvider, auditService);

        assertThrows(IllegalArgumentException.class,
                () -> service.export("pdf", null, null, null, null, null));
        verifyNoInteractions(queryService);
        verifyNoInteractions(auditService);
    }

    private static AuditLogResponse sample(String actorName) {
        return new AuditLogResponse(
                UUID.randomUUID(),
                "BOOKING_RESCHEDULE",
                "BOOKING",
                UUID.randomUUID(),
                "SUCCESS",
                "HUMAN",
                UUID.randomUUID(),
                actorName,
                "carolina@example.com",
                "OPERATOR",
                Map.of("status", "CONFIRMED"),
                Map.of("status", "CONFIRMED", "startAt", "2026-09-18T15:00:00Z"),
                Instant.parse("2026-09-18T18:05:00Z"));
    }

    private static String unzipEntry(byte[] content, String expectedName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (expectedName.equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
