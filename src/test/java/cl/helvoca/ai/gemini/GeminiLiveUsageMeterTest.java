package cl.helvoca.ai.gemini;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLiveUsageMeterTest {
    @Test
    void parsesProviderAudioTextThoughtsCacheWithoutDoubleCountingModalities() {
        JSONObject usage = new JSONObject()
                .put("promptTokenCount", 120)
                .put("responseTokenCount", 48)
                .put("thoughtsTokenCount", 12)
                .put("cachedContentTokenCount", 16)
                .put("totalTokenCount", 180)
                .put("promptTokensDetails", new JSONArray()
                        .put(new JSONObject().put("modality", "AUDIO").put("tokenCount", 74))
                        .put(new JSONObject().put("modality", "TEXT").put("tokenCount", 30))
                        .put(new JSONObject().put("modality", "audio").put("tokenCount", 5))
                        .put(new JSONObject().put("modality", "IMAGE").put("tokenCount", 11)))
                .put("responseTokensDetails", new JSONArray()
                        .put(new JSONObject().put("modality", "AUDIO").put("tokenCount", 33))
                        .put(new JSONObject().put("modality", "TEXT").put("tokenCount", 15)));
        GeminiLiveUsageMeter.Snapshot snap = GeminiLiveUsageMeter.parse(usage);
        assertEquals(120L, snap.promptTokens());
        assertEquals(48L, snap.responseTokens());
        assertEquals(12L, snap.thoughtsTokens());
        assertEquals(16L, snap.cachedTokens());
        assertEquals(180L, snap.totalTokens());
        assertEquals(79L, snap.inputAudioTokens());
        assertEquals(33L, snap.outputAudioTokens());
        assertEquals(30L, snap.inputTextTokens());
        assertEquals(15L, snap.outputTextTokens());
        assertTrue(snap.hasTotalTokenCount());
    }

    @Test
    void missingOrMalformedDetailsNeverLookLikeNegativeSpending() {
        GeminiLiveUsageMeter.Snapshot absent = GeminiLiveUsageMeter.parse(null);
        assertEquals(0L, absent.totalTokens());
        assertEquals(0L, absent.inputAudioTokens());
        assertFalse(absent.hasTotalTokenCount());

        GeminiLiveUsageMeter.Snapshot partial = GeminiLiveUsageMeter.parse(new JSONObject()
                .put("promptTokenCount", -10)
                .put("responseTokenCount", -5)
                .put("totalTokenCount", -2)
                .put("promptTokensDetails", new JSONArray()
                        .put(JSONObject.NULL)
                        .put("not-a-token-object")
                        .put(new JSONObject().put("modality", "AUDIO").put("tokenCount", -7))
                        .put(new JSONObject().put("modality", "TEXT").put("tokenCount", 2)))
                .put("responseTokensDetails", new JSONArray()
                        .put(new JSONObject().put("modality", "VIDEO").put("tokenCount", 8))));
        assertEquals(0L, partial.promptTokens());
        assertEquals(0L, partial.responseTokens());
        assertEquals(0L, partial.totalTokens());
        assertEquals(0L, partial.inputAudioTokens());
        assertEquals(2L, partial.inputTextTokens());
        assertEquals(0L, partial.outputAudioTokens());
        assertEquals(0L, partial.outputTextTokens());
        assertTrue(partial.hasTotalTokenCount());
    }

    @Test
    void recordsDistinctEventsWithoutSummingOrLeakingProviderPayload() {
        UUID call = UUID.randomUUID();
        UUID business = UUID.randomUUID();
        GeminiLiveUsageMeter meter = new GeminiLiveUsageMeter(call, business, "gemini-3.8-live");
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiLiveUsageMeter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            meter.record(null);
            assertEquals(0, meter.snapshotCount());
            JSONObject input = new JSONObject()
                    .put("totalTokenCount", 23)
                    .put("apiKey", "SENSITIVE_PRIVATE_KEY")
                    .put("transcription", "SENSITIVE_PATIENT_NAME");
            meter.record(input);
            meter.record(input);  // two events, not an inferred billed total of 46
            assertEquals(2, meter.snapshotCount());
            meter.logFinalSummary();
            List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertEquals(3, logs.size());
            assertTrue(logs.get(0).contains("call_id=" + call));
            assertTrue(logs.get(0).contains("business_id=" + business));
            assertTrue(logs.get(0).contains("model=gemini-3.8-live"));
            assertTrue(logs.get(0).contains("sequence=1"));
            assertTrue(logs.get(1).contains("sequence=2"));
            assertTrue(logs.get(1).contains("kind=PROVIDER_EVENT_NOT_INVOICE"));
            assertTrue(logs.get(2).contains("provider_usage_events=2 usage_reported=true"));
            assertFalse(String.join(" ", logs).contains("SENSITIVE_PRIVATE_KEY"));
            assertFalse(String.join(" ", logs).contains("SENSITIVE_PATIENT_NAME"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void recordsUnavailableProviderUsageForFailedSessionWithoutClaimingZeroInvoice() {
        GeminiLiveUsageMeter meter = new GeminiLiveUsageMeter(
                UUID.randomUUID(), UUID.randomUUID(), "gemini-3.8-live");
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiLiveUsageMeter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            meter.logFinalSummary();
            assertEquals(0, meter.snapshotCount());
            assertTrue(appender.list.get(0).getFormattedMessage()
                    .contains("provider_usage_events=0 usage_reported=false billed_cost_usd=UNKNOWN"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
