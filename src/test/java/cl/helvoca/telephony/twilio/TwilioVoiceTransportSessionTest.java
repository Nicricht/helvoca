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
}
