package cl.helvoca.onboarding;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoOnboardingServiceTest {

    @Test
    void parsesJsonInsideCodeFence() {
        JSONObject result = AutoOnboardingService.parseJsonObject("```json\n{\"businessName\":\"Don Pepe\"}\n```");
        assertEquals("Don Pepe", result.getString("businessName"));
    }

    @Test
    void sanitizesUnsupportedAndMalformedProposalValues() {
        JSONObject root = new JSONObject("""
                {
                  "businessName":"Don Pepe",
                  "timezone":"Not/AZone",
                  "language":"ES",
                  "services":[
                    {"name":"Reserva de mesa","durationMinutes":9999,"price":-5},
                    {"name":"","durationMinutes":20}
                  ],
                  "hours":[
                    {"dayOfWeek":1,"openTime":"12:00","closeTime":"23:00"},
                    {"dayOfWeek":9,"openTime":"09:00","closeTime":"18:00"},
                    {"dayOfWeek":2,"openTime":"18:00","closeTime":"10:00"}
                  ],
                  "knowledge":[{"title":"Estacionamiento","category":"Info","content":"Hay estacionamiento."}]
                }
                """);
        var source = new PublicBusinessSourceService.SourceReadResult("https://example.com", true, "texto", null);
        AutoOnboardingProposal proposal = AutoOnboardingService.sanitizeProposal(
                "Don Pepe", source, "America/Santiago", "es", root, List.of());

        assertEquals("America/Santiago", proposal.timezone());
        assertEquals("es", proposal.language());
        assertEquals(1, proposal.services().size());
        assertEquals(30, proposal.services().getFirst().durationMinutes());
        assertNull(proposal.services().getFirst().price());
        assertEquals(1, proposal.hours().size());
        assertEquals(1, proposal.knowledge().size());
    }

    @Test
    void addsWarningsWhenRequiredBusinessFactsAreMissing() {
        JSONObject root = new JSONObject("{\"businessName\":\"Don Pepe\",\"services\":[],\"hours\":[]}");
        var source = new PublicBusinessSourceService.SourceReadResult("https://example.com", true, "texto", null);
        AutoOnboardingProposal proposal = AutoOnboardingService.sanitizeProposal(
                "Don Pepe", source, "America/Santiago", "es", root, List.of());

        assertTrue(proposal.warnings().stream().anyMatch(w -> w.contains("servicios")));
        assertTrue(proposal.warnings().stream().anyMatch(w -> w.contains("horarios")));
    }
}
