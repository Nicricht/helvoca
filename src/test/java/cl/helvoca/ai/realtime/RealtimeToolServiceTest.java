package cl.helvoca.ai.realtime;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeToolServiceTest {
    @Test
    void modelCannotSelectAnotherTenant() {
        UUID trustedBusiness = UUID.randomUUID();
        UUID attackerBusiness = UUID.randomUUID();
        RealtimeToolService tools = service();
        ServiceItemRepository services = services(tools);
        when(services.findAllByBusinessIdOrderByNameAsc(trustedBusiness)).thenReturn(List.of());

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), trustedBusiness, null, "+56911111111", "+56222222222", "MZstream");
        String result = tools.execute(context, "list_services",
                new JSONObject().put("businessId", attackerBusiness.toString()).toString());

        assertTrue(new JSONObject(result).getBoolean("success"));
        verify(services).findAllByBusinessIdOrderByNameAsc(trustedBusiness);
        verify(services, never()).findAllByBusinessIdOrderByNameAsc(attackerBusiness);
    }

    @Test
    void createBookingFailsClosedWhenCallerIsNotRegistered() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        String streamSid = "MZstream";

        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        RealtimeToolService tools = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls, schedule,
                mock(BusinessRequestService.class), mock(UnansweredQuestionService.class));

        ServiceItem service = serviceItem(businessId, serviceId, "Consulta", 30);
        when(services.findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));

        CallSession call = trustedCall(businessId, null, streamSid);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(customers.findFirstByBusinessIdAndPhone(businessId, "+56911111111")).thenReturn(Optional.empty());

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject args = new JSONObject()
                .put("serviceId", serviceId.toString())
                .put("startAt", Instant.now().plusSeconds(7200).toString());

        JSONObject result = new JSONObject(tools.execute(context, "create_booking", args.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CUSTOMER_NOT_REGISTERED", result.getJSONObject("error").getString("code"));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void listCustomerBookingsUsesTrustedCustomerFromCall() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String streamSid = "MZtrusted";

        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        RealtimeToolService tools = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls, schedule,
                mock(BusinessRequestService.class), mock(UnansweredQuestionService.class));

        CallSession call = trustedCall(businessId, customerId, streamSid);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        Business business = business("America/Santiago");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        ServiceItem service = serviceItem(businessId, serviceId, "Corte", 30);
        when(services.findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));

        Booking booking = booking(bookingId, businessId, customerId, serviceId,
                Instant.now().plusSeconds(7200), Instant.now().plusSeconds(9000));
        when(bookings.findAllByBusinessIdAndCustomerIdAndStatusAndStartAtAfterOrderByStartAtAsc(
                eq(businessId), eq(customerId), eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(booking));

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject result = new JSONObject(tools.execute(context, "list_customer_bookings", "{}"));

        assertTrue(result.getBoolean("success"));
        JSONArray items = result.getJSONObject("data").getJSONArray("bookings");
        assertEquals(1, items.length());
        assertEquals(bookingId.toString(), items.getJSONObject(0).getString("bookingId"));
        assertEquals("Corte", items.getJSONObject(0).getString("service"));
        verify(bookings).findAllByBusinessIdAndCustomerIdAndStatusAndStartAtAfterOrderByStartAtAsc(
                eq(businessId), eq(customerId), eq(BookingStatus.CONFIRMED), any(Instant.class));
    }

    @Test
    void rescheduleBookingFailsWhenNewSlotOverlaps() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String streamSid = "MZtrusted";
        Instant oldStart = Instant.now().plusSeconds(3600);
        Instant newStart = Instant.now().plusSeconds(10800);

        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        RealtimeToolService tools = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls, schedule,
                mock(BusinessRequestService.class), mock(UnansweredQuestionService.class));

        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(trustedCall(businessId, customerId, streamSid)));
        Booking booking = booking(bookingId, businessId, customerId, serviceId,
                oldStart, oldStart.plusSeconds(1800));
        when(bookings.findByIdAndBusinessIdAndCustomerId(bookingId, businessId, customerId))
                .thenReturn(Optional.of(booking));
        ServiceItem service = serviceItem(businessId, serviceId, "Consulta", 30);
        when(services.findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));
        when(schedule.isWithinBusinessHours(eq(businessId), eq(newStart), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(eq(businessId), eq(serviceId), eq(newStart), any(Instant.class),
                eq(BookingStatus.CANCELLED), eq(bookingId))).thenReturn(1L);

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject args = new JSONObject()
                .put("bookingId", bookingId.toString())
                .put("newStartAt", newStart.toString());
        JSONObject result = new JSONObject(tools.execute(context, "reschedule_booking", args.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("BOOKING_SLOT_UNAVAILABLE", result.getJSONObject("error").getString("code"));
        assertEquals(oldStart, booking.getStartAt());
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancelBookingPersistsCancelledStatusForTrustedCustomer() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String streamSid = "MZtrusted";
        Instant start = Instant.now().plusSeconds(7200);

        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        RealtimeToolService tools = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls, schedule,
                mock(BusinessRequestService.class), mock(UnansweredQuestionService.class));

        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(trustedCall(businessId, customerId, streamSid)));
        Booking booking = booking(bookingId, businessId, customerId, serviceId, start, start.plusSeconds(1800));
        when(bookings.findByIdAndBusinessIdAndCustomerId(bookingId, businessId, customerId))
                .thenReturn(Optional.of(booking));
        when(bookings.saveAndFlush(booking)).thenReturn(booking);
        when(services.findByIdAndBusinessId(serviceId, businessId))
                .thenReturn(Optional.of(serviceItem(businessId, serviceId, "Consulta", 30)));
        when(businesses.findById(businessId)).thenReturn(Optional.of(business("America/Santiago")));

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject result = new JSONObject(tools.execute(context, "cancel_booking",
                new JSONObject().put("bookingId", bookingId.toString()).toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals("CANCELLED", result.getJSONObject("data").getString("status"));
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookings).saveAndFlush(booking);
    }

    private static RealtimeToolService service() {
        return new RealtimeToolService(
                mock(BusinessRepository.class), mock(CustomerRepository.class), mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class), mock(BookingRepository.class), mock(CallSessionRepository.class),
                mock(BusinessScheduleService.class), mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class));
    }

    private static ServiceItemRepository services(RealtimeToolService service) {
        try {
            var field = RealtimeToolService.class.getDeclaredField("services");
            field.setAccessible(true);
            return (ServiceItemRepository) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static CallSession trustedCall(UUID businessId, UUID customerId, String streamSid) {
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setCustomerId(customerId);
        call.setStreamSid(streamSid);
        return call;
    }

    private static Business business(String timezone) {
        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone(timezone);
        business.setLanguage("es");
        return business;
    }

    private static ServiceItem serviceItem(UUID businessId, UUID serviceId, String name, int minutes) {
        ServiceItem service = new ServiceItem();
        setId(service, serviceId);
        service.setBusinessId(businessId);
        service.setName(name);
        service.setDurationMinutes(minutes);
        service.setActive(true);
        return service;
    }

    private static Booking booking(UUID bookingId, UUID businessId, UUID customerId, UUID serviceId,
                                   Instant startAt, Instant endAt) {
        Booking booking = new Booking();
        setId(booking, bookingId);
        booking.setBusinessId(businessId);
        booking.setCustomerId(customerId);
        booking.setServiceId(serviceId);
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    private static void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
