package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoRuntimePreparationServiceTest {

    @Test
    void stagesApprovedProfileWithoutTouchingChannelsOrHistoricalOperations() {
        UUID runtimeId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessProfileRepository businessProfiles = mock(BusinessProfileRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);

        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", runtimeId);
        business.setName("Old Demo");
        when(businesses.findById(runtimeId)).thenReturn(Optional.of(business));

        AiAgent existingAgent = new AiAgent();
        existingAgent.setBusinessId(runtimeId);
        existingAgent.setName("Old Agent");
        existingAgent.setLanguage("es");
        existingAgent.setGreeting("Old greeting");
        existingAgent.setVoice("seductive_female");
        existingAgent.setActive(true);
        when(agents.findByBusinessId(runtimeId)).thenReturn(Optional.of(existingAgent));

        when(catalog.findAllByBusinessIdOrderByNameAsc(runtimeId)).thenReturn(List.of());
        when(services.findAllByBusinessIdOrderByNameAsc(runtimeId)).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(runtimeId)).thenReturn(List.of());
        when(businessProfiles.findById(runtimeId)).thenReturn(Optional.empty());
        when(services.save(any())).thenAnswer(invocation -> {
            ServiceItem item = invocation.getArgument(0);
            if (item.getId() == null) ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            return item;
        });
        when(catalog.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledge.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hours.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agents.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(businesses.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DemoProfile profile = profile(profileId,
                Map.of("items", List.of(
                        Map.of("kind", "PRODUCT", "name", "Sushi 20 piezas", "price", 12990, "currency", "CLP"),
                        Map.of("kind", "SERVICE", "name", "Reserva mesa", "price", 0, "currency", "CLP",
                                "durationMinutes", 60))),
                Map.of("intervals", List.of(
                        Map.of("dayOfWeek", 1, "openTime", "12:00", "closeTime", "22:00"))),
                Map.of("items", List.of(
                        Map.of("title", "Despacho", "category", "Política", "content", "Despacho hasta 5 km."))),
                List.of("ORDER", "BOOKING"));

        DemoRuntimePreparationService service = new DemoRuntimePreparationService(
                businesses, businessProfiles, agents, catalog, services, hours, knowledge);

        DemoRuntimePreparationService.StageResult result = service.stage(runtimeId, profile);

        assertNotNull(result.revision());
        assertEquals(64, result.revision().length());

        assertEquals("Sushi Akira", business.getName());
        assertEquals("America/Santiago", business.getTimezone());
        assertEquals("es", business.getLanguage());

        // The certified base voice belongs to the runtime and is preserved.
        assertEquals("seductive_female", existingAgent.getVoice());
        assertEquals("Sushi Akira", existingAgent.getName());
        assertEquals("Hola, gracias por llamar a Sushi Akira.", existingAgent.getGreeting());
        assertTrue(existingAgent.getCapabilities().contains(AiCapability.CREATE_ORDER));
        assertTrue(existingAgent.getCapabilities().contains(AiCapability.CREATE_BOOKING));
        assertFalse(existingAgent.getCapabilities().contains(AiCapability.SEND_WHATSAPP_OPERATION));
        assertFalse(existingAgent.getCapabilities().contains(AiCapability.TRANSFER_TO_HUMAN));

        verify(hours).deleteAllByBusinessId(runtimeId);
        verify(hours).save(argThat(hour ->
                hour.getBusinessId().equals(runtimeId)
                        && hour.getDayOfWeek() == 1
                        && LocalTime.NOON.equals(hour.getOpenTime())
                        && LocalTime.of(22, 0).equals(hour.getCloseTime())));
        verify(knowledge).save(argThat(item ->
                item.getBusinessId().equals(runtimeId)
                        && "Despacho".equals(item.getTitle())
                        && item.isActive()));
        verify(services).save(argThat(item ->
                item.getBusinessId().equals(runtimeId)
                        && "Reserva mesa".equals(item.getName())
                        && item.getDurationMinutes() == 60
                        && item.isActive()));
        verify(catalog, atLeast(2)).save(any(CatalogItem.class));
        verify(businessProfiles).saveAndFlush(argThat(value ->
                value.getBusinessId().equals(runtimeId)
                        && Boolean.TRUE.equals(value.getSellsProducts())
                        && Boolean.TRUE.equals(value.getSellsServices())
                        && Boolean.TRUE.equals(value.getUsesReservations())));

        // This service has no telephony, WhatsApp, customer or operation dependencies by construction.
        assertEquals(7, DemoRuntimePreparationService.class.getDeclaredFields().length);
    }

    @Test
    void validatesWholeSnapshotBeforeMutatingRuntime() {
        UUID runtimeId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessProfileRepository businessProfiles = mock(BusinessProfileRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);

        DemoProfile profile = profile(UUID.randomUUID(),
                Map.of("items", List.of(Map.of(
                        "kind", "PRODUCT", "name", "Producto roto", "price", -1, "currency", "CLP"))),
                Map.of(), Map.of(), List.of("ORDER"));

        DemoRuntimePreparationService service = new DemoRuntimePreparationService(
                businesses, businessProfiles, agents, catalog, services, hours, knowledge);

        assertThrows(IllegalArgumentException.class, () -> service.stage(runtimeId, profile));

        verifyNoInteractions(businesses, businessProfiles, agents, catalog, services, hours, knowledge);
    }

    @Test
    void omittedApprovedItemsDeactivateOldDemoConfigurationInsteadOfDeletingHistory() {
        UUID runtimeId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessProfileRepository businessProfiles = mock(BusinessProfileRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);

        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", runtimeId);
        when(businesses.findById(runtimeId)).thenReturn(Optional.of(business));
        when(businessProfiles.findById(runtimeId)).thenReturn(Optional.of(profileEntity(runtimeId)));
        when(agents.findByBusinessId(runtimeId)).thenReturn(Optional.empty());
        when(agents.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(businesses.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogItem oldProduct = new CatalogItem();
        ReflectionTestUtils.setField(oldProduct, "id", UUID.randomUUID());
        oldProduct.setBusinessId(runtimeId);
        oldProduct.setKind(CatalogItem.Kind.PRODUCT);
        oldProduct.setName("Producto viejo");
        oldProduct.setActive(true);
        when(catalog.findAllByBusinessIdOrderByNameAsc(runtimeId)).thenReturn(List.of(oldProduct));

        ServiceItem oldService = new ServiceItem();
        ReflectionTestUtils.setField(oldService, "id", UUID.randomUUID());
        oldService.setBusinessId(runtimeId);
        oldService.setName("Servicio viejo");
        oldService.setDurationMinutes(30);
        oldService.setActive(true);
        when(services.findAllByBusinessIdOrderByNameAsc(runtimeId)).thenReturn(List.of(oldService));

        KnowledgeItem oldKnowledge = new KnowledgeItem();
        ReflectionTestUtils.setField(oldKnowledge, "id", UUID.randomUUID());
        oldKnowledge.setBusinessId(runtimeId);
        oldKnowledge.setTitle("FAQ vieja");
        oldKnowledge.setContent("Viejo");
        oldKnowledge.setActive(true);
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(runtimeId)).thenReturn(List.of(oldKnowledge));

        DemoProfile profile = profile(UUID.randomUUID(), Map.of(), Map.of(), Map.of(), List.of());

        DemoRuntimePreparationService service = new DemoRuntimePreparationService(
                businesses, businessProfiles, agents, catalog, services, hours, knowledge);

        service.stage(runtimeId, profile);

        assertFalse(oldProduct.isActive());
        assertFalse(oldService.isActive());
        assertFalse(oldKnowledge.isActive());
        verify(catalog).save(oldProduct);
        verify(services).save(oldService);
        verify(knowledge).save(oldKnowledge);
    }

    private static DemoProfile profile(UUID id,
                                       Map<String, Object> catalog,
                                       Map<String, Object> hours,
                                       Map<String, Object> knowledge,
                                       List<String> capabilities) {
        DemoProfile profile = new DemoProfile();
        ReflectionTestUtils.setField(profile, "id", id);
        profile.setDisplayName("Sushi Akira Demo");
        profile.setBusinessName("Sushi Akira");
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setCatalog(catalog);
        profile.setHours(hours);
        profile.setKnowledge(knowledge);
        profile.setGreeting("Hola, gracias por llamar a Sushi Akira.");
        profile.setInstructions("No inventes precios.");
        profile.setCapabilities(capabilities);
        ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T05:00:00Z"));
        return profile;
    }

    private static BusinessProfile profileEntity(UUID id) {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(id);
        return profile;
    }
}
