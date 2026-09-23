package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppReceptionistStaleHandoffHistoryTest {

    @Test
    void newBookingIntentDoesNotSendOldHumanHandoffReplyBackToAi() {
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

        MessagingMessage staleHandoff = new MessagingMessage();
        staleHandoff.setConversationId(conversationId);
        staleHandoff.setDirection("OUTBOUND");
        staleHandoff.setRole("ASSISTANT");
        staleHandoff.setContent("He escalado tu solicitud a nuestro equipo humano para que puedan ayudarte con la reserva.");

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Helvoca");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);

        BusinessSubscriptionService.SubscriptionView subscription = mock(BusinessSubscriptionService.SubscriptionView.class);
        when(subscription.serviceAllowed()).thenReturn(true);
        when(messages.findByExternalMessageId("SM-new-booking")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(subscription);
        when(aiAgents.runtime(businessId)).thenReturn(agent);
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of());
        when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                eq(businessId),
                eq(WhatsAppReceptionistService.CHANNEL),
                eq("+56911111111"),
                eq("+56922222222"),
                any()))
                .thenReturn(Optional.of(conversation));
        when(tools.buildInstructions(conversation)).thenReturn("Instrucciones oficiales");

        AtomicReference<MessagingMessage> currentInbound = new AtomicReference<>();
        when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> {
            MessagingMessage saved = invocation.getArgument(0);
            currentInbound.set(saved);
            return saved;
        });
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenAnswer(invocation ->
                List.of(staleHandoff, currentInbound.get()));
        when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenReturn("¿Qué servicio deseas reservar?");

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        service.handle(
                "SM-new-booking",
                "whatsapp:+56911111111",
                "whatsapp:+56922222222",
                "Quiero reservar mañana a las 15:00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MessagingAiClient.Turn>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(ai).respond(anyString(), historyCaptor.capture(), anySet(), any(MessagingAiClient.ToolInvoker.class));

        List<MessagingAiClient.Turn> history = historyCaptor.getValue();
        assertEquals(1, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("Quiero reservar mañana a las 15:00", history.get(0).content());
    }
}
