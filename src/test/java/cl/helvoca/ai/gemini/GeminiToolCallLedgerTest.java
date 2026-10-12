package cl.helvoca.ai.gemini;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GeminiToolCallLedgerTest {
    @Test
    void duplicateActionIdReplaysTheOriginalBookingWithoutRepeatingSideEffects() {
        GeminiToolCallLedger ledger = new GeminiToolCallLedger();
        AtomicInteger executed = new AtomicInteger();
        String id = "create-booking-1";
        String args = "{\"confirmationToken\":\"token\",\"operationId\":\"operation\"}";

        GeminiToolCallLedger.Resolution first = ledger.resolve(id, "create_booking", args,
                () -> new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("bookingId", "booking-1")
                                .put("timesExecuted", executed.incrementAndGet())));
        GeminiToolCallLedger.Resolution replay = ledger.resolve(id, "create_booking", args,
                () -> {
                    fail("Duplicate function ID must not run the booking again");
                    return new JSONObject();
                });

        assertTrue(first.executed());
        assertFalse(replay.executed());
        assertEquals("booking-1", replay.result().getJSONObject("data").getString("bookingId"));
        assertEquals(1, replay.result().getJSONObject("data").getInt("timesExecuted"));
        assertEquals(1, executed.get());

        first.result().put("success", false);
        assertTrue(ledger.resolve(id, "create_booking", args, JSONObject::new)
                .result().getBoolean("success"), "Replay is a defensive copy of the committed result");
    }

    @Test
    void conflictsInActionNameOrArgumentsCannotReuseAnExistingToolId() {
        GeminiToolCallLedger ledger = new GeminiToolCallLedger();
        ledger.resolve("call-1", "create_booking", "{\"slot\":1}",
                () -> new JSONObject().put("success", true));

        GeminiToolCallLedger.Resolution changedTool = ledger.resolve(
                "call-1", "cancel_booking", "{\"slot\":1}",
                () -> {
                    fail("Name collision must not mutate");
                    return new JSONObject();
                });
        GeminiToolCallLedger.Resolution changedArgs = ledger.resolve(
                "call-1", "create_booking", "{\"slot\":2}",
                () -> {
                    fail("Argument collision must not mutate");
                    return new JSONObject();
                });

        for (GeminiToolCallLedger.Resolution blocked : new GeminiToolCallLedger.Resolution[]{
                changedTool, changedArgs}) {
            assertFalse(blocked.executed());
            assertFalse(blocked.result().getBoolean("success"));
            assertEquals("TOOL_CALL_ID_CONFLICT",
                    blocked.result().getJSONObject("error").getString("code"));
        }
    }

    @Test
    void safetyCapPreservesAllEarlierResultsAndRejectsNewActions() {
        GeminiToolCallLedger ledger = new GeminiToolCallLedger();
        for (int i = 0; i < GeminiToolCallLedger.MAX_CALLS; i++) {
            int number = i;
            assertTrue(ledger.resolve("operation-" + i, "list_services", "{}",
                    () -> new JSONObject().put("success", true).put("number", number)).executed());
        }

        GeminiToolCallLedger.Resolution blocked = ledger.resolve("new-operation", "cancel_booking", "{}",
                () -> {
                    fail("Must not execute operation after safety cap");
                    return new JSONObject();
                });
        assertFalse(blocked.executed());
        assertEquals("TOOL_CALL_LIMIT_REACHED", blocked.result().getJSONObject("error").getString("code"));
        GeminiToolCallLedger.Resolution oldest = ledger.resolve("operation-0", "list_services", "{}",
                () -> {
                    fail("Oldest operation must not be evicted");
                    return new JSONObject();
                });
        assertFalse(oldest.executed());
        assertEquals(0, oldest.result().getInt("number"));
    }

    @Test
    void concurrentRetriesCannotExecuteTheSameBookingTwice() throws Exception {
        GeminiToolCallLedger ledger = new GeminiToolCallLedger();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Future<GeminiToolCallLedger.Resolution>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(workers.submit(() -> {
                    start.await();
                    return ledger.resolve("same-booking", "create_booking", "{\"operationId\":\"a\"}", () ->
                            new JSONObject().put("success", true)
                                    .put("execution", executions.incrementAndGet()));
                }));
            }
            start.countDown();
            int newExecutions = 0;
            for (Future<GeminiToolCallLedger.Resolution> task : tasks) {
                var resolution = task.get();
                assertEquals(1, resolution.result().getInt("execution"));
                if (resolution.executed()) newExecutions++;
            }
            assertEquals(1, newExecutions);
            assertEquals(1, executions.get());
        }
    }
}
