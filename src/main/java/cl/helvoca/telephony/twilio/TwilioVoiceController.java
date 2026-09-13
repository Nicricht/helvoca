package cl.helvoca.telephony.twilio;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.voice.VoiceCallRouter;
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
    private final VoiceCallRouter voiceRouter;
    private final CallSummaryService summaries;
    private final TwilioProperties properties;

    public TwilioVoiceController(TwilioCallService calls,
                                 VoiceCallRouter voiceRouter,
                                 CallSummaryService summaries,
                                 TwilioProperties properties) {
        this.calls = calls;
        this.voiceRouter = voiceRouter;
        this.summaries = summaries;
        this.properties = properties;
    }

    @PostMapping(value = "/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> incoming(@RequestParam("CallSid") String callSid,
                                           @RequestParam("From") String from,
                                           @RequestParam("To") String to) {
        return route(to, from, callSid, "inbound");
    }

    @PostMapping(value = "/outbound-test", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> outboundTest(@RequestParam("CallSid") String callSid,
                                               @RequestParam("From") String from,
                                               @RequestParam("To") String to) {
        return route(from, to, callSid, "outbound-test");
    }

    /**
     * Internal, disabled-by-default ingress for an explicitly authorized
     * inbound-equivalent certification call. Twilio signature validation still
     * applies to this endpoint before the controller is reached.
     */
    @PostMapping(value = "/inbound-certification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> inboundCertification(@RequestParam("CallSid") String callSid,
                                                       @RequestParam("From") String from,
                                                       @RequestParam("To") String to) {
        if (!properties.isCertificationIngressEnabled()) {
            log.warn("Blocked disabled Twilio certification ingress call={}", callSid);
            return ResponseEntity.ok(SILENT_HANGUP_TWIML);
        }
        return route(from, to, callSid, "inbound-certification");
    }

    @PostMapping(value = "/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestParam("StreamSid") String streamSid,
                                             @RequestParam("StreamEvent") String streamEvent,
                                             @RequestParam(value = "CallSid", required = false) String callSid,
                                             @RequestParam(value = "StreamError", required = false) String streamError) {
        if ("stream-stopped".equalsIgnoreCase(streamEvent)
                || "stream-error".equalsIgnoreCase(streamEvent)) {
            calls.markStreamStopped(streamSid);
        }
        if ("stream-error".equalsIgnoreCase(streamEvent)) {
            log.warn("Twilio Media Stream error call={} stream={} error={}",
                    callSid, streamSid, streamError == null ? "unknown" : streamError);
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
            log.debug("Ignoring Twilio status for untracked call={}", callSid);
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<String> route(String businessPhone,
                                         String callerPhone,
                                         String callSid,
                                         String direction) {
        return voiceRouter.route(businessPhone, callerPhone, callSid)
                .map(decision -> {
                    log.info("Routing Twilio {} call={} provider={} mode={}",
                            direction, callSid, decision.providerId(), decision.mode());
                    return ResponseEntity.ok(decision.twiml());
                })
                .orElseGet(() -> {
                    log.error("Blocking Twilio {} call={} because no healthy voice provider is available",
                            direction, callSid);
                    return ResponseEntity.ok(SILENT_HANGUP_TWIML);
                });
    }
}
