package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.user.InviteUserRequest;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.TeamInvitationResponse;
import cl.helvoca.user.TeamInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformBusinessProvisioningServiceTest {

    @Test
    void createsTenantTrialAndInitialAdminInvitationWithoutPassword() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        TeamInvitationService invitations = mock(TeamInvitationService.class);
        AuditService audit = mock(AuditService.class);
        PlatformBusinessProvisioningService service =
                new PlatformBusinessProvisioningService(businesses, subscriptions, invitations, audit);

        UUID businessId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            ReflectionTestUtils.setField(business, "id", businessId);
            return business;
        });
        when(invitations.createForPlatform(eq(businessId), any(InviteUserRequest.class)))
                .thenReturn(new TeamInvitationResponse(
                        invitationId,
                        businessId,
                        "Clínica Norte",
                        "Ana Pérez",
                        "ana@clinica.cl",
                        "BUSINESS_ADMIN",
                        Instant.now().plusSeconds(3600),
                        "PENDING",
                        "/invite.html?businessId=" + businessId + "&token=one-time"));

        PlatformBusinessProvisioningResponse result = service.provision(
                new PlatformBusinessProvisioningRequest(
                        "Clínica Norte",
                        "America/Santiago",
                        "es",
                        "+56911112222",
                        "Ana Pérez",
                        "ANA@CLINICA.CL"));

        assertEquals(businessId, result.businessId());
        assertEquals("Clínica Norte", result.businessName());
        assertEquals("ana@clinica.cl", result.adminEmail());
        assertEquals("PENDING", result.invitationStatus());
        assertTrue(result.invitePath().startsWith("/invite.html?businessId=" + businessId));
        assertEquals("/", result.onboardingPath());

        verify(subscriptions).startBasicTrial(businessId);
        verify(invitations).createForPlatform(
                eq(businessId),
                argThat(request -> request.role() == RoleCode.BUSINESS_ADMIN
                        && "Ana Pérez".equals(request.name())
                        && "ana@clinica.cl".equals(request.email())));
        verify(audit).platformHumanSuccess(
                eq(businessId),
                eq("BUSINESS_PROVISION"),
                eq("BUSINESS"),
                eq(businessId),
                isNull(),
                argThat(after -> "Clínica Norte".equals(after.get("name"))
                        && "America/Santiago".equals(after.get("timezone"))
                        && "es".equals(after.get("language"))));
    }

    @Test
    void invalidTimezoneFailsBeforeCreatingTenant() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        TeamInvitationService invitations = mock(TeamInvitationService.class);
        AuditService audit = mock(AuditService.class);
        PlatformBusinessProvisioningService service =
                new PlatformBusinessProvisioningService(businesses, subscriptions, invitations, audit);

        assertThrows(IllegalArgumentException.class, () -> service.provision(
                new PlatformBusinessProvisioningRequest(
                        "Negocio",
                        "Mars/Olympus",
                        "es",
                        null,
                        "Admin",
                        "admin@example.com")));

        verifyNoInteractions(businesses, subscriptions, invitations, audit);
    }
}
