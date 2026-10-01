package cl.helvoca.platform;

import java.util.UUID;

public record PlatformDemoReadinessResponse(
        boolean runtimeConfigured,
        UUID runtimeBusinessId,
        ReadinessItem runtime,
        ReadinessItem voiceNumber,
        ReadinessItem voiceAi,
        ReadinessItem businessData,
        ReadinessItem operations,
        ReadinessItem whatsapp,
        ReadinessItem payment,
        ReadinessItem externalEffects
) {
    public record ReadinessItem(String state, String detail) {
        public static ReadinessItem of(String state, String detail) {
            return new ReadinessItem(state, detail);
        }
    }
}
