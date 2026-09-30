package cl.helvoca.onboarding;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessImportSpreadsheetParserCoverageTest {

    private final BusinessImportSpreadsheetParser parser = new BusinessImportSpreadsheetParser();

    @Test
    void handlesEmptyOversizedUnsupportedAndBrokenWorkbookSafely() throws Exception {
        var empty = new MockMultipartFile("files", "empty.csv", "text/csv", new byte[0]);
        assertFalse(parser.parse(empty).recognized());

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(10L * 1024L * 1024L + 1L);
        when(oversized.getOriginalFilename()).thenReturn("huge.xlsx");
        assertFalse(parser.parse(oversized).recognized());
        verify(oversized, never()).getBytes();

        var unsupported = new MockMultipartFile(
                "files", "catalog.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        var unsupportedResult = parser.parse(unsupported);
        assertFalse(unsupportedResult.recognized());
        assertTrue(unsupportedResult.warnings().getFirst().contains("CSV/XLSX"));

        var broken = new MockMultipartFile(
                "files", "broken.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3, 4, 5});
        var brokenResult = parser.parse(broken);
        assertFalse(brokenResult.recognized());
        assertTrue(brokenResult.warnings().getFirst().contains("leer"));
    }

    @Test
    void parsesBomSemicolonCrLfQuotedCellsAndTextContentType() {
        String csv = "\uFEFFCódigo;Producto;Precio;Stock;Categoría;Descripción\r\n"
                + "HAM-01;\"Hamburguesa \"\"Doble\"\"\";7.990;5;Hamburguesas;\"Carne; queso\"\r\n";

        var file = new MockMultipartFile("files", null, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var result = parser.parse(file);

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS, result.kind());
        assertEquals(1, result.products().size());
        assertEquals("Hamburguesa \"Doble\"", result.products().getFirst().name());
        assertEquals("7990", result.products().getFirst().price().toPlainString());
        assertEquals("Carne; queso", result.products().getFirst().description());
    }

    @Test
    void parsesTabsAndCommonMoneyAndStockFormats() {
        String tsv = """
                SKU\tProducto\tPrecio\tStock
                A\tEuropeo\t1.234,56\t10
                B\tUS\t1,234.56\t11
                C\tEntero\t$1.990\t2.5
                D\tComa decimal\t1,99\t-4
                """;
        var file = new MockMultipartFile(
                "files", "productos.tsv", "text/tab-separated-values", tsv.getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(file);

        assertEquals(4, result.products().size());
        assertEquals("1234.56", result.products().get(0).price().toPlainString());
        assertEquals("1234.56", result.products().get(1).price().toPlainString());
        assertEquals("1990", result.products().get(2).price().toPlainString());
        assertNull(result.products().get(2).onHand());
        assertEquals("1.99", result.products().get(3).price().toPlainString());
        assertNull(result.products().get(3).onHand());
    }

    @Test
    void recognizesReceiptCustomerAndMissingProductNameCases() {
        var receipt = new MockMultipartFile(
                "files", "boletas.csv", "text/csv",
                "Nro Boleta,Total\n1001,14980\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.RECEIPTS, parser.parse(receipt).kind());

        var customers = new MockMultipartFile(
                "files", "clientes.csv", "text/csv",
                "Cliente,Correo\nAna,ana@example.com\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.CUSTOMERS, parser.parse(customers).kind());

        var missingName = new MockMultipartFile(
                "files", "productos.csv", "text/csv",
                "Producto,Precio,Stock\n,6990,4\n".getBytes(StandardCharsets.UTF_8));
        var missing = parser.parse(missingName);
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS, missing.kind());
        assertTrue(missing.products().isEmpty());
        assertTrue(missing.warnings().stream().anyMatch(w -> w.contains("nombre válido")));
    }

    @Test
    void findsHeadersAfterLeadingNoiseAndRejectsSingleColumnText() {
        var withNoise = new MockMultipartFile(
                "files", "productos.csv", "text/csv",
                "Reporte mensual\n\nSKU,Producto,Precio\nX-1,Papas,3490\n".getBytes(StandardCharsets.UTF_8));
        var parsed = parser.parse(withNoise);
        assertTrue(parsed.recognized());
        assertEquals("Papas", parsed.products().getFirst().name());

        var singleColumn = new MockMultipartFile(
                "files", "notas.csv", "text/csv",
                "Solo una columna\notra\n".getBytes(StandardCharsets.UTF_8));
        var rejected = parser.parse(singleColumn);
        assertFalse(rejected.recognized());
        assertTrue(rejected.warnings().getFirst().contains("encabezados"));
    }

    @Test
    void workbookCanCombineProductAndSalesSheetsIntoMixedDataset() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet products = workbook.createSheet("Productos");
            Row p0 = products.createRow(0);
            p0.createCell(0).setCellValue("SKU");
            p0.createCell(1).setCellValue("Producto");
            p0.createCell(2).setCellValue("Precio");
            p0.createCell(3).setCellValue("Stock");
            Row p1 = products.createRow(1);
            p1.createCell(0).setCellValue("HAM-01");
            p1.createCell(1).setCellValue("Hamburguesa");
            p1.createCell(2).setCellValue(6990);
            p1.createCell(3).setCellValue(7);
            products.createRow(3).createCell(3).setCellValue("");

            Sheet sales = workbook.createSheet("Ventas");
            Row s0 = sales.createRow(0);
            s0.createCell(0).setCellValue("Fecha");
            s0.createCell(1).setCellValue("Nro Boleta");
            s0.createCell(2).setCellValue("Total");
            Row s1 = sales.createRow(1);
            s1.createCell(0).setCellValue("2026-09-01");
            s1.createCell(1).setCellValue("1001");
            s1.createCell(2).setCellValue(6990);

            workbook.createSheet("Vacía");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        var file = new MockMultipartFile(
                "files", "negocio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var result = parser.parse(file);

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.MIXED, result.kind());
        assertEquals(1, result.products().size());
        assertEquals(2, result.rowCount());
        assertTrue(result.extractedText().contains("HOJA: Productos"));
        assertTrue(result.extractedText().contains("HOJA: Ventas"));
    }

    @Test
    void workbookWithOnlyUnknownSheetFailsClosed() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Raro");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Columna rara");
            h.createCell(1).setCellValue("Otra cosa");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("abc");
            row.createCell(1).setCellValue("123");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        var file = new MockMultipartFile(
                "files", "raro.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var result = parser.parse(file);

        assertFalse(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN, result.kind());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void negativeAndInvalidNumbersBecomeUnknownFactsInsteadOfInventedValues() {
        String csv = """
                Producto,Precio,Stock
                Uno,-1,-2
                Dos,abc,999999999999999999999
                Tres,1.2.3,3
                """;
        var file = new MockMultipartFile(
                "files", "invalidos.csv", "application/csv", csv.getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(file);

        assertEquals(3, result.products().size());
        assertNull(result.products().get(0).price());
        assertNull(result.products().get(0).onHand());
        assertNull(result.products().get(1).price());
        assertNull(result.products().get(1).onHand());
        assertNull(result.products().get(2).price());
        assertEquals(3, result.products().get(2).onHand());
    }
}
