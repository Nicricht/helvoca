package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionServiceTest {

    @Test
    void subscribesOnlyAfterSystemUserAssignmentIsEnsured() {
        UUID businessId = UUID.randomUUID();
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscribe = mock(MetaWhatsAppEmbeddedSignupSubscribeAppClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        when(assignment.ensureAssigned("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                        "SYSTEM_USER_ALREADY_ASSIGNED",
                        true,
                        false));
        when(subscribe.subscribe(
                "1906385232743451",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSubscribeAppResult(true));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
                assignment,
                subscribe,
                meta,
                tenantProvider,
                audit);

        var result = service.ensureSubscribed("1906385232743451");

        assertEquals("APP_SUBSCRIBED", result.state());
        assertTrue(result.systemUserAssigned());
        assertFalse(result.assignmentChanged());
        assertTrue(result.appSubscribed());

        var ordered = inOrder(assignment, subscribe);
        ordered.verify(assignment).ensureAssigned("1906385232743451");
        ordered.verify(subscribe).subscribe(
                "1906385232743451",
                "system-user-secret");
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_APP_SUBSCRIBED"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                isNull(),
                eq(Map.of(
                        "appSubscribed", true,
                        "assignmentChanged", false)));
    }

    @Test
    void preservesAssignmentChangedWhenSystemUserHadToBeAdded() {
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscribe = mock(MetaWhatsAppEmbeddedSignupSubscribeAppClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(assignment.ensureAssigned("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                        "SYSTEM_USER_ASSIGNED",
                        true,
                        true));
        when(subscribe.subscribe(
                "1906385232743451",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSubscribeAppResult(true));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
                assignment,
                subscribe,
                meta);

        var result = service.ensureSubscribed("1906385232743451");

        assertTrue(result.assignmentChanged());
        assertTrue(result.appSubscribed());
    }

    @Test
    void doesNotSubscribeWhenAssignmentPreconditionFails() {
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscribe = mock(MetaWhatsAppEmbeddedSignupSubscribeAppClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(assignment.ensureAssigned("1906385232743451"))
                .thenThrow(new ConflictException("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
                assignment,
                subscribe,
                meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureSubscribed("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED", error.getMessage());
        verifyNoInteractions(subscribe);
    }

    @Test
    void doesNotSubscribeWhenAssignmentDoesNotConfirmSystemUser() {
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscribe = mock(MetaWhatsAppEmbeddedSignupSubscribeAppClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(assignment.ensureAssigned("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                        "SYSTEM_USER_ASSIGNMENT_UNCONFIRMED",
                        false,
                        false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
                assignment,
                subscribe,
                meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureSubscribed("1906385232743451"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_SYSTEM_USER_NOT_ASSIGNED",
                error.getMessage());
        verifyNoInteractions(subscribe);
    }

    @Test
    void failsClosedWhenMetaDoesNotConfirmSubscription() {
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscribe = mock(MetaWhatsAppEmbeddedSignupSubscribeAppClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(assignment.ensureAssigned("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                        "SYSTEM_USER_ALREADY_ASSIGNED",
                        true,
                        false));
        when(subscribe.subscribe(
                "1906385232743451",
                "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSubscribeAppResult(false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService(
                assignment,
                subscribe,
                meta,
                tenantProvider,
                audit);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.ensureSubscribed("1906385232743451"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_APP_SUBSCRIPTION_FAILED",
                error.getMessage());
        verifyNoInteractions(audit);
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        return meta;
    }
}
