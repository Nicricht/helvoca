package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupAuthorizationCodeService {
    private final MetaWhatsAppEmbeddedSignupReadinessService readinessService;
    private final MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient;
    private final MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient;
    private final MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
            MetaWhatsAppEmbeddedSignupReadinessService readinessService,
            MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient,
            MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient,
            MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient,
            MetaWhatsAppProperties metaProperties) {
        this.readinessService = readinessService;
        this.tokenExchangeClient = tokenExchangeClient;
        this.tokenDebugClient = tokenDebugClient;
        this.sharedWabaClient = sharedWabaClient;
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse accept(
            MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest request) {
        if (!readinessService.readiness().readyForEmbeddedSignup()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_NOT_READY");
        }

        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Meta authorization code is required");
        }

        // The authorization code and resulting OAuth token only live in memory for this request.
        // Neither value is logged, returned to the browser, or persisted.
        MetaWhatsAppEmbeddedSignupToken token =
                tokenExchangeClient.exchange(request.code());

        MetaWhatsAppEmbeddedSignupTokenDebugResult debug =
                tokenDebugClient.debug(
                        token.accessToken(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        if (!debug.valid()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_TOKEN_INVALID");
        }

        if (debug.appId() == null
                || !metaProperties.getEmbeddedSignupAppId().equals(debug.appId())) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_TOKEN_APP_MISMATCH");
        }

        MetaWhatsAppEmbeddedSignupSharedWabaPage sharedWabas =
                sharedWabaClient.list(
                        metaProperties.getEmbeddedSignupBusinessId(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        return new MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
                "AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED",
                true,
                false,
                false,
                sharedWabas.wabas(),
                sharedWabas.afterCursor());
    }
}
