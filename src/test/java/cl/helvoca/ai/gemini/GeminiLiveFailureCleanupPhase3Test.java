package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Offline failures must release BOTH provider and carrier and finalize once.
 * No real Gemini API key or Twilio call is involved.
 */
class GeminiLiveFailureCleanupPhase3Test {
    private static final class Scenario {
        final RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-phase3-test");
        final VoiceTransportSession transport = mock(VoiceTransportSession.class);
        final RealtimeToolService tools = mock(RealtimeToolService.class);
        final CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        final CallSummaryService summaries = mock(CallSummaryService.class);
        final CallCertificationService certifications = mock(CallCertificationService.class);
        final VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        final WebSocket socket = mock(WebSocket.class);
        final GeminiLiveVoiceSession session;

        Scenario() {
            var props = new GeminiLiveProperties();
            props.setEnabled(true);
            props.setApiKey("synthetic-only");
            props.setModel("gemini-3.1-flash-live-preview");
            props.setVoice("Kore");
            when(tools.toolDefinitions(context)).thenReturn(new JSONArray());
            when(tools.buildInstructions(context)).thenReturn("Synthetic instructions");
            when(socket.sendText(any(CharSequence.class), eq(true)))
                    .thenReturn(CompletableFuture.completedFuture(socket));
            session = new GeminiLiveVoiceSession(
                    context, transport, props, tools, mock(CallTranscriptService.class),
                    summaries, lifecycle, certifications, health, HttpClient.newHttpClient());
        }

        void open() {
            session.onOpen(socket);
        }

        void assertFinalizedOnce() {
            verify(lifecycle, times(1)).updateStatus(context.callId(), "failed", null);
            verify(transport, times(1)).closeOnUpstreamFailure();
            verify(summaries, times(1)).generate(context.callId());
            verify(certifications, times(1)).verifyAfterCall(context.callId());
            assertEquals("OPEN", health.snapshot(GeminiLiveVoiceProvider.ID, true).state());
        }
    }

    @Test
    void websocketErrorAbortsUpstreamClosesCarrierAndNeverDoubleFinalizes() {
        var scenario = new Scenario();
        scenario.open();
        scenario.session.onError(scenario.socket,
                new IllegalStateException("sensitive-provider-token-not-for-logs"));
        scenario.session.onError(scenario.socket, new IllegalStateException("duplicate"));
        scenario.session.close();

        verify(scenario.socket, times(1)).abort();
        scenario.assertFinalizedOnce();
        assertFalse(scenario.health.snapshot(GeminiLiveVoiceProvider.ID, true)
                .detail().contains("sensitive-provider-token-not-for-logs"));
    }

    @Test
    void providerCloseFailureReleasesTheUpstreamEvenIfItsAlreadyClosing() {
        var scenario = new Scenario();
        scenario.open();
        scenario.session.onClose(scenario.socket, 1011, "upstream failure");
        verify(scenario.socket, times(1)).abort();
        scenario.assertFinalizedOnce();
    }

    @Test
    void abortAndCarrierCloseExceptionsStillAllowExactlyOnceFinalization() {
        var scenario = new Scenario();
        scenario.open();
        doThrow(new IllegalStateException("upstream already closed")).when(scenario.socket).abort();
        doThrow(new IllegalStateException("carrier transport failed"))
                .when(scenario.transport).closeOnUpstreamFailure();
        doThrow(new IllegalStateException("database unavailable"))
                .when(scenario.lifecycle).updateStatus(scenario.context.callId(), "failed", null);

        assertDoesNotThrow(() -> scenario.session.onError(scenario.socket,
                new IllegalStateException("upstream reset")));
        scenario.assertFinalizedOnce();
        verify(scenario.socket, times(1)).abort();
    }

    @Test
    void preConnectErrorClosesCarrierWithoutTryingToAbortMissingSocket() {
        var scenario = new Scenario();
        scenario.session.onError(scenario.socket, new IllegalStateException("connect failed"));
        verify(scenario.socket, never()).abort();
        scenario.assertFinalizedOnce();
    }

    @Test
    void normalEndRequestsGracefulCloseNotAbruptAbort() {
        var scenario = new Scenario();
        scenario.open();
        when(scenario.socket.sendClose(eq(WebSocket.NORMAL_CLOSURE), anyString()))
                .thenReturn(CompletableFuture.completedFuture(scenario.socket));
        scenario.session.close();
        scenario.session.close();

        verify(scenario.socket, times(1)).sendClose(eq(WebSocket.NORMAL_CLOSURE),
                eq("carrier stream ended"));
        verify(scenario.socket, never()).abort();
        verify(scenario.summaries, times(1)).generate(scenario.context.callId());
        verify(scenario.certifications, times(1)).verifyAfterCall(scenario.context.callId());
        verify(scenario.transport, never()).closeOnUpstreamFailure();
    }
}
