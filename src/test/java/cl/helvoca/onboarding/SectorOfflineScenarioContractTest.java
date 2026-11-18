package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.operations.CommercialToolDefinitions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1 contract catalog ONLY. These tests validate 180 executable scenario
 * definitions but do not simulate Gemini audio, execute tools or certify
 * database/provider state transitions. Functional E2E remains a separate gate.
 */
class SectorOfflineScenarioContractTest {
    private static final String PATH = "/fixtures/sector-offline-180-v1.json";
    private static final Set<String> SECTORS = Set.of(
            "clinic", "dental", "veterinary", "salon", "restaurant",
            "hardware_store", "store", "workshop", "legal", "real_estate", "gym", "hospitality");
    private static final Set<String> OUTCOMES = Set.of(
            "ANSWERED_VERIFIED", "OPERATION_CONFIRMED", "FOLLOW_UP_OPENED",
            "TRANSFERRED_VERIFIED", "SAFETY_ESCALATION", "UNRESOLVED");
    private static final Set<String> EVIDENCE = Set.of(
            "official_source_verified", "booking_persisted_after_confirmation",
            "operation_persisted_after_confirmation", "request_id_persisted",
            "transfer_destination_verified", "safety_guidance_no_operation",
            "explicit_unresolved");
    private static final Set<String> SOURCES = Set.of("VERIFIED", "MISSING", "RESOURCE_UNVERIFIED",
            "PROVIDER_DOWN", "UNAUTHORIZED", "UNTRUSTED");

    @Test
    void exactly120Typical36DifficultAnd24CrossCuttingWithEverySector() {
        JSONObject root = load();
        assertEquals(1, root.getInt("schemaVersion"));
        assertEquals("FIXTURE_ONLY_NOT_EXECUTED", root.getString("certificationState"));
        JSONArray cases = root.getJSONArray("scenarios");
        assertEquals(180, cases.length());
        Map<String, Integer> actualCounts = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject testCase = cases.getJSONObject(i);
            assertTrue(ids.add(testCase.getString("id")), "duplicate scenario id");
            String category = testCase.getString("category");
            assertTrue(Set.of("TYPICAL", "DIFFICULT", "CROSS").contains(category), category);
            String sector = testCase.getString("sector");
            assertTrue(SECTORS.contains(sector) || ("CROSS".equals(category) && "*".equals(sector)));
            actualCounts.merge(category + ":" + sector, 1, Integer::sum);
        }
        for (String sector : SECTORS) {
            assertEquals(10, actualCounts.get("TYPICAL:" + sector), sector);
            assertEquals(3, actualCounts.get("DIFFICULT:" + sector), sector);
        }
        assertEquals(24, actualCounts.get("CROSS:*"));
    }

    @TestFactory
    Collection<DynamicTest> eachScenarioHasGroundedActionAndExplicitForbiddenClaims() {
        JSONObject root = load();
        JSONArray cases = root.getJSONArray("scenarios");
        Set<String> declaredTools = knownTools();
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            tests.add(DynamicTest.dynamicTest(c.getString("id"), () -> {
                String label = c.getString("id") + ": ";
                assertFalse(c.getString("utterance").isBlank(), label + "caller utterance missing");
                assertTrue(c.getString("utterance").length() >= 12, label + "utterance too vague");
                assertTrue(OUTCOMES.contains(c.getString("expectedOutcome")), label + "unrecognized outcome");
                assertTrue(EVIDENCE.contains(c.getString("requiredEvidence")), label + "unrecognized evidence");
                assertTrue(SOURCES.contains(c.getString("sourceState")), label + "missing official source truth");
                assertTrue(c.getJSONArray("forbiddenClaims").length() > 0, label + "prohibited claim missing");

                JSONArray allowed = c.getJSONArray("allowedTools");
                for (int j = 0; j < allowed.length(); j++) {
                    String tool = allowed.getString(j);
                    assertTrue(declaredTools.contains(tool), label + "undefined tool " + tool);
                    assertNotEquals("create_payment", tool, label + "must not initiate real payment");
                    assertNotEquals("send_whatsapp_operation", tool, label + "must not send messages");
                }
                String outcome = c.getString("expectedOutcome");
                String evidence = c.getString("requiredEvidence");
                String source = c.getString("sourceState");
                if ("ANSWERED_VERIFIED".equals(outcome)) {
                    assertEquals("VERIFIED", source, label + "facts need an authorized source");
                    assertEquals("official_source_verified", evidence);
                }
                if ("OPERATION_CONFIRMED".equals(outcome)) {
                    assertEquals("VERIFIED", source);
                    assertTrue(Set.of("booking_persisted_after_confirmation",
                            "operation_persisted_after_confirmation").contains(evidence));
                    assertTrue(c.getBoolean("explicitConfirmation"), label + "operation requires consent");
                }
                if ("FOLLOW_UP_OPENED".equals(outcome)) {
                    assertEquals("request_id_persisted", evidence);
                    assertTrue(allowed.toList().contains("create_request"), label + "no durable follow-up tool");
                }
                if ("SAFETY_ESCALATION".equals(outcome)) {
                    assertEquals("safety_guidance_no_operation", evidence);
                }
                if ("UNRESOLVED".equals(outcome)) {
                    assertEquals("explicit_unresolved", evidence);
                }
                assertFalse(c.getBoolean("realExternalEffects"), label + "fixtures must be offline");
            }));
        }
        return tests;
    }

    private static Set<String> knownTools() {
        Set<String> names = new HashSet<>();
        for (JSONArray group : List.of(RealtimeToolDefinitions.all(), CommercialToolDefinitions.all())) {
            for (int i = 0; i < group.length(); i++) {
                names.add(group.getJSONObject(i).getString("name"));
            }
        }
        return names;
    }

    private static JSONObject load() {
        try (InputStream input = SectorOfflineScenarioContractTest.class.getResourceAsStream(PATH)) {
            assertNotNull(input, "missing declarative 180 cross-sector scenario fixture");
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("cannot read scenario fixture", error);
        }
    }
}
