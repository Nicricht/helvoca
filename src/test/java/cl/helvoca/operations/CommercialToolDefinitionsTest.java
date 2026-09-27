package cl.helvoca.operations;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CommercialToolDefinitionsTest {

    @Test
    void allowedHandlesNullEmptyAndFiltersExactToolNames() {
        assertEquals(0, CommercialToolDefinitions.allowed(null).length());
        assertEquals(0, CommercialToolDefinitions.allowed(Set.of()).length());

        Set<String> allowed = Set.of(
                "list_catalog",
                CommercialOperationToolService.GET_STOCK_TOOL,
                "not-a-commercial-tool");
        JSONArray filtered = CommercialToolDefinitions.allowed(allowed);

        Set<String> names = IntStream.range(0, filtered.length())
                .mapToObj(index -> filtered.getJSONObject(index).getString("name"))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("list_catalog", CommercialOperationToolService.GET_STOCK_TOOL), names);
    }

    @Test
    void instructionsCoverEveryCommercialCapabilityAndEmptyCases() {
        assertEquals("", CommercialToolDefinitions.instructions(null));
        assertEquals("", CommercialToolDefinitions.instructions(Set.of()));

        String all = CommercialToolDefinitions.instructions(
                EnumSet.allOf(BusinessOperationCapability.class));

        assertTrue(all.contains("list_catalog"));
        assertTrue(all.contains("quote_order"));
        assertTrue(all.contains("quote_delivery"));
        assertTrue(all.contains("create_quote"));
        assertTrue(all.contains("create_lead"));
        assertTrue(all.contains("quote_payment"));

        String leadOnly = CommercialToolDefinitions.instructions(
                Set.of(BusinessOperationCapability.LEAD));
        assertTrue(leadOnly.contains("create_lead"));
        assertFalse(leadOnly.contains("quote_order"));
        assertFalse(leadOnly.contains("quote_payment"));

        String catalogOnly = CommercialToolDefinitions.instructions(
                Set.of(BusinessOperationCapability.CATALOG));
        assertTrue(catalogOnly.contains("list_catalog"));
        assertFalse(catalogOnly.contains("create_lead"));
        assertFalse(catalogOnly.contains("quote_delivery"));
    }

    @Test
    void allDefinitionsExposeFunctionShapeAndNonEmptyDescriptions() {
        JSONArray definitions = CommercialToolDefinitions.all();
        assertTrue(definitions.length() > 10);

        for (int i = 0; i < definitions.length(); i++) {
            var definition = definitions.getJSONObject(i);
            assertEquals("function", definition.getString("type"));
            assertFalse(definition.getString("name").isBlank());
            assertFalse(definition.getString("description").isBlank());
            assertEquals("object", definition.getJSONObject("parameters").getString("type"));
        }
    }
}
