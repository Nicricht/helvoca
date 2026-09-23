package cl.helvoca.messaging;

import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingOperationSyncService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UniversalWhatsAppBookingNameTest {

    @Test
    void knownCustomerStillRequiresExplicitNameForNewBookingProposal() {
        Fixture fixture = fixture("Nombre antiguo");

        String raw = fixture.tools.execute(
                fixture.conversation,
                "create_booking",
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", "2026-09-24T19:00:00Z")
                        .toString());

        JSONObject result = new JSONObject(raw);
        assertFalse(result.getBoolean("success"));
        assertEquals("BOOKING_NAME_REQUIRED",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(fixture.workflow);
    }

    @Test
    void explicitlyConfirmedNameUpdatesProfileAndAllowsProposal() {
        Fixture fixture = fixture("Nombre antiguo");
        UUID operationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(fixture.workflow.execute(
                eq(fixture.businessId),
                eq(fixture.customerId),
                eq(fixture.conversationId),
                eq("+56911111111"),
                any(),
                any(),
                any(JSONObject.class)))
                .thenReturn(new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject()
                                .put("operationId", operationId.toString())
                                .put("confirmationToken", token.toString())
                                .put("requiresConfirmation", true))
                        .put("error", JSONObject.NULL));

        String raw = fixture.tools.execute(
                fixture.conversation,
                "create_booking",
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", "2026-09-24T19:00:00Z")
                        .put("customerName", "Nicolás Vega")
                        .toString());

        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        verify(fixture.customer).setName("Nicolás Vega");
        verify(fixture.customers).saveAndFlush(fixture.customer);
        verify(fixture.workflow).execute(
                eq(fixture.businessId),
                eq(fixture.customerId),
                eq(fixture.conversationId),
                eq("+56911111111"),
                any(),
                any(),
                argThat(args -> "Nicolás Vega".equals(args.optString("customerName"))));
    }

    private static Fixture fixture(String existingName) {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customer.getName()).thenReturn(existingName);
        when(customers.findByIdAndBusinessId(customerId, businessId))
                .thenReturn(Optional.of(customer));
        when(customers.saveAndFlush(customer)).thenReturn(customer);

        MessagingConversation conversation = mock(MessagingConversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getBusinessId()).thenReturn(businessId);
        when(conversation.getCustomerId()).thenReturn(customerId);
        when(conversation.getSender()).thenReturn("+56911111111");

        BookingConfirmationWorkflowService workflow = mock(BookingConfirmationWorkflowService.class);
        UniversalWhatsAppToolService tools = new UniversalWhatsAppToolService(
                mock(BusinessRepository.class),
                customers,
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class),
                mock(MessagingConversationRepository.class),
                mock(JdbcTemplate.class),
                mock(CommercialOperationToolService.class),
                mock(BusinessOperationCapabilityService.class),
                mock(BookingOperationSyncService.class),
                workflow);

        return new Fixture(
                tools,
                conversation,
                workflow,
                customers,
                customer,
                businessId,
                customerId,
                conversationId);
    }

    private record Fixture(
            UniversalWhatsAppToolService tools,
            MessagingConversation conversation,
            BookingConfirmationWorkflowService workflow,
            CustomerRepository customers,
            Customer customer,
            UUID businessId,
            UUID customerId,
            UUID conversationId) {
    }
}
