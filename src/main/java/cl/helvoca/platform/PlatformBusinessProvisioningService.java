package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.user.InviteUserRequest;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.TeamInvitationResponse;
import cl.helvoca.user.TeamInvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformBusinessProvisioningService {
    private final BusinessRepository businesses;
    private final BusinessSubscriptionService subscriptions;
    private final TeamInvitationService invitations;
    private final AuditService audit;

    public PlatformBusinessProvisioningService(BusinessRepository businesses,
                                               BusinessSubscriptionService subscriptions,
                                               TeamInvitationService invitations,
                                               AuditService audit) {
        this.businesses = businesses;
        this.subscriptions = subscriptions;
        this.invitations = invitations;
        this.audit = audit;
    }

    @Transactional
    public PlatformBusinessProvisioningResponse provision(PlatformBusinessProvisioningRequest request) {
        validateTimezone(request.timezone());

        String businessName = request.businessName().trim();
        String timezone = request.timezone().trim();
        String language = request.language().trim().toLowerCase(Locale.ROOT);
        String humanTransferPhone = blankToNull(request.humanTransferPhone());
        String adminName = request.adminName().trim();
        String adminEmail = request.adminEmail().trim().toLowerCase(Locale.ROOT);

        Business business = new Business();
        business.setName(businessName);
        business.setTimezone(timezone);
        business.setLanguage(language);
        business.setHumanTransferPhone(humanTransferPhone);
        business = businesses.saveAndFlush(business);

        subscriptions.startBasicTrial(business.getId());

        TeamInvitationResponse invitation = invitations.createForPlatform(
                business.getId(),
                new InviteUserRequest(adminName, adminEmail, RoleCode.BUSINESS_ADMIN));

        audit.platformHumanSuccess(
                business.getId(),
                "BUSINESS_PROVISION",
                "BUSINESS",
                business.getId(),
                null,
                Map.of(
                        "name", businessName,
                        "timezone", timezone,
                        "language", language,
                        "adminEmail", adminEmail));

        return new PlatformBusinessProvisioningResponse(
                business.getId(),
                business.getName(),
                business.getTimezone(),
                business.getLanguage(),
                adminName,
                adminEmail,
                invitation.id(),
                invitation.status(),
                invitation.expiresAt(),
                invitation.invitePath(),
                "/");
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid business timezone");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
