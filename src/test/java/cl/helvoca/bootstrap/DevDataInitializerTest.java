package cl.helvoca.bootstrap;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.Role;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DevDataInitializerTest {

    @Test
    void createsCompleteCommercialBarbershopDemoTenantWhenEnabled() throws Exception {
        DemoMocks f = new DemoMocks();
        DevDataInitializer initializer = f.initializer(true);

        initializer.run();

        assertNotNull(f.business.get());
        assertEquals("Barbería Norte Demo", f.business.get().getName());
        assertEquals("America/Santiago", f.business.get().getTimezone());
        assertEquals("es", f.business.get().getLanguage());
        assertNull(f.business.get().getHumanTransferPhone());
        verify(f.subscriptions).startBasicTrial(f.businessId);

        assertNotNull(f.admin.get());
        assertSame(f.business.get(), f.admin.get().getBusiness());
        assertEquals("Administración Demo", f.admin.get().getName());
        assertEquals("demo@helvoca.local", f.admin.get().getEmail());
        assertEquals("hashed", f.admin.get().getPasswordHash());

        BusinessProfile profile = f.profile.get();
        assertNotNull(profile);
        assertEquals("barbershop", profile.getPresetKey());
        assertTrue(profile.getPublicDescription().contains("ficticia"));
        assertEquals("Pasaje Demo 123", profile.getAddressLine());
        assertEquals("Providencia", profile.getCommune());
        assertEquals("Santiago", profile.getCity());
        assertEquals("CL", profile.getCountryCode());
        assertEquals("CLP", profile.getDefaultCurrency());
        assertEquals(Boolean.TRUE, profile.getSellsProducts());
        assertEquals(Boolean.TRUE, profile.getSellsServices());
        assertEquals(Boolean.TRUE, profile.getUsesReservations());

        assertEquals(6, f.serviceStore.size());
        assertEquals(
                List.of("Corte clásico", "Corte + barba", "Perfilado de barba", "Fade premium", "Corte infantil", "Tratamiento capilar"),
                f.serviceStore.stream().map(ServiceItem::getName).toList());
        assertTrue(f.serviceStore.stream().allMatch(ServiceItem::isActive));
        assertTrue(f.serviceStore.stream().allMatch(item -> item.getPrice() != null && item.getPrice().signum() > 0));

        assertEquals(2, f.catalogStore.size());
        assertEquals(List.of("Cera mate demo", "Aceite para barba demo"),
                f.catalogStore.stream().map(CatalogItem::getName).toList());
        assertTrue(f.catalogStore.stream().allMatch(item -> item.getKind() == CatalogItem.Kind.PRODUCT));

        assertEquals(6, f.hourStore.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                f.hourStore.stream().map(BusinessHour::getDayOfWeek).toList());
        assertTrue(f.hourStore.subList(0, 5).stream().allMatch(hour ->
                hour.getOpenTime().equals(LocalTime.of(9, 0)) && hour.getCloseTime().equals(LocalTime.of(19, 0))));
        assertEquals(LocalTime.of(10, 0), f.hourStore.get(5).getOpenTime());
        assertEquals(LocalTime.of(15, 0), f.hourStore.get(5).getCloseTime());

        assertEquals(6, f.knowledgeStore.size());
        assertTrue(f.knowledgeStore.stream().allMatch(KnowledgeItem::isActive));
        assertTrue(f.knowledgeStore.stream().anyMatch(item ->
                item.getTitle().equals("Cambios y cancelaciones") && item.getContent().contains("4 horas")));
        assertTrue(f.knowledgeStore.stream().anyMatch(item ->
                item.getTitle().equals("Ubicación y llegada") && item.getContent().contains("ubicación completamente ficticia")));

        AiAgent agent = f.agent.get();
        assertNotNull(agent);
        assertEquals("RecepVoz Demo", agent.getName());
        assertTrue(agent.isActive());
        assertNull(agent.getVoice());
        assertTrue(agent.getCapabilities().contains(AiCapability.GET_BUSINESS_INFORMATION));
        assertTrue(agent.getCapabilities().contains(AiCapability.LIST_SERVICES));
        assertTrue(agent.getCapabilities().contains(AiCapability.SEARCH_KNOWLEDGE));
        assertTrue(agent.getCapabilities().contains(AiCapability.CHECK_BOOKING_AVAILABILITY));
        assertTrue(agent.getCapabilities().contains(AiCapability.CREATE_BOOKING));
        assertFalse(agent.getCapabilities().contains(AiCapability.TRANSFER_TO_HUMAN));
        assertFalse(agent.getCapabilities().contains(AiCapability.CREATE_PAYMENT));

        assertEquals(3, f.customerStore.size());
        assertEquals(List.of("Matías Demo", "Camila Demo", "Javiera Demo"),
                f.customerStore.stream().map(Customer::getName).toList());
        assertTrue(f.customerStore.stream().allMatch(customer ->
                customer.getEmail().endsWith("@example.invalid") && customer.getPhone() == null));

        assertEquals(3, f.bookingStore.size());
        assertTrue(f.bookingStore.stream().allMatch(booking -> booking.getStartAt().isAfter(Instant.now())));
        assertTrue(f.bookingStore.stream().allMatch(booking -> booking.getEndAt().isAfter(booking.getStartAt())));
        assertTrue(f.bookingStore.stream().allMatch(booking -> booking.getSource() == BookingSource.ADMIN));
        assertTrue(f.bookingStore.stream().allMatch(booking -> booking.getNotes().startsWith("DEMO_FIXTURE:")));
    }

    @Test
    void rerunIsIdempotentAndKeepsTheSameDemoTenant() throws Exception {
        DemoMocks f = new DemoMocks();
        DevDataInitializer initializer = f.initializer(true);

        initializer.run();
        initializer.run();

        verify(f.businesses, times(1)).saveAndFlush(any(Business.class));
        verify(f.users, times(1)).saveAndFlush(any(AppUser.class));
        verify(f.subscriptions, times(2)).startBasicTrial(f.businessId);
        assertEquals(6, f.serviceStore.size());
        assertEquals(2, f.catalogStore.size());
        assertEquals(6, f.hourStore.size());
        assertEquals(6, f.knowledgeStore.size());
        assertNotNull(f.agent.get());
        assertEquals(3, f.customerStore.size());
        assertEquals(3, f.bookingStore.size());
        assertNotNull(f.profile.get());
    }

    @Test
    void upgradesLegacyGenericDemoFixtureWithoutLeavingExtraActiveServices() throws Exception {
        DemoMocks f = new DemoMocks();

        Business legacyBusiness = new Business();
        ReflectionTestUtils.setField(legacyBusiness, "id", f.businessId);
        legacyBusiness.setName("Helvoca Demo Business");
        AppUser existingAdmin = new AppUser();
        existingAdmin.setBusiness(legacyBusiness);
        existingAdmin.setEmail("demo@helvoca.local");
        f.business.set(legacyBusiness);
        f.admin.set(existingAdmin);

        for (String name : List.of("Consulta inicial", "Servicio completo", "Control de seguimiento")) {
            ServiceItem legacy = new ServiceItem();
            ReflectionTestUtils.setField(legacy, "id", UUID.randomUUID());
            legacy.setBusinessId(f.businessId);
            legacy.setName(name);
            legacy.setDurationMinutes(30);
            legacy.setActive(true);
            f.serviceStore.add(legacy);
        }

        KnowledgeItem legacyCancellation = new KnowledgeItem();
        ReflectionTestUtils.setField(legacyCancellation, "id", UUID.randomUUID());
        legacyCancellation.setBusinessId(f.businessId);
        legacyCancellation.setTitle("Cambios y cancelaciones");
        legacyCancellation.setCategory("Reservas");
        legacyCancellation.setContent("Texto legacy sin política concreta.");
        legacyCancellation.setActive(true);
        f.knowledgeStore.add(legacyCancellation);

        DevDataInitializer initializer = f.initializer(true);
        initializer.run();

        assertEquals("Barbería Norte Demo", legacyBusiness.getName());
        assertEquals(6, f.serviceStore.stream().filter(ServiceItem::isActive).count());
        assertTrue(f.serviceStore.stream()
                .filter(item -> List.of("Consulta inicial", "Servicio completo", "Control de seguimiento").contains(item.getName()))
                .noneMatch(ServiceItem::isActive));
        assertEquals(6, f.knowledgeStore.size());
        KnowledgeItem upgraded = f.knowledgeStore.stream()
                .filter(item -> "Cambios y cancelaciones".equals(item.getTitle()))
                .findFirst()
                .orElseThrow();
        assertEquals("Políticas", upgraded.getCategory());
        assertTrue(upgraded.getContent().contains("4 horas"));
        assertEquals(3, f.customerStore.size());
        assertEquals(3, f.bookingStore.size());
    }

    @Test
    void keepsExistingDemoScheduleUntouched() throws Exception {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);

        UUID businessId = UUID.randomUUID();
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", businessId);
        business.setName("Existing Demo");
        AppUser admin = new AppUser();
        admin.setBusiness(business);
        admin.setEmail("demo@helvoca.local");

        when(users.findByEmailIgnoreCase("demo@helvoca.local")).thenReturn(Optional.of(admin));
        when(hours.countByBusinessId(businessId)).thenReturn(2L);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        verify(hours).countByBusinessId(businessId);
        verify(hours, never()).saveAndFlush(any(BusinessHour.class));
        verify(knowledge, times(6)).saveAndFlush(any(KnowledgeItem.class));
    }

    @Test
    void keepsExistingDemoKnowledgeAndAgentUntouched() throws Exception {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);

        UUID businessId = UUID.randomUUID();
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", businessId);
        business.setName("Existing Demo");
        AppUser admin = new AppUser();
        admin.setBusiness(business);
        admin.setEmail("demo@helvoca.local");

        when(users.findByEmailIgnoreCase("demo@helvoca.local")).thenReturn(Optional.of(admin));
        when(hours.countByBusinessId(businessId)).thenReturn(6L);
        for (String title : List.of(
                "Reservas y confirmación",
                "Cambios y cancelaciones",
                "Información no disponible",
                "Pagos en la demostración",
                "Ubicación y llegada",
                "Atrasos")) {
            when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, title)).thenReturn(true);
        }
        when(agents.existsByBusinessId(businessId)).thenReturn(true);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        initializer.setDemoAgentRepository(agents);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        verify(knowledge, never()).saveAndFlush(any(KnowledgeItem.class));
        verify(agents).existsByBusinessId(businessId);
        verify(agents, never()).saveAndFlush(any(AiAgent.class));
    }

    @Test
    void failsFastWhenFullyWiredDemoCannotPassReadiness() throws Exception {
        DemoMocks f = new DemoMocks();
        when(f.profiles.findById(f.businessId)).thenReturn(Optional.empty());

        DevDataInitializer initializer = f.initializer(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(error.getMessage().contains("BUSINESS_PROFILE"));
    }

    @Test
    void doesNothingWhenSeedIsDisabled() throws Exception {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        ReflectionTestUtils.setField(initializer, "enabled", false);

        initializer.run();

        verifyNoInteractions(businesses, users, roles, encoder);
    }

    private static final class DemoMocks {
        final UUID businessId = UUID.randomUUID();

        final BusinessRepository businesses = mock(BusinessRepository.class);
        final AppUserRepository users = mock(AppUserRepository.class);
        final RoleRepository roles = mock(RoleRepository.class);
        final PasswordEncoder encoder = mock(PasswordEncoder.class);
        final BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        final ServiceItemRepository services = mock(ServiceItemRepository.class);
        final CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        final BusinessHourRepository hours = mock(BusinessHourRepository.class);
        final KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        final AiAgentRepository agents = mock(AiAgentRepository.class);
        final BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        final CustomerRepository customers = mock(CustomerRepository.class);
        final BookingRepository bookings = mock(BookingRepository.class);

        final AtomicReference<Business> business = new AtomicReference<>();
        final AtomicReference<AppUser> admin = new AtomicReference<>();
        final AtomicReference<BusinessProfile> profile = new AtomicReference<>();
        final AtomicReference<AiAgent> agent = new AtomicReference<>();

        final List<ServiceItem> serviceStore = new ArrayList<>();
        final List<CatalogItem> catalogStore = new ArrayList<>();
        final List<BusinessHour> hourStore = new ArrayList<>();
        final List<KnowledgeItem> knowledgeStore = new ArrayList<>();
        final List<Customer> customerStore = new ArrayList<>();
        final List<Booking> bookingStore = new ArrayList<>();

        DemoMocks() {
            when(users.findByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
                    Optional.ofNullable(admin.get()));

            when(businesses.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
                Business saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", businessId);
                business.set(saved);
                return saved;
            });

            when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
                AppUser saved = invocation.getArgument(0);
                admin.set(saved);
                return saved;
            });

            Role adminRole = new Role();
            adminRole.setCode(RoleCode.BUSINESS_ADMIN);
            adminRole.setName("Business admin");
            when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(adminRole));
            when(encoder.encode("safe-demo-password")).thenReturn("hashed");

            when(profiles.findById(businessId)).thenAnswer(invocation -> Optional.ofNullable(profile.get()));
            when(profiles.saveAndFlush(any(BusinessProfile.class))).thenAnswer(invocation -> {
                BusinessProfile saved = invocation.getArgument(0);
                profile.set(saved);
                return saved;
            });

            when(services.existsByBusinessIdAndNameIgnoreCase(eq(businessId), anyString())).thenAnswer(invocation -> {
                String name = invocation.getArgument(1);
                return serviceStore.stream().anyMatch(item -> name.equalsIgnoreCase(item.getName()));
            });
            when(services.saveAndFlush(any(ServiceItem.class))).thenAnswer(invocation -> {
                ServiceItem saved = invocation.getArgument(0);
                if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                if (serviceStore.stream().noneMatch(item -> saved.getId().equals(item.getId()))) {
                    serviceStore.add(saved);
                }
                return saved;
            });
            when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenAnswer(invocation ->
                    new ArrayList<>(serviceStore));

            when(catalog.existsByBusinessIdAndKindAndNameIgnoreCase(eq(businessId), eq(CatalogItem.Kind.PRODUCT), anyString()))
                    .thenAnswer(invocation -> {
                        String name = invocation.getArgument(2);
                        return catalogStore.stream().anyMatch(item -> name.equalsIgnoreCase(item.getName()));
                    });
            when(catalog.saveAndFlush(any(CatalogItem.class))).thenAnswer(invocation -> {
                CatalogItem saved = invocation.getArgument(0);
                catalogStore.add(saved);
                return saved;
            });

            when(hours.countByBusinessId(businessId)).thenAnswer(invocation -> (long) hourStore.size());
            when(hours.saveAndFlush(any(BusinessHour.class))).thenAnswer(invocation -> {
                BusinessHour saved = invocation.getArgument(0);
                hourStore.add(saved);
                return saved;
            });

            when(knowledge.existsByBusinessIdAndTitleIgnoreCase(eq(businessId), anyString())).thenAnswer(invocation -> {
                String title = invocation.getArgument(1);
                return knowledgeStore.stream().anyMatch(item -> title.equalsIgnoreCase(item.getTitle()));
            });
            when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenAnswer(invocation ->
                    new ArrayList<>(knowledgeStore));
            when(knowledge.saveAndFlush(any(KnowledgeItem.class))).thenAnswer(invocation -> {
                KnowledgeItem saved = invocation.getArgument(0);
                if (!knowledgeStore.contains(saved)) knowledgeStore.add(saved);
                return saved;
            });
            when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)).thenAnswer(invocation ->
                    knowledgeStore.stream().filter(KnowledgeItem::isActive).toList());

            when(agents.existsByBusinessId(businessId)).thenAnswer(invocation -> agent.get() != null);
            when(agents.findByBusinessId(businessId)).thenAnswer(invocation -> Optional.ofNullable(agent.get()));
            when(agents.saveAndFlush(any(AiAgent.class))).thenAnswer(invocation -> {
                AiAgent saved = invocation.getArgument(0);
                agent.set(saved);
                return saved;
            });

            when(customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenAnswer(invocation ->
                    new ArrayList<>(customerStore));
            when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
                Customer saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                customerStore.add(saved);
                return saved;
            });

            when(bookings.findAllByBusinessIdOrderByStartAtDesc(businessId)).thenAnswer(invocation ->
                    new ArrayList<>(bookingStore));
            when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
                Booking saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                bookingStore.add(saved);
                return saved;
            });
        }

        DevDataInitializer initializer(boolean enabled) {
            DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
            initializer.setSubscriptions(subscriptions);
            initializer.setDemoCatalogRepositories(services, catalog);
            initializer.setDemoScheduleRepository(hours);
            initializer.setDemoKnowledgeRepository(knowledge);
            initializer.setDemoAgentRepository(agents);
            initializer.setDemoBusinessProfileRepository(profiles);
            initializer.setDemoOperationalRepositories(customers, bookings);
            ReflectionTestUtils.setField(initializer, "enabled", enabled);
            ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
            ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");
            return initializer;
        }
    }
}
