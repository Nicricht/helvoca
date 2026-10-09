package cl.helvoca.request;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestReplyDeliveryEvidenceServiceTest {
    @Test
    void confirmedDeliveredAndReadStatusUseTheExactPersistedProviderReceipt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new RequestReplyDeliveryEvidenceService(jdbc);
        UUID tenant = UUID.randomUUID();
        UUID message = UUID.randomUUID();

        service.recordMetaReceipt(tenant, message, "wamid.A", "DELIVERED");
        service.recordMetaReceipt(tenant, message, "wamid.A", "READ");

        verify(jdbc).update(argThat(sql ->
                    sql.contains("business_request_reply_delivery_event")
                    && sql.contains("business_request_reply_correlation")
                    && sql.contains("m.provider_message_id = ?")
                    && sql.contains("m.provider_delivery_status = ?")
                    && sql.contains("ON CONFLICT")
                    && !sql.contains("UPDATE public.business_request")),
                eq("DELIVERED"), eq("DELIVERED"), eq(tenant), eq(message),
                eq("wamid.A"), eq("DELIVERED"), eq("DELIVERED"));
        verify(jdbc).update(anyString(), eq("READ"), eq("READ"), eq(tenant), eq(message),
                eq("wamid.A"), eq("READ"), eq("READ"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void missingAndUnconfirmedProviderEventsCannotCreateEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new RequestReplyDeliveryEvidenceService(jdbc);
        UUID tenant = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        service.recordMetaReceipt(null, message, "wamid.A", "DELIVERED");
        service.recordMetaReceipt(tenant, null, "wamid.A", "DELIVERED");
        service.recordMetaReceipt(tenant, message, null, "DELIVERED");
        service.recordMetaReceipt(tenant, message, " ", "DELIVERED");
        service.recordMetaReceipt(tenant, message, "wamid.A", "SENT");
        service.recordMetaReceipt(tenant, message, "wamid.A", "FAILED");
        service.recordMetaReceipt(tenant, message, "wamid.A", null);
        verifyNoInteractions(jdbc);
    }
}
