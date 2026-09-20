package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantConfigurationControllerEmbeddedSignupTest {

    @Test
    void selectedWabaAssignmentEndpointIsAdminOnlyAndDelegatesSelectedId() throws Exception {
        var configuration = mock(MetaWhatsAppTenantConfigurationService.class);
        var health = mock(MetaWhatsAppTenantHealthService.class);
        var deployment = mock(MetaWhatsAppDeploymentReadinessService.class);
        var readiness = mock(MetaWhatsAppEmbeddedSignupReadinessService.class);
        var bootstrap = mock(MetaWhatsAppEmbeddedSignupBootstrapService.class);
        var authorization = mock(MetaWhatsAppEmbeddedSignupAuthorizationCodeService.class);
        var assignment = mock(MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService.class);

        var expected = new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                "SYSTEM_USER_ASSIGNED",
                true,
                true);
        when(assignment.ensureAssigned("1906385232743451")).thenReturn(expected);

        var controller = new MetaWhatsAppTenantConfigurationController(
                configuration,
                health,
                deployment,
                readiness,
                bootstrap,
                authorization,
                assignment);

        var actual = controller.assignSystemUserToSelectedWaba(
                new MetaWhatsAppEmbeddedSignupSelectedWabaRequest(" 1906385232743451 "));

        assertSame(expected, actual);
        verify(assignment).ensureAssigned("1906385232743451");

        Method endpoint = MetaWhatsAppTenantConfigurationController.class.getMethod(
                "assignSystemUserToSelectedWaba",
                MetaWhatsAppEmbeddedSignupSelectedWabaRequest.class);

        PreAuthorize rule = endpoint.getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());

        PostMapping mapping = endpoint.getAnnotation(PostMapping.class);
        assertEquals("/embedded-signup/waba/assign-system-user", mapping.value()[0]);
    }
}
