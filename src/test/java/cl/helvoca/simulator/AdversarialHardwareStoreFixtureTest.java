package cl.helvoca.simulator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdversarialHardwareStoreFixtureTest {

    private JSONObject loadFixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/adversarial-hardware-store-v1.json")) {
            assertNotNull(input, "hardware store adversarial fixture must exist");
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void fixtureIsExplicitlyFictionalAndCannotBeMistakenForARealCustomer() throws Exception {
        JSONObject root = loadFixture();
        JSONObject business = root.getJSONObject("business");

        assertTrue(root.getBoolean("fictional"));
        assertEquals("Ferretería San Martín Demo", business.getString("name"));
        assertTrue(business.getString("publicEmail").endsWith(".invalid"));
        assertTrue(business.getString("websiteUrl").contains(".invalid"));
        assertTrue(business.isNull("phone"));
        assertEquals("America/Santiago", business.getString("timezone"));
        assertEquals("CLP", business.getString("currency"));
    }

    @Test
    void fixtureHasEnoughCatalogDepthToExerciseAmbiguityUnitsAndInventory() throws Exception {
        JSONObject root = loadFixture();
        JSONArray products = root.getJSONArray("products");

        assertTrue(products.length() >= 30, "fixture must contain at least 30 products");

        Set<String> skus = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> categories = new HashSet<>();
        Set<String> units = new HashSet<>();
        boolean hasOutOfStock = false;
        boolean hasLowStock = false;
        boolean hasRestrictedAdvice = false;

        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.getJSONObject(i);
            assertTrue(skus.add(product.getString("sku")), "SKU must be unique");
            assertTrue(names.add(product.getString("name").toLowerCase()), "product name must be unique");
            assertTrue(product.getInt("price") > 0);
            assertTrue(product.getInt("stock") >= 0);
            assertTrue(product.getBoolean("trackInventory"));
            assertTrue(product.getJSONArray("aliases").length() >= 1);

            categories.add(product.getString("category"));
            units.add(product.getString("unit"));
            hasOutOfStock |= product.getInt("stock") == 0;
            hasLowStock |= product.getInt("stock") > 0 && product.getInt("stock") <= 5;
            hasRestrictedAdvice |= product.optBoolean("restrictedAdvice", false);
        }

        assertTrue(categories.size() >= 8);
        assertTrue(units.size() >= 8);
        assertTrue(units.contains("metro"));
        assertTrue(units.contains("caja_100"));
        assertTrue(units.contains("tubo_3m"));
        assertTrue(units.contains("saco_25kg"));
        assertTrue(hasOutOfStock);
        assertTrue(hasLowStock);
        assertTrue(hasRestrictedAdvice);
    }

    @Test
    void fixtureDefinesSafeCommercialBehaviorAndHardCustomerCases() throws Exception {
        JSONObject root = loadFixture();

        JSONArray policies = root.getJSONArray("policies");
        JSONArray capabilities = root.getJSONArray("capabilities");
        JSONArray personas = root.getJSONArray("customerPersonas");
        JSONArray scenarios = root.getJSONArray("adversarialScenarios");
        JSONObject week = root.getJSONObject("weekPlan");

        assertTrue(policies.length() >= 15);
        assertTrue(personas.length() >= 8);
        assertTrue(scenarios.length() >= 30);
        assertEquals(7, week.getInt("days"));
        assertTrue(week.getInt("targetConversations") >= 150);

        Set<String> capabilityNames = new HashSet<>();
        for (int i = 0; i < capabilities.length(); i++) capabilityNames.add(capabilities.getString(i));

        assertTrue(capabilityNames.contains("LIST_CATALOG"));
        assertTrue(capabilityNames.contains("QUOTE_ORDER"));
        assertTrue(capabilityNames.contains("CREATE_ORDER"));
        assertTrue(capabilityNames.contains("CREATE_QUOTE"));
        assertTrue(capabilityNames.contains("TRANSFER_TO_HUMAN"));
        assertFalse(capabilityNames.contains("CREATE_PAYMENT"));

        String policyText = policies.toString().toLowerCase();
        assertTrue(policyText.contains("stock"));
        assertTrue(policyText.contains("gas"));
        assertTrue(policyText.contains("eléctr"));
        assertTrue(policyText.contains("estruct"));
        assertTrue(policyText.contains("no procesa pagos reales"));

        String scenarioText = scenarios.toString();
        assertTrue(scenarioText.contains("dos_clientes_ultima_unidad"));
        assertTrue(scenarioText.contains("duplicado_de_confirmacion"));
        assertTrue(scenarioText.contains("aislamiento_multi_tenant"));
        assertTrue(scenarioText.contains("error_de_herramienta_y_reintento_acotado"));
    }

    @Test
    void agentInstructionsFailClosedOnUnknownsAndRequireConfirmationBeforeMutation() throws Exception {
        JSONObject root = loadFixture();
        String instructions = root.getJSONObject("agent").getJSONArray("instructions").toString().toLowerCase();

        assertTrue(instructions.contains("nunca inventes stock"));
        assertTrue(instructions.contains("antes de confirmar un pedido"));
        assertTrue(instructions.contains("no sustituyas"));
        assertTrue(instructions.contains("deriva"));
        assertTrue(instructions.contains("no procesa pagos reales") || instructions.contains("no afirmes que un pago"));
        assertTrue(instructions.contains("sin llamadas"));
        assertTrue(instructions.contains("whatsapp"));
    }
}
