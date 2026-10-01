package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoInboundSafetyReadinessTest {

    @Test
    void safeSnapshotFailsClosedForMissingRuntimeOrReadinessService() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformDemoReadinessService> provider = mock(ObjectProvider.class);
        DemoInboundSafetyReadiness gate = new DemoInboundSafetyReadiness(provider);

        assertTrue(gate.safeSnapshot(null).isEmpty());
        verifyNoInteractions(provider);

        UUID runtimeId = UUID.randomUUID();
        when(provider.getIfAvailable()).thenReturn(null);
        assertTrue(gate.safeSnapshot(runtimeId).isEmpty());
    }

    @Test
    void safeSnapshotReturnsCanonicalServerReadinessSnapshotWhenAllVoiceGuardsAreSafe() {
        UUID runtimeId = UUID.randomUUID();
        PlatformDemoReadinessResponse response = response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED"));

        DemoInboundSafetyReadiness gate = gate(response);
        Optional<Map<String, Object>> result = gate.safeSnapshot(runtimeId);

        assertTrue(result.isPresent());
        assertEquals(runtimeId, result.get().get("runtimeBusinessId"));
        assertEquals("READY", result.get().get("runtime"));
        assertEquals("READY", result.get().get("voiceNumber"));
        assertEquals("READY", result.get().get("voiceAi"));
        assertEquals("READY", result.get().get("businessData"));
        assertEquals("READY", result.get().get("operations"));
        assertEquals("SANDBOX_ONLY", result.get().get("payment"));
        assertEquals("DISARMED", result.get().get("externalEffects"));
    }

    @Test
    void safeSnapshotRejectsEveryDegradedAdmissionGuardIndependently() {
        UUID runtimeId = UUID.randomUUID();
        UUID otherRuntimeId = UUID.randomUUID();

        assertUnsafe(runtimeId, null);
        assertUnsafe(runtimeId, response(
                false, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, otherRuntimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                null, item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("UNAVAILABLE"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), null, item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("UNAVAILABLE"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), null, item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("UNAVAILABLE"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), null, item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("NOT_CONFIGURED"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), null,
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("FAILED"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), null, item("DISARMED")));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("LIVE"), item("DISARMED")));

        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), null));
        assertUnsafe(runtimeId, response(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("ARMED")));
    }

    private static DemoInboundSafetyReadiness gate(PlatformDemoReadinessResponse response) {
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        when(readiness.readiness()).thenReturn(response);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformDemoReadinessService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(readiness);
        return new DemoInboundSafetyReadiness(provider);
    }

    private static void assertUnsafe(UUID runtimeId, PlatformDemoReadinessResponse response) {
        assertTrue(gate(response).safeSnapshot(runtimeId).isEmpty());
    }

    private static PlatformDemoReadinessResponse response(
            boolean configured,
            UUID runtimeId,
            PlatformDemoReadinessResponse.ReadinessItem runtime,
            PlatformDemoReadinessResponse.ReadinessItem voiceNumber,
            PlatformDemoReadinessResponse.ReadinessItem voiceAi,
            PlatformDemoReadinessResponse.ReadinessItem businessData,
            PlatformDemoReadinessResponse.ReadinessItem operations,
            PlatformDemoReadinessResponse.ReadinessItem whatsapp,
            PlatformDemoReadinessResponse.ReadinessItem payment,
            PlatformDemoReadinessResponse.ReadinessItem externalEffects) {
        return new PlatformDemoReadinessResponse(
                configured, runtimeId, runtime, voiceNumber, voiceAi,
                businessData, operations, whatsapp, payment, externalEffects);
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state) {
        return new PlatformDemoReadinessResponse.ReadinessItem(state, state);
    }
}
