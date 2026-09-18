package cl.helvoca.booking;

import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingIncidentDeliveryStatusService {
    private final BookingIncidentRecipientRepository recipients;
    private final OutboundMessageRepository messages;
    private final CustomerRepository customers;

    public BookingIncidentDeliveryStatusService(
            BookingIncidentRecipientRepository recipients,
            OutboundMessageRepository messages,
            CustomerRepository customers) {
        this.recipients = recipients;
        this.messages = messages;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<RecipientDeliveryStatus> forCampaign(UUID businessId, UUID campaignId) {
        return recipients.findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(campaignId, businessId)
                .stream()
                .map(recipient -> map(businessId, recipient))
                .toList();
    }

    private RecipientDeliveryStatus map(UUID businessId, BookingIncidentRecipient recipient) {
        OutboundMessage message = recipient.getOutboundMessageId() == null
                ? null
                : messages.findByIdAndBusinessId(recipient.getOutboundMessageId(), businessId).orElse(null);
        String customerName = customers.findByIdAndBusinessId(recipient.getCustomerId(), businessId)
                .map(Customer::getName)
                .filter(value -> value != null && !value.isBlank())
                .orElse("Cliente");

        String state = state(recipient, message);
        Instant statusAt = statusAt(recipient, message);
        boolean retryable = message != null
                && message.getStatus() == OutboundMessage.Status.SENT
                && isProviderFailure(message.getProviderDeliveryStatus())
                && message.getRetryCount() < 3;

        return new RecipientDeliveryStatus(
                recipient.getId(),
                recipient.getCustomerId(),
                customerName,
                channel(recipient, message),
                state,
                statusAt,
                retryable,
                message == null ? 0 : message.getRetryCount());
    }

    private static String state(BookingIncidentRecipient recipient, OutboundMessage message) {
        if (message == null) {
            return recipient.getStatus() == BookingIncidentRecipient.Status.CANCELLED
                    ? "CANCELLED"
                    : recipient.getStatus() == BookingIncidentRecipient.Status.QUEUED ? "QUEUED" : "PENDING";
        }

        String provider = message.getProviderDeliveryStatus();
        if ("READ".equals(provider)) return "READ";
        if ("DELIVERED".equals(provider)) return "DELIVERED";
        if (isProviderFailure(provider)) return "FAILED";
        if ("SENT".equals(provider)) return "SENT";
        if ("QUEUED".equals(provider)) return "QUEUED";

        return switch (message.getStatus()) {
            case SENT -> "SENT";
            case FAILED, BLOCKED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case PREPARED, QUEUED -> "QUEUED";
        };
    }

    private static Instant statusAt(BookingIncidentRecipient recipient, OutboundMessage message) {
        if (message == null) return recipient.getUpdatedAt();
        if (message.getReadAt() != null) return message.getReadAt();
        if (message.getDeliveredAt() != null) return message.getDeliveredAt();
        if (message.getDeliveryUpdatedAt() != null) return message.getDeliveryUpdatedAt();
        if (message.getSentAt() != null) return message.getSentAt();
        return message.getUpdatedAt();
    }

    private static String channel(BookingIncidentRecipient recipient, OutboundMessage message) {
        if (message != null && message.getChannel() != null) return message.getChannel().name();
        return recipient.getChannelPreference().name();
    }

    private static boolean isProviderFailure(String value) {
        return "FAILED".equals(value) || "UNDELIVERED".equals(value) || "CANCELED".equals(value);
    }

    public record RecipientDeliveryStatus(
            UUID recipientId,
            UUID customerId,
            String customerName,
            String channel,
            String status,
            Instant statusAt,
            boolean retryable,
            int retryCount) { }
}
