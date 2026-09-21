package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantConfigurationControllerPhoneRegistrationTest {

    @Test
    void registrationEndpointIsAdminOnlyDelegatesValidatedInputAndRedactsPin() throws Exception {
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var health = mock(MetaWhatsAppTenantHealthService.class);
        var deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        var bootstrap = mock(MetaWhatsAppEmbeddedSignupBootstrapService.class);
        var authorization = mock(MetaWhatsAppEmbeddedSignupAuthorizationCodeService.class);
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phoneDiscovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneValidation = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var phoneRegistration = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class);

        String pin = "0".repeat(6);
        var expected = new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationResult(
                "PHONE_NUMBER_REGISTERED",
                "1913623884432103",
                "+56 9 3333 4444",
                "RecepVoz Demo",
                true);
        when(phoneRegistration.register(
                "1906385232743451",
                "1913623884432103",
                pin)).thenReturn(expected);

        var controller = new MetaWhatsAppTenantConfigurationController(
                configuration,
                health,
                deployment,
                readiness,
                bootstrap,
                authorization,
                assignment,
                subscription,
                phoneDiscovery,
                phoneValidation,
                phoneRegistration);

        var request = new MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationRequest(
                " 1906385232743451 ",
                " 1913623884432103 ",
                " " + pin + " ");

        var actual = controller.registerSelectedWabaPhoneNumber(request);

        assertSame(expected, actual);
        verify(phoneRegistration).register(
                "1906385232743451",
                "1913623884432103",
                pin);
        verifyNoInteractions(assignment, subscription, phoneDiscovery, phoneValidation);
        assertFalse(request.toString().contains(pin));

        Method endpoint = MetaWhatsAppTenantConfigurationController.class.getMethod(
                "registerSelectedWabaPhoneNumber",
                MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationRequest.class);

        PreAuthorize rule = endpoint.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());

        PostMapping mapping = endpoint.getAnnotation(PostMapping.class);
        assertEquals("/embedded-signup/waba/phone-number/register", mapping.value()[0]);
    }
}
