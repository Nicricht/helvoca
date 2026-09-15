package cl.helvoca.request;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BusinessRequestInvariantTest {

    @Test
    void whatsappConversationIdIsNeverPersistedAsLegacyCallId() {
        BusinessRequest request = new BusinessRequest();
        request.setCallId(UUID.randomUUID());
        request.setSource(RequestSource.AI_WHATSAPP);

        request.prePersist();

        assertNull(request.getCallId());
    }

    @Test
    void voiceRequestKeepsRealCallId() {
        UUID callId = UUID.randomUUID();
        BusinessRequest request = new BusinessRequest();
        request.setCallId(callId);
        request.setSource(RequestSource.AI_CALL);

        request.prePersist();

        assertEquals(callId, request.getCallId());
    }
}
