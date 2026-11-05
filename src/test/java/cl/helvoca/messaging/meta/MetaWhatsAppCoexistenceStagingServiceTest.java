package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppCoexistenceStagingServiceTest {
    private static final String WABA = "1906385232743451";
    private static final String PHONE_ID = "1913623884432103";
    private static final String PHONE = "+56950730447";

    @Test
    void defaultsToOffAndNeverCallsTheProviderOrWritesAnyPhone() {
        MetaWhatsAppProperties props = new MetaWhatsAppProperties();
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var error = assertThrows(ConflictException.class,
                () -> service.stage(WABA, PHONE_ID));

        assertEquals("META_COEXISTENCE_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
        verifyNoInteractions(readiness, status, validator, resolver, credentials, config);
    }

    @Test
    void providerCoexistenceReadbackMustConfirmBusinessAppBeforeAnyStaging() {
        UUID businessId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var readiness = ready();
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token"))
                .thenReturn(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                        false, "CLOUD_API", "+56 9 5073 0447"));

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var error = assertThrows(ConflictException.class,
                () -> service.stage(WABA, PHONE_ID));

        assertEquals("META_COEXISTENCE_NOT_CONFIRMED_BY_PROVIDER", error.getMessage());
        verifyNoInteractions(validator, resolver, credentials, config);
    }

    @Test
    void verifiedExistingBusinessAppIsStagedDisabledWithoutRegisteringItsNumber() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var readiness = ready();
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token"))
                .thenReturn(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                        true, "CLOUD_API", "+56 9 5073 0447"));
        when(validator.validate(WABA, PHONE_ID))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED", PHONE_ID,
                        "+56 9 5073 0447", "Aurora Sur", "GREEN", "VERIFIED"));
        when(credentials.requireReference()).thenReturn("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(resolver.resolve(PHONE)).thenReturn(phoneRecordId);
        when(config.replace(any(MetaWhatsAppTenantConfigurationRequest.class)))
                .thenReturn(new MetaWhatsAppTenantConfigurationResponse(
                        phoneRecordId, "META_WHATSAPP_CLOUD", PHONE_ID, WABA,
                        "EMBEDDED_SIGNUP_SYSTEM_USER", false));

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var result = service.stage(WABA, PHONE_ID);

        assertEquals("COEXISTENCE_VERIFIED_AND_STAGED", result.state());
        assertEquals(phoneRecordId, result.phoneRecordId());
        assertFalse(result.enabled());
        verify(config).replace(argThat(request ->
                request.phoneRecordId().equals(phoneRecordId)
                        && request.providerPhoneNumberId().equals(PHONE_ID)
                        && request.wabaId().equals(WABA)));
    }

    @Test
    void selfServiceStagesAnEligibleBusinessAppNumberForAnotherTenantAfterProviderProof() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        var props = selfServiceProperties();
        var readiness = ready();
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token"))
                .thenReturn(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                        true, "CLOUD_API", "+56 9 5073 0447"));
        when(validator.validate(WABA, PHONE_ID))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED", PHONE_ID,
                        "+56 9 5073 0447", "Independent Business", "GREEN", "VERIFIED"));
        when(credentials.requireReference()).thenReturn("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(resolver.resolve(PHONE)).thenReturn(phoneRecordId);
        when(config.replace(any(MetaWhatsAppTenantConfigurationRequest.class)))
                .thenReturn(new MetaWhatsAppTenantConfigurationResponse(
                        phoneRecordId, "META_WHATSAPP_CLOUD", PHONE_ID, WABA,
                        "EMBEDDED_SIGNUP_SYSTEM_USER", false));

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var result = service.stage(WABA, PHONE_ID);

        assertEquals("COEXISTENCE_VERIFIED_AND_STAGED", result.state());
        assertEquals(phoneRecordId, result.phoneRecordId());
        assertFalse(result.enabled());
        verify(config).replace(argThat(request ->
                request.phoneRecordId().equals(phoneRecordId)
                        && request.providerPhoneNumberId().equals(PHONE_ID)
                        && request.wabaId().equals(WABA)));
    }

    @Test
    void partialPilotAllowlistNeverFallsBackToTenantSelfService() {
        var props = selfServiceProperties();
        props.setCoexistenceAllowedBusinessId(UUID.randomUUID().toString());
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var error = assertThrows(ConflictException.class,
                () -> service.stage(WABA, PHONE_ID));

        assertEquals("META_COEXISTENCE_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
        verifyNoInteractions(readiness, status, validator, resolver, credentials, config);
    }

    @Test
    void pilotAllowlistStillRejectsAnyDifferentProviderPhone() {
        UUID businessId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var readiness = ready();
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token"))
                .thenReturn(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                        true, "CLOUD_API", "+56 9 9999 9999"));

        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        var error = assertThrows(ConflictException.class,
                () -> service.stage(WABA, PHONE_ID));

        assertEquals("META_COEXISTENCE_WRONG_PHONE", error.getMessage());
        verifyNoInteractions(validator, resolver, credentials, config);
    }


    @Test
    void rejectsWhenProviderReadinessIsNotComplete() {
        UUID businessId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var readiness = new MetaWhatsAppEmbeddedSignupReadinessService(new MetaWhatsAppProperties());
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        var service = new MetaWhatsAppCoexistenceStagingService(
                props, readiness, status, validator, resolver, credentials, config, tenants);
        ConflictException error = assertThrows(ConflictException.class, () -> service.stage(WABA, PHONE_ID));
        assertEquals("META_COEXISTENCE_PROVIDER_NOT_READY", error.getMessage());
        verifyNoInteractions(status, validator, resolver, credentials, config);
    }

    @Test
    void rejectsInvalidPhoneAndWabaIdentifiersBeforeProviderReadback() {
        UUID businessId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        var service = new MetaWhatsAppCoexistenceStagingService(
                props, ready(), status, validator, resolver, credentials, config, tenants);
        assertThrows(IllegalArgumentException.class, () -> service.stage(WABA, "bad-phone-id"));
        assertThrows(IllegalArgumentException.class, () -> service.stage("bad-waba-id", PHONE_ID));
        verifyNoInteractions(status, validator, resolver, credentials, config);
    }

    @Test
    void rejectsProviderAndWabaPhoneIdentityMismatches() {
        UUID businessId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token")).thenReturn(
                new MetaWhatsAppCoexistencePhoneStatusClient.Status(true, "CLOUD_API", "+56950730447"));
        when(validator.validate(WABA, PHONE_ID)).thenReturn(
                new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED", "1999999999999999",
                        "+56 9 5073 0447", "Aurora", "GREEN", "VERIFIED"));
        var service = new MetaWhatsAppCoexistenceStagingService(
                props, ready(), status, validator, resolver, credentials, config, tenants);
        ConflictException idMismatch = assertThrows(
                ConflictException.class, () -> service.stage(WABA, PHONE_ID));
        assertEquals("META_COEXISTENCE_WABA_PHONE_MISMATCH", idMismatch.getMessage());

        when(validator.validate(WABA, PHONE_ID)).thenReturn(
                new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED", PHONE_ID,
                        "+56 9 9999 9999", "Aurora", "GREEN", "VERIFIED"));
        ConflictException phoneMismatch = assertThrows(
                ConflictException.class, () -> service.stage(WABA, PHONE_ID));
        assertEquals("META_COEXISTENCE_WABA_PHONE_MISMATCH", phoneMismatch.getMessage());
        verifyNoInteractions(resolver, credentials, config);
    }

    @Test
    void refusesToStageWhenConfigurationWouldEnableDelivery() {
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        var props = enabledProperties(businessId);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(status.read(PHONE_ID, "test-system-token")).thenReturn(
                new MetaWhatsAppCoexistencePhoneStatusClient.Status(true, "CLOUD_API", "+56950730447"));
        when(validator.validate(WABA, PHONE_ID)).thenReturn(
                new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationResult(
                        "PHONE_NUMBER_VALIDATED", PHONE_ID,
                        "+56 9 5073 0447", "Aurora", "GREEN", "VERIFIED"));
        when(credentials.requireReference()).thenReturn("EMBEDDED_SIGNUP_SYSTEM_USER");
        when(resolver.resolve(PHONE)).thenReturn(phoneRecordId);
        when(config.replace(any(MetaWhatsAppTenantConfigurationRequest.class))).thenReturn(
                new MetaWhatsAppTenantConfigurationResponse(
                        phoneRecordId, "META_WHATSAPP_CLOUD", PHONE_ID, WABA,
                        "EMBEDDED_SIGNUP_SYSTEM_USER", true));
        var service = new MetaWhatsAppCoexistenceStagingService(
                props, ready(), status, validator, resolver, credentials, config, tenants);
        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.stage(WABA, PHONE_ID));
        assertTrue(error.getMessage().contains("must remain disabled"));
    }

    @Test
    void partialPhoneAllowlistNeverFallsBackToSelfService() {
        var props = selfServiceProperties();
        props.setCoexistenceAllowedPhone(PHONE);
        var status = mock(MetaWhatsAppCoexistencePhoneStatusClient.class);
        var validator = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var resolver = mock(MetaWhatsAppEmbeddedSignupPhoneRecordResolverService.class);
        var credentials = mock(MetaWhatsAppEmbeddedSignupCredentialReferenceResolver.class);
        var config = mock(MetaWhatsAppTenantConfigurationService.class);
        var tenants = mock(TenantProvider.class);
        when(tenants.requireBusinessId()).thenReturn(UUID.randomUUID());
        var service = new MetaWhatsAppCoexistenceStagingService(
                props, ready(), status, validator, resolver, credentials, config, tenants);
        ConflictException error = assertThrows(
                ConflictException.class, () -> service.stage(WABA, PHONE_ID));
        assertEquals("META_COEXISTENCE_NOT_AUTHORIZED_FOR_TENANT", error.getMessage());
        verifyNoInteractions(status, validator, resolver, credentials, config);
    }

    private static MetaWhatsAppProperties enabledProperties(UUID tenantId) {
        var props = new MetaWhatsAppProperties();
        props.setCoexistenceEnabled(true);
        props.setCoexistenceAllowedBusinessId(tenantId.toString());
        props.setCoexistenceAllowedPhone(PHONE);
        props.setEmbeddedSignupSystemUserAccessToken("test-system-token");
        return props;
    }

    private static MetaWhatsAppProperties selfServiceProperties() {
        var props = new MetaWhatsAppProperties();
        props.setCoexistenceEnabled(true);
        props.setCoexistenceSelfServiceEnabled(true);
        props.setEmbeddedSignupSystemUserAccessToken("test-system-token");
        return props;
    }

    private static MetaWhatsAppEmbeddedSignupReadinessService ready() {
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        when(readiness.readiness()).thenReturn(new MetaWhatsAppEmbeddedSignupReadinessResponse(
                "READY_FOR_EMBEDDED_SIGNUP", true, true, true, true, true, true,
                true, true, true, true, List.of()));
        return readiness;
    }
}
