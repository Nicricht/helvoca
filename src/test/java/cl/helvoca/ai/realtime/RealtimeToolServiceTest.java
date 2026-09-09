package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

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
        RealtimeToolService tools = new RealtimeToolService(businesses, customers, services, knowledge, bookings, calls);

        cl.helvoca.servicecatalog.ServiceItem service = new cl.helvoca.servicecatalog.ServiceItem();
        service.setBusinessId(businessId);
        service.setName("Consulta");
        service.setDurationMinutes(30);
        service.setActive(true);
        when(services.findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));

        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid(streamSid);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(customers.findFirstByBusinessIdAndPhone(businessId, "+56911111111")).thenReturn(Optional.empty());

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject args = new JSONObject()
                .put("serviceId", serviceId.toString())
                .put("startAt", java.time.Instant.now().plusSeconds(7200).toString());

        JSONObject result = new JSONObject(tools.execute(context, "create_booking", args.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CUSTOMER_NOT_REGISTERED", result.getJSONObject("error").getString("code"));
        verify(bookings, never()).saveAndFlush(any());
    }

    private static RealtimeToolService service() {
        return new RealtimeToolService(
                mock(BusinessRepository.class), mock(CustomerRepository.class), mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class), mock(BookingRepository.class), mock(CallSessionRepository.class));
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
}
