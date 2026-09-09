package cl.helvoca.user;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
public class UserAdminService {
    private static final EnumSet<RoleCode> BUSINESS_ROLES = EnumSet.of(RoleCode.BUSINESS_ADMIN, RoleCode.OPERATOR);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public UserAdminService(AppUserRepository users, RoleRepository roles, BusinessRepository businesses,
                            PasswordEncoder passwordEncoder, TenantProvider tenantProvider, AuditService auditService) {
        this.users = users;
        this.roles = roles;
        this.businesses = businesses;
        this.passwordEncoder = passwordEncoder;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        var businessId = tenantProvider.requireBusinessId();
        return users.findAllByBusinessIdOrderByName(businessId).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        var businessId = tenantProvider.requireBusinessId();
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (!BUSINESS_ROLES.containsAll(request.roles())) {
            throw new IllegalArgumentException("Business administrators may only assign BUSINESS_ADMIN or OPERATOR");
        }

        var business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));

        AppUser user = new AppUser();
        user.setBusiness(business);
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        request.roles().forEach(code -> user.getRoles().add(roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + code))));
        users.save(user);
        auditService.success(businessId, "USER_CREATE", "USER", user.getId());
        return UserResponse.from(user);
    }
}
