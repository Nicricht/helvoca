package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiLiveCertificationScopeTest {

    @Test
    void certificationSimulationRequiresExplicitCallerMatch() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setCertificationSimulation(true);
        properties.setCertificationCaller("+56966939611");

        assertTrue(properties.certificationSimulationAllowedFor("+56966939611"));
        assertFalse(properties.certificationSimulationAllowedFor("+56911111111"));
        assertFalse(properties.certificationSimulationAllowedFor(null));
    }

    @Test
    void blankCertificationCallerFailsClosed() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setCertificationSimulation(true);

        assertFalse(properties.certificationSimulationAllowedFor("+56966939611"));
    }
}
