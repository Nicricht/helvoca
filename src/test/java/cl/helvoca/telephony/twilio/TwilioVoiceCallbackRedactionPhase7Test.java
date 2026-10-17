package cl.helvoca.telephony.twilio;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.voice.VoiceCallRouter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Valid carrier signatures/bindings must not make untrusted callback payloads
 * safe for logs. Synthetic data only; this test makes no carrier requests.
 */
class TwilioVoiceCallbackRedactionPhase7Test {
    @Test
    void authenticatedStreamErrorAndUnknownEventNeverLogUntrustedCarrierPayload() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwilioCallBindingGuard guard = mock(TwilioCallBindingGuard.class);
        String callSid = "CA00000000000000000000000000000022";
        String streamSid = "MZ00000000000000000000000000000033";
        String accountSid = "AC00000000000000000000000000000011";
        String secret = "private-authorization-token-MUST-NOT-APPEAR";
        String eventSecret = "stream-synthetic-" + secret;
        when(guard.allowsStreamCallback(callSid, streamSid, accountSid)).thenReturn(true);

        TwilioVoiceController controller = new TwilioVoiceController(
                calls, mock(VoiceCallRouter.class), mock(CallSummaryService.class), new TwilioProperties());
        ReflectionTestUtils.setField(controller, "bindingGuard", guard);

        Logger logger = (Logger) LoggerFactory.getLogger(TwilioVoiceController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertEquals(204, controller.streamStatus(
                    streamSid, "stream-error", callSid, secret, accountSid).getStatusCode().value());
            assertEquals(204, controller.streamStatus(
                    streamSid, eventSecret, callSid, null, accountSid).getStatusCode().value());

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage).toList();
            assertEquals(2, messages.size());
            assertTrue(messages.get(0).contains("carrier detail suppressed"));
            assertTrue(messages.get(1).contains("callback"));
            assertFalse(String.join("\n", messages).contains(secret));
            verify(calls).markStreamStopped(streamSid);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
