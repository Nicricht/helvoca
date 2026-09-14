package cl.helvoca.ai.realtime;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeBusinessAdaptationContractTest {

    @Test
    void instructionsUseOnlyTheCurrentBusinessProfile() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID softwareId = UUID.randomUUID();

        Business clinic = business("Clínica Horizonte", "America/Santiago", "es");
        Business software = business("Northstar Software", "Europe/Madrid", "es");

        AiAgent clinicAgent = agent(
                clinicId,
                "Clara",
                "Hola, habla Clara de Clínica Horizonte. ¿En qué puedo orientarte?",
                "Prioriza orientación clara y agenda de especialidades. Evita presión comercial en temas de salud.");
        AiAgent softwareAgent = agent(
                softwareId,
                "Alex",
                "Hola, habla Alex de Northstar Software. ¿Qué necesitas resolver?",
                "Detecta el problema operativo, explica funcionalidades verificadas y propone una demo cuando corresponda.");

        BusinessRepository businesses = mock(BusinessRepository.class);
        when(businesses.findById(clinicId)).thenReturn(Optional.of(clinic));
        when(businesses.findById(softwareId)).thenReturn(Optional.of(software));

        AiAgentService aiAgents = mock(AiAgentService.class);
        when(aiAgents.runtime(clinicId)).thenReturn(clinicAgent);
        when(aiAgents.runtime(softwareId)).thenReturn(softwareAgent);

        RealtimeToolService service = service(businesses, aiAgents);

        String clinicInstructions = service.buildInstructions(context(clinicId));
        String softwareInstructions = service.buildInstructions(context(softwareId));

        assertTrue(clinicInstructions.contains("Clínica Horizonte"));
        assertTrue(clinicInstructions.contains("Clara"));
        assertTrue(clinicInstructions.contains("agenda de especialidades"));
        assertFalse(clinicInstructions.contains("Northstar Software"));
        assertFalse(clinicInstructions.contains("propone una demo"));

        assertTrue(softwareInstructions.contains("Northstar Software"));
        assertTrue(softwareInstructions.contains("Alex"));
        assertTrue(softwareInstructions.contains("propone una demo"));
        assertFalse(softwareInstructions.contains("Clínica Horizonte"));
        assertFalse(softwareInstructions.contains("agenda de especialidades"));
    }

    @Test
    void publishedToolsAreIsolatedPerBusinessCapabilities() throws Exception {
        UUID bookingBusinessId = UUID.randomUUID();
        UUID supportBusinessId = UUID.randomUUID();

        BusinessRepository businesses = mock(BusinessRepository.class);
        AiAgentService aiAgents = mock(AiAgentService.class);
        when(aiAgents.allowedToolNames(bookingBusinessId)).thenReturn(Set.of(
                "list_services", "check_booking_availability", "create_booking"));
        when(aiAgents.allowedToolNames(supportBusinessId)).thenReturn(Set.of(
                "search_knowledge", "create_request"));

        RealtimeToolService service = service(businesses, aiAgents);

        Set<String> bookingTools = toolNames(service.toolDefinitions(context(bookingBusinessId)));
        Set<String> supportTools = toolNames(service.toolDefinitions(context(supportBusinessId)));

        assertTrue(bookingTools.contains("list_services"));
        assertTrue(bookingTools.contains("create_booking"));
        assertFalse(bookingTools.contains("create_request"));

        assertTrue(supportTools.contains("search_knowledge"));
        assertTrue(supportTools.contains("create_request"));
        assertFalse(supportTools.contains("create_booking"));
    }

    private static RealtimeToolService service(BusinessRepository businesses, AiAgentService aiAgents) throws Exception {
        RealtimeToolService service = new RealtimeToolService(
                businesses,
                mock(CustomerRepository.class),
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                mock(CallSessionRepository.class),
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class));
        Field field = RealtimeToolService.class.getDeclaredField("aiAgents");
        field.setAccessible(true);
        field.set(service, aiAgents);
        return service;
    }

    private static Business business(String name, String timezone, String language) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone(timezone);
        business.setLanguage(language);
        return business;
    }

    private static AiAgent agent(UUID businessId, String name, String greeting, String instructions) {
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName(name);
        agent.setLanguage("es");
        agent.setGreeting(greeting);
        agent.setInstructions(instructions);
        return agent;
    }

    private static RealtimeCallContext context(UUID businessId) {
        return new RealtimeCallContext(
                UUID.randomUUID(),
                businessId,
                null,
                "+56911111111",
                "+56222222222",
                "MZ-" + UUID.randomUUID());
    }

    private static Set<String> toolNames(JSONArray definitions) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < definitions.length(); i++) {
            names.add(definitions.getJSONObject(i).getString("name"));
        }
        return names;
    }
}
