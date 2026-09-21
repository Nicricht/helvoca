package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService {
    private final MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService assignmentService;
    private final MetaWhatsAppEmbeddedSignupSubscribeAppClient subscribeAppClient;
    private final MetaWhatsAppProperties metaProperties;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
            MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService assignmentService,
            MetaWhatsAppEmbeddedSignupSubscribeAppClient subscribeAppClient,
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.assignmentService = assignmentService;
        this.subscribeAppClient = subscribeAppClient;
        this.metaProperties = metaProperties;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
            MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService assignmentService,
            MetaWhatsAppEmbeddedSignupSubscribeAppClient subscribeAppClient,
            MetaWhatsAppProperties metaProperties) {
        this(assignmentService, subscribeAppClient, metaProperties, null, null);
    }

    public MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult ensureSubscribed(
            String wabaId) {
        MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult assignment =
                assignmentService.ensureAssigned(wabaId);

        if (!assignment.systemUserAssigned()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_SYSTEM_USER_NOT_ASSIGNED");
        }

        MetaWhatsAppEmbeddedSignupSubscribeAppResult subscription =
                subscribeAppClient.subscribe(
                        wabaId,
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        if (!subscription.success()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_APP_SUBSCRIPTION_FAILED");
        }

        if (auditService != null && tenantProvider != null) {
            UUID businessId = tenantProvider.requireBusinessId();
            auditService.humanSuccess(
                    businessId,
                    "META_WHATSAPP_APP_SUBSCRIBED",
                    "META_WHATSAPP_CONFIG",
                    businessId,
                    null,
                    Map.of(
                            "appSubscribed", true,
                            "assignmentChanged", assignment.changed()));
        }

        return new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                "APP_SUBSCRIBED",
                assignment.systemUserAssigned(),
                assignment.changed(),
                true);
    }
}
