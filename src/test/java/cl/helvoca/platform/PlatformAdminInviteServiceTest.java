package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformAdminInviteServiceTest {
    @Test
    void rejectsWeakOrMalformedInvitationTokens() {
        assertFalse(PlatformAdminInviteService.validToken(null));
        assertFalse(PlatformAdminInviteService.validToken("short"));
        assertFalse(PlatformAdminInviteService.validToken("!".repeat(43)));
        assertTrue(PlatformAdminInviteService.validToken("A".repeat(43)));
    }

    @Test
    void fingerprintsInvitationTokensWithoutPersistingPlaintext() {
        String token = "C".repeat(43);
        String digest = PlatformAdminInviteService.fingerprint(token);
        assertNotEquals(token, digest);
        assertEquals(64, digest.length());
        assertEquals(digest, PlatformAdminInviteService.fingerprint(token));
        assertNotEquals(digest, PlatformAdminInviteService.fingerprint("D".repeat(43)));
    }
}
