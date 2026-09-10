package cl.helvoca.auth;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Locale;

@Service
public class RegistrationService {
    private final BusinessRepository businesses;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public RegistrationService(BusinessRepository businesses,
                               AppUserRepository users,
                               RoleRepository roles,
                               PasswordEncoder passwordEncoder,
                               AuthService authService) {
        this.businesses = businesses;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public LoginResponse register(RegisterBusinessRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }
        validateTimezone(request.timezone());

        Business business = new Business();
        business.setName(request.businessName().trim());
        business.setTimezone(request.timezone().trim());
        business.setLanguage(request.language().trim().toLowerCase(Locale.ROOT));
        business.setHumanTransferPhone(normalizePhone(request.humanTransferPhone()));
        business = businesses.saveAndFlush(business);

        var businessAdmin = roles.findByCode(RoleCode.BUSINESS_ADMIN)
                .orElseThrow(() -> new IllegalStateException("BUSINESS_ADMIN role is not configured"));

        AppUser user = new AppUser();
        user.setBusiness(business);
        user.setName(request.adminName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(businessAdmin);
        users.saveAndFlush(user);

        return authService.login(new LoginRequest(email, request.password()));
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid business timezone");
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return phone.trim();
    }
}
