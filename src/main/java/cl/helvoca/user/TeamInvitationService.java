package cl.helvoca.user;

import cl.helvoca.audit.AuditService;
import cl.helvoca.auth.AuthService;
import cl.helvoca.auth.LoginRequest;
import cl.helvoca.auth.LoginResponse;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TeamInvitationService {
    private static final EnumSet<RoleCode> INVITABLE_ROLES =
            EnumSet.of(RoleCode.BUSINESS_ADMIN, RoleCode.OPERATOR);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EXPIRY_HOURS = 72;

    private final TeamInvitationRepository invitations;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public TeamInvitationService(TeamInvitationRepository invitations,
                                 AppUserRepository users,
                                 RoleRepository roles,
                                 BusinessRepository businesses,
                                 PasswordEncoder passwordEncoder,
                                 AuthService authService,
                                 TenantProvider tenantProvider,
                                 AuditService auditService) {
        this.invitations = invitations;
        this.users = users;
        this.roles = roles;
        this.businesses = businesses;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return invitations.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(item -> response(item, null))
                .toList();
    }

    @Transactional
    public TeamInvitationResponse create(InviteUserRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        String email = normalizeEmail(request.email());
        if (!INVITABLE_ROLES.contains(request.role())) {
            throw new IllegalArgumentException("Only BUSINESS_ADMIN or OPERATOR may be invited");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));

        Instant now = Instant.now();
        for (TeamInvitation current : invitations
                .findAllByBusinessIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
                        businessId, email)) {
            current.setRevokedAt(now);
        }

        String rawToken = rawToken();
        TeamInvitation invitation = new TeamInvitation();
        invitation.setBusiness(business);
        invitation.setName(request.name().trim());
        invitation.setEmail(email);
        invitation.setRoleCode(request.role());
        invitation.setTokenHash(hash(rawToken));
        invitation.setExpiresAt(now.plus(EXPIRY_HOURS, ChronoUnit.HOURS));
        invitation = invitations.saveAndFlush(invitation);

        auditService.humanSuccess(
                businessId,
                "TEAM_INVITATION_CREATE",
                "TEAM_INVITATION",
                invitation.getId());

        String path = "/invite.html?businessId=" + businessId + "&token=" + rawToken;
        return response(invitation, path);
    }

    @Transactional
    public void revoke(UUID invitationId) {
        UUID businessId = tenantProvider.requireBusinessId();
        TeamInvitation invitation = invitations.findByIdAndBusinessId(invitationId, businessId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        if (invitation.getAcceptedAt() != null) {
            throw new ConflictException("Accepted invitation cannot be revoked");
        }
        if (invitation.getRevokedAt() == null) {
            invitation.setRevokedAt(Instant.now());
            invitations.save(invitation);
            auditService.humanSuccess(
                    businessId,
                    "TEAM_INVITATION_REVOKE",
                    "TEAM_INVITATION",
                    invitation.getId());
        }
    }

    @Transactional(readOnly = true)
    public TeamInvitationResponse preview(UUID businessId, String rawToken) {
        TeamInvitation invitation = requireByToken(businessId, rawToken);
        return response(invitation, null);
    }

    @Transactional
    public LoginResponse accept(UUID businessId,
                                String rawToken,
                                AcceptInvitationRequest request) {
        TeamInvitation invitation = requireByToken(businessId, rawToken);
        ensureAcceptable(invitation);

        String email = normalizeEmail(invitation.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        Role role = roles.findByCode(invitation.getRoleCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Role not seeded: " + invitation.getRoleCode()));

        AppUser user = new AppUser();
        user.setBusiness(invitation.getBusiness());
        user.setName(invitation.getName());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(role);
        users.saveAndFlush(user);

        invitation.setAcceptedAt(Instant.now());
        invitations.saveAndFlush(invitation);
        auditService.success(businessId, "TEAM_INVITATION_ACCEPT", "USER", user.getId());

        return authService.login(new LoginRequest(email, request.password()));
    }

    private TeamInvitation requireByToken(UUID businessId, String rawToken) {
        if (businessId == null || rawToken == null || rawToken.isBlank()) {
            throw new NotFoundException("Invitation not found");
        }
        return invitations.findByBusinessIdAndTokenHash(businessId, hash(rawToken))
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
    }

    private static void ensureAcceptable(TeamInvitation invitation) {
        Instant now = Instant.now();
        if (invitation.getAcceptedAt() != null) {
            throw new ConflictException("Invitation has already been accepted");
        }
        if (invitation.getRevokedAt() != null) {
            throw new ConflictException("Invitation has been revoked");
        }
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new ConflictException("Invitation has expired");
        }
    }

    private static TeamInvitationResponse response(TeamInvitation invitation, String invitePath) {
        String status;
        Instant now = Instant.now();
        if (invitation.getAcceptedAt() != null) status = "ACCEPTED";
        else if (invitation.getRevokedAt() != null) status = "REVOKED";
        else if (!invitation.getExpiresAt().isAfter(now)) status = "EXPIRED";
        else status = "PENDING";

        Business business = invitation.getBusiness();
        return new TeamInvitationResponse(
                invitation.getId(),
                business.getId(),
                business.getName(),
                invitation.getName(),
                invitation.getEmail(),
                invitation.getRoleCode().name(),
                invitation.getExpiresAt(),
                status,
                invitePath);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String rawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash invitation token", e);
        }
    }
}
