package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupAuthorizationCodeService {
    private static final String REQUIRED_EMBEDDED_SIGNUP_SCOPE = "whatsapp_business_management";
    private final MetaWhatsAppEmbeddedSignupReadinessService readinessService;
    private final MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient;
    private final MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient;
    private final MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient;
    private final MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient;
    private final MetaWhatsAppProperties metaProperties;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
            MetaWhatsAppEmbeddedSignupReadinessService readinessService,
            MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient,
            MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient,
            MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient,
            MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient,
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.readinessService = readinessService;
        this.tokenExchangeClient = tokenExchangeClient;
        this.tokenDebugClient = tokenDebugClient;
        this.sharedWabaClient = sharedWabaClient;
        this.assignedUsersClient = assignedUsersClient;
        this.metaProperties = metaProperties;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
            MetaWhatsAppEmbeddedSignupReadinessService readinessService,
            MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient,
            MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient,
            MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient,
            MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient,
            MetaWhatsAppProperties metaProperties) {
        this(
                readinessService,
                tokenExchangeClient,
                tokenDebugClient,
                sharedWabaClient,
                assignedUsersClient,
                metaProperties,
                null,
                null);
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

        if (!debug.scopes().contains(REQUIRED_EMBEDDED_SIGNUP_SCOPE)) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_REQUIRED_SCOPE_MISSING");
        }

        MetaWhatsAppEmbeddedSignupSharedWabaPage sharedWabas =
                sharedWabaClient.list(
                        metaProperties.getEmbeddedSignupBusinessId(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        List<MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse.WabaCandidate> candidates =
                sharedWabas.wabas().stream()
                        .map(this::toCandidate)
                        .toList();

        MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse response =
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
                        "AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED",
                        true,
                        false,
                        false,
                        candidates,
                        sharedWabas.afterCursor());

        if (auditService != null && tenantProvider != null) {
            UUID businessId = tenantProvider.requireBusinessId();
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_AUTHORIZATION_ACCEPTED",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    null,
                    Map.of(
                            "accepted", true,
                            "wabaCandidates", candidates.size()));
        }

        return response;
    }

    private MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse.WabaCandidate toCandidate(
            MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba waba) {
        MetaWhatsAppEmbeddedSignupAssignedUsersResult assignedUsers =
                assignedUsersClient.fetch(
                        waba.id(),
                        metaProperties.getEmbeddedSignupBusinessId(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        boolean systemUserAssigned = assignedUsers.users().stream()
                .anyMatch(user -> metaProperties.getEmbeddedSignupSystemUserId().equals(user.id()));

        return new MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse.WabaCandidate(
                waba.id(),
                waba.name(),
                waba.currency(),
                waba.timezoneId(),
                waba.messageTemplateNamespace(),
                systemUserAssigned);
    }
}
