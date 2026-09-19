package cl.helvoca.messaging.meta;

public record MetaWhatsAppInboundMessage(
        String messageId,
        String phoneNumberId,
        String from,
        String text) {
}
