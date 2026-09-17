package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WhatsAppReceptionistServiceTest {

    @Test
    void repeatedMessageSidReturnsStoredReplyWithoutRepeatingBusinessActions() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);

        MessagingMessage previous = new MessagingMessage();
        previous.setReplyText("respuesta guardada");
        when(messages.findByExternalMessageId("SM-repeat")).thenReturn(Optional.of(previous));

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        String reply = service.handle("SM-repeat", "whatsapp:+56911111111", "whatsapp:+56922222222", "hola");

        assertEquals("respuesta guardada", reply);
        verify(messages).findByExternalMessageId("SM-repeat");
        verifyNoMoreInteractions(messages);
        verifyNoInteractions(phones, customers, conversations, tools, subscriptions, ai, aiAgents);
    }

    @Test
    void repeatedMessageSidStillInProgressDoesNotRepeatBusinessActions() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);

        MessagingMessage previous = new MessagingMessage();
        when(messages.findByExternalMessageId("SM-in-progress")).thenReturn(Optional.of(previous));

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.handle("SM-in-progress", "whatsapp:+56911111111", "whatsapp:+56922222222", "hola"));

        assertEquals("WhatsApp message is already being processed", error.getMessage());
        verify(messages).findByExternalMessageId("SM-in-progress");
        verifyNoMoreInteractions(messages);
        verifyNoInteractions(phones, customers, conversations, tools, subscriptions, ai, aiAgents);
    }
}
