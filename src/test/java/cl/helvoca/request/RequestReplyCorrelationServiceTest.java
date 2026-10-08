package cl.helvoca.request;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestReplyCorrelationServiceTest {
    @Test
    void onlySuccessfulServerToolResponsesProduceReferences() {
        UUID request = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        String result = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("requestId", request.toString())
                        .put("operationId", operation.toString())
                        .put("status", "OPEN"))
                .toString();

        assertEquals(new RequestReplyCorrelationService.CreatedRequest(request, operation),
                RequestReplyCorrelationService.verifiedToolResult("create_request", result));
        assertNull(RequestReplyCorrelationService.verifiedToolResult("create_booking", result));
        assertNull(RequestReplyCorrelationService.verifiedToolResult("create_request", "not json"));
        assertNull(RequestReplyCorrelationService.verifiedToolResult("create_request",
                new JSONObject(result).put("success", false).toString()));
        assertNull(RequestReplyCorrelationService.verifiedToolResult("create_request",
                new JSONObject().put("success", true).put("data",
                        new JSONObject().put("status", "RESOLVED")
                                .put("requestId", request.toString())
                                .put("operationId", operation.toString())).toString()));
        assertNull(RequestReplyCorrelationService.verifiedToolResult("create_request",
                "{\"success\":true,\"data\":{\"requestId\":\"forged\",\"operationId\":\"no\"}}"));
    }

    @Test
    void duplicateReferencesInsertOnlyOnceAndNeverChangeRequestStatus() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RequestReplyCorrelationService service = new RequestReplyCorrelationService(jdbc);
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var ref = new RequestReplyCorrelationService.CreatedRequest(requestId, operationId);

        service.capture(businessId, conversationId, messageId, List.of(ref, ref));

        verify(jdbc, times(1)).update(
                argThat(sql -> sql.contains("business_request_reply_correlation")
                        && sql.contains("REQUEST_CREATED")
                        && sql.contains("ON CONFLICT")
                        && !sql.contains("UPDATE public.business_request SET status")),
                eq(businessId), eq(requestId), eq(operationId), eq(conversationId), eq(messageId));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void missingEvidenceDoesNotTouchDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RequestReplyCorrelationService service = new RequestReplyCorrelationService(jdbc);
        service.capture(null, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new RequestReplyCorrelationService.CreatedRequest(UUID.randomUUID(), UUID.randomUUID())));
        service.capture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of());
        verifyNoInteractions(jdbc);
    }
}
