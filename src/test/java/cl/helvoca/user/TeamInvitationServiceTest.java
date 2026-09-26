package cl.helvoca.user;

import cl.helvoca.audit.AuditService;
import cl.helvoca.auth.AuthService;
import cl.helvoca.auth.LoginRequest;
import cl.helvoca.auth.LoginResponse;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceTest {
    @Mock TeamInvitationRepository invitations;
    @Mock AppUserRepository users;
    @Mock RoleRepository roles;
    @Mock BusinessRepository businesses;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthService authService;
    @Mock TenantProvider tenantProvider;
    @Mock AuditService auditService;

    private TeamInvitationService service;
    private UUID businessId;
    private Business business;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        business = mock(Business.class);
        lenient().when(business.getId()).thenReturn(businessId);
        lenient().when(business.getName()).thenReturn("Negocio Piloto");
        lenient().when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        service = new TeamInvitationService(
                invitations, users, roles, businesses, passwordEncoder,
                authService, tenantProvider, auditService);
    }

    @Test
    void createStoresOnlyTokenHashAndReturnsOneTimeLink() {
        when(users.existsByEmailIgnoreCase("operador@negocio.cl")).thenReturn(false);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(invitations.findAllByBusinessIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
                businessId, "operador@negocio.cl")).thenReturn(List.of());
        when(invitations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        TeamInvitationResponse result = service.create(new InviteUserRequest(
                "Operador Uno", "Operador@Negocio.cl", RoleCode.OPERATOR));

        ArgumentCaptor<TeamInvitation> captor = ArgumentCaptor.forClass(TeamInvitation.class);
        verify(invitations).saveAndFlush(captor.capture());
        TeamInvitation saved = captor.getValue();

        assertNotNull(result.invitePath());
        assertTrue(result.invitePath().startsWith("/invite.html?businessId=" + businessId + "&token="));
        String rawToken = result.invitePath().substring(result.invitePath().indexOf("&token=") + 7);
        assertTrue(rawToken.length() >= 40);
        assertEquals(64, saved.getTokenHash().length());
        assertEquals(TeamInvitationService.hash(rawToken), saved.getTokenHash());
        assertNotEquals(rawToken, saved.getTokenHash());
        assertEquals("operador@negocio.cl", saved.getEmail());
        assertEquals(RoleCode.OPERATOR, saved.getRoleCode());
        assertTrue(saved.getExpiresAt().isAfter(before.plusSeconds(71 * 3600)));
        assertEquals("PENDING", result.status());
    }

    @Test
    void platformProvisioningCreatesInitialBusinessAdminInvitationWithoutTenantJwt() {
        when(users.existsByEmailIgnoreCase("admin@negocio.cl")).thenReturn(false);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(invitations.findAllByBusinessIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
                businessId, "admin@negocio.cl")).thenReturn(List.of());
        when(invitations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInvitationResponse result = service.createForPlatform(
                businessId,
                new InviteUserRequest("Admin Inicial", "ADMIN@NEGOCIO.CL", RoleCode.BUSINESS_ADMIN));

        assertEquals(businessId, result.businessId());
        assertEquals("BUSINESS_ADMIN", result.role());
        assertEquals("admin@negocio.cl", result.email());
        assertNotNull(result.invitePath());
        verify(tenantProvider, never()).requireBusinessId();
        verify(auditService).platformHumanSuccess(
                eq(businessId),
                eq("TEAM_INVITATION_CREATE"),
                eq("TEAM_INVITATION"),
                any(),
                isNull(),
                argThat(after -> "admin@negocio.cl".equals(after.get("email"))
                        && "BUSINESS_ADMIN".equals(after.get("role"))));
        verify(auditService, never()).humanSuccess(
                any(), anyString(), anyString(), any());
    }

    @Test
    void platformAdminCannotBeInvitedIntoBusiness() {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new InviteUserRequest("Root", "root@example.cl", RoleCode.PLATFORM_ADMIN)));
        verifyNoInteractions(invitations, businesses, passwordEncoder, authService);
    }

    @Test
    void acceptCreatesUserWithOwnPasswordAndConsumesInvitation() {
        String rawToken = "very-secret-invitation-token";
        TeamInvitation invitation = invitation(rawToken, RoleCode.OPERATOR, Instant.now().plusSeconds(3600));
        Role operator = mock(Role.class);

        when(invitations.findByBusinessIdAndTokenHash(
                businessId, TeamInvitationService.hash(rawToken))).thenReturn(Optional.of(invitation));
        when(users.existsByEmailIgnoreCase("operador@negocio.cl")).thenReturn(false);
        when(roles.findByCode(RoleCode.OPERATOR)).thenReturn(Optional.of(operator));
        when(passwordEncoder.encode("UnaClaveMuySegura123")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LoginResponse login = new LoginResponse(
                "jwt-token", "Bearer", 3600,
                new LoginResponse.UserInfo(UUID.randomUUID(), businessId, "Operador Uno",
                        "operador@negocio.cl", List.of("OPERATOR")));
        when(authService.login(new LoginRequest("operador@negocio.cl", "UnaClaveMuySegura123")))
                .thenReturn(login);

        LoginResponse result = service.accept(
                businessId, rawToken, new AcceptInvitationRequest("UnaClaveMuySegura123"));

        assertEquals("jwt-token", result.accessToken());
        assertNotNull(invitation.getAcceptedAt());

        ArgumentCaptor<AppUser> user = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(user.capture());
        assertSame(business, user.getValue().getBusiness());
        assertEquals("Operador Uno", user.getValue().getName());
        assertEquals("operador@negocio.cl", user.getValue().getEmail());
        assertEquals("bcrypt-hash", user.getValue().getPasswordHash());
        assertTrue(user.getValue().getRoles().contains(operator));
    }

    @Test
    void expiredInvitationCannotBeAccepted() {
        String rawToken = "expired-token";
        TeamInvitation invitation = invitation(rawToken, RoleCode.OPERATOR, Instant.now().minusSeconds(1));
        when(invitations.findByBusinessIdAndTokenHash(
                businessId, TeamInvitationService.hash(rawToken))).thenReturn(Optional.of(invitation));

        assertThrows(ConflictException.class, () -> service.accept(
                businessId, rawToken, new AcceptInvitationRequest("UnaClaveMuySegura123")));

        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder, authService);
    }

    private TeamInvitation invitation(String rawToken, RoleCode role, Instant expiresAt) {
        TeamInvitation invitation = new TeamInvitation();
        invitation.setBusiness(business);
        invitation.setName("Operador Uno");
        invitation.setEmail("operador@negocio.cl");
        invitation.setRoleCode(role);
        invitation.setTokenHash(TeamInvitationService.hash(rawToken));
        invitation.setExpiresAt(expiresAt);
        return invitation;
    }
}
