package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WhatsAppReceptionistMessageIdempotencyLockTest {

    @Test
    void acquiresDatabaseMessageLockBeforeLookingUpPriorReply() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        MessagingMessage previous = new MessagingMessage();
        previous.setReplyText("respuesta guardada");
        when(messages.findByExternalMessageId("wamid.concurrent")).thenReturn(Optional.of(previous));

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);

        String reply = service.handleResolved(
                "wamid.concurrent",
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "56911112222",
                "Quiero reservar mañana a las 11:30");

        assertEquals("respuesta guardada", reply);

        InOrder order = inOrder(jdbcTemplate, messages);
        order.verify(jdbcTemplate).queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                "wamid.concurrent");
        order.verify(messages).findByExternalMessageId("wamid.concurrent");
        verifyNoInteractions(phones, customers, conversations, tools, subscriptions, ai, aiAgents);
    }
}
