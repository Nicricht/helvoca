package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppReceptionistBookingConfirmationGuardTest {

    @Test
    void doesNotTellCustomerBookingIsConfirmedWithoutSuccessfulCreateBookingResult() {
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
        agent.setActive(true);

        BusinessSubscriptionService.SubscriptionView subscription =
                mock(BusinessSubscriptionService.SubscriptionView.class);

        when(messages.findByExternalMessageId("SM-phantom-booking")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(subscription);
        when(subscription.serviceAllowed()).thenReturn(true);
        when(aiAgents.runtime(businessId)).thenReturn(agent);
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of("create_booking"));
        when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                eq(businessId),
                eq(WhatsAppReceptionistService.CHANNEL),
                eq("+56911111111"),
                eq("+56922222222"),
                any()))
                .thenReturn(Optional.of(conversation));
        when(tools.buildInstructions(conversation)).thenReturn("Instrucciones oficiales");
        when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of());
        when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenReturn("Tu reserva quedó confirmada para mañana a las 11:30. ¡Te esperamos!");

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        String reply = service.handle(
                "SM-phantom-booking",
                "whatsapp:+56911111111",
                "whatsapp:+56922222222",
                "Sí");

        assertEquals(
                "No pude confirmar la reserva porque no recibí una confirmación válida del sistema. ¿Quieres que lo intente nuevamente?",
                reply);
        verify(tools, never()).execute(any(), eq("create_booking"), anyString());
    }

    @Test
    void keepsBookingConfirmationWhenCreateBookingReturnsBookingId() {
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
        UUID bookingId = UUID.randomUUID();

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
        agent.setActive(true);

        BusinessSubscriptionService.SubscriptionView subscription =
                mock(BusinessSubscriptionService.SubscriptionView.class);

        when(messages.findByExternalMessageId("SM-real-booking")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(subscription);
        when(subscription.serviceAllowed()).thenReturn(true);
        when(aiAgents.runtime(businessId)).thenReturn(agent);
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of("create_booking"));
        when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                eq(businessId),
                eq(WhatsAppReceptionistService.CHANNEL),
                eq("+56911111111"),
                eq("+56922222222"),
                any()))
                .thenReturn(Optional.of(conversation));
        when(tools.buildInstructions(conversation)).thenReturn("Instrucciones oficiales");
        when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of());
        when(tools.execute(eq(conversation), eq("create_booking"), anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"bookingId\":\"" + bookingId + "\"},\"error\":null}");
        when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenAnswer(invocation -> {
                    MessagingAiClient.ToolInvoker invoker =
                            invocation.getArgument(3, MessagingAiClient.ToolInvoker.class);
                    invoker.execute("create_booking", "{\"serviceId\":\"service\",\"startAt\":\"tomorrow\"}");
                    return "Tu reserva quedó confirmada para mañana a las 11:30. ¡Te esperamos!";
                });

        WhatsAppReceptionistService service = new WhatsAppReceptionistService(
                phones, customers, conversations, messages, tools, subscriptions, ai, properties, aiAgents);

        String reply = service.handle(
                "SM-real-booking",
                "whatsapp:+56911111111",
                "whatsapp:+56922222222",
                "Sí");

        assertEquals("Tu reserva quedó confirmada para mañana a las 11:30. ¡Te esperamos!", reply);
        verify(tools).execute(eq(conversation), eq("create_booking"), anyString());
    }
}
