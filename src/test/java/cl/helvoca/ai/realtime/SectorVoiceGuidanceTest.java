package cl.helvoca.ai.realtime;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectorVoiceGuidanceTest {

    @Test
    void everySupportedBusinessUsesOnlyOneSmallSafeSectorRule() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("clinic", "CLÍNICA"),
                Map.entry("dental", "DENTISTA"),
                Map.entry("veterinary", "VETERINARIA"),
                Map.entry("salon", "PELUQUERÍA"),
                Map.entry("restaurant", "RESTAURANTE"),
                Map.entry("hardware_store", "FERRETERÍA"),
                Map.entry("store", "TIENDA"),
                Map.entry("workshop", "TALLER"),
                Map.entry("legal", "JURÍDICO"),
                Map.entry("real_estate", "INMOBILIARIA"),
                Map.entry("gym", "GIMNASIO"),
                Map.entry("hospitality", "HOTEL"));

        assertEquals(12, expected.size());
        expected.forEach((preset, label) -> {
            String instruction = SectorVoiceGuidance.forPreset(preset);
            assertTrue(instruction.contains("SECTOR " + label), preset);
            assertTrue(instruction.length() <= 260, "sector runtime setup is too long: " + preset);
            assertFalse(instruction.contains("CLP"), "no invented prices: " + preset);
            assertFalse(instruction.contains("09:00"), "no invented hours: " + preset);
        });
    }

    @Test
    void legacyUnknownAndUnsetPresetsAddNoInstructions() {
        assertEquals("", SectorVoiceGuidance.forPreset(null));
        assertEquals("", SectorVoiceGuidance.forPreset(""));
        assertEquals("", SectorVoiceGuidance.forPreset("unknown-sector"));
        assertEquals("", SectorVoiceGuidance.forPreset("professional"));
        assertEquals("", SectorVoiceGuidance.forPreset("services"));
    }

    @Test
    void runtimeGuidanceIsOptInAndIsolatedToSavedTenantProfile() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        AiAgentService agents = mock(AiAgentService.class);
        when(businesses.findById(clinicId)).thenReturn(Optional.of(business("Centro Médico")));
        when(businesses.findById(hotelId)).thenReturn(Optional.of(business("Hospedaje Austral")));
        when(businesses.findById(unknownId)).thenReturn(Optional.of(business("Otro negocio")));
        when(profiles.findById(clinicId)).thenReturn(Optional.of(profile(clinicId, "clinic")));
        when(profiles.findById(hotelId)).thenReturn(Optional.of(profile(hotelId, "hospitality")));
        when(profiles.findById(unknownId)).thenReturn(Optional.empty());
        when(agents.runtime(clinicId)).thenReturn(agent(clinicId));
        when(agents.runtime(hotelId)).thenReturn(agent(hotelId));
        when(agents.runtime(unknownId)).thenReturn(agent(unknownId));

        RealtimeToolService service = new RealtimeToolService(
                businesses, mock(CustomerRepository.class), mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class), mock(BookingRepository.class),
                mock(CallSessionRepository.class), mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class), mock(UnansweredQuestionService.class));
        inject(service, "aiAgents", agents);
        inject(service, "businessProfiles", profiles);

        assertFalse(service.buildInstructions(context(clinicId)).contains("SECTOR CLÍNICA"),
                "default OFF protects Aurora and existing voice tenants");
        inject(service, "sectorGuidanceEnabled", true);
        String clinic = service.buildInstructions(context(clinicId));
        String hotel = service.buildInstructions(context(hotelId));
        String unknown = service.buildInstructions(context(unknownId));
        assertTrue(clinic.contains("SECTOR CLÍNICA"));
        assertFalse(clinic.contains("SECTOR HOTEL"));
        assertFalse(clinic.contains("Hospedaje Austral"));
        assertTrue(hotel.contains("SECTOR HOTEL"));
        assertFalse(hotel.contains("SECTOR CLÍNICA"));
        assertFalse(hotel.contains("Centro Médico"));
        assertFalse(unknown.contains("SECTOR CLÍNICA"));
        assertFalse(unknown.contains("SECTOR HOTEL"));
        // No repository binding must fail closed without adding a fabricated sector.
        inject(service, "businessProfiles", null);
        assertFalse(service.buildInstructions(context(clinicId)).contains("SECTOR CLÍNICA"));
    }

    private static Business business(String name) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        return business;
    }

    private static BusinessProfile profile(UUID businessId, String preset) {
        BusinessProfile result = new BusinessProfile();
        result.setBusinessId(businessId);
        result.setPresetKey(preset);
        return result;
    }

    private static AiAgent agent(UUID id) {
        AiAgent agent = new AiAgent();
        agent.setBusinessId(id);
        agent.setName("RecepVoz");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setInstructions("Información oficial únicamente.");
        return agent;
    }

    private static RealtimeCallContext context(UUID id) {
        return new RealtimeCallContext(UUID.randomUUID(), id, null,
                "+56911111111", "+56222222222", "MZ-" + UUID.randomUUID());
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field reflect = target.getClass().getDeclaredField(field);
        reflect.setAccessible(true);
        reflect.set(target, value);
    }
}
