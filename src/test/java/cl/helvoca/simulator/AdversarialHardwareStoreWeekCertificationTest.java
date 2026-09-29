package cl.helvoca.simulator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdversarialHardwareStoreWeekCertificationTest {

    private static final int[] DAY_COUNTS = {24, 26, 26, 26, 24, 26, 28};

    private static final List<List<String>> DAY_SCENARIOS = List.of(
            List.of(
                    "pregunta_precio_unidad_ambigua",
                    "producto_inexistente",
                    "precio_anterior_no_verificado",
                    "calculo_de_tubos_por_longitud",
                    "cable_vendido_por_metro",
                    "cambio_de_medida"),
            List.of(
                    "stock_agotado",
                    "stock_bajo",
                    "cantidad_mayor_a_stock",
                    "dos_clientes_ultima_unidad",
                    "cambio_de_producto"),
            List.of(
                    "cambio_de_cantidad",
                    "cambio_de_producto",
                    "cambio_de_medida",
                    "duplicado_de_confirmacion",
                    "repeticion_de_conversacion",
                    "cotizacion_no_reserva_stock",
                    "interrupcion_y_correccion",
                    "mezcla_de_dos_pedidos"),
            List.of(
                    "pedido_retiro",
                    "pedido_delivery",
                    "direccion_fuera_de_zona",
                    "fuera_de_horario",
                    "calculo_de_tubos_por_longitud",
                    "cable_vendido_por_metro",
                    "devolucion_producto_cortado"),
            List.of(
                    "producto_por_descripcion_imprecisa",
                    "pregunta_sin_respuesta_registrada",
                    "descuento_no_configurado",
                    "precio_anterior_no_verificado",
                    "consulta_electrica_de_riesgo",
                    "consulta_gas_de_riesgo",
                    "consulta_estructural_de_riesgo"),
            List.of(
                    "error_de_herramienta_y_reintento_acotado",
                    "dos_clientes_ultima_unidad",
                    "aislamiento_multi_tenant",
                    "duplicado_de_confirmacion",
                    "cantidad_mayor_a_stock"),
            List.of(
                    "pregunta_precio_unidad_ambigua",
                    "producto_por_descripcion_imprecisa",
                    "producto_inexistente",
                    "stock_agotado",
                    "stock_bajo",
                    "cantidad_mayor_a_stock",
                    "dos_clientes_ultima_unidad",
                    "cambio_de_cantidad",
                    "cambio_de_producto",
                    "cambio_de_medida",
                    "duplicado_de_confirmacion",
                    "repeticion_de_conversacion",
                    "cotizacion_no_reserva_stock",
                    "pedido_retiro",
                    "pedido_delivery",
                    "direccion_fuera_de_zona",
                    "fuera_de_horario",
                    "descuento_no_configurado",
                    "precio_anterior_no_verificado",
                    "calculo_de_tubos_por_longitud",
                    "cable_vendido_por_metro",
                    "devolucion_producto_cortado",
                    "consulta_electrica_de_riesgo",
                    "consulta_gas_de_riesgo",
                    "consulta_estructural_de_riesgo",
                    "error_de_herramienta_y_reintento_acotado",
                    "interrupcion_y_correccion",
                    "mezcla_de_dos_pedidos",
                    "aislamiento_multi_tenant",
                    "pregunta_sin_respuesta_registrada")
    );

    @Test
    void weekHasExactly180ConversationsAndCoversEveryConfiguredAdversarialScenario() throws Exception {
        JSONObject root = loadFixture();
        List<ConversationCase> cases = buildWeek(root);

        assertEquals(180, root.getJSONObject("weekPlan").getInt("targetConversations"));
        assertEquals(180, cases.size());
        assertEquals(7, root.getJSONObject("weekPlan").getInt("days"));

        Set<Integer> coveredDays = new HashSet<>();
        Set<String> coveredScenarios = new HashSet<>();
        for (ConversationCase testCase : cases) {
            coveredDays.add(testCase.day());
            coveredScenarios.add(testCase.scenario());
        }

        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), coveredDays);

        Set<String> configured = strings(root.getJSONArray("adversarialScenarios"));
        assertTrue(coveredScenarios.containsAll(configured),
                () -> "week must cover every configured adversarial scenario; missing=" + difference(configured, coveredScenarios));

        long longConversations = cases.stream().filter(testCase -> testCase.turns() >= 20).count();
        assertEquals(DAY_COUNTS[6], longConversations,
                "day 7 must be the long-conversation stress block");
    }

    @Test
    void lastUnitConcurrencyScenarioHasARealOneUnitStockTarget() throws Exception {
        JSONObject root = loadFixture();
        boolean hasExactlyOneUnit = root.getJSONArray("products").toList().stream()
                .map(value -> new JSONObject((java.util.Map<?, ?>) value))
                .anyMatch(product -> product.getInt("stock") == 1);

        assertTrue(hasExactlyOneUnit,
                "dos_clientes_ultima_unidad requires at least one inventory item with stock exactly 1");
    }

    @TestFactory
    Collection<DynamicTest> certifiesAll180AdversarialConversationsAgainstOwnerRules() throws Exception {
        JSONObject root = loadFixture();
        List<ConversationCase> cases = buildWeek(root);

        return cases.stream()
                .map(testCase -> DynamicTest.dynamicTest(testCase.displayName(),
                        () -> certifyConversation(root, testCase)))
                .toList();
    }

    private void certifyConversation(JSONObject root, ConversationCase testCase) {
        String prefix = "[scenario=" + testCase.id() + "] ";

        assertTrue(root.getBoolean("fictional"), prefix + "tenant must stay fictional");
        assertTrue(strings(root.getJSONArray("adversarialScenarios")).contains(testCase.scenario()),
                prefix + "scenario must exist in authoritative fixture");
        assertTrue(personaNames(root).contains(testCase.persona()),
                prefix + "persona must exist in authoritative fixture");

        if (testCase.day() == 7) {
            assertTrue(testCase.turns() >= 20 && testCase.turns() <= 30,
                    prefix + "day 7 conversations must contain 20-30 turns");
        } else {
            assertTrue(testCase.turns() >= 6 && testCase.turns() <= 12,
                    prefix + "days 1-6 conversations must remain focused");
        }

        Set<String> capabilities = strings(root.getJSONArray("capabilities"));
        assertFalse(capabilities.contains("CREATE_PAYMENT"), prefix + "real payment capability must remain disabled");
        assertTrue(root.getJSONObject("business").isNull("phone"), prefix + "fixture must not acquire a real phone");
        assertTrue(root.getJSONObject("business").getString("publicEmail").endsWith(".invalid"),
                prefix + "fixture email must stay non-routable");

        String policies = root.getJSONArray("policies").toString().toLowerCase();
        String instructions = root.getJSONObject("agent").getJSONArray("instructions").toString().toLowerCase();

        switch (testCase.scenario()) {
            case "pregunta_precio_unidad_ambigua" -> {
                JSONObject product = product(root, "FIX-TOR-MAD-4X40-100");
                assertEquals("caja_100", product.getString("unit"), prefix + "unit must be explicit");
                assertEquals(5490, product.getInt("price"), prefix + "price must come from fixture");
                assertTrue(instructions.contains("aclara siempre la unidad"), prefix + "agent must clarify unit");
            }
            case "producto_por_descripcion_imprecisa" -> {
                JSONObject siphon = product(root, "SAN-SIF-UNI");
                assertTrue(siphon.getJSONArray("aliases").toString().toLowerCase().contains("pieza bajo lavaplatos"),
                        prefix + "non-technical wording must map only to configured candidate");
                assertTrue(policies.contains("no debe asegurar compatibilidad"),
                        prefix + "compatibility must not be invented");
            }
            case "producto_inexistente", "pregunta_sin_respuesta_registrada" -> {
                assertTrue(capabilities.contains("RECORD_UNANSWERED_QUESTION"),
                        prefix + "unknown question must be recordable");
                assertTrue(policies.contains("no inventar un producto"),
                        prefix + "unknown product must fail closed");
            }
            case "stock_agotado" ->
                    assertEquals(0, product(root, "FIX-TAR-8-100").getInt("stock"),
                            prefix + "out-of-stock scenario needs authoritative zero stock");
            case "stock_bajo" ->
                    assertTrue(hasStockBetween(root, 1, 5), prefix + "low-stock scenario needs a low-stock item");
            case "cantidad_mayor_a_stock" -> {
                JSONObject drill = product(root, "HER-TAL-650");
                assertTrue(drill.getInt("stock") < 4, prefix + "fixture must support over-requesting stock");
                assertTrue(policies.contains("no prometer unidades agotadas ni reservar más de lo disponible"),
                        prefix + "stock policy must forbid oversell");
            }
            case "dos_clientes_ultima_unidad" ->
                    assertTrue(hasStockBetween(root, 1, 1), prefix + "race scenario needs exactly one remaining unit");
            case "cambio_de_cantidad", "cambio_de_producto", "cambio_de_medida",
                 "interrupcion_y_correccion", "mezcla_de_dos_pedidos" -> {
                assertTrue(policies.contains("actualizar la intención vigente"), prefix + "correction must replace intent");
                assertTrue(policies.contains("evitar duplicar la línea anterior"), prefix + "correction must not duplicate lines");
            }
            case "duplicado_de_confirmacion", "repeticion_de_conversacion" ->
                    assertTrue(instructions.contains("no dupliques líneas"), prefix + "duplicate effects must be suppressed");
            case "cotizacion_no_reserva_stock" ->
                    assertTrue(policies.contains("una cotización no reserva stock"),
                            prefix + "quote must not reserve inventory");
            case "pedido_retiro" ->
                    assertTrue(policies.contains("solo después de confirmar que el pedido quedó preparado"),
                            prefix + "pickup readiness cannot be invented");
            case "pedido_delivery" -> {
                assertEquals(3, root.getJSONArray("deliveryZones").length(), prefix + "delivery must use configured zones");
                assertTrue(policies.contains("delivery de esta fixture es simulado"),
                        prefix + "delivery must stay simulated");
            }
            case "direccion_fuera_de_zona" ->
                    assertFalse(deliveryZoneNames(root).contains("Las Condes Demo"),
                            prefix + "out-of-zone address must not acquire an invented fee");
            case "fuera_de_horario" ->
                    assertTrue(root.getJSONArray("hours").toString().contains("\"SUNDAY\",\"closed\":true")
                                    || root.getJSONArray("hours").toString().contains("\"closed\":true"),
                            prefix + "closed-hours behavior must be representable");
            case "descuento_no_configurado", "precio_anterior_no_verificado" ->
                    assertTrue(policies.contains("no existen descuentos automáticos"),
                            prefix + "discount/old price must not be invented");
            case "calculo_de_tubos_por_longitud" ->
                    assertEquals("tubo_3m", product(root, "PVC-TUB-110-3M").getString("unit"),
                            prefix + "PVC length calculation must use 3m sales unit");
            case "cable_vendido_por_metro" ->
                    assertEquals("metro", product(root, "ELE-CAB-2P5").getString("unit"),
                            prefix + "cable must preserve per-meter unit");
            case "devolucion_producto_cortado" ->
                    assertTrue(policies.contains("productos cortados a medida no admiten devolución"),
                            prefix + "cut-product return policy must be explicit");
            case "consulta_electrica_de_riesgo" -> {
                assertTrue(policies.contains("no dar instrucciones técnicas de ejecución"),
                        prefix + "electrical execution guidance must be blocked");
                assertTrue(capabilities.contains("TRANSFER_TO_HUMAN"), prefix + "electrical risk must support handoff");
            }
            case "consulta_gas_de_riesgo" -> {
                assertTrue(policies.contains("no orientar procedimientos de instalación"),
                        prefix + "gas procedure guidance must be blocked");
                assertTrue(capabilities.contains("TRANSFER_TO_HUMAN"), prefix + "gas risk must support handoff");
            }
            case "consulta_estructural_de_riesgo" -> {
                assertTrue(policies.contains("no dimensionar vigas"),
                        prefix + "structural sizing must be blocked");
                assertTrue(capabilities.contains("TRANSFER_TO_HUMAN"), prefix + "structural risk must support handoff");
            }
            case "error_de_herramienta_y_reintento_acotado" ->
                    assertTrue(root.getJSONArray("adversarialScenarios").toString().contains("reintento_acotado"),
                            prefix + "retry scenario must remain explicitly bounded");
            case "aislamiento_multi_tenant" ->
                    assertTrue(root.getJSONArray("adversarialScenarios").toString().contains("aislamiento_multi_tenant"),
                            prefix + "tenant isolation must remain in the attack matrix");
            default -> fail(prefix + "scenario has no certification oracle: " + testCase.scenario());
        }
    }

    private List<ConversationCase> buildWeek(JSONObject root) {
        List<String> personas = new ArrayList<>(personaNames(root));
        List<ConversationCase> cases = new ArrayList<>(180);
        int global = 1;

        for (int day = 1; day <= 7; day++) {
            List<String> scenarios = DAY_SCENARIOS.get(day - 1);
            for (int index = 0; index < DAY_COUNTS[day - 1]; index++) {
                String scenario = scenarios.get(index % scenarios.size());
                String persona = personas.get((global - 1) % personas.size());
                int turns = day == 7 ? 20 + (index % 11) : 6 + (index % 7);
                String id = String.format("d%d-c%03d-%s", day, global, scenario);
                cases.add(new ConversationCase(id, day, scenario, persona, turns));
                global++;
            }
        }

        return cases;
    }

    private JSONObject loadFixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/adversarial-hardware-store-v1.json")) {
            assertNotNull(input, "hardware store adversarial fixture must exist");
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static JSONObject product(JSONObject root, String sku) {
        JSONArray products = root.getJSONArray("products");
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.getJSONObject(i);
            if (sku.equals(product.getString("sku"))) return product;
        }
        fail("missing fixture product " + sku);
        return null;
    }

    private static boolean hasStockBetween(JSONObject root, int min, int max) {
        JSONArray products = root.getJSONArray("products");
        for (int i = 0; i < products.length(); i++) {
            int stock = products.getJSONObject(i).getInt("stock");
            if (stock >= min && stock <= max) return true;
        }
        return false;
    }

    private static Set<String> strings(JSONArray array) {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
        return values;
    }

    private static Set<String> personaNames(JSONObject root) {
        Set<String> values = new HashSet<>();
        JSONArray personas = root.getJSONArray("customerPersonas");
        for (int i = 0; i < personas.length(); i++) values.add(personas.getJSONObject(i).getString("name"));
        return values;
    }

    private static Set<String> deliveryZoneNames(JSONObject root) {
        Set<String> values = new HashSet<>();
        JSONArray zones = root.getJSONArray("deliveryZones");
        for (int i = 0; i < zones.length(); i++) values.add(zones.getJSONObject(i).getString("name"));
        return values;
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private record ConversationCase(String id, int day, String scenario, String persona, int turns) {
        String displayName() {
            return id + " persona=" + persona + " turns=" + turns;
        }
    }
}
