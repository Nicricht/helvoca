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
    void voicePolicyRequiresShortNonRepetitiveCommercialTurns() {
        String policy = RecepVozConversationPolicyService.conversationGuidance();

        assertTrue(policy.contains("Responde normalmente en una o dos frases"));
        assertTrue(policy.contains("máximo aproximado de 25 palabras"));
        assertTrue(policy.contains("No repitas ni resumas lo que el cliente acaba de decir"));
        assertTrue(policy.contains("termina con una sola siguiente acción o pregunta breve"));
        assertTrue(policy.contains("No esperes a que el cliente te pida que vendas"));
    }

    @Test
    void availabilityPolicyLooksAheadWithoutAskingPermissionForEveryDate() {
        String policy = RecepVozConversationPolicyService.conversationGuidance();

        assertTrue(policy.contains("no pidas permiso para revisar el día siguiente"));
        assertTrue(policy.contains("consulta de forma proactiva los próximos días"));
        assertTrue(policy.contains("devuelve opciones concretas de inmediato"));
        assertTrue(policy.contains("No narres que vas a consultar una herramienta"));
    }

    @Test
    void bookingPolicyUsesOnlyOneExplicitConfirmationAtTheMutationBoundary() {
        String policy = RecepVozConversationPolicyService.conversationGuidance();

        assertTrue(policy.contains("una sola confirmación explícita"));
        assertTrue(policy.contains("No uses frases como \"para confirmar\" mientras todavía estás recopilando"));
        assertTrue(policy.contains("Recopila primero los datos imprescindibles"));
        assertTrue(policy.contains("Después de un sí claro, ejecuta la segunda fase sin volver a pedir confirmación"));
        assertTrue(policy.contains("Al terminar, informa el éxito una sola vez"));
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
