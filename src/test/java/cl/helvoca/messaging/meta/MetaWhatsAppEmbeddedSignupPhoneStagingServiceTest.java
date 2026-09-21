package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupPhoneStagingServiceTest {

    @Test
    void resolvesPhoneRecordAfterRegistrationAndPersistsDisabledConfiguration() {
        UUID phoneRecordId = UUID.randomUUID();
        var registration = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class);
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentialReference = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);

        when(registration.register("1906385232743451", "1913623884432103", "123456"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                        "PHONE_NUMBER_REGISTERED",
                        "1913623884432103",
                        "+56 9 3333 4444",
                        "RecepVoz Demo",
                        true));
        when(resolver.resolve("+56 9 3333 4444")).thenReturn(phoneRecordId);
        when(credentialReference.requireReference())
                .thenReturn(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER);
        when(configuration.replace(any(MetaWhatsAppTenantConfigurationRequest.class)))
                .thenReturn(new MetaWhatsAppTenantConfigurationResponse(
                        phoneRecordId,
                        MetaWhatsAppMessagingProvider.ID,
                        "1913623884432103",
                        "1906385232743451",
                        MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER,
                        false));

        var service = new MetaWhatsAppEmbeddedSignupPhoneStagingService(
                registration,
                configuration,
                resolver,
                credentialReference);

        var result = service.registerAndStage(
                "1906385232743451",
                "1913623884432103",
                "123456");

        assertEquals("PHONE_NUMBER_REGISTERED_AND_STAGED", result.state());
        assertEquals(phoneRecordId, result.phoneRecordId());
        assertEquals(
                MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER,
                result.credentialRef());
        assertFalse(result.enabled());

        var ordered = inOrder(registration, credentialReference, resolver, configuration);
        ordered.verify(registration).register(
                "1906385232743451",
                "1913623884432103",
                "123456");
        ordered.verify(credentialReference).requireReference();
        ordered.verify(resolver).resolve("+56 9 3333 4444");
        ordered.verify(configuration).replace(argThat(request ->
                phoneRecordId.equals(request.phoneRecordId())
                        && MetaWhatsAppMessagingProvider.ID.equals(request.provider())
                        && "1913623884432103".equals(request.providerPhoneNumberId())
                        && MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.EMBEDDED_SIGNUP_SYSTEM_USER
                                .equals(request.credentialRef())
                        && "1906385232743451".equals(request.wabaId())));
    }

    @Test
    void missingCredentialReferenceStopsBeforePhonePersistence() {
        var registration = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class);
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentialReference = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);

        when(registration.register("1906385232743451", "1913623884432103", "123456"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                        "PHONE_NUMBER_REGISTERED",
                        "1913623884432103",
                        "+56 9 3333 4444",
                        "RecepVoz Demo",
                        true));
        when(credentialReference.requireReference())
                .thenThrow(new IllegalStateException("credential unavailable"));

        var service = new MetaWhatsAppEmbeddedSignupPhoneStagingService(
                registration,
                configuration,
                resolver,
                credentialReference);

        assertThrows(
                IllegalStateException.class,
                () -> service.registerAndStage(
                        "1906385232743451",
                        "1913623884432103",
                        "123456"));

        verifyNoInteractions(resolver, configuration);
    }

    @Test
    void neverResolvesOrPersistsWhenRegistrationIsUnconfirmed() {
        var registration = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class);
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentialReference = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);

        when(registration.register(anyString(), anyString(), anyString()))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                        "PHONE_NUMBER_REGISTRATION_UNCONFIRMED",
                        "1913623884432103",
                        "+56 9 3333 4444",
                        "RecepVoz Demo",
                        false));

        var service = new MetaWhatsAppEmbeddedSignupPhoneStagingService(
                registration,
                configuration,
                resolver,
                credentialReference);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.registerAndStage(
                        "1906385232743451",
                        "1913623884432103",
                        "123456"));

        assertEquals("META_EMBEDDED_SIGNUP_PHONE_NOT_REGISTERED", error.getMessage());
        verifyNoInteractions(resolver, credentialReference, configuration);
    }
}
