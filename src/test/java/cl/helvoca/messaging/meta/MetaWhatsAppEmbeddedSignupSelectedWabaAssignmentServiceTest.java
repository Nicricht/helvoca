package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
                readiness, sharedWabas, assignedUsers, assign, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_NOT_READY", error.getMessage());
        verifyNoInteractions(sharedWabas, assignedUsers, assign);
    }

    @Test
    void rejectsWabaThatIsNotSharedWithRecepVoz() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);

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
                readiness, sharedWabas, assignedUsers, assign, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED", error.getMessage());
        verifyNoInteractions(assignedUsers, assign);
    }

    @Test
    void findsSelectedWabaOnLaterPageAndSkipsWriteWhenAlreadyAssigned() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);

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
                readiness, sharedWabas, assignedUsers, assign, meta);

        var result = service.ensureAssigned("1906385232743451");

        assertEquals("SYSTEM_USER_ALREADY_ASSIGNED", result.state());
        assertTrue(result.systemUserAssigned());
        assertFalse(result.changed());
        verifyNoInteractions(assign);
    }

    @Test
    void assignsManageTaskOnlyAfterSelectedWabaIsVerifiedShared() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);

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
                readiness, sharedWabas, assignedUsers, assign, meta);

        var result = service.ensureAssigned("1906385232743451");

        assertEquals("SYSTEM_USER_ASSIGNED", result.state());
        assertTrue(result.systemUserAssigned());
        assertTrue(result.changed());
        verify(assign).assign(
                "1906385232743451",
                "998877665544332",
                "MANAGE",
                "admin-system-user-secret");
    }

    @Test
    void failsClosedWhenMetaDoesNotConfirmAssignment() {
        MetaWhatsAppProperties meta = readyProperties();
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(meta);
        var sharedWabas = mock(MetaWhatsAppEmbeddedSignupSharedWabaClient.class);
        var assignedUsers = mock(MetaWhatsAppEmbeddedSignupAssignedUsersClient.class);
        var assign = mock(MetaWhatsAppEmbeddedSignupAssignSystemUserClient.class);

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
                readiness, sharedWabas, assignedUsers, assign, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureAssigned("1906385232743451"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_SYSTEM_USER_ASSIGNMENT_FAILED",
                error.getMessage());
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
