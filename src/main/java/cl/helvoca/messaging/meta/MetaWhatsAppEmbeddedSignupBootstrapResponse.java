package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupBootstrapResponse(
        boolean enabled,
        boolean available,
        String appId,
        String configId,
        String graphApiVersion
) {
}
