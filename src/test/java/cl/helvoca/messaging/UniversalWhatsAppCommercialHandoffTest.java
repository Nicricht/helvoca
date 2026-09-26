package cl.helvoca.messaging;

import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingOperationSyncService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UniversalWhatsAppCommercialHandoffTest {

    @Test
    void commercialSelectionContinuesSameConversationAndOperationContext() {
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);
        BusinessOperationCapabilityService capabilities = mock(BusinessOperationCapabilityService.class);

        UniversalWhatsAppToolService service = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class),
                mock(CustomerRepository.class),
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class),
                mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class),
                commercial,
                capabilities,
                mock(BookingOperationSyncService.class),
                mock(BookingConfirmationWorkflowService.class));

        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setCustomerId(customerId);
        conversation.setSender("+56911112222");

        try {
            var id = MessagingConversation.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(conversation, conversationId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        String args = new JSONObject()
                .put("operationId", operationId.toString())
                .put("selectionIndex", 2)
                .toString();

        when(commercial.supports("select_showcase_product")).thenReturn(true);
        when(capabilities.isToolAllowed(businessId, "select_showcase_product")).thenReturn(true);
        when(commercial.execute(
                businessId,
                customerId,
                conversationId,
                "+56911112222",
                BusinessOrder.Source.WHATSAPP,
                "select_showcase_product",
                args))
                .thenReturn(new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject()
                                .put("operationId", operationId.toString())
                                .put("selectionIndex", 2))
                        .put("error", JSONObject.NULL)
                        .toString());

        JSONObject result = new JSONObject(service.execute(
                conversation,
                "select_showcase_product",
                args));

        assertTrue(result.getBoolean("success"), result::toString);
        assertEquals(operationId.toString(),
                result.getJSONObject("data").getString("operationId"));
        assertEquals(2, result.getJSONObject("data").getInt("selectionIndex"));

        verify(commercial).execute(
                businessId,
                customerId,
                conversationId,
                "+56911112222",
                BusinessOrder.Source.WHATSAPP,
                "select_showcase_product",
                args);
    }

    @Test
    void disabledCommercialCapabilityFailsBeforeCommercialMutation() {
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);
        BusinessOperationCapabilityService capabilities = mock(BusinessOperationCapabilityService.class);

        UniversalWhatsAppToolService service = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class),
                mock(CustomerRepository.class),
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class),
                mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class),
                commercial,
                capabilities,
                mock(BookingOperationSyncService.class),
                mock(BookingConfirmationWorkflowService.class));

        UUID businessId = UUID.randomUUID();
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);

        when(commercial.supports("quote_payment")).thenReturn(true);
        when(capabilities.isToolAllowed(businessId, "quote_payment")).thenReturn(false);

        JSONObject result = new JSONObject(service.execute(
                conversation,
                "quote_payment",
                new JSONObject().put("targetOperationId", UUID.randomUUID().toString()).toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("TOOL_DISABLED", result.getJSONObject("error").getString("code"));
        verify(commercial, never()).execute(any(), any(), any(), any(), any(), any(), any());
    }
}
