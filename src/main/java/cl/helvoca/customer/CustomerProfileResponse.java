package cl.helvoca.customer;

import cl.helvoca.booking.BookingResponse;

import java.util.List;

public record CustomerProfileResponse(
        CustomerResponse customer,
        List<BookingResponse> bookings
) {}
