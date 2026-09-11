package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeHumanTransferToolTest {

    @Test
    void transferUsesTrustedBusinessTargetAndIgnoresModelSuppliedNumber() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String streamSid = "trial:trusted";
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        RealtimeToolService tools = service(businesses, calls);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business.setHumanTransferPhone("+56922222222");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(trustedCall(businessId, streamSid)));

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+17372508034", streamSid);
        JSONObject maliciousArgs = new JSONObject().put("targetPhone", "+56999999999");

        JSONObject result = new JSONObject(tools.execute(context, "transfer_to_human", maliciousArgs.toString()));

        assertTrue(result.getBoolean("success"));
        assertTrue(result.getJSONObject("data").getBoolean("transferRequested"));
        assertEquals("+56922222222", result.getJSONObject("data").getString("targetPhone"));
        assertNotEquals("+56999999999", result.getJSONObject("data").getString("targetPhone"));
    }

    @Test
    void transferFailsClosedWhenBusinessHasNoHumanTarget() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String streamSid = "trial:trusted";
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        RealtimeToolService tools = service(businesses, calls);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(trustedCall(businessId, streamSid)));

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+17372508034", streamSid);

        JSONObject result = new JSONObject(tools.execute(context, "transfer_to_human", "{}"));

        assertFalse(result.getBoolean("success"));
        assertEquals("HUMAN_TRANSFER_UNAVAILABLE", result.getJSONObject("error").getString("code"));
    }

    private static RealtimeToolService service(BusinessRepository businesses, CallSessionRepository calls) {
        return new RealtimeToolService(
                businesses,
                mock(CustomerRepository.class),
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                calls,
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class));
    }

    private static CallSession trustedCall(UUID businessId, String streamSid) {
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid(streamSid);
        return call;
    }
}
