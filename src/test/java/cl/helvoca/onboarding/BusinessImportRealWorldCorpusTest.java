package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BusinessImportRealWorldCorpusTest {

    @Test
    void restaurantMenuWithoutSkuOrStockKeepsChileanPriceAndCategory() {
        String csv = """
                Producto;Precio;Categoría;Descripción
                Tabla mediterránea;$22.900;Para compartir;Quesos embutidos aceitunas y tostadas
                Langostinos al pil pil;$19.900;Para compartir;Ajo vino blanco mantequilla y perejil
                """;

        var result = new BusinessImportSpreadsheetParser().parse(file("carta-restaurante.csv", csv));

        assertTrue(result.recognized());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS, result.kind());
        assertEquals(2, result.products().size());
        assertEquals("22900", result.products().getFirst().price().toPlainString());
        assertNull(result.products().getFirst().sku());
        assertNull(result.products().getFirst().onHand());
        assertEquals("Para compartir", result.products().getFirst().category());
    }

    @Test
    void hardwareCatalogFindsActualHeaderAfterPreambleAndDelimiterNoise() {
        String csv = """
                CATÁLOGO FERRETERÍA,Septiembre 2026
                Moneda,CLP
                Código;Artículo;P. Venta;Existencia;Familia
                MRT-01;Martillo carpintero;$ 12.990;7;Herramientas manuales
                TAL-18;Taladro percutor;$ 84.990;3;Herramientas eléctricas
                """;

        var result = new BusinessImportSpreadsheetParser().parse(file("catalogo-ferreteria.csv", csv));

        assertTrue(result.recognized(), result.warnings().toString());
        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS, result.kind());
        assertEquals(2, result.products().size());
        assertEquals("MRT-01", result.products().getFirst().sku());
        assertEquals("12990", result.products().getFirst().price().toPlainString());
        assertEquals(7, result.products().getFirst().onHand());
    }

    @Test
    void salonCatalogIsServiceDataAndPreservesDurationAndPriceFloor() throws Exception {
        String csv = """
                Servicio;Precio;Duración;Categoría
                Balayage;Desde $100.000;2 h;Coloración
                Lavado y brushing;$18.000;45 min;Styling
                """;

        var result = new BusinessImportSpreadsheetParser().parse(file("servicios-peluqueria.csv", csv));

        assertTrue(result.recognized(), result.warnings().toString());
        assertEquals("SERVICES", result.kind().name());
        assertEquals(2, result.products().size());
        assertEquals("100000", result.products().getFirst().price().toPlainString());
        Method duration = result.products().getFirst().getClass().getMethod("durationMinutes");
        assertEquals(120, duration.invoke(result.products().getFirst()));
        assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("desde")));
    }

    @Test
    void veterinaryCatalogKeepsNonNumericPriceUnknownAndPreservesDuration() throws Exception {
        String csv = """
                Servicio;Precio;Duración;Categoría
                Consulta Veterinaria;$20.000;30 min;Consulta
                Cirugía Veterinaria;Según procedimiento;4 h;Cirugía
                """;

        var result = new BusinessImportSpreadsheetParser().parse(file("servicios-veterinaria.csv", csv));

        assertTrue(result.recognized(), result.warnings().toString());
        assertEquals("SERVICES", result.kind().name());
        assertEquals(2, result.products().size());
        assertNull(result.products().get(1).price());
        Method duration = result.products().get(1).getClass().getMethod("durationMinutes");
        assertEquals(240, duration.invoke(result.products().get(1)));
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.toLowerCase().contains("procedimiento") || w.toLowerCase().contains("precio")));
    }

    @Test
    void serviceWithUnrecognizedDurationKeepsDurationUnknownAndAddsReviewWarning() {
        String csv = """
                Servicio;Precio;Duración
                Evaluación especial;$25.000;depende del caso
                """;

        var result = new BusinessImportSpreadsheetParser().parse(file("duracion-rara.csv", csv));

        assertEquals(BusinessImportSpreadsheetParser.DatasetKind.SERVICES, result.kind());
        assertNull(result.products().getFirst().durationMinutes());
        assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("duración no reconocida")));
    }

    @Test
    void durationNormalizerCoversHumanHourMinuteFormatsAndRejectsInvalidValues() {
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes(null));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("  "));
        assertEquals(120, BusinessImportSpreadsheetParser.parseDurationMinutes("2 horas"));
        assertEquals(30, BusinessImportSpreadsheetParser.parseDurationMinutes("30 minutos"));
        assertEquals(90, BusinessImportSpreadsheetParser.parseDurationMinutes("1 h 30 min"));
        assertEquals(45, BusinessImportSpreadsheetParser.parseDurationMinutes("45"));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("0 h"));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("0 min"));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("1 h 70 min"));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("depende"));
        assertNull(BusinessImportSpreadsheetParser.parseDurationMinutes("999999999999999999999999 h"));
    }

    private static MockMultipartFile file(String name, String csv) {
        return new MockMultipartFile(
                "files", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
