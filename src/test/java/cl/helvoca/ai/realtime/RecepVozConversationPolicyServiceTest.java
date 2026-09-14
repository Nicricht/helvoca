package cl.helvoca.ai.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecepVozConversationPolicyServiceTest {

    @Test
    void containsAdaptiveAdviceCommercialAndClosingGuidance() {
        String instructions = RecepVozConversationPolicyService.conversationGuidance();

        assertTrue(instructions.contains("POLÍTICA CONVERSACIONAL ADAPTATIVA DE RECEPVOZ"));
        assertTrue(instructions.contains("qué corte de cabello suele verse formal"));
        assertTrue(instructions.contains("ASISTENCIA COMERCIAL"));
        assertTrue(instructions.contains("español chileno neutro"));
        assertTrue(instructions.contains("no vuelvas a preguntar si necesita algo"));
        assertTrue(instructions.contains("No repitas “¿aló?”"));
    }

    @Test
    void certificationGuardRemainsPrimaryRealtimeToolServiceForProductionInjection() {
        assertTrue(CertificationGuardedRealtimeToolService.class.isAnnotationPresent(Primary.class));
    }
}
