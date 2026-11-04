package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupBootstrapResponse(
        boolean enabled,
        boolean available,
        String appId,
        String configId,
        String graphApiVersion,
        boolean coexistenceAvailable
) {
    // Preserve existing tests and clients that construct the five-field response.
    public MetaWhatsAppEmbeddedSignupBootstrapResponse(
            boolean enabled, boolean available, String appId, String configId,
            String graphApiVersion) {
        this(enabled, available, appId, configId, graphApiVersion, false);
    }
}
