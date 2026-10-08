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
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.request.RequestCreationObservationDispatcher;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestSource;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UniversalWhatsAppRequestObservationTest {
    @Test
    void realCreatedRequestSchedulesObservationWithoutChangingAiToolResponse() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        String sender = "+56911112222";

        MessagingConversation conversation = new MessagingConversation();
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        conversation.setBusinessId(businessId);
        conversation.setSender(sender);

        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findFirstByBusinessIdAndPhone(businessId, sender))
                .thenReturn(Optional.empty());

        BusinessRequestService requests = mock(BusinessRequestService.class);
        BusinessRequest request = mock(BusinessRequest.class);
        when(request.getId()).thenReturn(requestId);
        when(request.getOperationId()).thenReturn(operationId);
        when(request.getStatus()).thenReturn(cl.helvoca.request.RequestStatus.OPEN);
        when(request.getRequestType()).thenReturn("GENERAL");
        when(request.getTitle()).thenReturn("Consulta pendiente");

        when(requests.createFromAi(
                eq(businessId), isNull(), eq(conversationId),
                eq("GENERAL"), eq("Consulta pendiente"),
                isNull(), isNull(), eq(sender),
                eq(RequestPriority.NORMAL), isNull(), eq(RequestSource.AI_WHATSAPP)))
                .thenReturn(request);

        UniversalWhatsAppToolService tools = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class), customers,
                mock(ServiceItemRepository.class), mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class), mock(BusinessScheduleService.class), requests,
                mock(UnansweredQuestionService.class), mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class), mock(CommercialOperationToolService.class),
                mock(BusinessOperationCapabilityService.class),
                mock(BookingOperationSyncService.class),
                mock(BookingConfirmationWorkflowService.class));

        RequestCreationObservationDispatcher observer = mock(RequestCreationObservationDispatcher.class);
        ReflectionTestUtils.setField(tools, "requestObservations", observer);

        JSONObject result = new JSONObject(tools.execute(
                conversation, "create_request",
                new JSONObject().put("requestType", "GENERAL")
                        .put("title", "Consulta pendiente").toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(requestId.toString(), result.getJSONObject("data").getString("requestId"));
        assertEquals(operationId.toString(), result.getJSONObject("data").getString("operationId"));
        verify(observer).afterSuccessfulCommit(businessId, conversationId,
                RequestSource.AI_WHATSAPP, requestId, operationId);
        verify(requests, times(1)).createFromAi(
                eq(businessId), isNull(), eq(conversationId), eq("GENERAL"),
                eq("Consulta pendiente"), isNull(), isNull(), eq(sender),
                eq(RequestPriority.NORMAL), isNull(), eq(RequestSource.AI_WHATSAPP));
    }

    @Test
    void malformedRequestDoesNotScheduleObservation() {
        UUID businessId = UUID.randomUUID();
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setSender("+56911112222");

        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findFirstByBusinessIdAndPhone(eq(businessId), anyString()))
                .thenReturn(Optional.empty());
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UniversalWhatsAppToolService tools = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class), customers,
                mock(ServiceItemRepository.class), mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class), mock(BusinessScheduleService.class), requests,
                mock(UnansweredQuestionService.class), mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class), mock(CommercialOperationToolService.class),
                mock(BusinessOperationCapabilityService.class),
                mock(BookingOperationSyncService.class),
                mock(BookingConfirmationWorkflowService.class));
        RequestCreationObservationDispatcher observer = mock(RequestCreationObservationDispatcher.class);
        ReflectionTestUtils.setField(tools, "requestObservations", observer);

        JSONObject result = new JSONObject(tools.execute(conversation, "create_request",
                new JSONObject().put("requestType", "GENERAL").toString()));
        assertFalse(result.getBoolean("success"));
        verifyNoInteractions(observer);
    }
}
