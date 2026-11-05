package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupAuthorizationCodeServiceTest {

    private static final String WABA_ID = "1906385232743451";
    private static final String OTHER_WABA_ID = "1972385232742141";

    @Test
    void rejectsHandoffWhenEmbeddedSignupIsNotReadyBeforeCallingMeta() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var wabaAccess = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        var service = service(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, null, null);

        assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                        "opaque-code", WABA_ID)));
        verifyNoInteractions(exchange, debug, sharedWabas, assignedUsers, wabaAccess, wabaAuthorization);
    }

    @Test
    void bindsOnlyTheWabaProvenByCustomerTokenToTheCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var wabaAccess = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        when(exchange.exchange("temporary-sensitive-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken(
                        "oauth-user-sensitive-token", "bearer", 3600L));
        when(debug.debug("oauth-user-sensitive-token", "system-user-secret"))
                .thenReturn(validDebug());
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(
                                new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                        WABA_ID, "Primary WABA", "USD", "1", "namespace-one"),
                                new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                        OTHER_WABA_ID, "Other customer WABA", "CLP", "74", null)),
                        "next-page-cursor"));
        when(assignedUsers.fetch(WABA_ID, "112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignedUsersResult(
                        List.of(new MetaWhatsAppEmbeddedSignupAssignedUsersResult.AssignedUser(
                                "998877665544332", "RecepVoz System User", List.of("MANAGE")))));

        var service = service(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, tenantProvider, audit);
        var request = new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                "  temporary-sensitive-code  ", WABA_ID);

        var response = service.accept(request);

        assertEquals("temporary-sensitive-code", request.code());
        assertEquals(WABA_ID, request.wabaId());
        assertEquals(
                "MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest[code=REDACTED, wabaId="
                        + WABA_ID + "]",
                request.toString());
        assertEquals("AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED", response.state());
        assertTrue(response.accepted());
        assertFalse(response.retained());
        assertFalse(response.exchangePending());
        assertEquals(1, response.wabas().size());
        assertEquals(WABA_ID, response.wabas().getFirst().id());
        assertEquals("Primary WABA", response.wabas().getFirst().name());
        assertTrue(response.wabas().getFirst().systemUserAssigned());
        assertNull(response.wabaAfterCursor());
        assertFalse(response.toString().contains("temporary-sensitive-code"));
        assertFalse(response.toString().contains("oauth-user-sensitive-token"));
        assertFalse(response.toString().contains("system-user-secret"));
        assertFalse(response.toString().contains(OTHER_WABA_ID));

        var ordered = inOrder(exchange, debug, wabaAccess, sharedWabas, wabaAuthorization);
        ordered.verify(exchange).exchange("temporary-sensitive-code");
        ordered.verify(debug).debug("oauth-user-sensitive-token", "system-user-secret");
        ordered.verify(wabaAccess).requireAccess(WABA_ID, "oauth-user-sensitive-token");
        ordered.verify(sharedWabas).list("112233445566778", "system-user-secret");
        ordered.verify(wabaAuthorization).authorize(WABA_ID);

        verify(assignedUsers).fetch(WABA_ID, "112233445566778", "system-user-secret");
        verifyNoMoreInteractions(assignedUsers);
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_AUTHORIZATION_ACCEPTED"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                isNull(),
                eq(Map.of("accepted", true, "wabaCandidates", 1)));
    }

    @Test
    void rejectsWabaThatWasNotSharedWithRecepVozAfterCustomerTokenProof() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var wabaAccess = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        doNothing().when(wabaAccess).requireAccess(OTHER_WABA_ID, "oauth-token");
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), null));

        var service = service(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                        "temporary-code", OTHER_WABA_ID)));

        assertEquals("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED", error.getMessage());
        verifyNoInteractions(assignedUsers, wabaAuthorization);
    }

    @Test
    void rejectsCustomerTokenWithoutAccessToTheSelectedWabaBeforeProviderLookup() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var wabaAccess = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        doThrow(new ConflictException("META_CUSTOMER_WABA_ACCESS_DENIED"))
                .when(wabaAccess).requireAccess(WABA_ID, "oauth-token");

        var service = service(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                        "temporary-code", WABA_ID)));

        assertEquals("META_CUSTOMER_WABA_ACCESS_DENIED", error.getMessage());
        verifyNoInteractions(sharedWabas, assignedUsers, wabaAuthorization);
    }

    @Test
    void rejectsInvalidDebugResultBeforeCustomerWabaAccess() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var wabaAccess = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        when(exchange.exchange("temporary-code"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        false, "123456789", "USER", null, null, List.of(), List.of()));

        var service = service(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                        "temporary-code", WABA_ID)));

        assertEquals("META_EMBEDDED_SIGNUP_TOKEN_INVALID", error.getMessage());
        verifyNoInteractions(wabaAccess, sharedWabas, assignedUsers, wabaAuthorization);
    }


    @Test
    void rejectsMissingRequestCodeAndInvalidSelectedWabaBeforeExchange() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.accept(null));
        assertThrows(IllegalArgumentException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(null, WABA_ID)));
        assertThrows(IllegalArgumentException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("  ", WABA_ID)));
        assertThrows(IllegalArgumentException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", null)));
        assertThrows(IllegalArgumentException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", "not-numeric")));
        verifyNoInteractions(exchange, debug, shared, assigned, access, authorization);
    }

    @Test
    void rejectsWhenTenantWabaProofDependenciesAreUnavailable() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        var missingAccess = service(readiness, exchange, debug, shared, assigned,
                null, authorization, meta, null, null);
        assertThrows(ConflictException.class, () -> missingAccess.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));

        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var missingAuthorization = service(readiness, exchange, debug, shared, assigned,
                access, null, meta, null, null);
        assertThrows(ConflictException.class, () -> missingAuthorization.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));

        verifyNoInteractions(exchange, debug, shared, assigned, access, authorization);
    }

    @Test
    void rejectsDebugTokenWithMissingOrMismatchedAppIdAndMissingScope() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        when(exchange.exchange("code")).thenReturn(
                new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, null);

        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        true, null, "USER", 1790000000L, 1800000000L,
                        List.of("whatsapp_business_management"), List.of()));
        ConflictException missing = assertThrows(ConflictException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));
        assertEquals("META_EMBEDDED_SIGNUP_TOKEN_APP_MISMATCH", missing.getMessage());

        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        true, "999999999", "USER", 1790000000L, 1800000000L,
                        List.of("whatsapp_business_management"), List.of()));
        ConflictException mismatch = assertThrows(ConflictException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));
        assertEquals("META_EMBEDDED_SIGNUP_TOKEN_APP_MISMATCH", mismatch.getMessage());

        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                        true, "123456789", "USER", 1790000000L, 1800000000L,
                        List.of("business_management"), List.of()));
        ConflictException scope = assertThrows(ConflictException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));
        assertEquals("META_EMBEDDED_SIGNUP_REQUIRED_SCOPE_MISSING", scope.getMessage());
        verifyNoInteractions(shared, assigned, access, authorization);
    }

    @Test
    void findsSelectedWabaOnTheSecondSharedPageAndHandlesOptionalAuditDependencies() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var audit = mock(AuditService.class);
        when(exchange.exchange("code")).thenReturn(
                new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "cursor-page-2"));
        when(shared.list("112233445566778", "system-user-secret", "cursor-page-2")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                WABA_ID, "Selected WABA", "CLP", "74", null)), null));
        when(assigned.fetch(WABA_ID, "112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupAssignedUsersResult(List.of()));

        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, audit);
        var response = service.accept(new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID));

        assertTrue(response.accepted());
        assertFalse(response.wabas().getFirst().systemUserAssigned());
        verify(shared).list("112233445566778", "system-user-secret");
        verify(shared).list("112233445566778", "system-user-secret", "cursor-page-2");
        verify(authorization).authorize(WABA_ID);
        verifyNoInteractions(audit);
    }

    @Test
    void selectsMatchingWabaAfterAnUnrelatedEntryOnTheSamePage() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        when(exchange.exchange("code")).thenReturn(
                new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(
                                new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                        WABA_ID, "Unrelated WABA", "USD", "1", null),
                                new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                        OTHER_WABA_ID, "Selected WABA", "CLP", "74", null)),
                        null));
        when(assigned.fetch(OTHER_WABA_ID, "112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignedUsersResult(List.of()));

        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, null);
        var response = service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", OTHER_WABA_ID));

        assertTrue(response.accepted());
        assertEquals(OTHER_WABA_ID, response.wabas().getFirst().id());
        assertFalse(response.wabas().getFirst().systemUserAssigned());
        verify(shared).list("112233445566778", "system-user-secret");
        verify(authorization).authorize(OTHER_WABA_ID);
    }

    @Test
    void rejectsRepeatedSharedWabaPaginationCursor() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        when(exchange.exchange("code")).thenReturn(
                new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "same-cursor"));
        when(shared.list("112233445566778", "system-user-secret", "same-cursor")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "same-cursor"));

        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));

        assertTrue(error.getMessage().contains("repeated a cursor"));
        verifyNoInteractions(assigned, authorization);
    }

    @Test
    void stopsSharedWabaPaginationAtTheConfiguredSafetyLimit() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var exchange = mock(MetaWhatsAppEmbeddedSignupTokenExchangeClient.class);
        var debug = mock(MetaWhatsAppEmbeddedSignupTokenDebugClient.class);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var access = mock(MetaWhatsAppEmbeddedSignupWabaAccessClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var cursor = new java.util.concurrent.atomic.AtomicInteger();
        when(exchange.exchange("code")).thenReturn(
                new MetaWhatsAppEmbeddedSignupToken("oauth-token", "bearer", 3600L));
        when(debug.debug("oauth-token", "system-user-secret")).thenReturn(validDebug());
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "cursor-0"));
        when(shared.list(anyString(), anyString(), anyString())).thenAnswer(invocation ->
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(), "cursor-" + cursor.incrementAndGet()));

        var service = service(readiness, exchange, debug, shared, assigned,
                access, authorization, meta, null, null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.accept(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("code", WABA_ID)));

        assertTrue(error.getMessage().contains("exceeded safe limit"));
        verify(shared, times(99)).list(anyString(), anyString(), anyString());
    }

    private static MetaWhatsAppEmbeddedSignupAuthorizationCodeService service(
            MetaWhatsAppEmbeddedSignupReadinessService readiness,
            MetaWhatsAppEmbeddedSignupTokenExchangeClient exchange,
            MetaWhatsAppEmbeddedSignupTokenDebugClient debug,
            MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabas,
            MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsers,
            MetaWhatsAppEmbeddedSignupWabaAccessClient wabaAccess,
            MetaWhatsAppTenantWabaAuthorizationService wabaAuthorization,
            MetaWhatsAppProperties meta,
            TenantProvider tenantProvider,
            AuditService audit) {
        return new MetaWhatsAppEmbeddedSignupAuthorizationCodeService(
                readiness, exchange, debug, sharedWabas, assignedUsers,
                wabaAccess, wabaAuthorization, meta, tenantProvider, audit);
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

    private static MetaWhatsAppEmbeddedSignupTokenDebugResult validDebug() {
        return new MetaWhatsAppEmbeddedSignupTokenDebugResult(
                true,
                "123456789",
                "USER",
                1790000000L,
                1800000000L,
                List.of("whatsapp_business_management", "business_management"),
                List.of());
    }
}
