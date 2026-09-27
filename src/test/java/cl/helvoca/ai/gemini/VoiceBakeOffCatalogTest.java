package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceBakeOffCatalogTest {

    @Test
    void curatedCandidatesAreStableAndCaseInsensitive() {
        assertEquals(List.of("Leda", "Sadachbia", "Laomedeia", "Achird", "Aoede", "Sulafat"),
                VoiceBakeOffCatalog.candidates().stream()
                        .map(VoiceBakeOffCatalog.Candidate::voiceName)
                        .toList());
        assertEquals("Sadachbia", VoiceBakeOffCatalog.normalize(" sadachbia "));
        assertTrue(VoiceBakeOffCatalog.allowed("LEDA"));
        assertFalse(VoiceBakeOffCatalog.allowed("Despina"));
        assertNull(VoiceBakeOffCatalog.normalize("unknown"));
        assertNull(VoiceBakeOffCatalog.normalize(null));
    }

    @Test
    void providerTraitsStayAttachedToCandidates() {
        assertEquals("Youthful", VoiceBakeOffCatalog.find("Leda").orElseThrow().providerTrait());
        assertEquals("Lively", VoiceBakeOffCatalog.find("Sadachbia").orElseThrow().providerTrait());
        assertEquals("Upbeat", VoiceBakeOffCatalog.find("Laomedeia").orElseThrow().providerTrait());
        assertEquals("Friendly", VoiceBakeOffCatalog.find("Achird").orElseThrow().providerTrait());
        assertEquals("Breezy", VoiceBakeOffCatalog.find("Aoede").orElseThrow().providerTrait());
        assertEquals("Warm", VoiceBakeOffCatalog.find("Sulafat").orElseThrow().providerTrait());
    }
}
