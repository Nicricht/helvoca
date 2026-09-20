package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupAuthorizationCodeService {
    private final MetaWhatsAppEmbeddedSignupReadinessService readinessService;

    public MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
            MetaWhatsAppEmbeddedSignupReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse accept(
            MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest request) {
        if (!readinessService.readiness().readyForEmbeddedSignup()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_NOT_READY");
        }

        // The authorization code is intentionally not logged or persisted here.
        // A later isolated step will exchange it server-side with Meta.
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Meta authorization code is required");
        }

        return new MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
                "AUTHORIZATION_CODE_HANDOFF_VALIDATED",
                true,
                false,
                true);
    }
}
