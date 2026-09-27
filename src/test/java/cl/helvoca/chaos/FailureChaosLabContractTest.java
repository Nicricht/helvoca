package cl.helvoca.chaos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FailureChaosLabContractTest {

    @Test
    void chaosLabProvidesDeterministicFailureInjectionAndCertificationMatrix() {
        assertDoesNotThrow(() -> Class.forName("cl.helvoca.chaos.DeterministicFailureInjector"));
        assertDoesNotThrow(() -> Class.forName("cl.helvoca.chaos.FailureChaosMatrix"));
    }
}
