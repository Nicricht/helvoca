package cl.helvoca.onboarding;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class BusinessImportSpreadsheetParser {
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_ROWS_PER_SHEET = 5_000;
    private static final int MAX_COLUMNS = 80;
    private static final int MAX_EXTRACTED_CHARS = 200_000;

    private static final Set<String> NAME_HEADERS = Set.of(
            "producto", "product", "nombre", "nombre producto", "articulo", "item", "servicio");
    private static final Set<String> PRICE_HEADERS = Set.of(
            "precio", "price", "valor", "precio venta", "precio unitario", "p venta");
    private static final Set<String> SKU_HEADERS = Set.of(
            "sku", "codigo", "cod", "codigo producto", "codigo articulo", "id producto");
    private static final Set<String> STOCK_HEADERS = Set.of(
            "stock", "cantidad", "existencia", "existencias", "inventario", "on hand", "unidades");
    private static final Set<String> CATEGORY_HEADERS = Set.of(
            "categoria", "category", "familia", "grupo", "rubro");
    private static final Set<String> DESCRIPTION_HEADERS = Set.of(
            "descripcion", "description", "detalle", "observacion", "observaciones");
    private static final Set<String> DATE_HEADERS = Set.of(
            "fecha", "date", "fecha venta", "fecha emision");
    private static final Set<String> TOTAL_HEADERS = Set.of(
            "total", "monto", "importe", "total venta", "venta total");
    private static final Set<String> RECEIPT_HEADERS = Set.of(
            "boleta", "nro boleta", "numero boleta", "folio", "documento", "nro documento", "factura");
    private static final Set<String> CUSTOMER_HEADERS = Set.of(
            "cliente", "customer", "nombre cliente", "rut", "run", "email", "correo", "telefono");

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return unknown("El archivo está vacío.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return unknown("El archivo supera el límite de 10 MB.");
        }

        String filename = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")
                    || isTextContentType(file.getContentType())) {
                return parseDelimited(filename, new String(file.getBytes(), StandardCharsets.UTF_8));
            }
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return parseWorkbook(filename, file.getBytes());
            }
            return unknown("El archivo no es una planilla CSV/XLSX reconocida.");
        } catch (Exception e) {
            return unknown("No pude leer la planilla de forma segura: " + safeMessage(e));
        }
    }

    private ParseResult parseDelimited(String sourceName, String text) {
        String cleaned = stripBom(text);
        if (cleaned.isBlank()) return unknown("La planilla no contiene filas.");

        char delimiter = detectDelimiter(cleaned);
        List<List<String>> rows = parseDelimitedRows(cleaned, delimiter);
        if (rows.isEmpty()) return unknown("La planilla no contiene filas.");
        return parseTable(sourceName, null, rows);
    }

    private ParseResult parseWorkbook(String sourceName, byte[] bytes) throws Exception {
        List<ProductRow> products = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder extracted = new StringBuilder();
        int rowCount = 0;
        List<DatasetKind> kinds = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-CL"));
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                List<List<String>> table = new ArrayList<>();
                int last = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET);
                if (sheet.getLastRowNum() > MAX_ROWS_PER_SHEET) {
                    warnings.add("La hoja " + sheet.getSheetName() + " fue limitada a "
                            + MAX_ROWS_PER_SHEET + " filas para proteger el importador.");
                }
                for (int r = 0; r <= last; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int cells = Math.min(Math.max(row.getLastCellNum(), 0), MAX_COLUMNS);
                    List<String> values = new ArrayList<>(cells);
                    boolean any = false;
                    for (int c = 0; c < cells; c++) {
                        Cell cell = row.getCell(c);
                        String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                        values.add(value);
                        any |= !value.isBlank();
                    }
                    if (any) table.add(values);
                }
                if (table.isEmpty()) continue;

                ParseResult one = parseTable(sourceName, sheet.getSheetName(), table);
                if (one.recognized()) kinds.add(one.kind());
                products.addAll(one.products());
                warnings.addAll(one.warnings());
                rowCount += one.rowCount();

                appendLimited(extracted, "\nHOJA: " + sheet.getSheetName() + "\n");
                appendLimited(extracted, one.extractedText());
            }
        }

        DatasetKind kind = mergeKinds(kinds);
        boolean recognized = kind != DatasetKind.UNKNOWN;
        if (!recognized && warnings.isEmpty()) {
            warnings.add("No reconocí columnas de productos, ventas, boletas o clientes.");
        }
        return new ParseResult(recognized, kind, List.copyOf(products), rowCount,
                extracted.toString().trim(), List.copyOf(dedupe(warnings)));
    }

    private ParseResult parseTable(String sourceName, String sheetName, List<List<String>> rows) {
        int headerIndex = firstUsefulRow(rows);
        if (headerIndex < 0) return unknown("La planilla no contiene encabezados utilizables.");

        List<String> headers = rows.get(headerIndex);
        Map<String, Integer> columns = normalizedColumns(headers);
        DatasetKind kind = classify(columns.keySet());

        List<ProductRow> products = new ArrayList<>();
        int dataRows = 0;
        StringBuilder extracted = new StringBuilder();
        appendLimited(extracted, String.join(" | ", headers) + "\n");

        int max = Math.min(rows.size(), headerIndex + 1 + MAX_ROWS_PER_SHEET);
        for (int i = headerIndex + 1; i < max; i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(String::isBlank)) continue;
            dataRows++;
            appendLimited(extracted, String.join(" | ", row) + "\n");
            if (kind == DatasetKind.PRODUCTS || kind == DatasetKind.MIXED) {
                ProductRow product = productFromRow(sourceName, sheetName, i + 1, columns, row);
                if (product != null) products.add(product);
            }
        }

        List<String> warnings = new ArrayList<>();
        if (kind == DatasetKind.UNKNOWN) {
            warnings.add("No reconocí columnas de productos, ventas, boletas o clientes.");
        } else if ((kind == DatasetKind.PRODUCTS || kind == DatasetKind.MIXED) && products.isEmpty()) {
            warnings.add("La hoja parece contener productos, pero no encontré filas con nombre válido.");
        }

        return new ParseResult(kind != DatasetKind.UNKNOWN, kind, List.copyOf(products),
                dataRows, extracted.toString().trim(), List.copyOf(warnings));
    }

    private static ProductRow productFromRow(String sourceName,
                                             String sheetName,
                                             int sourceRow,
                                             Map<String, Integer> columns,
                                             List<String> row) {
        String name = value(row, first(columns, NAME_HEADERS));
        if (name == null || name.isBlank()) return null;

        String rawPrice = value(row, first(columns, PRICE_HEADERS));
        String rawStock = value(row, first(columns, STOCK_HEADERS));
        BigDecimal price = parseDecimal(rawPrice);
        Integer onHand = parseInteger(rawStock);

        return new ProductRow(
                truncate(name.trim(), 150),
                nullable(value(row, first(columns, DESCRIPTION_HEADERS)), 500),
                price,
                "CLP",
                nullable(value(row, first(columns, SKU_HEADERS)), 80),
                onHand,
                nullable(value(row, first(columns, CATEGORY_HEADERS)), 120),
                sourceName,
                sheetName,
                sourceRow
        );
    }

    private static DatasetKind classify(Set<String> headers) {
        boolean name = containsAny(headers, NAME_HEADERS);
        boolean productSignals = containsAny(headers, PRICE_HEADERS)
                || containsAny(headers, SKU_HEADERS)
                || containsAny(headers, STOCK_HEADERS)
                || containsAny(headers, CATEGORY_HEADERS);
        boolean date = containsAny(headers, DATE_HEADERS);
        boolean total = containsAny(headers, TOTAL_HEADERS);
        boolean receipt = containsAny(headers, RECEIPT_HEADERS);
        boolean customer = containsAny(headers, CUSTOMER_HEADERS);

        // Transaction evidence has priority over product-like columns.
        // Sales exports often contain Producto/Precio/Cantidad per receipt line and must never become live catalog/stock.
        if (date && total && receipt) return DatasetKind.SALES;
        if (receipt && total) return DatasetKind.RECEIPTS;
        if (name && productSignals) return DatasetKind.PRODUCTS;
        if (customer && !productSignals && (name || headers.contains("cliente") || headers.contains("nombre cliente"))) {
            return DatasetKind.CUSTOMERS;
        }
        return DatasetKind.UNKNOWN;
    }

    private static DatasetKind mergeKinds(List<DatasetKind> kinds) {
        DatasetKind found = DatasetKind.UNKNOWN;
        for (DatasetKind kind : kinds) {
            if (kind == DatasetKind.UNKNOWN) continue;
            if (found == DatasetKind.UNKNOWN) found = kind;
            else if (found != kind) return DatasetKind.MIXED;
        }
        return found;
    }

    private static Map<String, Integer> normalizedColumns(List<String> headers) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(headers.size(), MAX_COLUMNS); i++) {
            String normalized = normalize(headers.get(i));
            if (!normalized.isBlank()) columns.putIfAbsent(normalized, i);
        }
        return columns;
    }

    private static int firstUsefulRow(List<List<String>> rows) {
        for (int i = 0; i < Math.min(rows.size(), 20); i++) {
            long nonBlank = rows.get(i).stream().filter(v -> v != null && !v.isBlank()).count();
            if (nonBlank >= 2) return i;
        }
        return -1;
    }

    private static List<List<String>> parseDelimitedRows(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS_PER_SHEET + 20) break;
            } else {
                cell.append(ch);
            }
        }
        row.add(cell.toString().trim());
        if (row.stream().anyMatch(v -> !v.isBlank()) && rows.size() <= MAX_ROWS_PER_SHEET + 20) rows.add(row);
        return rows;
    }

    private static char detectDelimiter(String text) {
        String first = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        int commas = count(first, ',');
        int semicolons = count(first, ';');
        int tabs = count(first, '\t');
        if (tabs >= commas && tabs >= semicolons && tabs > 0) return '\t';
        return semicolons > commas ? ';' : ',';
    }

    private static int count(String value, char needle) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == needle) count++;
        return count;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private static boolean containsAny(Set<String> headers, Set<String> aliases) {
        for (String alias : aliases) if (headers.contains(alias)) return true;
        return false;
    }

    private static Integer first(Map<String, Integer> columns, Set<String> aliases) {
        for (String alias : aliases) {
            Integer index = columns.get(alias);
            if (index != null) return index;
        }
        return null;
    }

    private static String value(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) return null;
        String value = row.get(index);
        return value == null ? null : value.trim();
    }

    static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().replaceAll("[^0-9,.-]", "");
        if (value.isBlank() || "-".equals(value)) return null;

        try {
            int comma = value.lastIndexOf(',');
            int dot = value.lastIndexOf('.');
            if (comma >= 0 && dot >= 0) {
                if (comma > dot) {
                    value = value.replace(".", "").replace(',', '.');
                } else {
                    value = value.replace(",", "");
                }
            } else if (comma >= 0) {
                int decimals = value.length() - comma - 1;
                value = decimals > 0 && decimals <= 2 ? value.replace(',', '.') : value.replace(",", "");
            } else if (dot >= 0) {
                int decimals = value.length() - dot - 1;
                if (decimals == 3 && value.indexOf('.') == dot) value = value.replace(".", "");
            }
            BigDecimal parsed = new BigDecimal(value);
            return parsed.signum() < 0 ? null : parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String raw) {
        BigDecimal value = parseDecimal(raw);
        if (value == null || value.scale() > 0 && value.stripTrailingZeros().scale() > 0) return null;
        try {
            int parsed = value.intValueExact();
            return parsed < 0 ? null : parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendLimited(StringBuilder target, String value) {
        if (value == null || target.length() >= MAX_EXTRACTED_CHARS) return;
        int remaining = MAX_EXTRACTED_CHARS - target.length();
        target.append(value, 0, Math.min(value.length(), remaining));
    }

    private static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private static boolean isTextContentType(String contentType) {
        return contentType != null && (contentType.startsWith("text/")
                || contentType.equalsIgnoreCase("application/csv")
                || contentType.equalsIgnoreCase("application/vnd.ms-excel"));
    }

    private static String nullable(String value, int max) {
        return value == null || value.isBlank() ? null : truncate(value.trim(), max);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String safeMessage(Exception e) {
        String value = e.getMessage();
        return value == null || value.isBlank() ? e.getClass().getSimpleName() : truncate(value, 180);
    }

    private static List<String> dedupe(List<String> warnings) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(warnings));
    }

    private static ParseResult unknown(String warning) {
        return new ParseResult(false, DatasetKind.UNKNOWN, List.of(), 0, "", List.of(warning));
    }

    public enum DatasetKind {
        PRODUCTS,
        SALES,
        RECEIPTS,
        CUSTOMERS,
        MIXED,
        UNKNOWN
    }

    public record ProductRow(
            String name,
            String description,
            BigDecimal price,
            String currency,
            String sku,
            Integer onHand,
            String category,
            String sourceName,
            String sheetName,
            int sourceRow
    ) {}

    public record ParseResult(
            boolean recognized,
            DatasetKind kind,
            List<ProductRow> products,
            int rowCount,
            String extractedText,
            List<String> warnings
    ) {}
}
