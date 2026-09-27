package cl.helvoca.payment;

import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundDispatchOutboxService;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts a provider-verified successful payment into a durable WhatsApp
 * confirmation. Delivery remains behind the existing outbound safety switch.
 *
 * The payment webhook must never be rolled back merely because WhatsApp is not
 * configured, the customer has no single verified recipient, or delivery is
 * disabled. Those cases leave the payment authoritative and skip notification.
 */
@Service
public class PaymentSuccessNotificationService {
    public enum Result { PREPARED, QUEUED, SKIPPED }

    private final OutboundMessagingService outbound;
    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;
    private final OutboundDispatchOutboxService outbox;

    public PaymentSuccessNotificationService(OutboundMessagingService outbound,
                                             OutboundMessagingProperties properties,
                                             MessagingProviderRegistry providers,
                                             OutboundDispatchOutboxService outbox) {
        this.outbound = outbound;
        this.properties = properties;
        this.providers = providers;
        this.outbox = outbox;
    }

    @Transactional
    public Result onVerifiedSuccess(BusinessPayment payment) {
        if (payment == null
                || payment.getStatus() != BusinessPayment.Status.SUCCEEDED
                || payment.getBusinessId() == null
                || payment.getCustomerId() == null
                || payment.getOperationId() == null) {
            return Result.SKIPPED;
        }

        try {
            OutboundMessage message = outbound.prepare(
                    payment.getBusinessId(),
                    payment.getCustomerId(),
                    OutboundMessage.Channel.WHATSAPP,
                    OutboundMessage.Purpose.PAYMENT_CONFIRMATION,
                    payment.getOperationId(),
                    null);

            if (!properties.isDeliveryEnabled()) {
                return Result.PREPARED;
            }

            providers.require(properties.getProvider(), OutboundMessage.Channel.WHATSAPP);
            outbox.queue(payment.getBusinessId(), message.getId());
            return Result.QUEUED;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.SKIPPED;
        }
    }
}
