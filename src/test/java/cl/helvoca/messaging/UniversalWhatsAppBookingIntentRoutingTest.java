package cl.helvoca.messaging;

import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingOperationSyncService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UniversalWhatsAppBookingIntentRoutingTest {

    @Test
    void rejectsBookingIntentSentToCreateRequest() {
        BusinessRequestService requests = mock(BusinessRequestService.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        UniversalWhatsAppToolService tools = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class),
                customers,
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(BusinessScheduleService.class),
                requests,
                mock(UnansweredQuestionService.class),
                mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class),
                mock(CommercialOperationToolService.class),
                mock(BusinessOperationCapabilityService.class),
                mock(BookingOperationSyncService.class),
                mock(BookingConfirmationWorkflowService.class));

        UUID businessId = UUID.randomUUID();
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setSender("+56911111111");
        when(customers.findFirstByBusinessIdAndPhone(businessId, conversation.getSender()))
                .thenReturn(Optional.empty());

        String raw = tools.execute(
                conversation,
                "create_request",
                new JSONObject()
                        .put("requestType", "reserva")
                        .put("title", "Reservar una hora")
                        .put("description", "Quiero agendar una cita")
                        .toString());

        JSONObject result = new JSONObject(raw);
        assertFalse(result.getBoolean("success"));
        assertEquals(
                "BOOKING_INTENT_MUST_USE_BOOKING",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(requests);
    }
}
