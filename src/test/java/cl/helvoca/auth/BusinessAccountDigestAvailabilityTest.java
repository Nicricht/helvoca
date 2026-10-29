package cl.helvoca.auth;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class BusinessAccountDigestAvailabilityTest {
    @Test
    void unavailableSha256ProviderFailsClosedInsteadOfReturningAPlaintextTokenHash() throws Exception {
        var fingerprint = BusinessAccountTokenService.class.getDeclaredMethod("fingerprint", String.class);
        fingerprint.setAccessible(true);
        try (MockedStatic<MessageDigest> digests = mockStatic(MessageDigest.class)) {
            digests.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("Simulated unavailable provider"));
            InvocationTargetException wrapped = assertThrows(InvocationTargetException.class,
                    () -> fingerprint.invoke(null, "a".repeat(43)));
            assertInstanceOf(IllegalStateException.class, wrapped.getCause());
            assertInstanceOf(NoSuchAlgorithmException.class, wrapped.getCause().getCause());
        }
    }
}
