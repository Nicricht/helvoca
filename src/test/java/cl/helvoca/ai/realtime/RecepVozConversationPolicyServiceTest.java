package cl.helvoca.ai.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecepVozConversationPolicyServiceTest {

    @Test
    void containsUniversalMultiTenantSalesAndClosingGuidance() {
        String instructions = RecepVozConversationPolicyService.conversationGuidance();

        assertTrue(instructions.contains("motor de atención universal y multiempresa"));
        assertTrue(instructions.contains("Nunca asumas un rubro"));
        assertTrue(instructions.contains("configuración vigente del negocio"));
        assertTrue(instructions.contains("Nunca mezcles información"));
        assertTrue(instructions.contains("La información más reciente y explícita del cliente reemplaza"));
        assertTrue(instructions.contains("cambia materialmente el enfoque"));
        assertTrue(instructions.contains("VENTA CONSULTIVA Y CONVINCENTE"));
        assertTrue(instructions.contains("atributos verificados de la oferta"));
        assertTrue(instructions.contains("no inventes superioridad"));
        assertTrue(instructions.contains("tenant asociados a esta llamada"));
        assertTrue(instructions.contains("catálogo real"));
        assertTrue(instructions.contains("La última elección explícita del cliente manda"));
        assertTrue(instructions.contains("reschedule_booking"));
        assertTrue(instructions.contains("luego usa end_call"));
        assertTrue(instructions.contains("Nunca digas que no puedes cortar la llamada"));
    }

    @Test
    void productionGuidanceDoesNotHardcodeTheHairSalonScenario() {
        String instructions = RecepVozConversationPolicyService.conversationGuidance().toLowerCase();

        assertFalse(instructions.contains("peluquería"));
        assertFalse(instructions.contains("cabello"));
        assertFalse(instructions.contains("ivy league"));
        assertFalse(instructions.contains("partidura lateral"));
        assertFalse(instructions.contains("25.000"));
        assertFalse(instructions.contains("30 minutos"));
        assertFalse(instructions.contains("restaurante"));
    }

    @Test
    void certificationGuardRemainsPrimaryRealtimeToolServiceForProductionInjection() {
        assertTrue(CertificationGuardedRealtimeToolService.class.isAnnotationPresent(Primary.class));
    }
}
