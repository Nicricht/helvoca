package cl.helvoca.publicbooking;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicBookingControllerTest {

    @Test
    void delegatesPublicReadAndCreateEndpointsWithoutAddingTenantIdentity() {
        PublicBookingService service = mock(PublicBookingService.class);
        PublicBookingController controller = new PublicBookingController(service);
        UUID key = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        Instant startAt = Instant.now().plusSeconds(3600);

        var page = new PublicBookingController.PublicBookingPageResponse(
                "Negocio",
                "America/Santiago",
                "Reserva pública",
                "Santiago",
                List.of());
        var availability = new PublicBookingController.PublicAvailabilityResponse(
                date,
                "America/Santiago",
                List.of(new PublicBookingController.PublicSlotResponse(
                        startAt,
                        startAt.plusSeconds(1800))));
        var request = new PublicBookingController.PublicBookingRequest(
                serviceId,
                startAt,
                new PublicBookingController.CustomerInput(
                        "Ana",
                        "+56911119999",
                        "ana@example.cl"));
        var confirmation = new PublicBookingController.PublicBookingConfirmation(
                UUID.randomUUID(),
                "CONFIRMED",
                "Consulta",
                startAt,
                "America/Santiago",
                "Ana");

        when(service.page(key)).thenReturn(page);
        when(service.availability(key, serviceId, date)).thenReturn(availability);
        when(service.create(key, "idem-controller-0001", request)).thenReturn(confirmation);

        assertSame(page, controller.page(key));
        assertSame(availability, controller.availability(key, serviceId, date));

        var created = controller.create(key, "idem-controller-0001", request);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertSame(confirmation, created.getBody());

        verify(service).page(key);
        verify(service).availability(key, serviceId, date);
        verify(service).create(key, "idem-controller-0001", request);
    }
}
