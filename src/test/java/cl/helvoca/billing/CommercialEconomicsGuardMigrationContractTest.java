package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CommercialEconomicsGuardMigrationContractTest {

    @Test
    void migrationSeedsEmergencyVoiceSafetyEntitlementsAndCostDimensions() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V82__commercial_economics_guard.sql")) {
            assertNotNull(input, "V82 commercial economics guard migration must exist");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("VOICE_SAFETY_SECONDS"));
            assertTrue(sql.contains("hard_limit"));
            assertTrue(sql.contains("ai_provider"));
            assertTrue(sql.contains("ai_model"));
            assertTrue(sql.contains("telephony_cost_usd"));
            assertTrue(sql.contains("ai_cost_usd"));
            assertTrue(sql.contains("record_call_usage_meter"));
        }
    }
}
