package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService {
    private final MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService assignmentService;
    private final MetaWhatsAppEmbeddedSignupSubscribeAppClient subscribeAppClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
            MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService assignmentService,
            MetaWhatsAppEmbeddedSignupSubscribeAppClient subscribeAppClient,
            MetaWhatsAppProperties metaProperties) {
        this.assignmentService = assignmentService;
        this.subscribeAppClient = subscribeAppClient;
        this.metaProperties = metaProperties;
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

        return new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                "APP_SUBSCRIBED",
                assignment.systemUserAssigned(),
                assignment.changed(),
                true);
    }
}
