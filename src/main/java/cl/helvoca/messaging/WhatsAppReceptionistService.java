package cl.helvoca.messaging;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public WhatsAppReceptionistService(PhoneNumberRepository phones,
                                       CustomerRepository customers,
                                       MessagingConversationRepository conversations,
                                       MessagingMessageRepository messages,
                                       WhatsAppToolService tools,
                                       BusinessSubscriptionService subscriptions,
                                       MessagingAiClient ai,
                                       WhatsAppProperties properties) {
        this.phones = phones;
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.tools = tools;
        this.subscriptions = subscriptions;
        this.ai = ai;
        this.properties = properties;
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
        conversation = conversations.saveAndFlush(conversation);

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
            reply = ai.respond(
                    tools.buildInstructions(current),
                    history(current.getId()),
                    (name, args) -> tools.execute(current, name, args));
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
