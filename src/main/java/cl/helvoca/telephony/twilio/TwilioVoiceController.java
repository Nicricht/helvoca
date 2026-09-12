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
        log.info("Routing inbound Twilio call={} to GPT-Live SIP", callSid);
        return ResponseEntity.ok(liveSip.twiml(to, from, callSid));
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
        log.info("Routing outbound Twilio test call={} to GPT-Live SIP", callSid);
        return ResponseEntity.ok(liveSip.twiml(from, to, callSid));
    }

    /**
     * Compatibility ingress only. It never restores Trial, TTS, Media Streams or Polly.
     * Stale Twilio/TwiML configuration that still points here is routed into the same
     * GPT-Live SIP implementation as /outbound-test and is deliberately logged.
     */
    @PostMapping(value = "/trial/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> legacyOutboundTest(@RequestParam("CallSid") String callSid,
                                                     @RequestParam("From") String from,
                                                     @RequestParam("To") String to) {
        log.warn("Deprecated Twilio route /trial/voice used; routing call={} to canonical GPT-Live flow", callSid);
        return outboundTest(callSid, from, to);
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
            log.debug("Ignoring Twilio status for untracked call={}", callSid);
        }
        return ResponseEntity.noContent().build();
    }
}
