package cl.helvoca.phone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioExistingEnrollmentAdminControllerContractTest {
    @Test
    void controllerIsOffByDefaultAndRequiresPlatformAdminForEveryRoute() {
        var conditional = TwilioExistingEnrollmentAdminController.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(conditional);
        assertEquals("true", conditional.havingValue());
        assertFalse(conditional.matchIfMissing());
        assertArrayEquals(new String[]{"app.telephony.twilio.existing-enrollment-admin-enabled"},
                conditional.name());
        var role = TwilioExistingEnrollmentAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(role);
        assertEquals("hasRole('PLATFORM_ADMIN')", role.value());
        assertEquals("/api/v1/platform/twilio-existing-enrollments",
                TwilioExistingEnrollmentAdminController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertNotNull(TwilioExistingEnrollmentAdminController.class.getAnnotation(RestController.class));
        assertThrows(NoSuchMethodException.class, () ->
                TwilioExistingEnrollmentAdminController.class.getMethod(
                        "approve", String.class));
    }

    @Test
    void everyRouteDelegatesToPrincipalBoundServiceWithNoClientSuppliedApprover() {
        var service = mock(TwilioExistingEnrollmentAdminService.class);
        var controller = new TwilioExistingEnrollmentAdminController(service);
        UUID change = UUID.randomUUID();
        var request = new TwilioExistingEnrollmentAdminService.Request(
                UUID.randomUUID(), UUID.randomUUID(), "AC"+"a".repeat(32));
        var pending = new TwilioExistingEnrollmentAdminService.View(change, "PENDING");
        var approved = new TwilioExistingEnrollmentAdminService.View(change, "APPROVED");
        var applied = new TwilioExistingEnrollmentAdminService.View(change, "APPLIED");
        when(service.request(request)).thenReturn(pending);
        when(service.approve(change)).thenReturn(approved);
        when(service.execute(change)).thenReturn(applied);
        when(service.status(change)).thenReturn(applied);
        assertEquals(pending, controller.request(request));
        assertEquals(approved, controller.approve(change));
        assertEquals(applied, controller.execute(change));
        assertEquals(applied, controller.status(change));
        verify(service).request(request);
        verify(service).approve(change);
        verify(service).execute(change);
        verify(service).status(change);
    }
}
