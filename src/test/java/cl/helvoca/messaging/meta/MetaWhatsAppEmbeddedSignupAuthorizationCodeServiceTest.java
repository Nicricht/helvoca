package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupAuthorizationCodeServiceTest {

    @Test
    void rejectsHandoffWhenEmbeddedSignupIsNotReady() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);

        assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("opaque-code")));
        verifyNoInteractions(exchange, debug, sharedWabas);
    }

    @Test
    void exchangesValidatesAndDiscoversWabasWithoutRetainingOrExposingTokens() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);

        when(exchange.exchange("temporary-sensitive-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken(
                        "oauth-user-sensitive-token", "bearer", 3600L));
        when(debug.debug("oauth-user-sensitive-token", "system-user-secret"))
                .thenReturn(validDebug("123456789"));
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(
                                new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                        "1906385232743451",
                                        "Primary WABA",
                                        "USD",
                                        "1",
                                        "namespace-one")),
                        "next-page-cursor"));

        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);
        var request = new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                "  temporary-sensitive-code  ");

        var response = service.accept(request);

        assertEquals("temporary-sensitive-code", request.code());
        assertEquals(
                "MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest[code=REDACTED]",
                request.toString());
        assertEquals("AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED", response.state());
        assertTrue(response.accepted());
        assertFalse(response.retained());
        assertFalse(response.exchangePending());
        assertEquals(1, response.wabas().size());
        assertEquals("1906385232743451", response.wabas().get(0).id());
        assertEquals("Primary WABA", response.wabas().get(0).name());
        assertEquals("next-page-cursor", response.wabaAfterCursor());
        assertFalse(response.toString().contains("temporary-sensitive-code"));
        assertFalse(response.toString().contains("oauth-user-sensitive-token"));
        assertFalse(response.toString().contains("system-user-secret"));

        verify(exchange).exchange("temporary-sensitive-code");
        verify(debug).debug("oauth-user-sensitive-token", "system-user-secret");
        verify(sharedWabas).list("112233445566778", "system-user-secret");
    }

    @Test
    void acceptsEmptySharedWabaResultWithoutPersistingAnything() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret"))
                .thenReturn(validDebug("123456789"));
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), null));

        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);

        var response = service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("temporary-code"));

        assertTrue(response.accepted());
        assertTrue(response.wabas().isEmpty());
        assertNull(response.wabaAfterCursor());
    }

    @Test
    void rejectsInvalidDebugResultBeforeWabaDiscovery() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        false, "123456789", "USER", null, null, List.of(), List.of()));

        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(
                        new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("temporary-code")));

        assertEquals("META_EMBEDDED_SIGNUP_TOKEN_INVALID", error.getMessage());
        verifyNoInteractions(sharedWabas);
    }

    @Test
    void rejectsTokenIssuedForAnotherMetaAppBeforeWabaDiscovery() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret"))
                .thenReturn(validDebug("different-app"));

        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(
                        new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("temporary-code")));

        assertEquals("META_EMBEDDED_SIGNUP_TOKEN_APP_MISMATCH", error.getMessage());
        verifyNoInteractions(sharedWabas);
    }

    @Test
    void rejectsTokenWithoutWhatsAppBusinessManagementScopeBeforeWabaDiscovery() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        true,
                        "123456789",
                        "USER",
                        1790000000L,
                        1800000000L,
                        List.of("business_management", "public_profile"),
                        List.of()));

        var service = new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(
                        new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("temporary-code")));

        assertEquals("META_EMBEDDED_SIGNUP_REQUIRED_SCOPE_MISSING", error.getMessage());
        verifyNoInteractions(sharedWabas);
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        meta.setEmbeddedSignupBusinessId("112233445566778");
        meta.setEmbeddedSignupSystemUserId("998877665544332");
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        meta.setEmbeddedSignupAdminSystemUserAccessToken("admin-system-user-secret");
        meta.setAppSecret("app-secret");
        meta.setVerifyToken("verify-token");
        meta.setWebhookValidationEnabled(true);
        return meta;
    }

    private static MetaWhatsAppEmbeddedSignupTokenDebugResult validDebug(String appId) {
        return new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                true,
                appId,
                "USER",
                1790000000L,
                1800000000L,
                List.of("whatsapp_business_management", "business_management"),
                List.of("111111111111111"));
    }
}
