package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectorResourceReadinessTest {

    @Test
    void specializedSectorsAreNeverCertifiedByGenericScheduling() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("clinic", "PROFESSIONAL");
        expected.put("dental", "PROFESSIONAL");
        expected.put("veterinary", "PROFESSIONAL");
        expected.put("salon", "PROFESSIONAL");
        expected.put("restaurant", "TABLE");
        expected.put("workshop", "SERVICE_BAY");
        expected.put("legal", "PROFESSIONAL");
        expected.put("real_estate", "PROPERTY_VISIT");
        expected.put("gym", "CLASS_SLOT");
        expected.put("hospitality", "ROOM_NIGHT");

        assertEquals(10, expected.size());
        expected.forEach((preset, kind) -> {
            SectorResourceReadiness result = SectorResourceReadiness.forPreset(preset);
            assertEquals(kind, result.resourceKind(), preset);
            assertEquals("NOT_CERTIFIED", result.verificationState(), preset);
            assertTrue(result.requiresDedicatedResource(), preset);
            assertFalse(result.nextStep().isBlank(), preset);
            assertFalse(result.nextStep().contains("confirmada"), preset);
        });
    }

    @Test
    void productBusinessesRequireRealInventoryButDoNotClaimResourceBookings() {
        for (String preset : new String[]{"hardware_store", "store"}) {
            SectorResourceReadiness result = SectorResourceReadiness.forPreset(preset);
            assertEquals("CATALOG_INVENTORY", result.resourceKind());
            assertEquals("NOT_APPLICABLE", result.verificationState());
            assertFalse(result.requiresDedicatedResource());
            assertTrue(result.nextStep().toLowerCase().contains("stock"));
        }
    }

    @Test
    void unknownOrLegacySectorsStayUnverifiedRatherThanImplicitlyReady() {
        for (String preset : new String[]{ "", "other", "services", "professional", "unrecognized", "CLINIC" }) {
            SectorResourceReadiness result = SectorResourceReadiness.forPreset(preset);
            assertEquals("UNSPECIFIED", result.resourceKind(), preset);
            assertEquals("NOT_VERIFIED", result.verificationState(), preset);
            assertFalse(result.requiresDedicatedResource());
            assertFalse(result.nextStep().isBlank());
        }
        SectorResourceReadiness missing = SectorResourceReadiness.forPreset(null);
        assertEquals("UNSPECIFIED", missing.resourceKind());
        assertEquals("NOT_VERIFIED", missing.verificationState());
    }

    @Test
    void oldOnboardingConstructorDoesNotClaimResourceCertification() {
        OnboardingStatusResponse status = new OnboardingStatusResponse(
                true, true, true, true, true, true, true, "READY");
        assertTrue(status.readyForCalls());
        assertEquals("NOT_VERIFIED", status.resourceBookingReadiness().verificationState());
        assertFalse(status.resourceBookingReadiness().requiresDedicatedResource());
    }
}
