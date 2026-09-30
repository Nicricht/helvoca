package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BusinessImportSpreadsheetParserTest {

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
