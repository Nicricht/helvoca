package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupToken(
        String accessToken,
        String tokenType,
        Long expiresInSeconds
) {
    public MetaWhatsAppEmbeddedSignupToken {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Meta access token is required");
        }
    }

    @Override
    public String toString() {
        return "MetaWhatsAppEmbeddedSignupToken[accessToken=REDACTED, tokenType="
                + tokenType + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
