package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationServiceTest {

    @Test
    void registersOnlyAfterSelectedPhoneWasValidatedAgainstWaba() {
        UUID businessId = UUID.randomUUID();
        var validation = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var registration = mock(MetaWhatsAppEmbeddedSignupRegisterPhoneClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        when(validation.validate("1906385232743451", "1913623884432103"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED",
                        "1913623884432103",
                        "+56 9 3333 4444",
                        "RecepVoz Demo",
                        "GREEN",
                        "VERIFIED"));
        when(registration.register(
                "1913623884432103",
                "system-user-secret",
                "123456"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupRegisterPhoneResult(true));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
                validation,
                registration,
                meta,
                tenantProvider,
                audit);

        var result = service.register(
                "1906385232743451",
                "1913623884432103",
                "123456");

        assertEquals("PHONE_NUMBER_REGISTERED", result.state());
        assertEquals("1913623884432103", result.phoneNumberId());
        assertEquals("+56 9 3333 4444", result.displayPhoneNumber());
        assertEquals("RecepVoz Demo", result.verifiedName());
        assertTrue(result.registered());
        assertFalse(result.toString().contains("123456"));
        assertFalse(result.toString().contains("system-user-secret"));

        var ordered = inOrder(validation, registration);
        ordered.verify(validation).validate(
                "1906385232743451",
                "1913623884432103");
        ordered.verify(registration).register(
                "1913623884432103",
                "system-user-secret",
                "123456");
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_PHONE_REGISTERED"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                isNull(),
                eq(Map.of(
                        "phoneRegistered", true,
                        "phoneValidated", true)));
    }

    @Test
    void doesNotRegisterWhenSelectedPhoneValidationFails() {
        var validation = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var registration = mock(MetaWhatsAppEmbeddedSignupRegisterPhoneClient.class);

        when(validation.validate("1906385232743451", "1913623884432103"))
                .thenThrow(new ConflictException("META_EMBEDDED_SIGNUP_PHONE_NOT_IN_WABA"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
                validation,
                registration,
                readyProperties());

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.register(
                        "1906385232743451",
                        "1913623884432103",
                        "123456"));

        assertEquals("META_EMBEDDED_SIGNUP_PHONE_NOT_IN_WABA", error.getMessage());
        verifyNoInteractions(registration);
    }

    @Test
    void failsClosedWhenMetaDoesNotConfirmRegistration() {
        var validation = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var registration = mock(MetaWhatsAppEmbeddedSignupRegisterPhoneClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(validation.validate("1906385232743451", "1913623884432103"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED",
                        "1913623884432103",
                        "+56 9 3333 4444",
                        "RecepVoz Demo",
                        "GREEN",
                        "VERIFIED"));
        when(registration.register(
                "1913623884432103",
                "system-user-secret",
                "123456"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupRegisterPhoneResult(false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService(
                validation,
                registration,
                meta,
                tenantProvider,
                audit);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.register(
                        "1906385232743451",
                        "1913623884432103",
                        "123456"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_PHONE_REGISTRATION_FAILED",
                error.getMessage());
        verifyNoInteractions(audit);
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        return meta;
    }
}
