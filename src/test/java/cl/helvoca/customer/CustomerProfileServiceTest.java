package cl.helvoca.customer;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CustomerProfileServiceTest {

    @Test
    void returnsCustomerAndAllBookingsInsideAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setName("Ana Reserva");
        customer.setPhone("+56922222222");

        Booking recent = new Booking();
        recent.setBusinessId(businessId);
        recent.setCustomerId(customerId);
        recent.setServiceId(serviceId);
        recent.setStartAt(Instant.parse("2026-09-19T16:00:00Z"));
        recent.setEndAt(Instant.parse("2026-09-19T17:00:00Z"));
        recent.setStatus(BookingStatus.CONFIRMED);
        recent.setSource(BookingSource.ADMIN);

        Booking old = new Booking();
        old.setBusinessId(businessId);
        old.setCustomerId(customerId);
        old.setServiceId(serviceId);
        old.setStartAt(Instant.parse("2026-09-10T16:00:00Z"));
        old.setEndAt(Instant.parse("2026-09-10T17:00:00Z"));
        old.setStatus(BookingStatus.CANCELLED);
        old.setSource(BookingSource.AI_CALL);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(bookings.findAllByBusinessIdAndCustomerIdOrderByStartAtDesc(businessId, customerId))
                .thenReturn(List.of(recent, old));

        CustomerProfileResponse result =
                new CustomerProfileService(customers, bookings, tenantProvider).get(customerId);

        assertEquals("Ana Reserva", result.customer().name());
        assertEquals(2, result.bookings().size());
        assertEquals(BookingStatus.CONFIRMED, result.bookings().get(0).status());
        assertEquals(BookingStatus.CANCELLED, result.bookings().get(1).status());

        verify(bookings).findAllByBusinessIdAndCustomerIdOrderByStartAtDesc(businessId, customerId);
    }

    @Test
    void doesNotExposeBookingsWhenCustomerDoesNotBelongToTenant() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        CustomerRepository customers = mock(CustomerRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());

        CustomerProfileService service = new CustomerProfileService(customers, bookings, tenantProvider);

        assertThrows(NotFoundException.class, () -> service.get(customerId));
        verifyNoInteractions(bookings);
    }
}
