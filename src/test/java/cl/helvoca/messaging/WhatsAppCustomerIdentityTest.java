package cl.helvoca.messaging;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsAppCustomerIdentityTest {

    @Test
    void registerCallerCreatesCustomerLinksConversationAndRecordsProviderAssertedPhone() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UnansweredQuestionService unansweredQuestions = mock(UnansweredQuestionService.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CustomerIdentityService identities = mock(CustomerIdentityService.class);

        WhatsAppToolService service = new WhatsAppToolService(
                businesses, customers, services, knowledge, bookings, schedule,
                requests, unansweredQuestions, conversations, jdbc);
        ReflectionTestUtils.setField(service, "customerIdentities", identities);

        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String sender = "+56911111111";

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(businessId);
        conversation.setSender(sender);

        when(customers.findFirstByBusinessIdAndPhone(businessId, sender)).thenReturn(Optional.empty());
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            ReflectionTestUtils.setField(customer, "id", customerId);
            return customer;
        });

        JSONObject result = new JSONObject(service.execute(
                conversation,
                "register_caller",
                new JSONObject().put("name", "Cliente WhatsApp").toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(customerId, conversation.getCustomerId());
        verify(customers).saveAndFlush(any(Customer.class));
        verify(conversations).save(conversation);
        verify(identities).recordProviderAssertedPhone(
                businessId, customerId, sender, "TWILIO_WHATSAPP");
    }
}
