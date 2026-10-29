package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformAdminInviteServiceTest {

    @Test
    void unavailableSha256ProviderMustFailClosedWithoutWritingASecret() {
        try (org.mockito.MockedStatic<java.security.MessageDigest> mocked =
                org.mockito.Mockito.mockStatic(java.security.MessageDigest.class)) {
            mocked.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException("fixture"));
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> PlatformAdminInviteService.fingerprint("A".repeat(43)));
            assertEquals("SHA-256 is unavailable", failure.getMessage());
        }
    }

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
