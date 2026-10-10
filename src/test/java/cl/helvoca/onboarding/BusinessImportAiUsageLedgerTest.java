package cl.helvoca.onboarding;

import cl.helvoca.security.TenantProvider;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessImportAiUsageLedgerTest {
    private static final String MODEL = "gpt-4.1-mini";

    private static BusinessImportAiUsageLedger ledger(JdbcTemplate jdbc, TenantProvider tenant) {
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        return new BusinessImportAiUsageLedger(jdbc, tenant);
    }

    private static HttpResponse<String> response(int status, String body, boolean withHeaders) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        if (withHeaders) {
            when(response.headers()).thenReturn(HttpHeaders.of(Map.of("x-request-id", 
                    java.util.List.of("req_public_1")), (key, value) -> true));
        }
        return response;
    }

    private static Object[] lastWrite(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .reduce((first, last) -> last)
                .orElseThrow().getArguments();
    }

    @Test
    void everyStartedAttemptIsSavedBeforeAnyProviderCallAndTimeoutIsNotZeroCost() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);
        var recorder = ledger(jdbc, tenant);
        UUID attempt = UUID.randomUUID();
        recorder.started(attempt, MODEL);
        Object[] start = lastWrite(jdbc);
        assertEquals("STARTED", start[3]);
        assertEquals(attempt, start[2]);
        assertNull(start[9]);
        recorder.uncertain(attempt, MODEL);
        Object[] unknown = lastWrite(jdbc);
        assertEquals("UNCERTAIN", unknown[3]);
        assertNull(unknown[12]);
        verify(tenant, times(2)).requireBusinessId();
    }

    @Test
    void capturesExactReturnedUsageWithCachedTokensAndExplicitlyConfiguredEstimate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var recorder = ledger(jdbc, mock(TenantProvider.class));
        ReflectionTestUtils.setField(recorder, "pricedModel", MODEL);
        ReflectionTestUtils.setField(recorder, "inputRate", new BigDecimal("0.40"));
        ReflectionTestUtils.setField(recorder, "cachedRate", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(recorder, "outputRate", new BigDecimal("1.60"));

        recorder.received(UUID.randomUUID(), MODEL, response(200, new JSONObject()
                .put("id", "resp_123").put("model", MODEL)
                .put("usage", new JSONObject().put("input_tokens", 100).put("output_tokens", 30)
                        .put("input_tokens_details", new JSONObject().put("cached_tokens", 25)))
                .toString(), true));

        Object[] values = lastWrite(jdbc);
        assertEquals("RESPONSE", values[3]);
        assertEquals(MODEL, values[5]);
        assertEquals("resp_123", values[6]);
        assertEquals("req_public_1", values[7]);
        assertEquals(200, values[8]);
        assertEquals(100L, values[9]);
        assertEquals(25L, values[10]);
        assertEquals(30L, values[11]);
        assertEquals(new BigDecimal("0.00008050"), values[12]);
    }

    @Test
    void incompleteUsageAndMalformedProviderBodiesAreMarkedUnknownNotZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var recorder = ledger(jdbc, mock(TenantProvider.class));
        recorder.received(UUID.randomUUID(), MODEL, response(502, "not-json", false));
        Object[] badBody = lastWrite(jdbc);
        assertEquals(502, badBody[8]);
        assertNull(badBody[9]);
        assertNull(badBody[12]);

        recorder.received(UUID.randomUUID(), MODEL, response(200, "{}", false));
        assertNull(lastWrite(jdbc)[9]);

        recorder.received(UUID.randomUUID(), MODEL, response(200,
                new JSONObject().put("usage", new JSONObject().put("input_tokens", 22)).toString(), false));
        assertNull(lastWrite(jdbc)[11]);
    }

    @Test
    void uncalibratedOrChangedModelNeverClaimsInvoiceCost() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var recorder = ledger(jdbc, mock(TenantProvider.class));
        String body = new JSONObject()
                .put("model", MODEL)
                .put("usage", new JSONObject().put("input_tokens", 12).put("output_tokens", 3))
                .toString();
        recorder.received(UUID.randomUUID(), MODEL, response(200, body, false));
        assertNull(lastWrite(jdbc)[12]);

        ReflectionTestUtils.setField(recorder, "pricedModel", MODEL);
        ReflectionTestUtils.setField(recorder, "inputRate", new BigDecimal("0.40"));
        ReflectionTestUtils.setField(recorder, "cachedRate", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(recorder, "outputRate", new BigDecimal("1.60"));
        recorder.received(UUID.randomUUID(), "gpt-other", response(200, body, false));
        assertNull(lastWrite(jdbc)[12]);

        recorder.received(UUID.randomUUID(), MODEL, response(200, body.replace(MODEL, "unpriced-version"), false));
        assertNull(lastWrite(jdbc)[12]);

        ReflectionTestUtils.setField(recorder, "inputRate", BigDecimal.ZERO);
        recorder.received(UUID.randomUUID(), MODEL, response(200, body, false));
        assertNull(lastWrite(jdbc)[12]);
        ReflectionTestUtils.setField(recorder, "inputRate", new BigDecimal("0.4"));
        ReflectionTestUtils.setField(recorder, "cachedRate", BigDecimal.ZERO);
        recorder.received(UUID.randomUUID(), MODEL, response(200, body, false));
        assertNull(lastWrite(jdbc)[12]);
        ReflectionTestUtils.setField(recorder, "cachedRate", new BigDecimal("0.1"));
        ReflectionTestUtils.setField(recorder, "outputRate", BigDecimal.ZERO);
        recorder.received(UUID.randomUUID(), MODEL, response(200, body, false));
        assertNull(lastWrite(jdbc)[12]);
    }

    @Test
    void unknownCacheDetailIsNotInventedAndBillEstimateUsesFullStandardInputRate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var recorder = ledger(jdbc, mock(TenantProvider.class));
        ReflectionTestUtils.setField(recorder, "pricedModel", MODEL);
        ReflectionTestUtils.setField(recorder, "inputRate", new BigDecimal("0.4"));
        ReflectionTestUtils.setField(recorder, "cachedRate", new BigDecimal("0.1"));
        ReflectionTestUtils.setField(recorder, "outputRate", new BigDecimal("1.6"));
        String body = new JSONObject().put("model", MODEL)
                .put("usage", new JSONObject().put("input_tokens", 100).put("output_tokens", 20))
                .toString();
        recorder.received(UUID.randomUUID(), MODEL, response(200, body, false));
        assertNull(lastWrite(jdbc)[10]);
        assertEquals(new BigDecimal("0.00007200"), lastWrite(jdbc)[12]);
    }

    @Test
    void rejectsInvalidOrUntrustworthyTokenTotalsAndKeepsUnknownUsage() {
        assertNull(BusinessImportAiUsageLedger.parseTokens(null));
        for (JSONObject bad : new JSONObject[] {
                new JSONObject().put("input_tokens", -1).put("output_tokens", 10),
                new JSONObject().put("input_tokens", 10).put("output_tokens", -1),
                new JSONObject().put("input_tokens", 2).put("output_tokens", 3)
                        .put("input_tokens_details", new JSONObject().put("cached_tokens", -1)),
                new JSONObject().put("input_tokens", 2).put("output_tokens", 3)
                        .put("input_tokens_details", new JSONObject().put("cached_tokens", 9))
        }) assertNull(BusinessImportAiUsageLedger.parseTokens(bad));
        assertEquals(new BusinessImportAiUsageLedger.Tokens(2, 1L, 3),
                BusinessImportAiUsageLedger.parseTokens(new JSONObject()
                        .put("input_tokens", 2).put("output_tokens", 3)
                        .put("input_tokens_details", new JSONObject().put("cached_tokens", 1))));
    }

    @Test
    void trimsIdentifiersAndBoundsExternalStringsBeforeWritingSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var recorder = ledger(jdbc, mock(TenantProvider.class));
        recorder.received(UUID.randomUUID(), MODEL, response(200,
                new JSONObject().put("id", "x".repeat(200)).put("model", " ")
                        .put("usage", new JSONObject().put("input_tokens", 1).put("output_tokens", 2))
                        .toString(), false));
        assertEquals(180, ((String) lastWrite(jdbc)[6]).length());
        assertNull(lastWrite(jdbc)[5]);
    }

    @Test
    void databaseErrorsPropagateAndCannotSilentlyAuthorizePaidCalls() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenant = mock(TenantProvider.class);
        var recorder = ledger(jdbc, tenant);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new IllegalStateException("db unavailable"));
        assertThrows(IllegalStateException.class,
                () -> recorder.started(UUID.randomUUID(), MODEL));
    }
}
