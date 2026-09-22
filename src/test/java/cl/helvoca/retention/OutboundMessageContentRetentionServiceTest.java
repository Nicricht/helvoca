package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboundMessageContentRetentionServiceTest {

    @Test
    void redactsOnlyExpiredTerminalOutboundMessageContentWithoutActiveLegalHold() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(4);

        OutboundMessageContentRetentionService service =
                new OutboundMessageContentRetentionService(
                        jdbc,
                        tenantProvider,
                        Clock.fixed(now, ZoneOffset.UTC));

        OutboundMessageContentRetentionService.Result result =
                service.redactExpiredContentForCurrentTenant();

        assertEquals(Instant.parse("2026-06-24T12:00:00Z"), result.cutoff());
        assertEquals(4, result.outboundMessagesRedacted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());

        String statement = sql.getValue();
        assertTrue(statement.stripLeading().toUpperCase().startsWith("UPDATE OUTBOUND_MESSAGE"));
        assertTrue(statement.contains("recipient_address = ?"));
        assertTrue(statement.contains("content_text = ?"));
        assertTrue(statement.contains("provider_message_id = NULL"));
        assertTrue(statement.contains("om.business_id = ?"));
        assertTrue(statement.contains("om.status IN ('SENT', 'FAILED', 'CANCELLED', 'BLOCKED')"));
        assertTrue(statement.contains("om.updated_at < ?"));
        assertTrue(statement.contains("retention_legal_hold"));
        assertTrue(statement.contains("h.business_id = om.business_id"));
        assertTrue(statement.contains("h.target_type = 'OUTBOUND_MESSAGE'"));
        assertTrue(statement.contains("h.target_id = om.id"));
        assertTrue(statement.contains("h.released_at IS NULL"));
        assertFalse(statement.contains("idempotency_key ="));
        assertFalse(statement.contains("operation_id ="));
        assertFalse(statement.contains("customer_id ="));
        assertArrayEquals(
                new Object[]{
                        "[redacted]",
                        "[redacted]",
                        businessId,
                        Instant.parse("2026-06-24T12:00:00Z"),
                        "[redacted]",
                        "[redacted]"
                },
                args.getValue());
    }

    @Test
    void failsClosedBeforeRedactionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        OutboundMessageContentRetentionService service =
                new OutboundMessageContentRetentionService(
                        jdbc,
                        tenantProvider,
                        Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(
                IllegalStateException.class,
                service::redactExpiredContentForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}
