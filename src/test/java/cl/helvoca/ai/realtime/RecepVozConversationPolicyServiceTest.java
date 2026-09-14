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
        assertTrue(instructions.contains("la corrección más reciente reemplaza inmediatamente la anterior"));
        assertTrue(instructions.contains("partidura lateral texturizada"));
        assertTrue(instructions.contains("VENTA CONSULTIVA Y CONVINCENTE"));
        assertTrue(instructions.contains("dos o tres atributos verificados"));
        assertTrue(instructions.contains("no inventes comparaciones con competidores"));
        assertTrue(instructions.contains("Nunca cambies de rubro por tu cuenta"));
        assertTrue(instructions.contains("La última elección explícita del cliente manda"));
        assertTrue(instructions.contains("reschedule_booking"));
        assertTrue(instructions.contains("español chileno neutro"));
        assertTrue(instructions.contains("luego usa end_call"));
        assertTrue(instructions.contains("Nunca digas “no puedo cortar la llamada”"));
        assertTrue(instructions.contains("No repitas “¿aló?”"));
    }

    @Test
    void certificationGuardRemainsPrimaryRealtimeToolServiceForProductionInjection() {
        assertTrue(CertificationGuardedRealtimeToolService.class.isAnnotationPresent(Primary.class));
    }
}
