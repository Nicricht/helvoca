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

class MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentServiceTest {

    @Test
    void rejectsWhenEmbeddedSignupIsNotReadyBeforeCallingMeta() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_NOT_READY", error.getMessage());
        verifyNoInteractions(sharedWabas, assignedUsers, assign, wabaAuthorization);
    }

    @Test
    void rejectsWabaNotAuthorizedForCurrentTenantBeforeProviderLookup() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        doThrow(new ConflictException("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT"))
                .when(wabaAuthorization).requireAuthorized("1906385232743451");

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, null, null);
        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals("META_WHATSAPP_WABA_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
        verifyNoInteractions(sharedWabas, assignedUsers, assign);
    }

    @Test
    void rejectsWabaThatIsNotSharedWithRecepVoz() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        doNothing().when(wabaAuthorization).requireAuthorized("1906385232743451");
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                "111111111111111",
                                "Different WABA",
                                "USD",
                                "1",
                                null)),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED", error.getMessage());
        verifyNoInteractions(assignedUsers, assign);
    }

    @Test
    void findsSelectedWabaOnLaterPageAndSkipsWriteWhenAlreadyAssigned() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        doNothing().when(wabaAuthorization).requireAuthorized("1906385232743451");
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(),
                        "cursor-page-2"));
        when(sharedWabas.list(
                "112233445566778",
                "system-user-secret",
                "cursor-page-2"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                "1906385232743451",
                                "Selected WABA",
                                "CLP",
                                "74",
                                null)),
                        null));
        when(assignedUsers.fetch(
                "1906385232743451",
                "112233445566778",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignedUsersResult(
                        List.of(new MetaWhatsAppEmbeddedSignupAssignedUsersResult.AssignedUser(
                                "998877665544332",
                                "RecepVoz System User",
                                List.of("MANAGE")))));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, tenantProvider, audit);

        var result = service.ensureAssigned("1906385232743451");

        assertEquals("SYSTEM_USER_ALREADY_ASSIGNED", result.state());
        assertTrue(result.systemUserAssigned());
        assertFalse(result.changed());
        verifyNoInteractions(assign, audit);
    }

    @Test
    void assignsManageTaskOnlyAfterSelectedWabaIsVerifiedShared() {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        doNothing().when(wabaAuthorization).requireAuthorized("1906385232743451");
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                "1906385232743451",
                                "Selected WABA",
                                "CLP",
                                "74",
                                null)),
                        null));
        when(assignedUsers.fetch(
                "1906385232743451",
                "112233445566778",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignedUsersResult(List.of()));
        when(assign.assign(
                "1906385232743451",
                "998877665544332",
                "MANAGE",
                "admin-system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignSystemUserResult(true));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, tenantProvider, audit);

        var result = service.ensureAssigned("1906385232743451");

        assertEquals("SYSTEM_USER_ASSIGNED", result.state());
        assertTrue(result.systemUserAssigned());
        assertTrue(result.changed());
        verify(assign).assign(
                "1906385232743451",
                "998877665544332",
                "MANAGE",
                "admin-system-user-secret");
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_SYSTEM_USER_ASSIGNED"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                isNull(),
                eq(Map.of(
                        "assigned", true,
                        "changed", true)));
    }

    @Test
    void failsClosedWhenMetaDoesNotConfirmAssignment() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var wabaAuthorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);

        doNothing().when(wabaAuthorization).requireAuthorized("1906385232743451");
        when(sharedWabas.list("112233445566778", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(new MetaWhatsAppEmbeddedSignupSharedWabaPage.Waba(
                                "1906385232743451",
                                "Selected WABA",
                                "CLP",
                                "74",
                                null)),
                        null));
        when(assignedUsers.fetch(
                "1906385232743451",
                "112233445566778",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignedUsersResult(List.of()));
        when(assign.assign(
                "1906385232743451",
                "998877665544332",
                "MANAGE",
                "admin-system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupAssignSystemUserResult(false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta, wabaAuthorization, null, null);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_SYSTEM_USER_ASSIGNMENT_FAILED",
                error.getMessage());
    }


    @Test
    void rejectsInvalidSelectedWabaIdsBeforeTenantAuthorization() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, shared, assigned, assign, meta, authorization, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.ensureAssigned(null));
        assertThrows(IllegalArgumentException.class, () -> service.ensureAssigned("not-numeric"));
        verifyNoInteractions(authorization, shared, assigned, assign);
    }

    @Test
    void failsClosedWhenTenantWabaAuthorizationDependencyIsMissing() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, shared, assigned, assign, meta, null, null, null);

        ConflictException error = assertThrows(
                ConflictException.class, () -> service.ensureAssigned("1906385232743451"));
        assertEquals("META_WHATSAPP_WABA_AUTHORIZATION_UNAVAILABLE", error.getMessage());
        verifyNoInteractions(shared, assigned, assign);
    }

    @Test
    void rejectsRepeatedSharedWabaPaginationCursor() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        doNothing().when(authorization).requireAuthorized("1906385232743451");
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "same-cursor"));
        when(shared.list("112233445566778", "system-user-secret", "same-cursor")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "same-cursor"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, shared, assigned, assign, meta, authorization, null, null);
        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.ensureAssigned("1906385232743451"));

        assertTrue(error.getMessage().contains("repeated a cursor"));
        verifyNoInteractions(assigned, assign);
    }

    @Test
    void stopsSharedWabaAssignmentLookupAtTheSafetyLimit() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var shared = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assigned = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);
        var authorization = mock(MetaWhatsAppTenantWabaAuthorizationService.class);
        var cursor = new java.util.concurrent.atomic.AtomicInteger();
        doNothing().when(authorization).requireAuthorized("1906385232743451");
        when(shared.list("112233445566778", "system-user-secret")).thenReturn(
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(List.of(), "cursor-0"));
        when(shared.list(anyString(), anyString(), anyString())).thenAnswer(invocation ->
                new MetaWhatsAppEmbeddedSignupSharedWabaPage(
                        List.of(), "cursor-" + cursor.incrementAndGet()));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, shared, assigned, assign, meta, authorization, null, null);
        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.ensureAssigned("1906385232743451"));

        assertTrue(error.getMessage().contains("exceeded safe limit"));
        verify(shared, times(99)).list(anyString(), anyString(), anyString());
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
}
