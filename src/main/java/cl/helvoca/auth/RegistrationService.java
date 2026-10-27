package cl.helvoca.auth;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private BusinessSubscriptionService subscriptions;
    private BusinessAccountVerificationService verification;

    public record RegistrationResult(LoginResponse session, boolean verificationPending) {}

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

    @Autowired(required = false)
    void setSubscriptions(BusinessSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Autowired(required = false)
    void setVerification(BusinessAccountVerificationService verification) {
        this.verification = verification;
    }

    @Transactional
    public RegistrationResult register(RegisterBusinessRequest request) {
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
        if (subscriptions != null) {
            subscriptions.startBasicTrial(business.getId());
        }

        var businessOwner = roles.findByCode(RoleCode.BUSINESS_OWNER)
                .orElseThrow(() -> new IllegalStateException("BUSINESS_OWNER role is not configured"));
        var businessAdmin = roles.findByCode(RoleCode.BUSINESS_ADMIN)
                .orElseThrow(() -> new IllegalStateException("BUSINESS_ADMIN role is not configured"));

        AppUser user = new AppUser();
        user.setBusiness(business);
        user.setName(request.adminName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        boolean pending = verification != null && verification.isRequired();
        user.getRoles().add(businessOwner);
        user.getRoles().add(businessAdmin);
        users.saveAndFlush(user);

        if (pending) {
            verification.scheduleRegistrationMessage(business.getId(), user.getId(), email);
            return new RegistrationResult(null, true);
        }
        if (verification != null) verification.markTrustedPossession(business.getId(), user.getId());
        return new RegistrationResult(authService.login(new LoginRequest(email, request.password())), false);
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
