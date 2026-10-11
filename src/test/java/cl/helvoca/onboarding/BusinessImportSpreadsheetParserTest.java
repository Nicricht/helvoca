package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BusinessImportSpreadsheetParserTest {

    @Test
    void recognizesFreeFaqRowsWithExactProvenanceAndRejectsMissingAnswers() {
        String csv = "Pregunta;Respuesta\n¿Hay estacionamiento?;Sí, en la entrada\n¿Horario?;\n";
        var file = new MockMultipartFile("files", "faq.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        var result = new BusinessImportSpreadsheetParser().parse(file);

        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.FAQS, result.kind());
        assertTrue(result.recognized());
        assertEquals(2, result.rowCount());
        assertTrue(result.products().isEmpty());
        assertEquals(1, result.setupRows().size());
        var first = result.setupRows().getFirst();
        assertEquals("FAQ", first.kind());
        assertEquals("¿Hay estacionamiento?", first.key());
        assertEquals("Sí, en la entrada", first.value());
        assertEquals("faq.csv", first.sourceName());
        assertNull(first.sheetName());
        assertEquals(2, first.sourceRow());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("incompleta")));
    }

    @Test
    void validatesExplicitWeekdayAndTimeRangeWithoutGuessingOpeningHours() {
        String csv = """
                Día,Apertura,Cierre
                Lunes,09:00,18:00
                Sábado,10:00,13:30
                Feriados,09:00,18:00
                Martes,18:00,09:00
                Miércoles,9:00,18:00
                Jueves,12:00,
                Viernes,10:00,10:00
                Domingo,00:00,23:59
                """;
        var file = new MockMultipartFile("files", "horario.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        var result = new BusinessImportSpreadsheetParser().parse(file);
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.BUSINESS_HOURS, result.kind());
        assertTrue(result.products().isEmpty());
        assertEquals(3, result.setupRows().size());
        assertEquals("MONDAY", result.setupRows().get(0).key());
        assertEquals("09:00-18:00", result.setupRows().get(0).value());
        assertEquals("SATURDAY", result.setupRows().get(1).key());
        assertEquals("SUNDAY", result.setupRows().get(2).key());
        assertEquals(5, result.warnings().size());
    }

    @Test
    void protectsAgainstExcessiveFaqAnswersAndQuestionsAndDoesNotInventMissingCells() {
        String csv = "Pregunta,Respuesta\n" +
                ("P".repeat(241)) + ",ok\n" +
                "¿Cuál es la política?," + "R".repeat(1201) + "\n" +
                ",sin pregunta\n" +
                "¿Sin respuesta?\n" +
                "¿Válida?,Respuesta declarada\n";
        var file = new MockMultipartFile("files", "preguntas.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        var result = new BusinessImportSpreadsheetParser().parse(file);
        assertEquals(1, result.setupRows().size());
        assertEquals("Respuesta declarada", result.setupRows().getFirst().value());
        assertEquals(4, result.warnings().size());
    }

    @Test
    void detectsFreeFaqAndBusinessHoursInSeparateExcelSheets() throws Exception {
        byte[] bytes;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var bytesOut = new java.io.ByteArrayOutputStream()) {
            var faq = workbook.createSheet("Preguntas");
            faq.createRow(0).createCell(0).setCellValue("Pregunta");
            faq.getRow(0).createCell(1).setCellValue("Respuesta");
            faq.createRow(1).createCell(0).setCellValue("¿Aceptan débito?");
            faq.getRow(1).createCell(1).setCellValue("Sí");
            var hours = workbook.createSheet("Horarios");
            hours.createRow(0).createCell(0).setCellValue("Día");
            hours.getRow(0).createCell(1).setCellValue("Apertura");
            hours.getRow(0).createCell(2).setCellValue("Cierre");
            hours.createRow(1).createCell(0).setCellValue("Tuesday");
            hours.getRow(1).createCell(1).setCellValue("08:00");
            hours.getRow(1).createCell(2).setCellValue("20:00");
            workbook.write(bytesOut);
            bytes = bytesOut.toByteArray();
        }
        var file = new MockMultipartFile("files", "negocio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var result = new BusinessImportSpreadsheetParser().parse(file);
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.MIXED, result.kind());
        assertEquals(2, result.setupRows().size());
        assertEquals("Preguntas", result.setupRows().getFirst().sheetName());
        assertEquals("TUESDAY", result.setupRows().get(1).key());
        assertEquals(2, result.rowCount());
        assertTrue(result.products().isEmpty());
    }

    @Test
    void parsesSpanishProductCsvWithoutCallingAi() {
        String csv = """
                Código,Producto,Precio,Stock,Categoría,Descripción
                HAM-01,Hamburguesa clásica,6990,12,Hamburguesas,Carne queso tomate
                BEB-01,Bebida lata,1990,24,Bebidas,350 ml
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files", "productos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BusinessImportSpreadsheetParser.ParseResult result =
                new BusinessImportSpreadsheetParser().parse(file);

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS, result.kind());
        assertEquals(2, result.products().size());
        assertEquals("HAM-01", result.products().getFirst().sku());
        assertEquals("Hamburguesa clásica", result.products().getFirst().name());
        assertEquals("6990", result.products().getFirst().price().toPlainString());
        assertEquals(12, result.products().getFirst().onHand());
        assertEquals("Hamburguesas", result.products().getFirst().category());
    }

    @Test
    void recognizesHistoricalSalesWithoutPretendingTheyAreLiveOrders() {
        String csv = """
                Fecha,Nro Boleta,Cliente,Total
                2026-09-01,1001,Ana,14980
                2026-09-02,1002,Luis,8990
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files", "ventas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BusinessImportSpreadsheetParser.ParseResult result =
                new BusinessImportSpreadsheetParser().parse(file);

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.SALES, result.kind());
        assertTrue(result.products().isEmpty());
        assertEquals(2, result.rowCount());
    }

    @Test
    void salesRowsWithProductColumnsRemainHistorical() {
        String csv = """
                Fecha,Nro Boleta,Producto,Precio,Cantidad,Total
                2026-09-01,1001,Hamburguesa clásica,6990,2,13980
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files", "ventas_detalle.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BusinessImportSpreadsheetParser.ParseResult result =
                new BusinessImportSpreadsheetParser().parse(file);

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.SALES, result.kind());
        assertTrue(result.products().isEmpty());
        assertEquals(1, result.rowCount());
    }

    @Test
    void malformedOrUnrecognizedSpreadsheetFailsClosed() {
        String csv = """
                Columna rara,Otra cosa
                abc,123
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files", "raro.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BusinessImportSpreadsheetParser.ParseResult result =
                new BusinessImportSpreadsheetParser().parse(file);

        assertFalse(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN, result.kind());
        assertTrue(result.products().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }
}
