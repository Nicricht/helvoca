package cl.helvoca.customer;

import cl.helvoca.audit.AuditService;
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

class CustomerExportServiceTest {

    @Test
    void exportsTenantCustomersAsSafeUtf8CsvAndAuditsDownload() {
        UUID businessId = UUID.randomUUID();
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        Customer customer = customer("=SUM(1,1)", "+56922222222", "ana@example.cl", "@nota");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(customer));

        CustomerExportFile file =
                new CustomerExportService(customers, tenantProvider, auditService).export("csv");

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFID,Nombre,Telefono,Email,Notas,Creado,Actualizado"));
        assertTrue(csv.contains("\"'=SUM(1,1)\""));
        assertTrue(csv.contains("\"'@nota\""));
        assertTrue(file.filename().endsWith(".csv"));
        assertEquals("text/csv;charset=UTF-8", file.contentType());

        verify(customers).findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("CUSTOMER_EXPORT"),
                eq("CUSTOMER"),
                isNull(),
                isNull(),
                eq(Map.of("format", "CSV", "count", 1)));
    }

    @Test
    void exportsRealXlsxContainerWithCustomerWorksheet() throws Exception {
        UUID businessId = UUID.randomUUID();
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        Customer customer = customer("Ana Reserva", "+56922222222", "ana@example.cl", "Cliente frecuente");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(customer));

        CustomerExportFile file =
                new CustomerExportService(customers, tenantProvider, auditService).export("xlsx");

        assertTrue(file.filename().endsWith(".xlsx"));
        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                file.contentType());

        String sheetXml = unzipEntry(file.content(), "xl/worksheets/sheet1.xml");
        assertNotNull(sheetXml);
        assertTrue(sheetXml.contains("Ana Reserva"));
        assertTrue(sheetXml.contains("+56922222222"));
        assertTrue(sheetXml.contains("autoFilter"));
        assertNotNull(unzipEntry(file.content(), "[Content_Types].xml"));
        assertNotNull(unzipEntry(file.content(), "xl/workbook.xml"));

        verify(customers).findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("CUSTOMER_EXPORT"),
                eq("CUSTOMER"),
                isNull(),
                isNull(),
                eq(Map.of("format", "XLSX", "count", 1)));
    }

    @Test
    void rejectsUnknownExportFormatBeforeReadingCustomers() {
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        CustomerExportService service = new CustomerExportService(customers, tenantProvider, auditService);

        assertThrows(IllegalArgumentException.class, () -> service.export("pdf"));
        verifyNoInteractions(customers);
        verifyNoInteractions(auditService);
    }

    private static Customer customer(String name, String phone, String email, String notes) {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(customer.getName()).thenReturn(name);
        when(customer.getPhone()).thenReturn(phone);
        when(customer.getEmail()).thenReturn(email);
        when(customer.getNotes()).thenReturn(notes);
        when(customer.getCreatedAt()).thenReturn(Instant.parse("2026-09-17T10:00:00Z"));
        when(customer.getUpdatedAt()).thenReturn(Instant.parse("2026-09-18T10:00:00Z"));
        return customer;
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
