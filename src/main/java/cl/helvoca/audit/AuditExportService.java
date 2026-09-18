package cl.helvoca.audit;

import cl.helvoca.security.TenantProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AuditExportService {
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AuditQueryService queryService;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditExportService(
            AuditQueryService queryService,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.queryService = queryService;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public AuditExportFile export(
            String requestedFormat,
            String actor,
            String action,
            String resourceType,
            Instant fromAt,
            Instant toAt) {
        UUID businessId = tenantProvider.requireBusinessId();
        String format = normalizeFormat(requestedFormat);
        boolean filtered = hasText(actor) || hasText(action) || hasText(resourceType) || fromAt != null || toAt != null;
        List<AuditLogResponse> rows = filtered
                ? queryService.search(actor, action, resourceType, fromAt, toAt)
                : queryService.recent();
        String date = LocalDate.now(ZoneOffset.UTC).toString();

        AuditExportFile file = switch (format) {
            case "csv" -> new AuditExportFile(
                    "helvoca-auditoria-" + date + ".csv",
                    CSV_CONTENT_TYPE,
                    csv(rows));
            case "xlsx" -> new AuditExportFile(
                    "helvoca-auditoria-" + date + ".xlsx",
                    XLSX_CONTENT_TYPE,
                    xlsx(rows));
            default -> throw new IllegalStateException("Unsupported export format");
        };

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("format", format.toUpperCase(Locale.ROOT));
        metadata.put("count", rows.size());
        putIfPresent(metadata, "actor", actor);
        putIfPresent(metadata, "action", action);
        putIfPresent(metadata, "resourceType", resourceType);
        if (fromAt != null) metadata.put("from", fromAt.toString());
        if (toAt != null) metadata.put("to", toAt.toString());

        auditService.humanSuccess(
                businessId,
                "AUDIT_EXPORT",
                "AUDIT",
                null,
                null,
                metadata);
        return file;
    }

    private byte[] csv(List<AuditLogResponse> rows) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append("Fecha,Accion,Recurso,Recurso ID,Resultado,Actor,Email,Rol,Tipo actor,Antes,Despues\r\n");
        for (AuditLogResponse row : rows) {
            out.append(csvCell(row.createdAt()))
                    .append(',').append(csvCell(row.action()))
                    .append(',').append(csvCell(row.resourceType()))
                    .append(',').append(csvCell(row.resourceId()))
                    .append(',').append(csvCell(row.result()))
                    .append(',').append(csvCell(row.actorName()))
                    .append(',').append(csvCell(row.actorEmail()))
                    .append(',').append(csvCell(row.actorRole()))
                    .append(',').append(csvCell(row.actorType()))
                    .append(',').append(csvCell(json(row.beforeState())))
                    .append(',').append(csvCell(json(row.afterState())))
                    .append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<AuditLogResponse> rows) {
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
            throw new IllegalStateException("No se pudo generar el archivo XLSX de auditoría", ex);
        }
    }

    private String worksheet(List<AuditLogResponse> rows) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
                  <cols>
                    <col min="1" max="1" width="24" customWidth="1"/>
                    <col min="2" max="3" width="24" customWidth="1"/>
                    <col min="4" max="4" width="38" customWidth="1"/>
                    <col min="5" max="10" width="24" customWidth="1"/>
                    <col min="11" max="11" width="48" customWidth="1"/>
                  </cols>
                  <sheetData>
                """);

        xml.append(rowXml(1, new String[] {
                "Fecha", "Accion", "Recurso", "Recurso ID", "Resultado",
                "Actor", "Email", "Rol", "Tipo actor", "Antes", "Despues"
        }));

        int rowNumber = 2;
        for (AuditLogResponse row : rows) {
            xml.append(rowXml(rowNumber++, new String[] {
                    value(row.createdAt()),
                    value(row.action()),
                    value(row.resourceType()),
                    value(row.resourceId()),
                    value(row.result()),
                    value(row.actorName()),
                    value(row.actorEmail()),
                    value(row.actorRole()),
                    value(row.actorType()),
                    json(row.beforeState()),
                    json(row.afterState())
            }));
        }

        int lastRow = Math.max(1, rowNumber - 1);
        xml.append("</sheetData><autoFilter ref=\"A1:K")
                .append(lastRow)
                .append("\"/></worksheet>");
        return xml.toString();
    }

    private String json(Map<String, Object> value) {
        if (value == null) return "";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el estado de auditoría", ex);
        }
    }

    private static String normalizeFormat(String value) {
        String format = value == null ? "csv" : value.trim().toLowerCase(Locale.ROOT);
        if (!format.equals("csv") && !format.equals("xlsx")) {
            throw new IllegalArgumentException("Formato de exportación no soportado. Usa csv o xlsx.");
        }
        return format;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (hasText(value)) target.put(key, value.trim());
    }

    private static String csvCell(Object value) {
        String text = spreadsheetSafe(value(value));
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

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
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
                  <sheets><sheet name="Auditoria" sheetId="1" r:id="rId1"/></sheets>
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

    private static String xmlEscape(String value) {
        String clean = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return clean.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
