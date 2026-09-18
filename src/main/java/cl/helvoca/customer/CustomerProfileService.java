package cl.helvoca.customer;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingResponse;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerProfileService {
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final TenantProvider tenantProvider;

    public CustomerProfileService(
            CustomerRepository customers,
            BookingRepository bookings,
            TenantProvider tenantProvider) {
        this.customers = customers;
        this.bookings = bookings;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse get(UUID customerId) {
        UUID businessId = tenantProvider.requireBusinessId();
        Customer customer = customers.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        return new CustomerProfileResponse(
                CustomerResponse.from(customer),
                bookings.findAllByBusinessIdAndCustomerIdOrderByStartAtDesc(businessId, customerId)
                        .stream()
                        .map(BookingResponse::from)
                        .toList());
    }
}
