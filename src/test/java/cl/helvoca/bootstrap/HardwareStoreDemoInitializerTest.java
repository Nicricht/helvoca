package cl.helvoca.bootstrap;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.inventory.InventoryStock;
import cl.helvoca.inventory.InventoryStockRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HardwareStoreDemoInitializerTest {

    @Test
    void provisionsCompleteHardwareStoreTenantIdempotently() throws Exception {
        UUID businessId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        InventoryStockRepository stocks = mock(InventoryStockRepository.class);
        DeliveryZoneRepository zones = mock(DeliveryZoneRepository.class);

        AtomicReference<Business> business = new AtomicReference<>();
        AtomicReference<AppUser> admin = new AtomicReference<>();
        AtomicReference<BusinessProfile> profile = new AtomicReference<>();
        AtomicReference<AiAgent> agent = new AtomicReference<>();
        List<ServiceItem> serviceStore = new ArrayList<>();
        List<CatalogItem> catalogStore = new ArrayList<>();
        List<BusinessHour> hourStore = new ArrayList<>();
        List<KnowledgeItem> knowledgeStore = new ArrayList<>();
        List<InventoryStock> stockStore = new ArrayList<>();
        List<DeliveryZone> zoneStore = new ArrayList<>();

        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> Optional.ofNullable(admin.get()));
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
            Business value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", businessId);
            business.set(value);
            return value;
        });
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser value = invocation.getArgument(0);
            admin.set(value);
            return value;
        });
        Role role = new Role();
        role.setCode(RoleCode.BUSINESS_ADMIN);
        role.setName("Business admin");
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(role));
        when(encoder.encode(anyString())).thenReturn("hashed");

        when(profiles.findById(businessId)).thenAnswer(invocation -> Optional.ofNullable(profile.get()));
        when(profiles.saveAndFlush(any(BusinessProfile.class))).thenAnswer(invocation -> {
            BusinessProfile value = invocation.getArgument(0);
            profile.set(value);
            return value;
        });

        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenAnswer(invocation -> new ArrayList<>(serviceStore));
        when(services.saveAndFlush(any(ServiceItem.class))).thenAnswer(invocation -> {
            ServiceItem value = invocation.getArgument(0);
            if (!serviceStore.contains(value)) serviceStore.add(value);
            return value;
        });

        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenAnswer(invocation -> new ArrayList<>(catalogStore));
        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenAnswer(invocation ->
                catalogStore.stream().filter(CatalogItem::isActive).toList());
        when(catalog.saveAndFlush(any(CatalogItem.class))).thenAnswer(invocation -> {
            CatalogItem value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            if (catalogStore.stream().noneMatch(item -> item.getId().equals(value.getId()))) catalogStore.add(value);
            return value;
        });

        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)).thenAnswer(invocation -> new ArrayList<>(hourStore));
        when(hours.countByBusinessId(businessId)).thenAnswer(invocation -> (long) hourStore.size());
        doAnswer(invocation -> { hourStore.clear(); return null; }).when(hours).deleteAllByBusinessId(businessId);
        when(hours.saveAndFlush(any(BusinessHour.class))).thenAnswer(invocation -> {
            BusinessHour value = invocation.getArgument(0);
            hourStore.add(value);
            return value;
        });

        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenAnswer(invocation -> new ArrayList<>(knowledgeStore));
        when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)).thenAnswer(invocation ->
                knowledgeStore.stream().filter(KnowledgeItem::isActive).toList());
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(eq(businessId), anyString())).thenAnswer(invocation -> {
            String title = invocation.getArgument(1);
            return knowledgeStore.stream().anyMatch(item -> title.equalsIgnoreCase(item.getTitle()));
        });
        when(knowledge.saveAndFlush(any(KnowledgeItem.class))).thenAnswer(invocation -> {
            KnowledgeItem value = invocation.getArgument(0);
            if (!knowledgeStore.contains(value)) knowledgeStore.add(value);
            return value;
        });

        when(agents.findByBusinessId(businessId)).thenAnswer(invocation -> Optional.ofNullable(agent.get()));
        when(agents.saveAndFlush(any(AiAgent.class))).thenAnswer(invocation -> {
            AiAgent value = invocation.getArgument(0);
            agent.set(value);
            return value;
        });

        when(stocks.findByBusinessIdAndCatalogItemId(eq(businessId), any(UUID.class))).thenAnswer(invocation -> {
            UUID itemId = invocation.getArgument(1);
            return stockStore.stream().filter(value -> itemId.equals(value.getCatalogItemId())).findFirst();
        });
        when(stocks.findAllByBusinessIdOrderByUpdatedAtDesc(businessId)).thenAnswer(invocation -> new ArrayList<>(stockStore));
        when(stocks.saveAndFlush(any(InventoryStock.class))).thenAnswer(invocation -> {
            InventoryStock value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            if (stockStore.stream().noneMatch(item -> item.getId().equals(value.getId()))) stockStore.add(value);
            return value;
        });

        when(zones.findAllByBusinessIdOrderByNameAsc(businessId)).thenAnswer(invocation -> new ArrayList<>(zoneStore));
        when(zones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenAnswer(invocation ->
                zoneStore.stream().filter(DeliveryZone::isActive).toList());
        when(zones.saveAndFlush(any(DeliveryZone.class))).thenAnswer(invocation -> {
            DeliveryZone value = invocation.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
            if (zoneStore.stream().noneMatch(item -> item.getId().equals(value.getId()))) zoneStore.add(value);
            return value;
        });

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        initializer.setDemoAgentRepository(agents);
        initializer.setDemoBusinessProfileRepository(profiles);
        initializer.setHardwareStoreRepositories(stocks, zones);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "ferreteria.demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");
        ReflectionTestUtils.setField(initializer, "seedPreset", "hardware_store");

        initializer.run();

        assertEquals("Ferretería San Martín Demo", business.get().getName());
        assertEquals("Administración Ferretería Demo", admin.get().getName());
        assertNull(business.get().getHumanTransferPhone());

        assertNotNull(profile.get());
        assertEquals("hardware_store", profile.get().getPresetKey());
        assertEquals(Boolean.TRUE, profile.get().getSellsProducts());
        assertEquals(Boolean.FALSE, profile.get().getSellsServices());
        assertEquals(Boolean.FALSE, profile.get().getUsesReservations());
        assertEquals("Pasaje Tuerca Demo 742", profile.get().getAddressLine());

        assertEquals(34, catalogStore.stream().filter(CatalogItem::isActive).count());
        assertEquals(34, stockStore.size());
        InventoryStock lastUnit = stockStore.stream()
                .filter(value -> "ELE-ALZ-6".equals(value.getSku()))
                .findFirst().orElseThrow();
        assertEquals(1, lastUnit.getOnHand());
        assertTrue(lastUnit.isTrackingEnabled());
        assertEquals(0, stockStore.stream().filter(value -> "FIX-TAR-8-100".equals(value.getSku()))
                .findFirst().orElseThrow().getOnHand());

        assertEquals(6, hourStore.size());
        assertTrue(hourStore.stream().filter(value -> value.getDayOfWeek() <= 5)
                .allMatch(value -> LocalTime.of(8, 0).equals(value.getOpenTime())
                        && LocalTime.of(18, 30).equals(value.getCloseTime())));
        assertTrue(hourStore.stream().anyMatch(value -> value.getDayOfWeek() == 6
                && LocalTime.of(9, 0).equals(value.getOpenTime())
                && LocalTime.of(14, 0).equals(value.getCloseTime())));

        assertEquals(17, knowledgeStore.stream().filter(KnowledgeItem::isActive).count());
        assertTrue(knowledgeStore.stream().anyMatch(item ->
                item.getTitle().equals("Plazos de delivery")
                        && item.getContent().contains("Providencia Demo")
                        && item.getContent().contains("corte 13:00")
                        && item.getContent().contains("Ñuñoa Demo")
                        && item.getContent().contains("Santiago Centro Demo")));
        assertEquals(3, zoneStore.stream().filter(DeliveryZone::isActive).count());
        assertTrue(zoneStore.stream().anyMatch(value -> value.getName().equals("Providencia Demo")
                && value.getFee().intValueExact() == 3990));

        assertNotNull(agent.get());
        assertEquals("RecepVoz Ferretería", agent.get().getName());
        assertEquals("Hola, te comunicaste con Ferretería San Martín Demo. ¿Qué necesitas cotizar o comprar?",
                agent.get().getGreeting());
        assertTrue(agent.get().getInstructions().contains("Nunca inventes stock"));
        assertTrue(agent.get().getCapabilities().contains(AiCapability.LIST_CATALOG));
        assertTrue(agent.get().getCapabilities().contains(AiCapability.CREATE_ORDER));
        assertTrue(agent.get().getCapabilities().contains(AiCapability.QUOTE_DELIVERY));
        assertTrue(agent.get().getCapabilities().contains(AiCapability.CREATE_QUOTE));
        assertFalse(agent.get().getCapabilities().contains(AiCapability.CREATE_PAYMENT));

        initializer.run();

        assertEquals(34, catalogStore.size());
        assertEquals(34, stockStore.size());
        assertEquals(17, knowledgeStore.size());
        assertEquals(3, zoneStore.size());
        assertEquals(6, hourStore.size());
        verify(subscriptions, times(2)).startBasicTrial(businessId);
    }

    @Test
    void repairsExistingHardwareStoreArtifactsAndCoversStableScheduleBranch() {
        UUID businessId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);

        ServiceItemRepository services = mock(ServiceItemRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        InventoryStockRepository stocks = mock(InventoryStockRepository.class);
        DeliveryZoneRepository zones = mock(DeliveryZoneRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);

        ServiceItem staleService = new ServiceItem();
        staleService.setActive(true);

        CatalogItem staleProduct = new CatalogItem();
        staleProduct.setKind(CatalogItem.Kind.PRODUCT);
        staleProduct.setName("Producto viejo");
        staleProduct.setActive(true);

        when(services.findAllByBusinessIdOrderByNameAsc(businessId))
                .thenReturn(List.of(staleService));
        when(services.saveAndFlush(any(ServiceItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId))
                .thenReturn(List.of(staleProduct));
        when(catalog.saveAndFlush(any(CatalogItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        initializer.setDemoCatalogRepositories(services, catalog);
        ReflectionTestUtils.invokeMethod(
                initializer, "ensureHardwareStoreCatalogAndInventory", businessId);

        assertFalse(staleProduct.isActive());
        assertFalse(staleService.isActive());
        verify(catalog, atLeastOnce()).saveAndFlush(staleProduct);
        verify(services).saveAndFlush(staleService);

        CatalogItem existingCatalogItem = new CatalogItem();
        ReflectionTestUtils.setField(existingCatalogItem, "id", UUID.randomUUID());
        HardwareStoreDemoData.Product product = HardwareStoreDemoData.products().get(0);

        InventoryStock existingStock = new InventoryStock();
        existingStock.setSku("SKU-ANTIGUO");
        existingStock.setTrackingEnabled(false);
        existingStock.setReorderThreshold(0);

        when(stocks.findByBusinessIdAndCatalogItemId(
                businessId, existingCatalogItem.getId()))
                .thenReturn(Optional.of(existingStock));

        initializer.setHardwareStoreRepositories(stocks, zones);
        ReflectionTestUtils.invokeMethod(
                initializer,
                "ensureHardwareStoreStock",
                businessId,
                existingCatalogItem,
                product);

        assertEquals(product.sku(), existingStock.getSku());
        assertTrue(existingStock.isTrackingEnabled());
        assertEquals(5, existingStock.getReorderThreshold());
        verify(stocks).saveAndFlush(existingStock);

        clearInvocations(stocks);
        ReflectionTestUtils.invokeMethod(
                initializer,
                "ensureHardwareStoreStock",
                businessId,
                existingCatalogItem,
                product);
        verify(stocks, never()).saveAndFlush(any(InventoryStock.class));

        List<BusinessHour> correctHours = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            BusinessHour value = mock(BusinessHour.class);
            when(value.getDayOfWeek()).thenReturn(day);
            when(value.getOpenTime()).thenReturn(LocalTime.of(8, 0));
            when(value.getCloseTime()).thenReturn(LocalTime.of(18, 30));
            correctHours.add(value);
        }
        BusinessHour saturday = mock(BusinessHour.class);
        when(saturday.getDayOfWeek()).thenReturn(6);
        when(saturday.getOpenTime()).thenReturn(LocalTime.of(9, 0));
        when(saturday.getCloseTime()).thenReturn(LocalTime.of(14, 0));
        correctHours.add(saturday);

        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId))
                .thenReturn(correctHours);
        initializer.setDemoScheduleRepository(hours);
        ReflectionTestUtils.invokeMethod(
                initializer, "ensureHardwareStoreSchedule", businessId);
        verify(hours, never()).deleteAllByBusinessId(any(UUID.class));

        KnowledgeItem staleKnowledge = new KnowledgeItem();
        staleKnowledge.setTitle("Política obsoleta");
        staleKnowledge.setActive(true);
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId))
                .thenReturn(List.of(staleKnowledge));
        when(knowledge.existsByBusinessIdAndTitleIgnoreCase(
                eq(businessId), anyString())).thenReturn(true);
        when(knowledge.saveAndFlush(any(KnowledgeItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        initializer.setDemoKnowledgeRepository(knowledge);
        ReflectionTestUtils.invokeMethod(
                initializer, "ensureHardwareStoreKnowledge", businessId);

        assertFalse(staleKnowledge.isActive());
        verify(knowledge).saveAndFlush(staleKnowledge);

        DeliveryZone staleZone = new DeliveryZone();
        staleZone.setName("Zona obsoleta");
        staleZone.setActive(true);
        when(zones.findAllByBusinessIdOrderByNameAsc(businessId))
                .thenReturn(List.of(staleZone));
        when(zones.saveAndFlush(any(DeliveryZone.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(
                initializer, "ensureHardwareStoreDeliveryZones", businessId);

        assertFalse(staleZone.isActive());
        verify(zones, atLeastOnce()).saveAndFlush(staleZone);
    }

    @Test
    void hardwareStoreReadinessFailsClosedWhenRequiredDataIsIncomplete() {
        UUID businessId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                initializer, "validateHardwareStoreReadiness", businessId));

        ServiceItemRepository services = mock(ServiceItemRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        InventoryStockRepository stocks = mock(InventoryStockRepository.class);
        DeliveryZoneRepository zones = mock(DeliveryZoneRepository.class);

        initializer.setDemoCatalogRepositories(services, catalog);
        initializer.setDemoScheduleRepository(hours);
        initializer.setDemoKnowledgeRepository(knowledge);
        initializer.setDemoAgentRepository(agents);
        initializer.setDemoBusinessProfileRepository(profiles);
        initializer.setHardwareStoreRepositories(stocks, zones);

        when(profiles.findById(businessId)).thenReturn(Optional.empty());
        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId))
                .thenReturn(List.of());
        when(stocks.findAllByBusinessIdOrderByUpdatedAtDesc(businessId))
                .thenReturn(List.of());
        when(hours.countByBusinessId(businessId)).thenReturn(0L);
        when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId))
                .thenReturn(List.of());
        when(zones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId))
                .thenReturn(List.of());
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        initializer, "validateHardwareStoreReadiness", businessId));

        assertTrue(error.getMessage().contains("BUSINESS_PROFILE"));
        assertTrue(error.getMessage().contains("CATALOG"));
        assertTrue(error.getMessage().contains("INVENTORY"));
        assertTrue(error.getMessage().contains("SCHEDULE"));
        assertTrue(error.getMessage().contains("KNOWLEDGE"));
        assertTrue(error.getMessage().contains("DELIVERY"));
        assertTrue(error.getMessage().contains("AI_AGENT"));
    }

}
