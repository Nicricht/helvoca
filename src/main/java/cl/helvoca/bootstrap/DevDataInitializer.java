package cl.helvoca.bootstrap;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevDataInitializer implements CommandLineRunner {
    private final BusinessRepository businesses;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    @Value("${app.seed.enabled:false}") private boolean enabled;
    @Value("${app.seed.admin-email:admin@helvoca.local}") private String adminEmail;
    @Value("${app.seed.admin-password:ChangeMe123!}") private String adminPassword;

    public DevDataInitializer(BusinessRepository businesses, AppUserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.businesses = businesses; this.users = users; this.roles = roles; this.encoder = encoder;
    }

    @Override @Transactional
    public void run(String... args) {
        if (!enabled || users.existsByEmailIgnoreCase(adminEmail)) return;
        Business business = new Business(); business.setName("Helvoca Demo Business"); business = businesses.save(business);
        AppUser admin = new AppUser(); admin.setBusiness(business); admin.setName("Demo Administrator"); admin.setEmail(adminEmail.toLowerCase());
        admin.setPasswordHash(encoder.encode(adminPassword)); admin.getRoles().add(roles.findByCode(RoleCode.BUSINESS_ADMIN).orElseThrow()); users.save(admin);
    }
}
