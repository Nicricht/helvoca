package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantConfigurationControllerCoexistenceTest {
    private static final String WABA = "1906385232743451";
    private static final String PHONE_ID = "1913623884432103";

    @Test
    void delegatesAllConfigurationAndReadinessEndpoints() {
        Fixture f = fixture();

        assertNull(f.controller.status());
        assertNull(f.controller.health());
        assertNull(f.controller.deploymentReadiness());
        assertNull(f.controller.embeddedSignupReadiness());
        assertNull(f.controller.embeddedSignupBootstrap());
        assertNull(f.controller.acceptAuthorizationCode(
                new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest("opaque-code", WABA)));
        assertNull(f.controller.deactivate());
        assertNull(f.controller.activate());
        assertNull(f.controller.replace(null));

        verify(f.configuration).status();
        verify(f.health).health();
        verify(f.deployment).readiness();
        verify(f.readiness).readiness();
        verify(f.bootstrap).bootstrap();
        verify(f.authorization).accept(any(MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest.class));
        verify(f.configuration).deactivate();
        verify(f.configuration).activate();
        verify(f.configuration).replace(null);
    }

    @Test
    void coexistenceStageEndpointFailsClosedWhenOptionalServiceIsAbsent() throws Exception {
        Fixture f = fixture();
        var request = new MetaWhatsAppCoexistenceStageRequest(WABA, PHONE_ID);

        assertThrows(IllegalStateException.class,
                () -> f.controller.stageVerifiedCoexistence(request));

        Method endpoint = MetaWhatsAppTenantConfigurationController.class.getMethod(
                "stageVerifiedCoexistence", MetaWhatsAppCoexistenceStageRequest.class);
        assertEquals("hasRole('BUSINESS_ADMIN')",
                endpoint.getAnnotation(PreAuthorize.class).value());
        assertEquals("/embedded-signup/waba/phone-number/coexistence/stage",
                endpoint.getAnnotation(PostMapping.class).value()[0]);
    }

    @Test
    void coexistenceStageEndpointDelegatesOnlyTheSelectedTenantIds() {
        Fixture f = fixture();
        var staging = mock(MetaWhatsAppCoexistenceStagingService.class);
        ReflectionTestUtils.setField(f.controller, "coexistenceStagingService", staging);
        var request = new MetaWhatsAppCoexistenceStageRequest(WABA, PHONE_ID);

        assertNull(f.controller.stageVerifiedCoexistence(request));

        verify(staging).stage(WABA, PHONE_ID);
        verifyNoInteractions(f.phoneRegistration);
    }

    private static Fixture fixture() {
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var health = mock(MetaWhatsAppTenantHealthService.class);
        var deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        var bootstrap = mock(MetaWhatsAppEmbeddedSignupBootstrapService.class);
        var authorization = mock(MetaWhatsAppEmbeddedSignupAuthorizationCodeService.class);
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var validation = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService.class);
        var phoneRegistration = mock(MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService.class);
        var phoneStaging = mock(MetaWhatsAppEmbeddedSignupPhoneStagingService.class);
        var controller = new MetaWhatsAppTenantConfigurationController(
                configuration, health, deployment, readiness, bootstrap, authorization,
                assignment, subscription, discovery, validation, phoneRegistration, phoneStaging);
        return new Fixture(controller, configuration, health, deployment, readiness,
                bootstrap, authorization, phoneRegistration);
    }

    private record Fixture(
            MetaWhatsAppTenantConfigurationController controller,
            MetaWhatsAppTenantConfigurationService configuration,
            MetaWhatsAppTenantHealthService health,
            MetaWhatsAppDeploymentReadinessService deployment,
            MetaWhatsAppEmbeddedSignupReadinessService readiness,
            MetaWhatsAppEmbeddedSignupBootstrapService bootstrap,
            MetaWhatsAppEmbeddedSignupAuthorizationCodeService authorization,
            MetaWhatsAppEmbeddedSignupSelectedPhoneRegistrationService phoneRegistration) {
    }
}
