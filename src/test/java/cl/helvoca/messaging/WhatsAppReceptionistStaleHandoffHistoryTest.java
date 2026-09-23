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
        TestHarness harness = new TestHarness();

        MessagingMessage staleHandoff = new MessagingMessage();
        staleHandoff.setConversationId(harness.conversationId);
        staleHandoff.setDirection("OUTBOUND");
        staleHandoff.setRole("ASSISTANT");
        staleHandoff.setContent("He escalado tu solicitud a nuestro equipo humano para que puedan ayudarte con la reserva.");

        when(harness.messages.findAllByConversationIdOrderByCreatedAtAsc(harness.conversationId)).thenAnswer(invocation ->
                List.of(staleHandoff, harness.currentInbound.get()));

        List<MessagingAiClient.Turn> history = harness.handleAndCaptureHistory();

        assertEquals(1, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("Quiero reservar mañana a las 15:00", history.get(0).content());
    }

    @Test
    void usefulAssistantHistoryMentioningHumanTeamIsPreservedWhenItIsNotAHandoff() {
        TestHarness harness = new TestHarness();

        MessagingMessage usefulAssistantContext = new MessagingMessage();
        usefulAssistantContext.setConversationId(harness.conversationId);
        usefulAssistantContext.setDirection("OUTBOUND");
        usefulAssistantContext.setRole("ASSISTANT");
        usefulAssistantContext.setContent("Nuestro equipo humano atiende de 09:00 a 18:00.");

        when(harness.messages.findAllByConversationIdOrderByCreatedAtAsc(harness.conversationId)).thenAnswer(invocation ->
                List.of(usefulAssistantContext, harness.currentInbound.get()));

        List<MessagingAiClient.Turn> history = harness.handleAndCaptureHistory();

        assertEquals(2, history.size());
        assertEquals("assistant", history.get(0).role());
        assertEquals("Nuestro equipo humano atiende de 09:00 a 18:00.", history.get(0).content());
        assertEquals("user", history.get(1).role());
        assertEquals("Quiero reservar mañana a las 15:00", history.get(1).content());
    }

    private static final class TestHarness {
        private final PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        private final MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        private final WhatsAppToolService tools = mock(WhatsAppToolService.class);
        private final BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        private final MessagingAiClient ai = mock(MessagingAiClient.class);
        private final WhatsAppProperties properties = new WhatsAppProperties();
        private final AiAgentService aiAgents = mock(AiAgentService.class);
        private final UUID businessId = UUID.randomUUID();
        private final UUID conversationId = UUID.randomUUID();
        private final AtomicReference<MessagingMessage> currentInbound = new AtomicReference<>();
        private final WhatsAppReceptionistService service;

        private TestHarness() {
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
            when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> {
                MessagingMessage saved = invocation.getArgument(0);
                currentInbound.set(saved);
                return saved;
            });
            when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                    .thenReturn("¿Qué servicio deseas reservar?");

            service = new WhatsAppReceptionistService(
                    phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);
        }

        private List<MessagingAiClient.Turn> handleAndCaptureHistory() {
            service.handle(
                    "SM-new-booking",
                    "whatsapp:+56911111111",
                    "whatsapp:+56922222222",
                    "Quiero reservar mañana a las 15:00");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<MessagingAiClient.Turn>> historyCaptor = ArgumentCaptor.forClass(List.class);
            verify(ai).respond(anyString(), historyCaptor.capture(), anySet(), any(MessagingAiClient.ToolInvoker.class));
            return historyCaptor.getValue();
        }
    }
}
