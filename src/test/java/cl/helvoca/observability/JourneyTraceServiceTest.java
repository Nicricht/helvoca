package cl.helvoca.observability;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JourneyTraceServiceTest {

    @Test
    void buildsSummaryFromExistingSanitizedEventsInsideAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-27T05:00:00Z");

        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);

        List<JourneyTraceService.EventView> timeline = List.of(
                event(start, "CALL", "CALL_STARTED", "IN_PROGRESS", callId, null, "twilio",
                        null, null, null, false),
                event(start.plusSeconds(1), "RETRY", "RETRY_SCHEDULED", "RETRY_SCHEDULED",
                        callId, operationId, null, 1, 3, "TEMPORARY_BACKEND_FAILURE", false),
                event(start.plusSeconds(2), "RETRY", "SUCCEEDED_AFTER_RETRY", "SUCCEEDED_AFTER_RETRY",
                        callId, operationId, null, 2, 3, null, true),
                event(start.plusSeconds(3), "JOB", "OUTBOUND_MESSAGE_DISPATCH", "SUCCEEDED",
                        callId, operationId, null, 2, 5, null, true, "corr-1"),
                event(start.plusSeconds(4), "WEBHOOK", "PAYMENT_WEBHOOK", "FAILED",
                        callId, operationId, "mercadopago", null, null, "PAYMENT_WEBHOOK_FAILED", false),
                event(start.plusSeconds(5), "PAYMENT", "PAYMENT_STATE", "SUCCEEDED",
                        callId, operationId, "mercadopago", null, null, null, false)
        );

        when(jdbc.query(eq(JourneyTraceService.TRACE_SQL),
                any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(timeline);

        JourneyTraceService.TraceView result = new JourneyTraceService(jdbc, tenant)
                .get(callId.toString());

        assertEquals(callId.toString(), result.identifier());
        assertEquals(List.of(callId), result.callIds());
        assertEquals(List.of(operationId), result.operationIds());
        assertEquals(List.of("corr-1"), result.correlationIds());
        assertEquals(List.of("twilio", "mercadopago"), result.providers());
        assertEquals(2, result.observedRetries());
        assertTrue(result.recoveredAutomatically());
        assertEquals("WEBHOOK:PAYMENT_WEBHOOK", result.failureStage());
        assertEquals(5_000L, result.elapsedMs());
        assertEquals(JourneyTraceService.DUPLICATE_VISIBILITY, result.duplicateObservation());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(eq(JourneyTraceService.TRACE_SQL), params.capture(), any(RowMapper.class));
        assertEquals(businessId, params.getValue().getValue("businessId"));
        assertEquals(callId.toString(), params.getValue().getValue("identifier"));
        assertEquals(callId, params.getValue().getValue("uuidIdentifier"));
        verify(tenant).requireBusinessId();
    }

    @Test
    void nonUuidIdentifierIsSupportedWithoutGuessingAnEntityUuid() {
        UUID businessId = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-27T05:00:00Z");
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(jdbc.query(eq(JourneyTraceService.TRACE_SQL),
                any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(event(at, "JOB", "WHATSAPP_INBOUND_TEXT_PROCESS", "SUCCEEDED",
                        null, null, null, 1, 5, null, false, "corr-external")));

        new JourneyTraceService(jdbc, tenant).get("corr-external");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(eq(JourneyTraceService.TRACE_SQL), params.capture(), any(RowMapper.class));
        assertEquals("corr-external", params.getValue().getValue("identifier"));
        assertNull(params.getValue().getValue("uuidIdentifier"));
    }

    @Test
    void blankIdentifierFailsBeforeTenantOrDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);

        assertThrows(IllegalArgumentException.class, () -> new JourneyTraceService(jdbc, tenant).get("   "));

        verifyNoInteractions(jdbc, tenant);
    }

    @Test
    void unknownIdentifierReturnsNotFoundInsteadOfCrossTenantFallback() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(jdbc.query(eq(JourneyTraceService.TRACE_SQL),
                any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> new JourneyTraceService(jdbc, tenant).get("missing"));
    }

    @Test
    void queryDoesNotProjectSensitiveConversationOrPaymentPayloads() {
        String sql = JourneyTraceService.TRACE_SQL.toLowerCase();

        assertFalse(sql.contains("caller_number"));
        assertFalse(sql.contains("destination_number"));
        assertFalse(sql.contains("content_text"));
        assertFalse(sql.contains("recipient_address"));
        assertFalse(sql.contains("checkout_url"));
        assertFalse(sql.contains("payload_hash"));
        assertFalse(sql.contains("contact_phone"));
        assertTrue(sql.contains("payload ->> 'correlationid'"));
        assertTrue(sql.contains("a.duration_ms"));
        assertTrue(sql.contains("c.ai_model"));
        assertTrue(sql.contains("usage_meter_event"));
        assertFalse(sql.contains("access_token"));
        assertFalse(sql.contains("api_key"));
        assertFalse(sql.contains("authorization"));
        assertTrue(sql.contains("business_id = :businessid"));
    }

    private static JourneyTraceService.EventView event(
            Instant at,
            String stage,
            String event,
            String status,
            UUID callId,
            UUID operationId,
            String provider,
            Integer attemptNo,
            Integer maxAttempts,
            String errorCode,
            boolean recovered) {
        return event(at, stage, event, status, callId, operationId, provider,
                attemptNo, maxAttempts, errorCode, recovered, null);
    }

    private static JourneyTraceService.EventView event(
            Instant at,
            String stage,
            String event,
            String status,
            UUID callId,
            UUID operationId,
            String provider,
            Integer attemptNo,
            Integer maxAttempts,
            String errorCode,
            boolean recovered,
            String correlationId) {
        return new JourneyTraceService.EventView(
                at, stage, event, status, callId, operationId, UUID.randomUUID(),
                callId, provider, null, correlationId, attemptNo, maxAttempts,
                errorCode, null, null, recovered, null);
    }
}
