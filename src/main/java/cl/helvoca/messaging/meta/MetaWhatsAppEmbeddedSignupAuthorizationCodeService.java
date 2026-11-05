package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupAuthorizationCodeService {
    private static final String REQUIRED_EMBEDDED_SIGNUP_SCOPE = "whatsapp_business_management";
    private static final int MAX_WABA_PAGES = 100;

    private final MetaWhatsAppEmbeddedSignupReadinessService readinessService;
    private final MetaWhatsAppEmbeddedSignupTokenExchangeClient tokenExchangeClient;
    private final MetaWhatsAppEmbeddedSignupTokenDebugClient tokenDebugClient;
    private final MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient;
    private final MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient;
    private final MetaWhatsAppEmbeddedSignupWabaAccessClient wabaAccessClient;
    private final MetaWhatsAppTenantWabaAuthorizationService wabaAuthorization;
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
            MetaWhatsAppEmbeddedSignupWabaAccessClient wabaAccessClient,
            MetaWhatsAppTenantWabaAuthorizationService wabaAuthorization,
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.readinessService = readinessService;
        this.tokenExchangeClient = tokenExchangeClient;
        this.tokenDebugClient = tokenDebugClient;
        this.sharedWabaClient = sharedWabaClient;
        this.assignedUsersClient = assignedUsersClient;
        this.wabaAccessClient = wabaAccessClient;
        this.wabaAuthorization = wabaAuthorization;
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
        this(readinessService, tokenExchangeClient, tokenDebugClient, sharedWabaClient,
                assignedUsersClient, null, null, metaProperties, null, null);
    }

    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse accept(
            MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest request) {
        if (!readinessService.readiness().readyForEmbeddedSignup()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_NOT_READY");
        }

        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Meta authorization code is required");
        }
        String wabaId = requireWabaId(request.wabaId());
        if (wabaAccessClient == null || wabaAuthorization == null) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_TENANT_AUTHORIZATION_UNAVAILABLE");
        }

        // The authorization code and resulting customer token only live in memory.
        // The token is used to prove access to this exact WABA, then discarded.
        MetaWhatsAppEmbeddedSignupToken token = tokenExchangeClient.exchange(request.code());
        MetaWhatsAppEmbeddedSignupTokenDebugResult debug = tokenDebugClient.debug(
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

        // This is the tenant boundary: the token from this customer's signup
        // must be able to read the exact WABA selected in the Meta session.
        wabaAccessClient.requireAccess(wabaId, token.accessToken());

        MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba selectedWaba =
                findSharedWaba(wabaId);
        if (selectedWaba == null) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED");
        }

        // Persist the proven WABA against the authenticated tenant before any
        // later assignment, subscription, phone discovery, or staging operation.
        wabaAuthorization.authorize(wabaId);

        MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse.WabaCandidate candidate =
                toCandidate(selectedWaba);
        List<MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse.WabaCandidate> candidates =
                List.of(candidate);

        MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse response =
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse(
                        "AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED",
                        true,
                        false,
                        false,
                        candidates,
                        null);

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

    private MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba findSharedWaba(String selectedWabaId) {
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < MAX_WABA_PAGES; pageNumber++) {
            MetaWhatsAppEmbeddedSignupSharedWabaPage page = cursor == null
                    ? sharedWabaClient.list(
                            metaProperties.getEmbeddedSignupBusinessId(),
                            metaProperties.getEmbeddedSignupSystemUserAccessToken())
                    : sharedWabaClient.list(
                            metaProperties.getEmbeddedSignupBusinessId(),
                            metaProperties.getEmbeddedSignupSystemUserAccessToken(),
                            cursor);

            for (MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba waba : page.wabas()) {
                if (selectedWabaId.equals(waba.id())) return waba;
            }

            String nextCursor = clean(page.afterCursor());
            if (nextCursor == null) return null;
            if (!seenCursors.add(nextCursor)) {
                throw new IllegalStateException(
                        "Meta Embedded Signup shared WABA pagination repeated a cursor");
            }
            cursor = nextCursor;
        }
        throw new IllegalStateException(
                "Meta Embedded Signup shared WABA pagination exceeded safe limit");
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

    private static String requireWabaId(String value) {
        String clean = clean(value);
        if (clean == null || !clean.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("Meta WABA id is required and must be numeric");
        }
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}
