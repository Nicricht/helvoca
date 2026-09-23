package cl.helvoca.messaging;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.WhatsAppAssistantReplyDeliveryService;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class WhatsAppReceptionistService {
    public static final String CHANNEL = "whatsapp";
    private static final Logger log = LoggerFactory.getLogger(WhatsAppReceptionistService.class);
    private static final String MESSAGE_ID_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";
    private static final String UNVERIFIED_BOOKING_CONFIRMATION_REPLY =
            "No pude confirmar la reserva porque no recibí una confirmación válida del sistema. ¿Quieres que lo intente nuevamente?";

    private final PhoneNumberRepository phones;
    private final CustomerRepository customers;
    private final MessagingConversationRepository conversations;
    private final MessagingMessageRepository messages;
    private final WhatsAppToolService tools;
    private final BusinessSubscriptionService subscriptions;
    private final MessagingAiClient ai;
    private final WhatsAppProperties properties;
    private final AiAgentService aiAgents;

    @Autowired(required = false)
    private BusinessOperationCapabilityService operationCapabilities;

    @Autowired(required = false)
    private CustomerIdentityService customerIdentities;

    @Autowired(required = false)
    private WhatsAppAssistantReplyDeliveryService replyDelivery;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

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
        acquireMessageProcessingLock(messageSid);
        String priorReply = priorReply(messageSid);
        if (priorReply != null) return priorReply;

        String to = normalizeAddress(rawTo);
        if (to.isBlank()) throw new IllegalArgumentException("Invalid WhatsApp destination");
        String tenantDestination = properties.resolveTenantDestination(to);
        PhoneNumber phone = phones.findByPhoneNumberAndActiveTrue(tenantDestination)
                .filter(PhoneNumber::isWhatsappEnabled)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp destination is not registered or enabled"));
        return process(messageSid, rawFrom, phone, body, phone.getWhatsappProvider());
    }

    @Transactional
    public String handleResolved(String messageId,
                                 UUID businessId,
                                 UUID phoneNumberId,
                                 String rawFrom,
                                 String body) {
        acquireMessageProcessingLock(messageId);
        String priorReply = priorReply(messageId);
        if (priorReply != null) return priorReply;

        if (businessId == null || phoneNumberId == null) {
            throw new IllegalArgumentException("Resolved WhatsApp tenant route is required");
        }
        PhoneNumber phone = phones.findByIdAndBusinessId(phoneNumberId, businessId)
                .filter(PhoneNumber::isActive)
                .filter(PhoneNumber::isWhatsappEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Resolved WhatsApp destination is not registered or enabled"));
        return process(messageId, rawFrom, phone, body, MetaWhatsAppMessagingProvider.ID);
    }

    private void acquireMessageProcessingLock(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Message id is required");
        }
        if (jdbcTemplate != null) {
            jdbcTemplate.queryForList(MESSAGE_ID_LOCK_SQL, messageId);
        }
    }

    private String process(String messageId, String rawFrom, PhoneNumber phone, String body, String replyProviderId) {
        String from = normalizeAddress(rawFrom);
        String to = normalizeAddress(phone.getPhoneNumber());
        String text = body == null ? "" : body.trim();
        if (from.isBlank() || to.isBlank() || text.isBlank()) throw new IllegalArgumentException("Invalid WhatsApp message");

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

        attachVerifiedCustomer(conversation, phone.getBusinessId(), from);
        conversation.setLastMessageAt(now);
        conversations.saveAndFlush(conversation);

        MessagingMessage inbound = new MessagingMessage();
        inbound.setConversationId(conversation.getId());
        inbound.setExternalMessageId(messageId);
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent(text);
        inbound = messages.saveAndFlush(inbound);

        String reply;
        boolean[] successfulBookingCreation = {false};
        try {
            MessagingConversation current = conversation;
            Set<String> allowedTools = allowedTools(phone.getBusinessId());
            reply = ai.respond(
                    omnichannelInstructions(tools.buildInstructions(current), agent),
                    history(current.getId()),
                    allowedTools,
                    (name, args) -> {
                        String result = allowedTools.contains(name)
                                ? tools.execute(current, name, args)
                                : disabledToolResult();
                        if (isSuccessfulBookingCreation(name, result)) {
                            successfulBookingCreation[0] = true;
                        }
                        return result;
                    });
            reply = guardUnverifiedBookingConfirmation(text, reply, successfulBookingCreation[0]);
        } catch (Exception e) {
            log.warn("WhatsApp assistant failed message={} business={} type={}",
                    messageId, phone.getBusinessId(), e.getClass().getSimpleName());
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

        if (replyDelivery != null) {
            replyDelivery.scheduleMetaReply(
                    phone.getBusinessId(),
                    inbound.getId(),
                    messageId,
                    replyProviderId,
                    from,
                    reply);
        }
        return reply;
    }

    private String priorReply(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Message id is required");
        }
        MessagingMessage prior = messages.findByExternalMessageId(messageId).orElse(null);
        if (prior == null) return null;
        if (prior.getReplyText() != null) return prior.getReplyText();
        throw new IllegalStateException("WhatsApp message is already being processed");
    }

    private Set<String> allowedTools(UUID businessId) {
        HashSet<String> allowed = new HashSet<>(aiAgents.allowedToolNames(businessId));
        if (operationCapabilities != null) {
            allowed.addAll(operationCapabilities.allowedToolNames(businessId));
        }
        return Set.copyOf(allowed);
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

    private static boolean isSuccessfulBookingCreation(String toolName, String result) {
        if (!"create_booking".equals(toolName) || result == null || result.isBlank()) return false;
        try {
            JSONObject payload = new JSONObject(result);
            if (!payload.optBoolean("success", false)) return false;
            JSONObject data = payload.optJSONObject("data");
            return data != null && !data.optString("bookingId", "").isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String guardUnverifiedBookingConfirmation(String inboundText,
                                                              String reply,
                                                              boolean successfulBookingCreation) {
        if (successfulBookingCreation || !isBookingApproval(inboundText) || !claimsBookingConfirmed(reply)) {
            return reply;
        }
        return UNVERIFIED_BOOKING_CONFIRMATION_REPLY;
    }

    private static boolean isBookingApproval(String text) {
        if (text == null) return false;
        String value = text.trim().toLowerCase(Locale.ROOT);
        return value.matches("^(sí|si|confirmo|confirmar|ok|okay|dale|de acuerdo|correcto|yes|confirm)([\\s,.!¡?¿].*)?$");
    }

    private static boolean claimsBookingConfirmed(String reply) {
        if (reply == null || reply.isBlank()) return false;
        String value = reply.toLowerCase(Locale.ROOT);
        boolean bookingContext = value.contains("reserva")
                || value.contains("cita")
                || value.contains("booking")
                || value.contains("appointment");
        boolean positiveConfirmation = value.contains("confirmad")
                || value.contains("agendad")
                || value.contains("reservad")
                || value.contains("confirmed")
                || value.contains("booked");
        boolean explicitNegation = value.matches("(?s).*(no|aún no|aun no|todavía no|todavia no|sin).{0,24}(confirmad|agendad|reservad|confirmed|booked).*");
        return bookingContext && positiveConfirmation && !explicitNegation;
    }

    private MessagingConversation newConversation(PhoneNumber phone, String from, String to, Instant now) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(phone.getBusinessId());
        conversation.setChannel(CHANNEL);
        conversation.setSender(from);
        conversation.setRecipient(to);
        conversation.setOpenedAt(now);
        conversation.setLastMessageAt(now);
        attachVerifiedCustomer(conversation, phone.getBusinessId(), from);
        return conversation;
    }

    private void attachVerifiedCustomer(MessagingConversation conversation, UUID businessId, String from) {
        if (conversation.getCustomerId() != null || customerIdentities == null) return;
        customerIdentities.resolveVerifiedPhone(businessId, from)
                .ifPresent(conversation::setCustomerId);
    }

    private List<MessagingAiClient.Turn> history(UUID conversationId) {
        List<MessagingMessage> all = messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
        int start = Math.max(0, all.size() - 18);
        List<MessagingAiClient.Turn> out = new ArrayList<>();
        for (int i = start; i < all.size(); i++) {
            MessagingMessage item = all.get(i);
            if (isHistoricalHumanHandoff(item)) continue;
            if (item.getRole() == null || item.getRole().isBlank()) continue;
            if (item.getContent() == null || item.getContent().isBlank()) continue;
            out.add(new MessagingAiClient.Turn(item.getRole().toLowerCase(Locale.ROOT), item.getContent()));
        }
        return out;
    }

    private static boolean isHistoricalHumanHandoff(MessagingMessage item) {
        if (item == null || item.getRole() == null || item.getContent() == null) return false;
        if (!"assistant".equalsIgnoreCase(item.getRole().trim())) return false;

        String content = item.getContent().toLowerCase(Locale.ROOT);
        boolean humanDestination = content.contains("humano")
                || content.contains("humana")
                || content.contains("human team")
                || content.contains("human support");
        boolean handoffAction = content.contains("escalad")
                || content.contains("derivad")
                || content.contains("transferid")
                || content.contains("handoff");
        return humanDestination && handoffAction;
    }

    static String normalizeAddress(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            clean = clean.substring(9).trim();
        }

        String strict = CustomerIdentityService.normalizePhone(clean);
        if (strict != null) return strict;
        if (clean.matches("[1-9][0-9]{7,14}")) return "+" + clean;
        return clean;
    }
}
