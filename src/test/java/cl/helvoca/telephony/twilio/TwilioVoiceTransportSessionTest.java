package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TwilioVoiceTransportSessionTest {

    @Test
    void blocksAllOutboundMediaAfterDeferredHangupCompletes() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        when(socket.isOpen()).thenReturn(true);
        when(control.hangup("AC-test", "CA-test")).thenReturn(true);

        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, "MZ-test", "AC-test", "CA-test", control);

        transport.sendAudio("MZ-test", "before-hangup");
        assertTrue(transport.endAfterPlayback());

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, times(2)).sendMessage(sent.capture());
        String markName = sent.getAllValues().stream()
                .map(TextMessage::getPayload)
                .map(JSONObject::new)
                .filter(message -> "mark".equals(message.optString("event")))
                .map(message -> message.getJSONObject("mark").getString("name"))
                .findFirst()
                .orElseThrow();

        transport.onPlaybackMark(markName);

        verify(control).hangup("AC-test", "CA-test");
        assertFalse(transport.isOpen());

        transport.sendAudio("MZ-test", "after-hangup");
        transport.clearPlayback("MZ-test");

        verify(socket, times(2)).sendMessage(any(TextMessage.class));
    }


    @Test
    void bargeInCancelsOutstandingFarewellMarkAndAllowsALaterRealGoodbye() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        when(socket.isOpen()).thenReturn(true);
        when(control.hangup("AC-test", "CA-test")).thenReturn(true);
        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, "MZ-test", "AC-test", "CA-test", control);

        assertTrue(transport.endAfterPlayback());
        ArgumentCaptor<TextMessage> first = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket).sendMessage(first.capture());
        String interruptedMark = new JSONObject(first.getValue().getPayload())
                .getJSONObject("mark").getString("name");

        transport.clearPlayback("MZ-test");
        // Twilio acknowledges marks when clearing audio; this is not a goodbye.
        transport.onPlaybackMark(interruptedMark);
        verify(control, never()).hangup(anyString(), anyString());
        assertTrue(transport.isOpen());
        transport.sendAudio("MZ-test", "new-answer");

        assertTrue(transport.endAfterPlayback());
        ArgumentCaptor<TextMessage> all = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, times(4)).sendMessage(all.capture());
        String actualGoodbyeMark = new JSONObject(all.getAllValues().get(3).getPayload())
                .getJSONObject("mark").getString("name");
        transport.onPlaybackMark(interruptedMark);
        verify(control, never()).hangup(anyString(), anyString());

        transport.onPlaybackMark(actualGoodbyeMark);
        verify(control).hangup("AC-test", "CA-test");
        assertFalse(transport.isOpen());
    }

    @Test
    void clearForDifferentStreamDoesNotCancelFarewell() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        when(socket.isOpen()).thenReturn(true);
        when(control.hangup("AC-test", "CA-test")).thenReturn(true);
        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, "MZ-test", "AC-test", "CA-test", control);

        assertTrue(transport.endAfterPlayback());
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket).sendMessage(sent.capture());
        String mark = new JSONObject(sent.getValue().getPayload()).getJSONObject("mark").getString("name");
        transport.clearPlayback("MZ-other");
        transport.onPlaybackMark(mark);

        verify(socket, times(1)).sendMessage(any(TextMessage.class));
        verify(control).hangup("AC-test", "CA-test");
    }

    @Test
    void upstreamFailureClosesOutboundVoiceBeforeCarrierHangup() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        when(socket.isOpen()).thenReturn(true);
        when(control.hangup("AC-test", "CA-test")).thenReturn(true);

        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, "MZ-test", "AC-test", "CA-test", control);

        transport.closeOnUpstreamFailure();

        verify(control).hangup("AC-test", "CA-test");
        assertFalse(transport.isOpen());

        transport.sendAudio("MZ-test", "late-audio");
        verify(socket, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void rejectedDeferredHangupReopensOutputInsteadOfLeavingDeadAir() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        when(socket.isOpen()).thenReturn(true);
        when(control.hangup("AC-test", "CA-test")).thenReturn(false);

        TwilioVoiceTransportSession transport = new TwilioVoiceTransportSession(
                socket, "MZ-test", "AC-test", "CA-test", control);

        assertTrue(transport.endAfterPlayback());

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, times(1)).sendMessage(sent.capture());
        String markName = new JSONObject(sent.getValue().getPayload())
                .getJSONObject("mark")
                .getString("name");

        transport.onPlaybackMark(markName);

        verify(control).hangup("AC-test", "CA-test");
        assertTrue(transport.isOpen());

        transport.sendAudio("MZ-test", "recovered-output");
        verify(socket, times(2)).sendMessage(any(TextMessage.class));
    }

}
