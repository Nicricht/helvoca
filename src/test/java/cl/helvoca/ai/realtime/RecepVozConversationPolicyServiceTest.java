package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecepVozConversationPolicyServiceTest {

    @Test
    void appendsAdaptiveAdviceCommercialAndClosingGuidance() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        Business business = new Business();
        business.setName("Café Cliente Prueba");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        RecepVozConversationPolicyService service = new RecepVozConversationPolicyService(
                businesses,
                mock(CustomerRepository.class),
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(CallSessionRepository.class),
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class));

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), businessId, null,
                "+56911111111", "+14355652512", "MZ-test");

        String instructions = service.buildInstructions(context);

        assertTrue(instructions.contains("POLÍTICA CONVERSACIONAL ADAPTATIVA DE RECEPVOZ"));
        assertTrue(instructions.contains("qué corte de cabello suele verse formal"));
        assertTrue(instructions.contains("ASISTENCIA COMERCIAL"));
        assertTrue(instructions.contains("español chileno neutro"));
        assertTrue(instructions.contains("no vuelvas a preguntar si necesita algo"));
        assertTrue(instructions.contains("No repitas “¿aló?”"));
    }

    @Test
    void isPrimaryRealtimeToolServiceForProductionInjection() {
        assertTrue(RecepVozConversationPolicyService.class.isAnnotationPresent(Primary.class));
    }
}
