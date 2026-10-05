package cl.helvoca.bootstrap;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.inventory.InventoryStock;
import cl.helvoca.inventory.InventoryStockRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class DevDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
    private static final String DEMO_BUSINESS_NAME = "Barbería Norte Demo";
    private static final String LEGACY_DEMO_BUSINESS_NAME = "Helvoca Demo Business";
    private static final String DEMO_TIMEZONE = "America/Santiago";
    private static final String DEMO_BOOKING_PREFIX = "DEMO_FIXTURE:";

    private final BusinessRepository businesses;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private BusinessSubscriptionService subscriptions;
    private ServiceItemRepository services;
    private CatalogItemRepository catalog;
    private BusinessHourRepository hours;
    private KnowledgeItemRepository knowledge;
    private AiAgentRepository agents;
    private BusinessProfileRepository profiles;
    private CustomerRepository customers;
    private BookingRepository bookings;
    private InventoryStockRepository inventoryStocks;
    private DeliveryZoneRepository deliveryZones;
    private TenantDatabaseContext databaseContext;
    private PlatformTransactionManager transactionManager;

    @Value("${app.seed.enabled:false}") private boolean enabled;
    @Value("${app.seed.admin-email:admin@helvoca.local}") private String adminEmail;
    @Value("${app.seed.admin-password:ChangeMe123!}") private String adminPassword;
    @Value("${app.seed.preset:barbershop}") private String seedPreset;

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

    @Autowired(required = false)
    void setDemoKnowledgeRepository(KnowledgeItemRepository knowledge) {
        this.knowledge = knowledge;
    }

    @Autowired(required = false)
    void setDemoAgentRepository(AiAgentRepository agents) {
        this.agents = agents;
    }

    @Autowired(required = false)
    void setDemoBusinessProfileRepository(BusinessProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Autowired(required = false)
    void setDemoOperationalRepositories(CustomerRepository customers, BookingRepository bookings) {
        this.customers = customers;
        this.bookings = bookings;
    }

    @Autowired(required = false)
    void setHardwareStoreRepositories(InventoryStockRepository inventoryStocks,
                                      DeliveryZoneRepository deliveryZones) {
        this.inventoryStocks = inventoryStocks;
        this.deliveryZones = deliveryZones;
    }

    @Autowired
    void setDatabaseExecution(TenantDatabaseContext databaseContext,
                              PlatformTransactionManager transactionManager) {
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;

        if (databaseContext == null || transactionManager == null) {
            seedDemoTenant();
            return;
        }

        databaseContext.runAsSystem(() -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> seedDemoTenant());
        });
    }

    private void seedDemoTenant() {
        Optional<AppUser> existingAdmin = users.findByEmailIgnoreCase(adminEmail);
        if (existingAdmin.isPresent()) {
            Business existingBusiness = existingAdmin.get().getBusiness();
            if (existingBusiness != null) {
                boolean legacyDemoFixture = LEGACY_DEMO_BUSINESS_NAME.equals(existingBusiness.getName());
                ensureDemoBusinessBasics(existingBusiness, legacyDemoFixture);
                if (subscriptions != null) subscriptions.startBasicTrial(existingBusiness.getId());
                ensureDemoTenant(existingBusiness.getId(), legacyDemoFixture);
            }
            log.info("Commercial demo tenant seed is ready without real phone, WhatsApp or payment providers");
            return;
        }

        Business business = new Business();
        business.setName(demoBusinessName());
        business.setTimezone(DEMO_TIMEZONE);
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);
        if (subscriptions != null) {
            subscriptions.startBasicTrial(business.getId());
        }

        AppUser admin = new AppUser();
        admin.setBusiness(business);
        admin.setName(isHardwareStoreDemo() ? "Administración Ferretería Demo" : "Administración Demo");
        admin.setEmail(adminEmail.toLowerCase());
        admin.setPasswordHash(encoder.encode(adminPassword));
        admin.getRoles().add(roles.findByCode(RoleCode.BUSINESS_ADMIN).orElseThrow());
        users.saveAndFlush(admin);

        ensureDemoTenant(business.getId(), false);
        log.info("Commercial demo tenant was created and validated without activating external providers");
    }

    private void ensureDemoBusinessBasics(Business business, boolean legacyDemoFixture) {
        if (!legacyDemoFixture && !isHardwareStoreDemo()) return;
        business.setName(demoBusinessName());
        business.setTimezone(DEMO_TIMEZONE);
        business.setLanguage("es");
        business.setHumanTransferPhone(null);
        businesses.saveAndFlush(business);
    }

    private void ensureDemoTenant(UUID businessId, boolean legacyDemoFixture) {
        if (isHardwareStoreDemo()) {
            ensureHardwareStoreTenant(businessId);
            return;
        }
        ensureDemoProfile(businessId);
        ensureDemoCatalog(businessId, legacyDemoFixture);
        ensureDemoSchedule(businessId);
        ensureDemoKnowledge(businessId);
        ensureDemoAgent(businessId);
        ensureDemoCustomers(businessId);
        ensureDemoBookings(businessId);
        validateDemoReadiness(businessId);
    }

    private boolean isHardwareStoreDemo() {
        return HardwareStoreDemoData.PRESET.equalsIgnoreCase(seedPreset == null ? "" : seedPreset.trim());
    }

    private String demoBusinessName() {
        return isHardwareStoreDemo() ? HardwareStoreDemoData.BUSINESS_NAME : DEMO_BUSINESS_NAME;
    }

    private void ensureHardwareStoreTenant(UUID businessId) {
        ensureHardwareStoreProfile(businessId);
        ensureHardwareStoreCatalogAndInventory(businessId);
        ensureHardwareStoreSchedule(businessId);
        ensureHardwareStoreKnowledge(businessId);
        ensureHardwareStoreAgent(businessId);
        ensureHardwareStoreDeliveryZones(businessId);
        validateHardwareStoreReadiness(businessId);
    }

    private void ensureDemoProfile(UUID businessId) {
        if (businessId == null || profiles == null || profiles.findById(businessId).isPresent()) return;

        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(businessId);
        profile.setPresetKey("barbershop");
        profile.setPublicDescription("Barbería ficticia para demos comerciales de RecepVoz. Atiende cortes, barba y tratamientos con reserva.");
        profile.setPublicEmail("hola@barberia-norte-demo.invalid");
        profile.setWebsiteUrl("https://barberia-norte-demo.invalid");
        profile.setAddressLine("Pasaje Demo 123");
        profile.setCommune("Providencia");
        profile.setCity("Santiago");
        profile.setRegion("Región Metropolitana");
        profile.setCountryCode("CL");
        profile.setDefaultCurrency("CLP");
        profile.setSellsProducts(true);
        profile.setSellsServices(true);
        profile.setUsesReservations(true);
        profiles.saveAndFlush(profile);
    }

    private void ensureDemoCatalog(UUID businessId, boolean legacyDemoFixture) {
        if (businessId == null || services == null || catalog == null) return;

        if (legacyDemoFixture) deactivateLegacyDemoServices(businessId);
        ensureService(businessId, "Corte clásico",
                "Corte tradicional con asesoría breve de estilo.", 45, "15990");
        ensureService(businessId, "Corte + barba",
                "Corte de cabello y perfilado completo de barba.", 60, "22990");
        ensureService(businessId, "Perfilado de barba",
                "Perfilado, rebaje y terminaciones de barba.", 30, "11990");
        ensureService(businessId, "Fade premium",
                "Degradado detallado con terminaciones y peinado.", 60, "19990");
        ensureService(businessId, "Corte infantil",
                "Corte para niños con atención de ritmo tranquilo.", 40, "13990");
        ensureService(businessId, "Tratamiento capilar",
                "Lavado y tratamiento capilar de demostración.", 30, "12990");

        ensureProduct(businessId, "Cera mate demo",
                "Producto ficticio de styling para mostrar consultas de catálogo.", "9990");
        ensureProduct(businessId, "Aceite para barba demo",
                "Producto ficticio de cuidado de barba para la demostración.", "11990");
    }

    private void deactivateLegacyDemoServices(UUID businessId) {
        List<String> legacyNames = List.of("Consulta inicial", "Servicio completo", "Control de seguimiento");
        for (ServiceItem item : services.findAllByBusinessIdOrderByNameAsc(businessId)) {
            if (item.isActive() && legacyNames.stream().anyMatch(name -> name.equalsIgnoreCase(item.getName()))) {
                item.setActive(false);
                services.saveAndFlush(item);
            }
        }
    }

    private void ensureDemoAgent(UUID businessId) {
        if (businessId == null || agents == null || agents.existsByBusinessId(businessId)) return;

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("RecepVoz Demo");
        agent.setLanguage("es");
        agent.setVoice(null);
        agent.setGreeting("Hola, te comunicaste con Barbería Norte Demo. ¿En qué te puedo ayudar?");
        agent.setInstructions("Usa solo datos configurados del negocio. Este es un tenant ficticio de demostración. Responde de forma breve y natural. Antes de crear o cambiar una reserva, valida disponibilidad y confirma la intención del cliente. No proceses pagos reales, no envíes mensajes reales y no afirmes que existe una integración externa activa.");
        agent.setActive(true);
        agent.setCapabilities(EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.LIST_SERVICES,
                AiCapability.SEARCH_KNOWLEDGE,
                AiCapability.FIND_CALLER,
                AiCapability.REGISTER_CALLER,
                AiCapability.LIST_AVAILABLE_SLOTS,
                AiCapability.CHECK_BOOKING_AVAILABILITY,
                AiCapability.CREATE_BOOKING,
                AiCapability.LIST_CUSTOMER_BOOKINGS,
                AiCapability.RESCHEDULE_BOOKING,
                AiCapability.CANCEL_BOOKING,
                AiCapability.RECORD_UNANSWERED_QUESTION,
                AiCapability.LIST_CATALOG
        ));
        agents.saveAndFlush(agent);
    }

    private void ensureDemoKnowledge(UUID businessId) {
        if (businessId == null || knowledge == null) return;

        ensureKnowledge(businessId, "Reservas y confirmación", "Reservas",
                "Las horas se reservan solo después de validar disponibilidad y confirmar servicio, fecha y hora con el cliente.");
        ensureKnowledge(businessId, "Cambios y cancelaciones", "Políticas",
                "Política ficticia de demo: una reserva puede cancelarse o reprogramarse sin costo hasta 4 horas antes. Después de ese plazo se debe indicar que el caso requiere revisión.");
        ensureKnowledge(businessId, "Información no disponible", "Atención",
                "Si una respuesta no está respaldada por la configuración o la base de conocimiento, RecepVoz no debe inventarla y debe indicar que la información requiere revisión.");
        ensureKnowledge(businessId, "Pagos en la demostración", "Pagos",
                "Este tenant de demostración no procesa cobros reales. Puede informar precios configurados, pero nunca afirmar que un pago fue realizado.");
        ensureKnowledge(businessId, "Ubicación y llegada", "Información",
                "Barbería Norte Demo usa una ubicación completamente ficticia: Pasaje Demo 123, Providencia, Santiago. No corresponde a un local real.");
        ensureKnowledge(businessId, "Atrasos", "Políticas",
                "Política ficticia de demo: si el cliente anticipa un atraso mayor a 10 minutos, se debe recomendar reprogramar según disponibilidad.");
    }

    private void ensureKnowledge(UUID businessId, String title, String category, String content) {
        Optional<KnowledgeItem> existing = knowledge.findAllByBusinessIdOrderByTitleAsc(businessId).stream()
                .filter(item -> title.equalsIgnoreCase(item.getTitle()))
                .findFirst();
        if (existing.isPresent()) {
            KnowledgeItem item = existing.get();
            boolean changed = !item.isActive()
                    || !java.util.Objects.equals(category, item.getCategory())
                    || !java.util.Objects.equals(content, item.getContent());
            if (changed) {
                item.setCategory(category);
                item.setContent(content);
                item.setActive(true);
                knowledge.saveAndFlush(item);
            }
            return;
        }
        if (knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, title)) return;

        KnowledgeItem item = new KnowledgeItem();
        item.setBusinessId(businessId);
        item.setTitle(title);
        item.setCategory(category);
        item.setContent(content);
        item.setActive(true);
        knowledge.saveAndFlush(item);
    }

    private void ensureDemoSchedule(UUID businessId) {
        if (businessId == null || hours == null || hours.countByBusinessId(businessId) > 0) return;

        for (int day = 1; day <= 5; day++) {
            ensureHour(businessId, day, LocalTime.of(9, 0), LocalTime.of(19, 0));
        }
        ensureHour(businessId, 6, LocalTime.of(10, 0), LocalTime.of(15, 0));
    }

    private void ensureHour(UUID businessId, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(dayOfWeek);
        hour.setOpenTime(openTime);
        hour.setCloseTime(closeTime);
        hours.saveAndFlush(hour);
    }

    private void ensureService(UUID businessId, String name, String description, int durationMinutes, String price) {
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

    private void ensureProduct(UUID businessId, String name, String description, String price) {
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

    private void ensureDemoCustomers(UUID businessId) {
        if (businessId == null || customers == null) return;

        List<Customer> current = customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        ensureCustomer(businessId, current, "Matías Demo", "matias.demo@example.invalid", "Cliente ficticio para demostraciones.");
        ensureCustomer(businessId, current, "Camila Demo", "camila.demo@example.invalid", "Cliente ficticio para demostraciones.");
        ensureCustomer(businessId, current, "Javiera Demo", "javiera.demo@example.invalid", "Cliente ficticio para demostraciones.");
    }

    private void ensureCustomer(UUID businessId, List<Customer> current, String name, String email, String notes) {
        boolean exists = current.stream().anyMatch(customer -> email.equalsIgnoreCase(customer.getEmail()));
        if (exists) return;

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setName(name);
        customer.setEmail(email);
        customer.setNotes(notes);
        Customer saved = customers.saveAndFlush(customer);
        current.add(saved);
    }

    private void ensureDemoBookings(UUID businessId) {
        if (businessId == null || bookings == null || customers == null || services == null) return;

        List<Customer> demoCustomers = customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        List<ServiceItem> demoServices = services.findAllByBusinessIdOrderByNameAsc(businessId);
        List<Booking> existing = bookings.findAllByBusinessIdOrderByStartAtDesc(businessId);

        Customer matias = findCustomerByEmail(demoCustomers, "matias.demo@example.invalid");
        Customer camila = findCustomerByEmail(demoCustomers, "camila.demo@example.invalid");
        Customer javiera = findCustomerByEmail(demoCustomers, "javiera.demo@example.invalid");
        ServiceItem corte = findServiceByName(demoServices, "Corte clásico");
        ServiceItem barba = findServiceByName(demoServices, "Perfilado de barba");
        ServiceItem fade = findServiceByName(demoServices, "Fade premium");

        LocalDate demoDate = nextDemoBusinessDay();
        ensureBooking(businessId, existing, matias, corte, demoDate, LocalTime.of(10, 0), "matias-corte-clasico");
        ensureBooking(businessId, existing, camila, barba, demoDate, LocalTime.of(12, 0), "camila-perfilado-barba");
        ensureBooking(businessId, existing, javiera, fade, demoDate, LocalTime.of(16, 0), "javiera-fade-premium");
    }

    private void ensureBooking(UUID businessId,
                               List<Booking> existing,
                               Customer customer,
                               ServiceItem service,
                               LocalDate date,
                               LocalTime time,
                               String fixtureKey) {
        String marker = DEMO_BOOKING_PREFIX + fixtureKey;
        if (customer == null || customer.getId() == null || service == null || service.getId() == null) return;

        ZonedDateTime start = ZonedDateTime.of(date, time, ZoneId.of(DEMO_TIMEZONE));
        Instant targetStart = start.toInstant();
        Instant targetEnd = start.plusMinutes(service.getDurationMinutes()).toInstant();

        Optional<Booking> existingFixture = existing.stream()
                .filter(booking -> marker.equals(booking.getNotes()))
                .findFirst();
        if (existingFixture.isPresent()) {
            Booking booking = existingFixture.get();
            boolean changed = !customer.getId().equals(booking.getCustomerId())
                    || !service.getId().equals(booking.getServiceId())
                    || !targetStart.equals(booking.getStartAt())
                    || !targetEnd.equals(booking.getEndAt())
                    || booking.getStatus() != BookingStatus.CONFIRMED
                    || booking.getSource() != BookingSource.ADMIN;
            if (changed) {
                booking.setCustomerId(customer.getId());
                booking.setServiceId(service.getId());
                booking.setStartAt(targetStart);
                booking.setEndAt(targetEnd);
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setSource(BookingSource.ADMIN);
                bookings.saveAndFlush(booking);
            }
            return;
        }

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setCustomerId(customer.getId());
        booking.setServiceId(service.getId());
        booking.setStartAt(targetStart);
        booking.setEndAt(targetEnd);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.ADMIN);
        booking.setNotes(marker);
        bookings.saveAndFlush(booking);
    }

    private static Customer findCustomerByEmail(List<Customer> customers, String email) {
        return customers.stream()
                .filter(customer -> email.equalsIgnoreCase(customer.getEmail()))
                .findFirst()
                .orElse(null);
    }

    private static ServiceItem findServiceByName(List<ServiceItem> services, String name) {
        return services.stream()
                .filter(service -> name.equalsIgnoreCase(service.getName()))
                .findFirst()
                .orElse(null);
    }

    private static LocalDate nextDemoBusinessDay() {
        return nextDemoBusinessDay(LocalDate.now(ZoneId.of(DEMO_TIMEZONE)));
    }

    static LocalDate nextDemoBusinessDay(LocalDate currentDate) {
        LocalDate date = currentDate.plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private void ensureHardwareStoreProfile(UUID businessId) {
        if (businessId == null || profiles == null) return;
        BusinessProfile profile = profiles.findById(businessId).orElseGet(BusinessProfile::new);
        profile.setBusinessId(businessId);
        profile.setPresetKey(HardwareStoreDemoData.PRESET);
        profile.setPublicDescription("Ferretería ficticia de barrio para pruebas comerciales de RecepVoz. Vende fijaciones, PVC, electricidad, pintura, herramientas, madera, gasfitería, jardín, seguridad y materiales básicos.");
        profile.setPublicEmail("hola@ferreteria-san-martin-demo.invalid");
        profile.setWebsiteUrl("https://ferreteria-san-martin-demo.invalid");
        profile.setAddressLine("Pasaje Tuerca Demo 742");
        profile.setCommune("Providencia");
        profile.setCity("Santiago");
        profile.setRegion("Región Metropolitana");
        profile.setCountryCode("CL");
        profile.setDefaultCurrency(HardwareStoreDemoData.CURRENCY);
        profile.setSellsProducts(true);
        profile.setSellsServices(false);
        profile.setUsesReservations(false);
        profiles.saveAndFlush(profile);
    }

    private void ensureHardwareStoreCatalogAndInventory(UUID businessId) {
        if (businessId == null || catalog == null) return;

        Set<String> desiredNames = new HashSet<>();
        for (HardwareStoreDemoData.Product product : HardwareStoreDemoData.products()) {
            desiredNames.add(product.name().toLowerCase(java.util.Locale.ROOT));
        }

        for (CatalogItem existing : catalog.findAllByBusinessIdOrderByNameAsc(businessId)) {
            if (existing.getKind() == CatalogItem.Kind.PRODUCT
                    && existing.isActive()
                    && !desiredNames.contains(existing.getName().toLowerCase(java.util.Locale.ROOT))) {
                existing.setActive(false);
                catalog.saveAndFlush(existing);
            }
        }
        if (services != null) {
            for (ServiceItem existing : services.findAllByBusinessIdOrderByNameAsc(businessId)) {
                if (existing.isActive()) {
                    existing.setActive(false);
                    services.saveAndFlush(existing);
                }
            }
        }

        for (HardwareStoreDemoData.Product product : HardwareStoreDemoData.products()) {
            CatalogItem item = catalog.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                    .filter(value -> value.getKind() == CatalogItem.Kind.PRODUCT)
                    .filter(value -> product.name().equalsIgnoreCase(value.getName()))
                    .findFirst()
                    .orElseGet(CatalogItem::new);
            item.setBusinessId(businessId);
            item.setKind(CatalogItem.Kind.PRODUCT);
            item.setName(product.name());
            item.setDescription(HardwareStoreDemoData.productDescription(product));
            item.setPrice(product.price());
            item.setCurrency(HardwareStoreDemoData.CURRENCY);
            item.setMetadataJson("{\"sku\":\"" + product.sku() + "\",\"category\":\""
                    + product.category().replace("\"", "\\\"") + "\",\"unit\":\""
                    + product.unit() + "\",\"restrictedAdvice\":" + product.restrictedAdvice() + "}");
            item.setActive(true);
            item = catalog.saveAndFlush(item);
            ensureHardwareStoreStock(businessId, item, product);
        }
    }

    private void ensureHardwareStoreStock(UUID businessId,
                                          CatalogItem item,
                                          HardwareStoreDemoData.Product product) {
        if (inventoryStocks == null || item == null || item.getId() == null) return;
        Optional<InventoryStock> existing =
                inventoryStocks.findByBusinessIdAndCatalogItemId(businessId, item.getId());
        if (existing.isPresent()) {
            InventoryStock stock = existing.get();
            boolean changed = !product.sku().equalsIgnoreCase(stock.getSku() == null ? "" : stock.getSku())
                    || !stock.isTrackingEnabled()
                    || stock.getReorderThreshold() != 5;
            if (changed) {
                stock.setSku(product.sku());
                stock.setTrackingEnabled(true);
                stock.setReorderThreshold(5);
                inventoryStocks.saveAndFlush(stock);
            }
            return;
        }

        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(businessId);
        stock.setCatalogItemId(item.getId());
        stock.setSku(product.sku());
        stock.setTrackingEnabled(true);
        stock.setOnHand(product.stock());
        stock.setReserved(0);
        stock.setReorderThreshold(2);
        inventoryStocks.saveAndFlush(stock);
    }

    private void ensureHardwareStoreSchedule(UUID businessId) {
        if (businessId == null || hours == null) return;
        List<BusinessHour> current = hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId);
        boolean correct = current.size() == 6
                && current.stream().filter(value -> value.getDayOfWeek() >= 1 && value.getDayOfWeek() <= 5)
                        .allMatch(value -> LocalTime.of(8, 0).equals(value.getOpenTime())
                                && LocalTime.of(18, 30).equals(value.getCloseTime()))
                && current.stream().filter(value -> value.getDayOfWeek() == 6)
                        .anyMatch(value -> LocalTime.of(9, 0).equals(value.getOpenTime())
                                && LocalTime.of(14, 0).equals(value.getCloseTime()));
        if (correct) return;

        hours.deleteAllByBusinessId(businessId);
        for (int day = 1; day <= 5; day++) {
            ensureHour(businessId, day, LocalTime.of(8, 0), LocalTime.of(18, 30));
        }
        ensureHour(businessId, 6, LocalTime.of(9, 0), LocalTime.of(14, 0));
    }

    private void ensureHardwareStoreKnowledge(UUID businessId) {
        if (businessId == null || knowledge == null) return;
        Set<String> desiredTitles = new HashSet<>();
        for (HardwareStoreDemoData.Policy policy : HardwareStoreDemoData.policies()) {
            desiredTitles.add(policy.title().toLowerCase(java.util.Locale.ROOT));
        }

        for (KnowledgeItem item : knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)) {
            if (item.isActive()
                    && !desiredTitles.contains(item.getTitle().toLowerCase(java.util.Locale.ROOT))) {
                item.setActive(false);
                knowledge.saveAndFlush(item);
            }
        }
        for (HardwareStoreDemoData.Policy policy : HardwareStoreDemoData.policies()) {
            ensureKnowledge(businessId, policy.title(), policy.category(), policy.content());
        }
    }

    private void ensureHardwareStoreAgent(UUID businessId) {
        if (businessId == null || agents == null) return;
        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(AiAgent::new);
        agent.setBusinessId(businessId);
        agent.setName("RecepVoz Ferretería");
        agent.setLanguage("es");
        agent.setVoice(null);
        agent.setGreeting(HardwareStoreDemoData.greeting());
        agent.setInstructions(HardwareStoreDemoData.instructions());
        agent.setActive(true);
        agent.setCapabilities(HardwareStoreDemoData.capabilities());
        agents.saveAndFlush(agent);
    }

    private void ensureHardwareStoreDeliveryZones(UUID businessId) {
        if (businessId == null || deliveryZones == null) return;
        Set<String> desired = new HashSet<>();
        for (HardwareStoreDemoData.Delivery definition : HardwareStoreDemoData.deliveryZones()) {
            desired.add(definition.name().toLowerCase(java.util.Locale.ROOT));
        }

        for (DeliveryZone zone : deliveryZones.findAllByBusinessIdOrderByNameAsc(businessId)) {
            if (zone.isActive() && !desired.contains(zone.getName().toLowerCase(java.util.Locale.ROOT))) {
                zone.setActive(false);
                deliveryZones.saveAndFlush(zone);
            }
        }

        for (HardwareStoreDemoData.Delivery definition : HardwareStoreDemoData.deliveryZones()) {
            DeliveryZone zone = deliveryZones.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                    .filter(value -> definition.name().equalsIgnoreCase(value.getName()))
                    .findFirst()
                    .orElseGet(DeliveryZone::new);
            zone.setBusinessId(businessId);
            zone.setName(definition.name());
            zone.setCoverageTerms(definition.coverageTerms());
            zone.setFee(definition.feeAmount());
            zone.setMinimumOrder(BigDecimal.ZERO);
            zone.setActive(true);
            deliveryZones.saveAndFlush(zone);
        }
    }

    private void validateHardwareStoreReadiness(UUID businessId) {
        if (profiles == null || catalog == null || hours == null || knowledge == null
                || agents == null || inventoryStocks == null || deliveryZones == null) {
            log.debug("Hardware-store readiness validation skipped because optional demo repositories are not wired");
            return;
        }

        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        BusinessProfile profile = profiles.findById(businessId).orElse(null);
        if (profile == null
                || !HardwareStoreDemoData.PRESET.equalsIgnoreCase(profile.getPresetKey())
                || profile.getPublicDescription() == null || profile.getPublicDescription().isBlank()
                || profile.getAddressLine() == null || profile.getAddressLine().isBlank()
                || !Boolean.TRUE.equals(profile.getSellsProducts())
                || Boolean.TRUE.equals(profile.getSellsServices())
                || Boolean.TRUE.equals(profile.getUsesReservations())) {
            blockers.add("BUSINESS_PROFILE");
        }

        long activeProducts = catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId).stream()
                .filter(item -> item.getKind() == CatalogItem.Kind.PRODUCT)
                .count();
        if (activeProducts < HardwareStoreDemoData.products().size()) blockers.add("CATALOG");
        if (inventoryStocks.findAllByBusinessIdOrderByUpdatedAtDesc(businessId).size()
                < HardwareStoreDemoData.products().size()) blockers.add("INVENTORY");
        if (hours.countByBusinessId(businessId) < 6) blockers.add("SCHEDULE");
        if (knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId).size()
                < HardwareStoreDemoData.policies().size()) blockers.add("KNOWLEDGE");
        if (deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId).size()
                < HardwareStoreDemoData.deliveryZones().size()) blockers.add("DELIVERY");

        AiAgent agent = agents.findByBusinessId(businessId).orElse(null);
        Set<AiCapability> required = HardwareStoreDemoData.capabilities();
        if (agent == null || !agent.isActive() || !agent.getCapabilities().containsAll(required)
                || agent.getCapabilities().contains(AiCapability.CREATE_PAYMENT)) {
            blockers.add("AI_AGENT");
        }

        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Hardware-store demo tenant is incomplete: " + String.join(", ", blockers));
        }
        log.info("Hardware-store demo readiness validated: profile, catalog, inventory, schedule, knowledge, delivery and agent are ready");
    }

    private void validateDemoReadiness(UUID businessId) {
        if (profiles == null || services == null || hours == null || knowledge == null
                || agents == null || customers == null || bookings == null) {
            log.debug("Demo readiness validation skipped because optional demo repositories are not wired");
            return;
        }

        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        BusinessProfile profile = profiles.findById(businessId).orElse(null);
        if (profile == null || profile.getPublicDescription() == null || profile.getPublicDescription().isBlank()
                || profile.getAddressLine() == null || profile.getAddressLine().isBlank()
                || !Boolean.TRUE.equals(profile.getUsesReservations())) {
            blockers.add("BUSINESS_PROFILE");
        }

        long activeServices = services.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(ServiceItem::isActive)
                .count();
        if (activeServices < 5) blockers.add("SERVICES");
        if (hours.countByBusinessId(businessId) < 6) blockers.add("SCHEDULE");

        long activeKnowledge = knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId).size();
        if (activeKnowledge < 5) blockers.add("KNOWLEDGE");

        AiAgent agent = agents.findByBusinessId(businessId).orElse(null);
        if (agent == null || !agent.isActive()
                || !agent.getCapabilities().contains(AiCapability.GET_BUSINESS_INFORMATION)
                || !agent.getCapabilities().contains(AiCapability.LIST_SERVICES)
                || !agent.getCapabilities().contains(AiCapability.SEARCH_KNOWLEDGE)
                || !agent.getCapabilities().contains(AiCapability.CHECK_BOOKING_AVAILABILITY)
                || !agent.getCapabilities().contains(AiCapability.CREATE_BOOKING)) {
            blockers.add("AI_AGENT");
        }

        if (customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId).size() < 3) blockers.add("CUSTOMERS");
        if (bookings.findAllByBusinessIdOrderByStartAtDesc(businessId).size() < 3) blockers.add("BOOKINGS");

        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Commercial demo tenant is incomplete: " + String.join(", ", blockers));
        }
        log.info("Commercial demo readiness validated: profile, services, schedule, knowledge, agent, customers and bookings are ready");
    }
}
