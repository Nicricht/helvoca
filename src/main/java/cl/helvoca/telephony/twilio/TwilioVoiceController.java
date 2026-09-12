package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
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
    private final TwimlFactory twiml;
    private final OpenAiLiveSipService liveSip;
    private final TrialVoiceProperties trial;
    private final TrialVoiceConversationService trialConversation;
    private final TrialConversationStateService trialState;
    private final CallSummaryService summaries;

    public TwilioVoiceController(TwilioCallService calls,
                                 TwimlFactory twiml,
                                 OpenAiLiveSipService liveSip,
                                 TrialVoiceProperties trial,
                                 TrialVoiceConversationService trialConversation,
                                 TrialConversationStateService trialState,
                                 CallSummaryService summaries) {
        this.calls = calls;
        this.twiml = twiml;
        this.liveSip = liveSip;
        this.trial = trial;
        this.trialConversation = trialConversation;
        this.trialState = trialState;
        this.summaries = summaries;
    }

    /**
     * Production voice is GPT-Live SIP-only. If Live is not ready, fail closed
     * instead of falling back to the legacy Media Stream/STT/TTS architecture.
     */
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

    /**
     * Twilio Console outbound tests are GPT-Live-only. A Live configuration
     * problem must never silently fall back to legacy Gather/Say/Polly TTS.
     */
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
     * Retired trial endpoint. Keeping the URL as a silent tombstone prevents a
     * stale Twilio Console configuration from accidentally exercising the old
     * trial conversation architecture. Normal tests must use /outbound-test.
     */
    @PostMapping(value = "/trial/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialIncoming(@RequestParam("CallSid") String callSid,
                                                @RequestParam("From") String from,
                                                @RequestParam("To") String to) {
        log.warn("Blocked retired /trial/voice endpoint call={}; use /outbound-test", callSid);
        return ResponseEntity.ok(SILENT_HANGUP_TWIML);
    }

    /**
     * Retired legacy Gather/STT/TTS endpoint. It intentionally never calls
     * TrialVoiceConversationService or TwimlFactory.trialGather/trialSay.
     */
    @PostMapping(value = "/trial/gather", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialGather(@RequestParam("CallSid") String callSid,
                                              @RequestParam(value = "SpeechResult", required = false) String speechResult) {
        log.warn("Blocked retired /trial/gather endpoint call={}", callSid);
        return ResponseEntity.ok(SILENT_HANGUP_TWIML);
    }

    /** Retired legacy transfer callback from the Gather/Say trial flow. */
    @PostMapping(value = "/trial/transfer-result", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialTransferResult(@RequestParam("CallSid") String callSid,
                                                      @RequestParam(value = "DialCallStatus", required = false) String dialCallStatus) {
        log.warn("Blocked retired /trial/transfer-result endpoint call={}", callSid);
        return ResponseEntity.ok(SILENT_HANGUP_TWIML);
    }

    @PostMapping(value = "/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestParam("StreamSid") String streamSid,
                                             @RequestParam("StreamEvent") String streamEvent,
                                             @RequestParam(value = "CallSid", required = false) String callSid,
                                             @RequestParam(value = "StreamError", required = false) String streamError) {
        return handleStreamStatus(streamSid, streamEvent, callSid, streamError);
    }

    /** Retired callback for the old trial Media Stream path. */
    @PostMapping(value = "/trial/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> trialStreamStatus(@RequestParam("StreamSid") String streamSid,
                                                  @RequestParam("StreamEvent") String streamEvent,
                                                  @RequestParam(value = "CallSid", required = false) String callSid,
                                                  @RequestParam(value = "StreamError", required = false) String streamError) {
        log.warn("Ignored retired /trial/stream-status callback call={} stream={}", callSid, streamSid);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> handleStreamStatus(String streamSid,
                                                    String streamEvent,
                                                    String callSid,
                                                    String streamError) {
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
            // Direct GPT-Live SIP calls are tracked under the OpenAI Live session id,
            // so delayed Twilio status callbacks have no matching call record.
        }
        return ResponseEntity.noContent().build();
    }
}
