package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
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

    @Test
    void whatsappDestinationMustBeEnabledBeforeResolvingTenant() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(UUID.randomUUID());
        phone.setPhoneNumber("+56922222222");
        phone.setActive(true);
        phone.setWhatsappEnabled(false);

        when(messages.findByExternalMessageId("SM-wa-disabled")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.handle("SM-wa-disabled", "whatsapp:+56911111111", "whatsapp:+56922222222", "hola"));

        assertEquals("WhatsApp destination is not registered or enabled", error.getMessage());
        verify(messages).findByExternalMessageId("SM-wa-disabled");
        verify(phones).findByPhoneNumberAndActiveTrue("+56922222222");
        verifyNoMoreInteractions(phones, messages);
        verifyNoInteractions(customers, conversations, tools, subscriptions, ai, aiAgents);
    }

    @Test
    void inboundMessageIsPersistedBeforeAiResponds() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);

        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+56922222222");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);

        MessagingConversation conversation = new MessagingConversation();
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        conversation.setBusinessId(businessId);
        conversation.setChannel(WhatsAppReceptionistService.CHANNEL);
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Helvoca");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);

        BusinessSubscriptionService.SubscriptionView subscription = mock(BusinessSubscriptionService.SubscriptionView.class);
        when(subscription.serviceAllowed()).thenReturn(true);
        when(messages.findByExternalMessageId("SM-persist-first")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(subscription);
        when(aiAgents.runtime(businessId)).thenReturn(agent);
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of());
        when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                eq(businessId), eq(WhatsAppReceptionistService.CHANNEL), eq("+56911111111"), eq("+56922222222"), any()))
                .thenReturn(Optional.of(conversation));
        when(tools.buildInstructions(conversation)).thenReturn("Instrucciones oficiales");

        AtomicReference<MessagingMessage> persistedInbound = new AtomicReference<>();
        when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> {
            MessagingMessage saved = invocation.getArgument(0);
            persistedInbound.set(saved);
            return saved;
        });
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenAnswer(invocation -> {
            MessagingMessage saved = persistedInbound.get();
            return saved == null ? List.of() : List.of(saved);
        });
        when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenReturn("Respuesta IA");

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        String reply = service.handle(
                "SM-persist-first",
                "whatsapp:+56911111111",
                "whatsapp:+56922222222",
                "  Necesito una reserva  ");

        assertEquals("Respuesta IA", reply);
        MessagingMessage inbound = persistedInbound.get();
        assertNotNull(inbound);
        assertEquals("SM-persist-first", inbound.getExternalMessageId());
        assertEquals(conversationId, inbound.getConversationId());
        assertEquals("INBOUND", inbound.getDirection());
        assertEquals("USER", inbound.getRole());
        assertEquals("Necesito una reserva", inbound.getContent());

        InOrder order = inOrder(messages, ai);
        order.verify(messages).saveAndFlush(any(MessagingMessage.class));
        order.verify(ai).respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class));

        ArgumentCaptor<MessagingMessage> inboundCaptor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(messages).saveAndFlush(inboundCaptor.capture());
        assertEquals("SM-persist-first", inboundCaptor.getValue().getExternalMessageId());
    }

    @Test
    void outboundReplyIsPersistedAndAssociatedWithInboundMessage() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        WhatsAppToolService tools = mock(WhatsAppToolService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MessagingAiClient ai = mock(MessagingAiClient.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        AiAgentService aiAgents = mock(AiAgentService.class);

        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+56922222222");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);

        MessagingConversation conversation = new MessagingConversation();
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        conversation.setBusinessId(businessId);
        conversation.setChannel(WhatsAppReceptionistService.CHANNEL);
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Helvoca");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);

        BusinessSubscriptionService.SubscriptionView subscription = mock(BusinessSubscriptionService.SubscriptionView.class);
        when(subscription.serviceAllowed()).thenReturn(true);
        when(messages.findByExternalMessageId("SM-outbound-link")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(subscription);
        when(aiAgents.runtime(businessId)).thenReturn(agent);
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of());
        when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                eq(businessId), eq(WhatsAppReceptionistService.CHANNEL), eq("+56911111111"), eq("+56922222222"), any()))
                .thenReturn(Optional.of(conversation));
        when(tools.buildInstructions(conversation)).thenReturn("Instrucciones oficiales");

        AtomicReference<MessagingMessage> persistedInbound = new AtomicReference<>();
        when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> {
            MessagingMessage saved = invocation.getArgument(0);
            persistedInbound.set(saved);
            return saved;
        });
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenAnswer(invocation -> {
            MessagingMessage inbound = persistedInbound.get();
            return inbound == null ? List.of() : List.of(inbound);
        });
        when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenReturn("Reserva confirmada para mañana a las 10:00");

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        String reply = service.handle(
                "SM-outbound-link",
                "whatsapp:+56911111111",
                "whatsapp:+56922222222",
                "Reserva para mañana");

        assertEquals("Reserva confirmada para mañana a las 10:00", reply);

        ArgumentCaptor<MessagingMessage> savedCaptor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(messages, times(2)).save(savedCaptor.capture());
        List<MessagingMessage> savedMessages = savedCaptor.getAllValues();

        MessagingMessage outbound = savedMessages.get(0);
        MessagingMessage inbound = savedMessages.get(1);

        assertEquals(conversationId, outbound.getConversationId());
        assertEquals("OUTBOUND", outbound.getDirection());
        assertEquals("ASSISTANT", outbound.getRole());
        assertEquals(reply, outbound.getContent());

        assertEquals("SM-outbound-link", inbound.getExternalMessageId());
        assertEquals(conversationId, inbound.getConversationId());
        assertEquals("INBOUND", inbound.getDirection());
        assertEquals(reply, inbound.getReplyText());
        assertEquals(outbound.getContent(), inbound.getReplyText());
    }
}
