package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupAuthorizationCodeServiceTest {

    @Test
    void rejectsHandoffWhenEmbeddedSignupIsNotReady() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(readiness);

        assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("opaque-code")));
    }

    @Test
    void acceptsButDoesNotRetainOrExposeAuthorizationCodeWhenReady() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        meta.setAppSecret("app-secret");
        meta.setVerifyToken("verify-token");
        meta.setWebhookValidationEnabled(true);

        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(readiness);
        var request = new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("  temporary-sensitive-code  ");

        var response = service.accept(request);

        assertEquals("temporary-sensitive-code", request.code());
        assertEquals("MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest[code=REDACTED]", request.toString());
        assertEquals("AUTHORIZATION_CODE_HANDOFF_VALIDATED", response.state());
        assertTrue(response.accepted());
        assertFalse(response.retained());
        assertTrue(response.exchangePending());
        assertFalse(response.toString().contains("temporary-sensitive-code"));
    }
}
