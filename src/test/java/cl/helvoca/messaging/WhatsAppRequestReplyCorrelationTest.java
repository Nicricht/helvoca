package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.request.RequestReplyCorrelationService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PR #764 regression: prove a real server tool result is linked only after its
 * exact inbound reply was persisted, and never after an AI fallback.
 * No model or external provider is contacted by these tests.
 */
class WhatsAppRequestReplyCorrelationTest {
    private static final String SENDER = "whatsapp:+56911111111";
    private static final String RECIPIENT = "whatsapp:+56922222222";

    private static final class Scenario {
        final PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        final CustomerRepository customers = mock(CustomerRepository.class);
        final MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        final MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        final WhatsAppToolService tools = mock(WhatsAppToolService.class);
        final BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        final MessagingAiClient ai = mock(MessagingAiClient.class);
        final AiAgentService agents = mock(AiAgentService.class);
        final RequestReplyCorrelationService correlations = mock(RequestReplyCorrelationService.class);
        final UUID tenant = UUID.randomUUID();
        final UUID conversationId = UUID.randomUUID();
        final UUID inboundId = UUID.randomUUID();
        final MessagingConversation conversation = new MessagingConversation();
        final WhatsAppReceptionistService service;

        Scenario(Set<String> allowedTools, boolean wireCorrelations) {
            PhoneNumber phone = new PhoneNumber();
            phone.setBusinessId(tenant);
            phone.setPhoneNumber("+56922222222");
            phone.setActive(true);
            phone.setWhatsappEnabled(true);

            ReflectionTestUtils.setField(conversation, "id", conversationId);
            conversation.setBusinessId(tenant);
            conversation.setChannel("whatsapp");
            conversation.setSender("+56911111111");
            conversation.setRecipient("+56922222222");

            AiAgent agent = new AiAgent();
            agent.setBusinessId(tenant);
            agent.setActive(true);
            agent.setName("RecepVoz");

            var subscription = mock(BusinessSubscriptionService.SubscriptionView.class);
            when(subscription.serviceAllowed()).thenReturn(true);
            when(messages.findByExternalMessageId(anyString())).thenReturn(Optional.empty());
            when(phones.findByPhoneNumberAndActiveTrue("+56922222222")).thenReturn(Optional.of(phone));
            when(subscriptions.view(tenant)).thenReturn(subscription);
            when(agents.runtime(tenant)).thenReturn(agent);
            when(agents.allowedToolNames(tenant)).thenReturn(allowedTools);
            when(conversations.findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                    eq(tenant), eq("whatsapp"), eq("+56911111111"), eq("+56922222222"), any()))
                    .thenReturn(Optional.of(conversation));
            when(tools.buildInstructions(conversation)).thenReturn("Official tenant instructions");
            when(messages.saveAndFlush(any(MessagingMessage.class))).thenAnswer(invocation -> {
                MessagingMessage inbound = invocation.getArgument(0);
                ReflectionTestUtils.setField(inbound, "id", inboundId);
                return inbound;
            });
            when(messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId))
                    .thenReturn(List.of());
            service = new WhatsAppReceptionistService(phones, customers, conversations, messages,
                    tools, subscriptions, ai, new WhatsAppProperties(), agents);
            if (wireCorrelations) {
                ReflectionTestUtils.setField(service, "requestReplyCorrelations", correlations);
            }
        }

        String reply() {
            return service.handle("SM-correlation", SENDER, RECIPIENT, "necesito presupuesto");
        }

        void answerWithTool(String toolName, String toolResult, String answer) {
            when(tools.execute(eq(conversation), eq(toolName), anyString())).thenReturn(toolResult);
            when(ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                    .thenAnswer(invocation -> {
                        MessagingAiClient.ToolInvoker invoker = invocation.getArgument(3);
                        invoker.execute(toolName, "{}");
                        return answer;
                    });
        }
    }

    private static String validReceipt(UUID requestId, UUID operationId) {
        return new JSONObject().put("success", true).put("data",
                new JSONObject().put("status", "OPEN")
                        .put("requestId", requestId.toString())
                        .put("operationId", operationId.toString())).toString();
    }

    @Test
    void verifiedRequestIsCorrelatedAfterPersistingExactReplyAndBeforeReturning() {
        Scenario scenario = new Scenario(Set.of("create_request"), true);
        UUID request = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        scenario.answerWithTool("create_request", validReceipt(request, operation), "Recibimos tu solicitud");

        assertEquals("Recibimos tu solicitud", scenario.reply());

        InOrder ordered = inOrder(scenario.messages, scenario.correlations);
        ordered.verify(scenario.messages).saveAndFlush(any(MessagingMessage.class));
        ordered.verify(scenario.messages).flush();
        ordered.verify(scenario.correlations).capture(eq(scenario.tenant), eq(scenario.conversationId),
                eq(scenario.inboundId), argThat(refs -> refs.size() == 1
                        && refs.contains(new RequestReplyCorrelationService.CreatedRequest(request, operation))));
        verify(scenario.messages, atLeastOnce()).save(argThat(m -> m != null
                && "Recibimos tu solicitud".equals(m.getReplyText())));
    }

    @Test
    void failedModelTurnDiscardsPreviouslySuccessfulToolReference() {
        Scenario scenario = new Scenario(Set.of("create_request"), true);
        String valid = validReceipt(UUID.randomUUID(), UUID.randomUUID());
        when(scenario.tools.execute(eq(scenario.conversation), eq("create_request"), anyString()))
                .thenReturn(valid);
        when(scenario.ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenAnswer(invocation -> {
                    MessagingAiClient.ToolInvoker invoker = invocation.getArgument(3);
                    assertEquals(valid, invoker.execute("create_request", "{}"));
                    throw new IllegalStateException("Model reply failed after tool creation");
                });

        assertEquals("No pude completar tu solicitud en este momento. Por favor intenta nuevamente en unos minutos.",
                scenario.reply());
        verifyNoInteractions(scenario.correlations);
        verify(scenario.messages, never()).flush();
        verify(scenario.messages).save(argThat(m -> m != null
                && m.getReplyText() != null && m.getReplyText().startsWith("No pude completar")));
    }

    @Test
    void invalidOrDisabledToolResultNeverCreatesCorrelation() {
        Scenario invalid = new Scenario(Set.of("create_request"), true);
        invalid.answerWithTool("create_request", new JSONObject().put("success", false).toString(), "Sin solicitud");
        assertEquals("Sin solicitud", invalid.reply());
        verifyNoInteractions(invalid.correlations);

        Scenario disabled = new Scenario(Set.of(), true);
        when(disabled.ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenAnswer(invocation -> {
                    String receipt = ((MessagingAiClient.ToolInvoker) invocation.getArgument(3))
                            .execute("create_request", "{}");
                    assertFalse(new JSONObject(receipt).getBoolean("success"));
                    return "Operación no habilitada";
                });
        assertEquals("Operación no habilitada", disabled.reply());
        verify(disabled.tools).buildInstructions(disabled.conversation);
        verify(disabled.tools, never()).execute(any(), anyString(), anyString());
        verifyNoInteractions(disabled.correlations);
    }

    @Test
    void missingCorrelationDependencyCannotBeMistakenForEvidence() {
        Scenario scenario = new Scenario(Set.of("create_request"), false);
        scenario.answerWithTool("create_request",
                validReceipt(UUID.randomUUID(), UUID.randomUUID()), "Solicitud recibida");
        assertEquals("Solicitud recibida", scenario.reply());
        verify(scenario.messages, never()).flush();
        verifyNoInteractions(scenario.correlations);
    }

    @Test
    void nullBlankOrWhitespaceModelReplyCannotBeCorrelated() {
        for (String answer : new String[] {null, "", "   "}) {
            Scenario scenario = new Scenario(Set.of("create_request"), true);
            scenario.answerWithTool("create_request",
                    validReceipt(UUID.randomUUID(), UUID.randomUUID()), answer);
            assertEquals(answer, scenario.reply());
            verifyNoInteractions(scenario.correlations);
            verify(scenario.messages, never()).flush();
        }
    }

    @Test
    void twoValidRequestsInSameReplyArePassedAsDistinctExactReferences() {
        Scenario scenario = new Scenario(Set.of("create_request"), true);
        UUID request = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        String valid = validReceipt(request, operation);
        when(scenario.tools.execute(eq(scenario.conversation), eq("create_request"), anyString()))
                .thenReturn(valid);
        when(scenario.ai.respond(anyString(), anyList(), anySet(), any(MessagingAiClient.ToolInvoker.class)))
                .thenAnswer(invocation -> {
                    MessagingAiClient.ToolInvoker invoker = invocation.getArgument(3);
                    assertEquals(valid, invoker.execute("create_request", "{}"));
                    assertEquals(valid, invoker.execute("create_request", "{}"));
                    return "Listo";
                });
        assertEquals("Listo", scenario.reply());
        verify(scenario.correlations).capture(eq(scenario.tenant), eq(scenario.conversationId),
                eq(scenario.inboundId), argThat(refs -> refs.size() == 2
                        && refs.stream().allMatch(x -> x.requestId().equals(request)
                                && x.operationId().equals(operation))));
        // The correlation service is independently idempotent on duplicate tool callbacks.
    }
}
