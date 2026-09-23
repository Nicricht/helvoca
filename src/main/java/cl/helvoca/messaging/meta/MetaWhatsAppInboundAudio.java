package cl.helvoca.messaging.meta;

public record MetaWhatsAppInboundAudio(
        String messageId,
        String phoneNumberId,
        String from,
        String mediaId,
        String mimeType) {
}
