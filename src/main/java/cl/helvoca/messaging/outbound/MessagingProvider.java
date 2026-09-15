package cl.helvoca.messaging.outbound;

import java.util.UUID;

/** Provider boundary. No provider is allowed to invent recipients or content. */
public interface MessagingProvider {
    String id();
    boolean supports(OutboundMessage.Channel channel);
    SendResult send(SendCommand command);

    record SendCommand(UUID businessId,
                       UUID messageId,
                       OutboundMessage.Channel channel,
                       String recipient,
                       String content,
                       String idempotencyKey) { }

    record SendResult(String providerMessageId) { }
}
