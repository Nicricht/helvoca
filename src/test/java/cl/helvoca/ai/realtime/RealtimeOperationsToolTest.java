package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.QuestionStatus;
import cl.helvoca.learning.UnansweredQuestion;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestStatus;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeOperationsToolTest {

    @Test
    void createRequestUsesTrustedTenantCallAndCallerNumber() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String streamSid = "MZ-ops";
        String caller = "+56911111111";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UnansweredQuestionService questions = mock(UnansweredQuestionService.class);
        RealtimeToolService tools = service(customers, calls, requests, questions);

        CallSession call = trustedCall(businessId, streamSid);
        setId(call, callId);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(customers.findFirstByBusinessIdAndPhone(businessId, caller)).thenReturn(Optional.empty());

        BusinessRequest saved = new BusinessRequest();
        setId(saved, UUID.randomUUID());
        saved.setBusinessId(businessId);
        saved.setRequestType("cotizacion");
        saved.setTitle("Cotizar reparación");
        saved.setPriority(RequestPriority.HIGH);
        saved.setStatus(RequestStatus.OPEN);
        when(requests.createFromAi(eq(businessId), isNull(), eq(callId), eq("cotizacion"),
                eq("Cotizar reparación"), eq("Necesita precio estimado"), isNull(), eq(caller),
                eq(RequestPriority.HIGH), isNull())).thenReturn(saved);

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, caller, "+56222222222", streamSid);
        JSONObject result = new JSONObject(tools.execute(context, "create_request", new JSONObject()
                .put("requestType", "cotizacion")
                .put("title", "Cotizar reparación")
                .put("description", "Necesita precio estimado")
                .put("priority", "HIGH")
                .toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(saved.getId().toString(), result.getJSONObject("data").getString("requestId"));
        verify(requests).createFromAi(eq(businessId), isNull(), eq(callId), eq("cotizacion"),
                eq("Cotizar reparación"), eq("Necesita precio estimado"), isNull(), eq(caller),
                eq(RequestPriority.HIGH), isNull());
    }

    @Test
    void unansweredQuestionIsRecordedInsideTrustedCallContext() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String streamSid = "MZ-learning";
        String caller = "+56922222222";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UnansweredQuestionService questions = mock(UnansweredQuestionService.class);
        RealtimeToolService tools = service(customers, calls, requests, questions);

        CallSession call = trustedCall(businessId, streamSid);
        setId(call, callId);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(customers.findFirstByBusinessIdAndPhone(businessId, caller)).thenReturn(Optional.empty());

        UnansweredQuestion saved = new UnansweredQuestion();
        setId(saved, UUID.randomUUID());
        saved.setBusinessId(businessId);
        saved.setQuestion("¿Tienen estacionamiento?");
        saved.setOccurrences(1);
        saved.setStatus(QuestionStatus.OPEN);
        when(questions.record(businessId, callId, null, "¿Tienen estacionamiento?")).thenReturn(saved);

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, caller, "+56222222222", streamSid);
        JSONObject result = new JSONObject(tools.execute(context, "record_unanswered_question",
                new JSONObject().put("question", "¿Tienen estacionamiento?").toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(1, result.getJSONObject("data").getInt("occurrences"));
        verify(questions).record(businessId, callId, null, "¿Tienen estacionamiento?");
    }

    @Test
    void operationsToolsArePublishedToTheModel() {
        String tools = RealtimeToolDefinitions.all().toString();
        assertTrue(tools.contains("create_request"));
        assertTrue(tools.contains("record_unanswered_question"));
    }

    private static RealtimeToolService service(CustomerRepository customers,
                                               CallSessionRepository calls,
                                               BusinessRequestService requests,
                                               UnansweredQuestionService questions) {
        return new RealtimeToolService(
                mock(BusinessRepository.class), customers, mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class), mock(BookingRepository.class), calls,
                mock(BusinessScheduleService.class), requests, questions);
    }

    private static CallSession trustedCall(UUID businessId, String streamSid) {
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid(streamSid);
        return call;
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
