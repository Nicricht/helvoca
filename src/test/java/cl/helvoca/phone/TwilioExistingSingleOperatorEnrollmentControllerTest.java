package cl.helvoca.phone;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioExistingSingleOperatorEnrollmentControllerTest {
    @Test
    void platformControllerDelegatesStagingConfirmationAndStatus() {
        var service = mock(TwilioExistingSingleOperatorEnrollmentService.class);
        var controller = new TwilioExistingSingleOperatorEnrollmentController(service);
        UUID tenant = UUID.randomUUID();
        UUID phone = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        var request = new TwilioExistingSingleOperatorEnrollmentService.Request(
                tenant, phone, "AC" + "a".repeat(32));
        var confirm = new TwilioExistingSingleOperatorEnrollmentService.Confirm(
                "secret-not-logged", "+16812706780");
        var pending = new TwilioExistingSingleOperatorEnrollmentService.View(change, "PENDING");
        var applied = new TwilioExistingSingleOperatorEnrollmentService.View(change, "APPLIED");

        when(service.request(request)).thenReturn(pending);
        when(service.confirm(change, confirm)).thenReturn(applied);
        when(service.status(change)).thenReturn(applied);

        assertEquals(pending, controller.request(request));
        assertEquals(applied, controller.confirm(change, confirm));
        assertEquals(applied, controller.status(change));

        verify(service).request(request);
        verify(service).confirm(change, confirm);
        verify(service).status(change);
        verifyNoMoreInteractions(service);
    }
}
