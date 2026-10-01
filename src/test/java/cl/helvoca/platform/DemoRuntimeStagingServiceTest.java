package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.operations.BusinessOperationCapability;
import cl.helvoca.operations.BusinessOperationCapabilityGrant;
import cl.helvoca.operations.BusinessOperationCapabilityRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoRuntimeStagingServiceTest {

    @Test
    void stagesApprovedBusinessDataWithoutChangingProviderVoiceOrEnablingOutboundEffects() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        Business runtime = new Business();
        ReflectionTestUtils.setField(runtime, "id", businessId);
        runtime.setName("Old Runtime");
        runtime.setMode(BusinessMode.DEMO);
        runtime.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(runtime));
        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of());

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Old Agent");
        agent.setLanguage("es");
        agent.setVoice("certified-provider-voice");
        agent.setGreeting("Old greeting");
        agent.setActive(true);
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));

        DemoProfile profile = profile();
        new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext())
                .stage(profile, businessId);

        assertEquals("Sushi Akira", runtime.getName());
        assertEquals("certified-provider-voice", agent.getVoice(), "provider/runtime voice must be preserved");
        assertEquals("Hola Akira", agent.getGreeting());
        assertTrue(agent.getCapabilities().contains(AiCapability.CREATE_ORDER));
        assertFalse(agent.getCapabilities().contains(AiCapability.SEND_WHATSAPP_OPERATION));
        assertFalse(agent.getCapabilities().contains(AiCapability.TRANSFER_TO_HUMAN));

        verify(grants).deleteAllByBusinessId(businessId);
        verify(grants).flush();
        verify(grants).saveAll(argThat(values -> {
            boolean catalogGrant = false;
            boolean orderGrant = false;
            for (BusinessOperationCapabilityGrant value : values) {
                catalogGrant |= value.getCapability() == BusinessOperationCapability.CATALOG;
                orderGrant |= value.getCapability() == BusinessOperationCapability.ORDER;
            }
            return catalogGrant && orderGrant;
        }));

        verify(hours).deleteAllByBusinessId(businessId);
        verify(hours).saveAll(argThat(values -> values.iterator().hasNext()));
        verify(knowledge).saveAll(argThat(values -> values.iterator().hasNext()));
        verify(catalog).saveAll(argThat(values -> values.iterator().hasNext()));
    }

    @Test
    void copiesApprovedConfigurationToFreshPilotWithoutProviderCredentials() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        Business pilot = new Business();
        ReflectionTestUtils.setField(pilot, "id", businessId);
        pilot.setMode(BusinessMode.PILOT);
        pilot.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(pilot));
        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of());
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext());

        service.stagePilot(profile(), businessId);

        assertEquals("Sushi Akira", pilot.getName());
        verify(businesses).save(pilot);
        verify(hours).deleteAllByBusinessId(businessId);
        verify(grants).deleteAllByBusinessId(businessId);
    }

    @Test
    void pilotCopyRejectsCustomerTarget() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        Business customer = new Business();
        ReflectionTestUtils.setField(customer, "id", businessId);
        customer.setMode(BusinessMode.CUSTOMER);
        customer.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(customer));

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses,
                mock(CatalogItemRepository.class),
                mock(BusinessHourRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(AiAgentRepository.class),
                mock(BusinessOperationCapabilityRepository.class),
                new TenantDatabaseContext());

        assertThrows(IllegalStateException.class, () -> service.stagePilot(profile(), businessId));
    }

    @Test
    void refusesPaymentCapabilityAndNonDemoRuntime() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        Business customer = new Business();
        ReflectionTestUtils.setField(customer, "id", businessId);
        customer.setMode(BusinessMode.CUSTOMER);
        customer.setStatus(BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(customer));

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext());

        assertThrows(IllegalStateException.class, () -> service.stage(profile(), businessId));
        verifyNoInteractions(catalog, hours, knowledge, agents, grants);
    }

    @Test
    void refusesStagingAnyRuntimeOtherThanTheServerOwnedDemoRuntime() {
        UUID configuredRuntime = UUID.randomUUID();
        UUID requestedRuntime = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(configuredRuntime.toString());

        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants,
                new TenantDatabaseContext(), properties);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.stage(profile(), requestedRuntime));

        assertTrue(error.getMessage().contains("server-owned DEMO runtime"));
        verifyNoInteractions(businesses, catalog, hours, knowledge, agents, grants);
    }

    @Test
    void refusesLivePaymentAndUnknownCapabilitiesEvenOnValidDemoRuntime() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        Business runtime = demoRuntime(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(runtime));
        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of());
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext());

        DemoProfile payment = minimalProfile();
        payment.setCapabilities(List.of("PAYMENT"));
        IllegalArgumentException paymentError = assertThrows(
                IllegalArgumentException.class,
                () -> service.stage(payment, businessId));
        assertTrue(paymentError.getMessage().contains("Live payment"));

        DemoProfile unknown = minimalProfile();
        unknown.setCapabilities(List.of("ROOT_ACCESS"));
        IllegalArgumentException unknownError = assertThrows(
                IllegalArgumentException.class,
                () -> service.stage(unknown, businessId));
        assertTrue(unknownError.getMessage().contains("Unsupported demo capability"));

        verify(agents, never()).save(any());
        verify(grants, never()).saveAll(any());
    }

    @Test
    void stagesStructuredCatalogHoursKnowledgeAndAllSafeCapabilitiesIdempotently() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        Business runtime = demoRuntime(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(runtime));

        CatalogItem existingCatalog = new CatalogItem();
        existingCatalog.setBusinessId(businessId);
        existingCatalog.setKind(CatalogItem.Kind.PRODUCT);
        existingCatalog.setName("Sake");
        existingCatalog.setPrice(BigDecimal.ONE);
        existingCatalog.setCurrency("CLP");
        existingCatalog.setActive(true);
        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(existingCatalog));

        KnowledgeItem existingKnowledge = new KnowledgeItem();
        existingKnowledge.setBusinessId(businessId);
        existingKnowledge.setTitle("Despacho");
        existingKnowledge.setContent("Antiguo");
        existingKnowledge.setActive(true);
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of(existingKnowledge));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());

        DemoProfile profile = minimalProfile();
        profile.setCatalog(Map.of("items", List.of(
                Map.of(
                        "name", "Sake",
                        "kind", "PRODUCT",
                        "description", "Botella",
                        "price", "5990",
                        "currency", "clp",
                        "active", true,
                        "metadata", Map.of("size", "750ml")),
                Map.of(
                        "name", "Reserva mesa",
                        "kind", "SERVICE",
                        "durationMinutes", "45",
                        "active", false))));
        profile.setHours(Map.of("items", List.of(
                Map.of("dayOfWeek", 2, "openTime", "10:00", "closeTime", "14:00"),
                Map.of("dayOfWeek", "miércoles", "openTime", "15:00", "closeTime", "20:00"))));
        profile.setKnowledge(Map.of("items", List.of(
                Map.of("title", "Despacho", "category", "Logística", "content", "Actualizado", "active", true),
                Map.of("title", "Pagos", "content", "Solo sandbox", "active", false))));
        List<String> capabilities = new ArrayList<>();
        capabilities.add("BOOKING");
        capabilities.add("REQUEST");
        capabilities.add("ORDER");
        capabilities.add("DELIVERY");
        capabilities.add("QUOTE");
        capabilities.add("LEAD");
        capabilities.add("CATALOG");
        capabilities.add("");
        capabilities.add(null);
        profile.setCapabilities(capabilities);

        new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext())
                .stage(profile, businessId);

        assertEquals("Sushi Akira", runtime.getName());
        assertEquals(new BigDecimal("5990"), existingCatalog.getPrice());
        assertEquals("CLP", existingCatalog.getCurrency());
        assertTrue(existingCatalog.getMetadataJson().contains("750ml"));
        assertEquals("Actualizado", existingKnowledge.getContent());

        verify(hours).saveAll(argThat(values -> {
            int count = 0;
            for (Object ignored : values) count++;
            return count == 2;
        }));
        verify(catalog).saveAll(argThat(values -> {
            int count = 0;
            boolean serviceFound = false;
            for (CatalogItem item : values) {
                count++;
                serviceFound |= item.getKind() == CatalogItem.Kind.SERVICE
                        && Integer.valueOf(45).equals(item.getDurationMinutes())
                        && !item.isActive();
            }
            return count == 2 && serviceFound;
        }));
        verify(knowledge).saveAll(argThat(values -> {
            int count = 0;
            for (KnowledgeItem ignored : values) count++;
            return count == 2;
        }));
        verify(agents).save(argThat(agent ->
                agent.getCapabilities().contains(AiCapability.CREATE_BOOKING)
                        && agent.getCapabilities().contains(AiCapability.CREATE_REQUEST)
                        && agent.getCapabilities().contains(AiCapability.CREATE_ORDER)
                        && !agent.getCapabilities().contains(AiCapability.SEND_WHATSAPP_OPERATION)
                        && !agent.getCapabilities().contains(AiCapability.TRANSFER_TO_HUMAN)));
        verify(grants).saveAll(argThat(values -> {
            boolean order = false;
            boolean delivery = false;
            boolean quote = false;
            boolean lead = false;
            boolean catalogGrant = false;
            for (BusinessOperationCapabilityGrant value : values) {
                order |= value.getCapability() == BusinessOperationCapability.ORDER;
                delivery |= value.getCapability() == BusinessOperationCapability.DELIVERY;
                quote |= value.getCapability() == BusinessOperationCapability.QUOTE;
                lead |= value.getCapability() == BusinessOperationCapability.LEAD;
                catalogGrant |= value.getCapability() == BusinessOperationCapability.CATALOG;
            }
            return order && delivery && quote && lead && catalogGrant;
        }));
    }

    @Test
    void malformedDemoConfigurationFailsClosedInsteadOfGuessing() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessOperationCapabilityRepository grants = mock(BusinessOperationCapabilityRepository.class);

        when(businesses.findById(businessId)).thenReturn(Optional.of(demoRuntime(businessId)));
        when(catalog.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of());

        DemoRuntimeStagingService service = new DemoRuntimeStagingService(
                businesses, catalog, hours, knowledge, agents, grants, new TenantDatabaseContext());

        DemoProfile negativePrice = minimalProfile();
        negativePrice.setCatalog(Map.of("items", List.of(Map.of("name", "Bad", "price", "-1"))));
        assertThrows(IllegalArgumentException.class, () -> service.stage(negativePrice, businessId));

        DemoProfile badKind = minimalProfile();
        badKind.setCatalog(Map.of("items", List.of(Map.of("name", "Bad", "kind", "UNKNOWN"))));
        assertThrows(IllegalArgumentException.class, () -> service.stage(badKind, businessId));

        DemoProfile badHours = minimalProfile();
        badHours.setHours(Map.of("monday", "20:00-10:00"));
        assertThrows(IllegalArgumentException.class, () -> service.stage(badHours, businessId));

        DemoProfile badKnowledge = minimalProfile();
        badKnowledge.setKnowledge(Map.of("items", List.of(Map.of("title", "Missing content"))));
        assertThrows(IllegalArgumentException.class, () -> service.stage(badKnowledge, businessId));
    }

    private static Business demoRuntime(UUID businessId) {
        Business runtime = new Business();
        ReflectionTestUtils.setField(runtime, "id", businessId);
        runtime.setName("Old Runtime");
        runtime.setMode(BusinessMode.DEMO);
        runtime.setStatus(BusinessStatus.ACTIVE);
        return runtime;
    }

    private static DemoProfile minimalProfile() {
        DemoProfile profile = profile();
        profile.setCatalog(Map.of());
        profile.setHours(Map.of());
        profile.setKnowledge(Map.of());
        profile.setCapabilities(List.of());
        return profile;
    }

    private static DemoProfile profile() {
        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T05:00:00Z"));
        profile.setDisplayName("Sushi Akira");
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setGreeting("Hola Akira");
        profile.setInstructions("No inventes precios.");
        profile.setCatalog(Map.of("items", List.of(Map.of(
                "name", "Sake",
                "kind", "PRODUCT",
                "price", 4990,
                "currency", "CLP"))));
        profile.setHours(Map.of("monday", "12:00-22:00"));
        profile.setKnowledge(Map.of("faq", List.of("Despacho disponible")));
        profile.setCapabilities(List.of("ORDER"));
        profile.setSourceMetadata(Map.of("source", "manual"));
        return profile;
    }
}
