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
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.operations.BusinessOperationCapability;
import cl.helvoca.operations.BusinessOperationCapabilityGrant;
import cl.helvoca.operations.BusinessOperationCapabilityRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
