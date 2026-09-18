package cl.helvoca.customer;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CustomerExportService {
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final CustomerRepository customers;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public CustomerExportService(
            CustomerRepository customers,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.customers = customers;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerExportFile export(String requestedFormat) {
        UUID businessId = tenantProvider.requireBusinessId();
        String format = normalizeFormat(requestedFormat);
        List<Customer> rows = customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        String date = LocalDate.now(ZoneOffset.UTC).toString();

        CustomerExportFile file = switch (format) {
            case "csv" -> new CustomerExportFile(
                    "helvoca-clientes-" + date + ".csv",
                    CSV_CONTENT_TYPE,
                    csv(rows));
            case "xlsx" -> new CustomerExportFile(
                    "helvoca-clientes-" + date + ".xlsx",
                    XLSX_CONTENT_TYPE,
                    xlsx(rows));
            default -> throw new IllegalStateException("Unsupported export format");
        };

        auditService.humanSuccess(
                businessId,
                "CUSTOMER_EXPORT",
                "CUSTOMER",
                null,
                null,
                Map.of("format", format.toUpperCase(Locale.ROOT), "count", rows.size()));
        return file;
    }

    private static String normalizeFormat(String value) {
        String format = value == null ? "csv" : value.trim().toLowerCase(Locale.ROOT);
        if (!format.equals("csv") && !format.equals("xlsx")) {
            throw new IllegalArgumentException("Formato de exportación no soportado. Usa csv o xlsx.");
        }
        return format;
    }

    private static byte[] csv(List<Customer> rows) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append("ID,Nombre,Telefono,Email,Notas,Creado,Actualizado\r\n");
        for (Customer customer : rows) {
            out.append(csvCell(customer.getId()))
                    .append(',').append(csvCell(customer.getName()))
                    .append(',').append(csvCell(customer.getPhone()))
                    .append(',').append(csvCell(customer.getEmail()))
                    .append(',').append(csvCell(customer.getNotes()))
                    .append(',').append(csvCell(customer.getCreatedAt()))
                    .append(',').append(csvCell(customer.getUpdatedAt()))
                    .append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvCell(Object value) {
        String text = spreadsheetSafe(value == null ? "" : String.valueOf(value));
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String spreadsheetSafe(String value) {
        if (value.isEmpty()) return value;
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static byte[] xlsx(List<Customer> rows) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zipEntry(zip, "[Content_Types].xml", contentTypes());
                zipEntry(zip, "_rels/.rels", rootRelationships());
                zipEntry(zip, "xl/workbook.xml", workbook());
                zipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
                zipEntry(zip, "xl/worksheets/sheet1.xml", worksheet(rows));
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo XLSX", ex);
        }
    }

    private static void zipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """;
    }

    private static String rootRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbook() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Clientes" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """;
    }

    private static String workbookRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;
    }

    private static String worksheet(List<Customer> rows) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
                  <cols>
                    <col min="1" max="1" width="38" customWidth="1"/>
                    <col min="2" max="2" width="24" customWidth="1"/>
                    <col min="3" max="3" width="18" customWidth="1"/>
                    <col min="4" max="4" width="30" customWidth="1"/>
                    <col min="5" max="5" width="45" customWidth="1"/>
                    <col min="6" max="7" width="24" customWidth="1"/>
                  </cols>
                  <sheetData>
                """);

        String[] headers = {"ID", "Nombre", "Telefono", "Email", "Notas", "Creado", "Actualizado"};
        xml.append(rowXml(1, headers));

        int rowNumber = 2;
        for (Customer customer : rows) {
            xml.append(rowXml(rowNumber++, new String[] {
                    value(customer.getId()),
                    value(customer.getName()),
                    value(customer.getPhone()),
                    value(customer.getEmail()),
                    value(customer.getNotes()),
                    value(customer.getCreatedAt()),
                    value(customer.getUpdatedAt())
            }));
        }

        int lastRow = Math.max(1, rowNumber - 1);
        xml.append("</sheetData><autoFilter ref=\"A1:G")
                .append(lastRow)
                .append("\"/></worksheet>");
        return xml.toString();
    }

    private static String rowXml(int row, String[] values) {
        StringBuilder xml = new StringBuilder("<row r=\"").append(row).append("\">");
        for (int column = 0; column < values.length; column++) {
            xml.append("<c r=\"")
                    .append(columnName(column + 1))
                    .append(row)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xmlEscape(values[column]))
                    .append("</t></is></c>");
        }
        return xml.append("</row>").toString();
    }

    private static String columnName(int number) {
        StringBuilder name = new StringBuilder();
        int value = number;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String xmlEscape(String value) {
        String clean = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return clean.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
