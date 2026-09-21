package cl.helvoca.messaging.outbound;

import cl.helvoca.audit.AuditService;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppDeliveryStatusService {
    public enum Result { UPDATED, IGNORED, NOT_FOUND }

    private final OutboundMessageRepository outboundMessages;
    private final MessagingMessageRepository conversationMessages;
    private final MessagingConversationRepository conversations;
    private final PhoneNumberRepository phones;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppDeliveryStatusService(
            OutboundMessageRepository outboundMessages,
            MessagingMessageRepository conversationMessages,
            MessagingConversationRepository conversations,
            PhoneNumberRepository phones,
            AuditService auditService) {
        this.outboundMessages = outboundMessages;
        this.conversationMessages = conversationMessages;
        this.conversations = conversations;
        this.phones = phones;
        this.auditService = auditService;
    }

    MetaWhatsAppDeliveryStatusService(
            OutboundMessageRepository outboundMessages,
            MessagingMessageRepository conversationMessages,
            MessagingConversationRepository conversations) {
        this(outboundMessages, conversationMessages, conversations, null, null);
    }

    @Transactional
    public Result apply(
            UUID businessId,
            String providerMessageId,
            String rawStatus,
            Instant occurredAt,
            String rawErrorCode) {
        if (businessId == null) return Result.IGNORED;
        String messageId = trim(providerMessageId);
        String next = normalizeStatus(rawStatus);
        if (messageId == null || next == null) return Result.IGNORED;

        OutboundMessage outbound = outboundMessages
                .findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                        MetaWhatsAppMessagingProvider.ID, messageId)
                .orElse(null);
        if (outbound != null && businessId.equals(outbound.getBusinessId())) {
            Result result = apply(outbound, next, occurredAt, rawErrorCode);
            certifyMetaSenderIfNeeded(businessId, next, occurredAt, result);
            return result;
        }

        MessagingMessage conversationMessage = conversationMessages
                .findByProviderAndProviderMessageId(
                        MetaWhatsAppMessagingProvider.ID, messageId)
                .orElse(null);
        if (conversationMessage == null
                || conversations.findByIdAndBusinessId(
                        conversationMessage.getConversationId(), businessId).isEmpty()) {
            return Result.NOT_FOUND;
        }
        Result result = apply(conversationMessage, next, occurredAt, rawErrorCode);
        certifyMetaSenderIfNeeded(businessId, next, occurredAt, result);
        return result;
    }

    private void certifyMetaSenderIfNeeded(
            UUID businessId,
            String next,
            Instant occurredAt,
            Result result) {
        if (result != Result.UPDATED
                || (!"DELIVERED".equals(next) && !"READ".equals(next))
                || phones == null
                || auditService == null) {
            return;
        }

        List<PhoneNumber> senders = phones
                .findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId)
                .stream()
                .filter(phone -> MetaWhatsAppMessagingProvider.ID.equalsIgnoreCase(phone.getWhatsappProvider()))
                .toList();
        if (senders.size() != 1) return;

        PhoneNumber sender = senders.getFirst();
        if (sender.getWhatsappCertifiedAt() != null) return;

        sender.setWhatsappCertifiedAt(occurredAt == null ? Instant.now() : occurredAt);
        phones.saveAndFlush(sender);
        auditService.success(
                businessId,
                "META_WHATSAPP_CERTIFICATION_COMPLETED",
                "WHATSAPP_SENDER",
                sender.getId());
    }

    private Result apply(
            OutboundMessage message,
            String next,
            Instant occurredAt,
            String rawErrorCode) {
        if (!shouldAdvance(message.getProviderDeliveryStatus(), next)) return Result.IGNORED;

        Instant at = occurredAt == null ? Instant.now() : occurredAt;
        message.setProviderDeliveryStatus(next);
        message.setDeliveryUpdatedAt(at);
        if ("DELIVERED".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
            message.setFailureCode(null);
        } else if ("READ".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
            if (message.getReadAt() == null) message.setReadAt(at);
            message.setFailureCode(null);
        } else if ("FAILED".equals(next)) {
            message.setFailureCode(failureCode(rawErrorCode));
        }
        outboundMessages.saveAndFlush(message);
        return Result.UPDATED;
    }

    private Result apply(
            MessagingMessage message,
            String next,
            Instant occurredAt,
            String rawErrorCode) {
        if (!shouldAdvance(message.getProviderDeliveryStatus(), next)) return Result.IGNORED;

        Instant at = occurredAt == null ? Instant.now() : occurredAt;
        message.setProviderDeliveryStatus(next);
        message.setDeliveryUpdatedAt(at);
        if ("DELIVERED".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
            message.setFailureCode(null);
        } else if ("READ".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
            if (message.getReadAt() == null) message.setReadAt(at);
            message.setFailureCode(null);
        } else if ("FAILED".equals(next)) {
            message.setFailureCode(failureCode(rawErrorCode));
        }
        conversationMessages.saveAndFlush(message);
        return Result.UPDATED;
    }

    static String normalizeStatus(String value) {
        String status = trim(value);
        if (status == null) return null;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "sent" -> "SENT";
            case "delivered" -> "DELIVERED";
            case "read" -> "READ";
            case "failed" -> "FAILED";
            default -> null;
        };
    }

    private static boolean shouldAdvance(String current, String next) {
        if (current == null || current.isBlank()) return true;
        if (current.equals(next)) return false;
        if ("READ".equals(current) || "FAILED".equals(current)) return false;
        if ("DELIVERED".equals(current)) return "READ".equals(next);
        if ("FAILED".equals(next)) return true;
        return rank(next) >= rank(current);
    }

    private static int rank(String status) {
        return switch (status) {
            case "SENT" -> 1;
            case "DELIVERED" -> 2;
            case "READ" -> 3;
            default -> 0;
        };
    }

    private static String failureCode(String rawErrorCode) {
        String code = trim(rawErrorCode);
        if (code == null) code = "FAILED";
        String safe = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        String value = "META_" + safe;
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
