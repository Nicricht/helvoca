package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/webhooks/v1/twilio")
public class TwilioVoiceController {
    private static final Logger log = LoggerFactory.getLogger(TwilioVoiceController.class);
    private static final String SILENT_HANGUP_TWIML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";

    private final TwilioCallService calls;
    private final OpenAiLiveSipService liveSip;
    private final CallSummaryService summaries;

    public TwilioVoiceController(TwilioCallService calls,
                                 OpenAiLiveSipService liveSip,
                                 CallSummaryService summaries) {
        this.calls = calls;
        this.liveSip = liveSip;
        this.summaries = summaries;
    }

    @PostMapping(value = "/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> incoming(@RequestParam("CallSid") String callSid,
                                           @RequestParam("From") String from,
                                           @RequestParam("To") String to) {
        if (!liveSip.isReady()) {
            log.warn("Blocked inbound voice because GPT-Live SIP is not ready call={}", callSid);
            return ResponseEntity.ok(SILENT_HANGUP_TWIML);
        }
        return ResponseEntity.ok(liveSip.twiml(to, from));
    }

    @PostMapping(value = "/outbound-test", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> outboundTest(@RequestParam("CallSid") String callSid,
                                               @RequestParam("From") String from,
                                               @RequestParam("To") String to) {
        if (!liveSip.isReady()) {
            log.warn("Blocked outbound voice test because GPT-Live SIP is not ready call={}", callSid);
            return ResponseEntity.ok(SILENT_HANGUP_TWIML);
        }
        return ResponseEntity.ok(liveSip.twiml(from, to));
    }

    /**
     * Backwards-compatible Twilio route. This does not restore the removed Trial/Polly stack.
     * Any stale Twilio configuration still pointing at /trial/voice is routed into the exact
     * same GPT-Live-only outbound flow as /outbound-test instead of dropping the call with 404.
     */
    @PostMapping(value = "/trial/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> legacyOutboundTest(@RequestParam("CallSid") String callSid,
                                                     @RequestParam("From") String from,
                                                     @RequestParam("To") String to) {
        log.warn("Legacy Twilio route /trial/voice used; routing to GPT-Live outbound flow call={}", callSid);
        return outboundTest(callSid, from, to);
    }

    @PostMapping(value = "/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestParam("StreamSid") String streamSid,
                                             @RequestParam("StreamEvent") String streamEvent,
                                             @RequestParam(value = "CallSid", required = false) String callSid,
                                             @RequestParam(value = "StreamError", required = false) String streamError) {
        if ("stream-stopped".equalsIgnoreCase(streamEvent) || "stream-error".equalsIgnoreCase(streamEvent)) {
            calls.markStreamStopped(streamSid);
        }
        if ("stream-error".equalsIgnoreCase(streamEvent)) {
            log.warn("Twilio Media Stream error call={} stream={} error={}", callSid, streamSid,
                    streamError == null ? "unknown" : streamError);
        } else {
            log.info("Twilio Media Stream event call={} stream={} event={}", callSid, streamSid, streamEvent);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestParam("CallSid") String callSid,
                                       @RequestParam("CallStatus") String callStatus,
                                       @RequestParam(value = "CallDuration", required = false) Integer callDuration) {
        try {
            UUID callId = calls.updateStatus(callSid, callStatus, callDuration);
            if (TwilioCallService.mapStatus(callStatus).terminal()) {
                summaries.generate(callId);
            }
        } catch (NotFoundException ignored) {
            // Direct GPT-Live SIP calls are tracked under the OpenAI Live session id.
        }
        return ResponseEntity.noContent().build();
    }
}
