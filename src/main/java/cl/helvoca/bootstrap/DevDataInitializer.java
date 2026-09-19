package cl.helvoca.bootstrap;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

@Component
public class DevDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final BusinessRepository businesses;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private BusinessSubscriptionService subscriptions;
    private ServiceItemRepository services;
    private CatalogItemRepository catalog;
    private BusinessHourRepository hours;

    @Value("${app.seed.enabled:false}") private boolean enabled;
    @Value("${app.seed.admin-email:admin@helvoca.local}") private String adminEmail;
    @Value("${app.seed.admin-password:ChangeMe123!}") private String adminPassword;

    public DevDataInitializer(BusinessRepository businesses, AppUserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.businesses = businesses; this.users = users; this.roles = roles; this.encoder = encoder;
    }

    @Autowired(required = false)
    void setSubscriptions(BusinessSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Autowired(required = false)
    void setDemoCatalogRepositories(ServiceItemRepository services, CatalogItemRepository catalog) {
        this.services = services;
        this.catalog = catalog;
    }

    @Autowired(required = false)
    void setDemoScheduleRepository(BusinessHourRepository hours) {
        this.hours = hours;
    }

    @Override @Transactional
    public void run(String... args) {
        if (!enabled) return;

        Optional<AppUser> existingAdmin = users.findByEmailIgnoreCase(adminEmail);
        if (existingAdmin.isPresent()) {
            Business existingBusiness = existingAdmin.get().getBusiness();
            if (existingBusiness != null) {
                if (subscriptions != null) subscriptions.startBasicTrial(existingBusiness.getId());
                ensureDemoCatalog(existingBusiness.getId());
                ensureDemoSchedule(existingBusiness.getId());
            }
            log.info("Sales demo tenant seed is ready with catalog and schedule");
            return;
        }

        Business business = new Business();
        business.setName("Helvoca Demo Business");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);
        if (subscriptions != null) {
            subscriptions.startBasicTrial(business.getId());
        }
        AppUser admin = new AppUser(); admin.setBusiness(business); admin.setName("Demo Administrator"); admin.setEmail(adminEmail.toLowerCase());
        admin.setPasswordHash(encoder.encode(adminPassword)); admin.getRoles().add(roles.findByCode(RoleCode.BUSINESS_ADMIN).orElseThrow()); users.saveAndFlush(admin);
        ensureDemoCatalog(business.getId());
        ensureDemoSchedule(business.getId());
        log.info("Sales demo tenant seed was created successfully with catalog and schedule");
    }

    private void ensureDemoCatalog(java.util.UUID businessId) {
        if (businessId == null || services == null || catalog == null) return;

        ensureService(businessId, "Consulta inicial",
                "Evaluación de necesidades y recomendación del siguiente paso.", 30, "19990");
        ensureService(businessId, "Servicio completo",
                "Atención principal de demostración con duración de una hora.", 60, "39990");
        ensureService(businessId, "Control de seguimiento",
                "Revisión breve posterior al servicio principal.", 30, "14990");

        ensureProduct(businessId, "Kit esencial",
                "Producto demo para mostrar consultas de catálogo y precio.", "15990");
        ensureProduct(businessId, "Kit premium",
                "Producto demo de mayor valor para cotización o pedido.", "29990");
    }

    private void ensureDemoSchedule(java.util.UUID businessId) {
        if (businessId == null || hours == null || hours.countByBusinessId(businessId) > 0) return;

        for (int day = 1; day <= 5; day++) {
            ensureHour(businessId, day, LocalTime.of(9, 0), LocalTime.of(18, 0));
        }
        ensureHour(businessId, 6, LocalTime.of(10, 0), LocalTime.of(14, 0));
    }

    private void ensureHour(java.util.UUID businessId, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(dayOfWeek);
        hour.setOpenTime(openTime);
        hour.setCloseTime(closeTime);
        hours.saveAndFlush(hour);
    }

    private void ensureService(java.util.UUID businessId, String name, String description, int durationMinutes, String price) {
        if (services.existsByBusinessIdAndNameIgnoreCase(businessId, name)) return;
        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName(name);
        item.setDescription(description);
        item.setDurationMinutes(durationMinutes);
        item.setPrice(new BigDecimal(price));
        item.setActive(true);
        services.saveAndFlush(item);
    }

    private void ensureProduct(java.util.UUID businessId, String name, String description, String price) {
        if (catalog.existsByBusinessIdAndKindAndNameIgnoreCase(businessId, CatalogItem.Kind.PRODUCT, name)) return;
        CatalogItem item = new CatalogItem();
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setDescription(description);
        item.setPrice(new BigDecimal(price));
        item.setCurrency("CLP");
        item.setActive(true);
        catalog.saveAndFlush(item);
    }
}
