package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantConfigurationControllerPhoneFinalizeTest {

    @Test
    void finalizeEndpointIsAdminOnlyAndDelegatesToStaging() throws Exception {
        var staging = mock(MetaWhatsAppEmbeddedSignupPhoneStagingService.class);
        UUID phoneRecordId = UUID.randomUUID();
        String registrationCode = "123" + "456";

        var expected = new MetaWhatsAppEmbeddedSignupPhoneStagingResult(
                "PHONE_NUMBER_REGISTERED_AND_STAGED",
                phoneRecordId,
                "META_WHATSAPP_CLOUD",
                "1913623884432103",
                "1906385232743451",
                "TENANT_01",
                false);

        when(staging.registerAndStage(
                phoneRecordId,
                "1906385232743451",
                "1913623884432103",
                "TENANT_01",
                registrationCode)).thenReturn(expected);

        var controller = new MetaWhatsAppTenantConfigurationController(
                mock(MetaWhatsAppTenantConfigurationService.class),
                mock(MetaWhatsAppTenantHealthService.class),
                mock(MetaWhatsAppDeploymentReadinessService.class),
                mock(MetaWhatsAppEmbeddedSignupReadinessService.class),
                mock(MetaWhatsAppEmbeddedSignupBootstrapService.class),
                mock(MetaWhatsAppEmbeddedSignupAuthorizationCodeService.class),
                mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class),
                mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class),
                mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class),
                mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class),
                mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class),
                staging);

        var request = new MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest(
                phoneRecordId,
                "1906385232743451",
                "1913623884432103",
                "TENANT_01",
                registrationCode);

        var actual = controller.finalizeSelectedWabaPhoneNumber(request);

        assertSame(expected, actual);
        assertFalse(actual.enabled());
        verify(staging).registerAndStage(
                phoneRecordId,
                "1906385232743451",
                "1913623884432103",
                "TENANT_01",
                registrationCode);

        Method endpoint = MetaWhatsAppTenantConfigurationController.class.getMethod(
                "finalizeSelectedWabaPhoneNumber",
                MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest.class);
        assertEquals(
                "hasRole('BUSINESS_ADMIN')",
                endpoint.getAnnotation(PreAuthorize.class).value());
        assertEquals(
                "/embedded-signup/waba/phone-number/finalize",
                endpoint.getAnnotation(PostMapping.class).value()[0]);
    }
}
