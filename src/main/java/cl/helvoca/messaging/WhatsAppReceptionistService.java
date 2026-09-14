package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class WhatsAppReceptionistService {
    public static final String CHANNEL = "whatsapp";
    private static final Logger log = LoggerFactory.getLogger(WhatsAppReceptionistService.class);

    private final PhoneNumberRepository phones;
    private final CustomerRepository customers;
    private final MessagingConversationRepository conversations;
    private final MessagingMessageRepository messages;
    private final WhatsAppToolService tools;
    private final BusinessSubscriptionService subscriptions;
    private final MessagingAiClient ai;
    private final WhatsAppProperties properties;
    private final AiAgentService aiAgents;

    public WhatsAppReceptionistService(PhoneNumberRepository phones,
                                       CustomerRepository customers,
                                       MessagingConversationRepository conversations,
                                       MessagingMessageRepository messages,
                                       WhatsAppToolService tools,
                                       BusinessSubscriptionService subscriptions,
                                       MessagingAiClient ai,
                                       WhatsAppProperties properties,
                                       AiAgentService aiAgents) {
        this.phones = phones;
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.tools = tools;
        this.subscriptions = subscriptions;
        this.ai = ai;
        this.properties = properties;
        this.aiAgents = aiAgents;
    }

    @Transactional
    public String handle(String messageSid, String rawFrom, String rawTo, String body) {
        if (messageSid == null || messageSid.isBlank()) throw new IllegalArgumentException("MessageSid is required");
        MessagingMessage prior = messages.findByExternalMessageId(messageSid).orElse(null);
        if (prior != null && prior.getReplyText() != null) return prior.getReplyText();

        String from = normalizeAddress(rawFrom);
        String to = normalizeAddress(rawTo);
        String text = body == null ? "" : body.trim();
        if (from.isBlank() || to.isBlank() || text.isBlank()) throw new IllegalArgumentException("Invalid WhatsApp message");

        PhoneNumber phone = phones.findByPhoneNumberAndActiveTrue(to)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp destination is not registered"));
        if (!subscriptions.view(phone.getBusinessId()).serviceAllowed()) {
            throw new IllegalStateException("Subscription does not allow service");
        }

        AiAgent agent = aiAgents.runtime(phone.getBusinessId());
        if (!agent.isActive()) {
            throw new IllegalStateException("AI agent is disabled for this business");
        }

        Instant now = Instant.now();
        Instant after = now.minus(Duration.ofHours(properties.getSessionHours()));
        MessagingConversation conversation = conversations
                .findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
                        phone.getBusinessId(), CHANNEL, from, to, after)
                .orElseGet(() -> newConversation(phone, from, to, now));

        if (conversation.getCustomerId() == null) {
            customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), from)
                    .ifPresent(customer -> conversation.setCustomerId(customer.getId()));
        }
        conversation.setLastMessageAt(now);
        conversations.saveAndFlush(conversation);

        MessagingMessage inbound = prior == null ? new MessagingMessage() : prior;
        inbound.setConversationId(conversation.getId());
        inbound.setExternalMessageId(messageSid);
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent(text);
        inbound = messages.saveAndFlush(inbound);

        String reply;
        try {
            MessagingConversation current = conversation;
            Set<String> allowedTools = aiAgents.allowedToolNames(phone.getBusinessId());
            reply = ai.respond(
                    omnichannelInstructions(tools.buildInstructions(current), agent),
                    history(current.getId()),
                    allowedTools,
                    (name, args) -> allowedTools.contains(name)
                            ? tools.execute(current, name, args)
                            : disabledToolResult());
        } catch (Exception e) {
            log.warn("WhatsApp assistant failed message={} business={} type={}",
                    messageSid, phone.getBusinessId(), e.getClass().getSimpleName());
            reply = "No pude completar tu solicitud en este momento. Por favor intenta nuevamente en unos minutos.";
        }

        MessagingMessage outbound = new MessagingMessage();
        outbound.setConversationId(conversation.getId());
        outbound.setDirection("OUTBOUND");
        outbound.setRole("ASSISTANT");
        outbound.setContent(reply);
        messages.save(outbound);

        inbound.setReplyText(reply);
        messages.save(inbound);
        conversation.setLastMessageAt(Instant.now());
        conversations.save(conversation);
        return reply;
    }

    private static String omnichannelInstructions(String base, AiAgent agent) {
        String name = agent.getName() == null || agent.getName().isBlank() ? "Helvoca" : agent.getName().trim();
        String language = agent.getLanguage() == null || agent.getLanguage().isBlank() ? "es" : agent.getLanguage().trim();
        String greeting = agent.getGreeting() == null || agent.getGreeting().isBlank()
                ? "Sin saludo personalizado."
                : agent.getGreeting().trim();
        String custom = agent.getInstructions() == null || agent.getInstructions().isBlank()
                ? "Sin instrucciones adicionales."
                : agent.getInstructions().trim();

        return base + "\n" + """
                PERFIL OMNICANAL DEL AGENTE:
                Tu identidad para este negocio es %s y debes comunicarte en el idioma %s.
                SALUDO DE REFERENCIA: %s
                INSTRUCCIONES PERSONALIZADAS DEL NEGOCIO: %s
                Aplica estas instrucciones únicamente dentro de las reglas obligatorias y los datos oficiales del tenant actual.

                COMPORTAMIENTO COMERCIAL ADAPTATIVO:
                Adapta vocabulario, recomendaciones y forma de vender al negocio y a la necesidad concreta del cliente.
                Cuando pidan una recomendación o que los convenzas, usa únicamente atributos verificados del catálogo o conocimiento oficial, conviértelos en beneficios relevantes y termina con una sola acción siguiente.
                No inventes superioridad frente a competidores, promociones, garantías, escasez, resultados ni líneas de negocio que no existan en los datos actuales.
                Si el cliente corrige una preferencia, fecha, hora, presupuesto o necesidad, la información más reciente reemplaza la anterior incompatible.
                Si cambia una reserva ya creada, modifica la reserva existente en lugar de crear una duplicada.
                Mantén continuidad con la misma identidad y reglas que usa la atención por voz del negocio.
                """.formatted(name, language, greeting, custom);
    }

    private static String disabledToolResult() {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "TOOL_DISABLED")
                        .put("message", "Esta operación no está habilitada para el agente de este negocio."))
                .toString();
    }

    private MessagingConversation newConversation(PhoneNumber phone, String from, String to, Instant now) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(phone.getBusinessId());
        conversation.setChannel(CHANNEL);
        conversation.setSender(from);
        conversation.setRecipient(to);
        conversation.setOpenedAt(now);
        conversation.setLastMessageAt(now);
        customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), from)
                .ifPresent(customer -> conversation.setCustomerId(customer.getId()));
        return conversation;
    }

    private List<MessagingAiClient.Turn> history(java.util.UUID conversationId) {
        List<MessagingMessage> all = messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
        int start = Math.max(0, all.size() - 18);
        List<MessagingAiClient.Turn> out = new ArrayList<>();
        for (int i = start; i < all.size(); i++) {
            MessagingMessage item = all.get(i);
            out.add(new MessagingAiClient.Turn(item.getRole().toLowerCase(), item.getContent()));
        }
        return out;
    }

    static String normalizeAddress(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.regionMatches(true, 0, "whatsapp:", 0, 9) ? clean.substring(9).trim() : clean;
    }
}
