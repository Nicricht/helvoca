package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallTraceService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioCallControl;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RealtimeEndCallToolTest {
    private static final String ACCOUNT_SID = "AC11111111111111111111111111111111";
    private static final String CALL_SID = "CA22222222222222222222222222222222";

    @Test
    void endCallUsesVerifiedTwilioCallContext() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String streamSid = "MZ33333333333333333333333333333333";
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);

        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UnansweredQuestionService unanswered = mock(UnansweredQuestionService.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        CallTraceService trace = mock(CallTraceService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid(streamSid);
        call.setTelephonyProvider("twilio");
        call.setProviderCallId(CALL_SID);
        call.setStatus(CallStatus.IN_PROGRESS);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        TwilioCallControl control = mock(TwilioCallControl.class);
        when(control.hangup(ACCOUNT_SID, CALL_SID)).thenReturn(true);
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT_SID);

        CertificationGuardedRealtimeToolService service = new CertificationGuardedRealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls, schedule, requests, unanswered,
                actions, trace, jdbc);
        setField(service, "twilioCallControl", control);
        setField(service, "twilioProperties", properties);

        JSONObject result = new JSONObject(service.execute(context, "end_call", "{}"));

        assertTrue(result.getBoolean("success"));
        assertTrue(result.getJSONObject("data").getBoolean("ended"));
        assertFalse(result.getJSONObject("data").getBoolean("alreadyEnded"));
        verify(control).hangup(ACCOUNT_SID, CALL_SID);
        verify(trace).recordTool(eq(businessId), eq(callId), eq("end_call"), any(JSONObject.class));
    }

    @Test
    void endCallRejectsMismatchedStreamWithoutTouchingCarrier() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", "MZ-current");

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid("MZ-other");
        call.setTelephonyProvider("twilio");
        call.setProviderCallId(CALL_SID);
        call.setStatus(CallStatus.IN_PROGRESS);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        TwilioCallControl control = mock(TwilioCallControl.class);
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT_SID);

        CertificationGuardedRealtimeToolService service = new CertificationGuardedRealtimeToolService(
                mock(BusinessRepository.class), mock(CustomerRepository.class), mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class), mock(BookingRepository.class), calls,
                mock(BusinessScheduleService.class), mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class), mock(CallActionRepository.class),
                mock(CallTraceService.class), mock(JdbcTemplate.class));
        setField(service, "twilioCallControl", control);
        setField(service, "twilioProperties", properties);

        JSONObject result = new JSONObject(service.execute(context, "end_call", "{}"));

        assertFalse(result.getBoolean("success"));
        verifyNoInteractions(control);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
