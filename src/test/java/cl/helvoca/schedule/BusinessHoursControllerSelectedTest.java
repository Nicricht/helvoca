package cl.helvoca.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BusinessHoursControllerSelectedTest {
    @Test
    void delegatesSelectedHoursOnlyToAuthorizedServiceMethod() {
        BusinessHoursAdminService service = mock(BusinessHoursAdminService.class);
        BusinessHoursController controller = new BusinessHoursController(service);
        var changes = List.of(new BusinessHoursRequest.Interval(
                1, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        var request = new BusinessHoursSelectedRequest(changes, List.of());
        var expected = List.of(new BusinessHourResponse(1, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        when(service.mergeSelected(request)).thenReturn(expected);

        assertEquals(expected, controller.mergeSelected(request));
        verify(service).mergeSelected(request);
        verifyNoMoreInteractions(service);
    }
}
