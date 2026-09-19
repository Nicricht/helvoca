package cl.helvoca.bootstrap;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevDataInitializerTest {

    @Test
    void createsAnIsolatedCommercialDemoTenantWithTrialWhenEnabled() throws Exception {
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
        when(users.findByEmailIgnoreCase("demo@helvoca.local")).thenReturn(Optional.empty());
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            ReflectionTestUtils.setField(business, "id", businessId);
            return business;
        });
        Role adminRole = new Role();
        adminRole.setCode(RoleCode.BUSINESS_ADMIN);
        adminRole.setName("Business admin");
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(adminRole));
        when(encoder.encode("safe-demo-password")).thenReturn("hashed");

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        var businessCaptor = org.mockito.ArgumentCaptor.forClass(Business.class);
        verify(businesses).saveAndFlush(businessCaptor.capture());
        Business business = businessCaptor.getValue();
        assertEquals("Helvoca Demo Business", business.getName());
        assertEquals("America/Santiago", business.getTimezone());
        assertEquals("es", business.getLanguage());
        assertNull(business.getHumanTransferPhone());
        verify(subscriptions).startBasicTrial(businessId);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(userCaptor.capture());
        AppUser admin = userCaptor.getValue();
        assertSame(business, admin.getBusiness());
        assertEquals("Demo Administrator", admin.getName());
        assertEquals("demo@helvoca.local", admin.getEmail());
        assertEquals("hashed", admin.getPasswordHash());
        assertTrue(admin.getRoles().contains(adminRole));

        var serviceCaptor = org.mockito.ArgumentCaptor.forClass(ServiceItem.class);
        verify(services, times(3)).saveAndFlush(serviceCaptor.capture());
        List<ServiceItem> seededServices = serviceCaptor.getAllValues();
        assertEquals(List.of("Consulta inicial", "Servicio completo", "Control de seguimiento"),
                seededServices.stream().map(ServiceItem::getName).toList());
        assertEquals(List.of("19990", "39990", "14990"),
                seededServices.stream().map(item -> item.getPrice().toPlainString()).toList());

        var productCaptor = org.mockito.ArgumentCaptor.forClass(CatalogItem.class);
        verify(catalog, times(2)).saveAndFlush(productCaptor.capture());
        List<CatalogItem> seededProducts = productCaptor.getAllValues();
        assertEquals(List.of("Kit esencial", "Kit premium"),
                seededProducts.stream().map(CatalogItem::getName).toList());
        assertTrue(seededProducts.stream().allMatch(item -> item.getKind() == CatalogItem.Kind.PRODUCT));

        var hourCaptor = org.mockito.ArgumentCaptor.forClass(BusinessHour.class);
        verify(hours, times(6)).saveAndFlush(hourCaptor.capture());
        List<BusinessHour> seededHours = hourCaptor.getAllValues();
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                seededHours.stream().map(BusinessHour::getDayOfWeek).toList());
        assertTrue(seededHours.subList(0, 5).stream().allMatch(hour ->
                hour.getOpenTime().equals(LocalTime.of(9, 0)) && hour.getCloseTime().equals(LocalTime.of(18, 0))));
        assertEquals(LocalTime.of(10, 0), seededHours.get(5).getOpenTime());
        assertEquals(LocalTime.of(14, 0), seededHours.get(5).getCloseTime());

        var knowledgeCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeItem.class);
        verify(knowledge, times(4)).saveAndFlush(knowledgeCaptor.capture());
        List<KnowledgeItem> seededKnowledge = knowledgeCaptor.getAllValues();
        assertEquals(List.of("Reservas y confirmación", "Cambios y cancelaciones", "Información no disponible", "Pagos en la demostración"),
                seededKnowledge.stream().map(KnowledgeItem::getTitle).toList());
        assertTrue(seededKnowledge.stream().allMatch(KnowledgeItem::isActive));
    }

    @Test
    void refreshesMissingDemoCatalogForAnExistingTenantWithoutDuplicatingTheTenant() throws Exception {
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
        business.setName("Helvoca Demo Business");
        AppUser admin = new AppUser();
        admin.setBusiness(business);
        admin.setEmail("demo@helvoca.local");

        when(users.findByEmailIgnoreCase("demo@helvoca.local")).thenReturn(Optional.of(admin));
        when(services.existsByBusinessIdAndNameIgnoreCase(businessId, "Consulta inicial")).thenReturn(true);
        when(catalog.existsByBusinessIdAndKindAndNameIgnoreCase(businessId, CatalogItem.Kind.PRODUCT, "Kit esencial")).thenReturn(true);
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, "Reservas y confirmación")).thenReturn(true);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        verifyNoInteractions(businesses, roles, encoder);
        verify(users, never()).saveAndFlush(any());
        verify(subscriptions).startBasicTrial(businessId);
        verify(services, times(2)).saveAndFlush(any(ServiceItem.class));
        verify(catalog, times(1)).saveAndFlush(any(CatalogItem.class));
        verify(hours, times(6)).saveAndFlush(any(BusinessHour.class));
        verify(knowledge, times(3)).saveAndFlush(any(KnowledgeItem.class));
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
        business.setName("Helvoca Demo Business");
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
        verify(knowledge, times(4)).saveAndFlush(any(KnowledgeItem.class));
    }

    @Test
    void keepsExistingDemoKnowledgeUntouchedByTitle() throws Exception {
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
        business.setName("Helvoca Demo Business");
        AppUser admin = new AppUser();
        admin.setBusiness(business);
        admin.setEmail("demo@helvoca.local");

        when(users.findByEmailIgnoreCase("demo@helvoca.local")).thenReturn(Optional.of(admin));
        when(hours.countByBusinessId(businessId)).thenReturn(6L);
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, "Reservas y confirmación")).thenReturn(true);
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, "Cambios y cancelaciones")).thenReturn(true);
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, "Información no disponible")).thenReturn(true);
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(businessId, "Pagos en la demostración")).thenReturn(true);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        verify(knowledge, never()).saveAndFlush(any(KnowledgeItem.class));
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
}
