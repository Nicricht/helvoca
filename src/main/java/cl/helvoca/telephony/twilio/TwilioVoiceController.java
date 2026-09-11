package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.telephony.twilio.trial.TrialVoiceReply;
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

    private final TwilioCallService calls;
    private final TwimlFactory twiml;
    private final TrialVoiceProperties trial;
    private final TrialVoiceConversationService trialConversation;
    private final TrialConversationStateService trialState;
    private final CallSummaryService summaries;

    public TwilioVoiceController(TwilioCallService calls,
                                 TwimlFactory twiml,
                                 TrialVoiceProperties trial,
                                 TrialVoiceConversationService trialConversation,
                                 TrialConversationStateService trialState,
                                 CallSummaryService summaries) {
        this.calls = calls;
        this.twiml = twiml;
        this.trial = trial;
        this.trialConversation = trialConversation;
        this.trialState = trialState;
        this.summaries = summaries;
    }

    @PostMapping(value = "/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> incoming(@RequestParam("CallSid") String callSid,
                                           @RequestParam("From") String from,
                                           @RequestParam("To") String to) {
        try {
            return ResponseEntity.ok(calls.startInboundCall(callSid, from, to));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(twiml.rejectUnknownNumber());
        }
    }

    /**
     * Twilio Console outbound tests put the business Twilio number in From and
     * the tester phone in To. Keep this demo on its own Media Stream handshake
     * route so production inbound WebSockets remain signature-protected.
     */
    @PostMapping(value = "/outbound-test", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> outboundTest(@RequestParam("CallSid") String callSid,
                                               @RequestParam("From") String from,
                                               @RequestParam("To") String to) {
        try {
            return ResponseEntity.ok(calls.startOutboundTestCall(callSid, to, from));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(twiml.rejectUnknownNumber());
        }
    }

    @PostMapping(value = "/trial/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialIncoming(@RequestParam("CallSid") String callSid,
                                                @RequestParam("From") String from,
                                                @RequestParam("To") String to) {
        if (!trial.isEnabled()) {
            return ResponseEntity.ok(twiml.serviceUnavailable());
        }
        try {
            calls.startTrialInboundCall(callSid, from, to);
            return ResponseEntity.ok(twiml.trialGather(trial.getGreeting()));
        } catch (NotFoundException | IllegalArgumentException e) {
            return ResponseEntity.ok(twiml.trialSayAndHangup("No pude iniciar la demostración de RecepVoz para este número."));
        }
    }

    @PostMapping(value = "/trial/gather", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialGather(@RequestParam("CallSid") String callSid,
                                              @RequestParam(value = "SpeechResult", required = false) String speechResult) {
        if (!trial.isEnabled()) {
            return ResponseEntity.ok(twiml.serviceUnavailable());
        }
        try {
            RealtimeCallContext context = calls.getTrialContext(callSid);
            TrialVoiceReply reply = trialConversation.reply(context, speechResult);

            String transferTarget = trialState.consumeHumanTransferTarget(context.callId());
            if (transferTarget != null && !transferTarget.isBlank()) {
                return ResponseEntity.ok(twiml.trialTransfer(
                        "Claro, te comunico con una persona del negocio.", transferTarget));
            }

            if (reply.endCall()) {
                calls.markTrialEnded(callSid);
                summaries.generate(context.callId());
                return ResponseEntity.ok(twiml.trialSayAndHangup(reply.text()));
            }
            return ResponseEntity.ok(twiml.trialGather(reply.text()));
        } catch (NotFoundException | IllegalArgumentException e) {
            return ResponseEntity.ok(twiml.trialSayAndHangup("La sesión de prueba ya no está disponible."));
        }
    }

    @PostMapping(value = "/trial/transfer-result", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> trialTransferResult(@RequestParam("CallSid") String callSid,
                                                      @RequestParam(value = "DialCallStatus", required = false) String dialCallStatus) {
        if (!trial.isEnabled()) {
            return ResponseEntity.ok(twiml.serviceUnavailable());
        }
        try {
            RealtimeCallContext context = calls.getTrialContext(callSid);
            if ("completed".equalsIgnoreCase(dialCallStatus)) {
                calls.markTrialEnded(callSid);
                trialState.clear(context.callId());
                summaries.generate(context.callId());
                return ResponseEntity.ok(twiml.trialSayAndHangup("Gracias por comunicarte con nosotros. Hasta luego."));
            }
            return ResponseEntity.ok(twiml.trialGather(
                    "No pude comunicarte con una persona en este momento. Puedo seguir ayudándote por aquí."));
        } catch (NotFoundException | IllegalArgumentException e) {
            return ResponseEntity.ok(twiml.trialSayAndHangup("La sesión de prueba ya no está disponible."));
        }
    }

    @PostMapping(value = "/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestParam("StreamSid") String streamSid,
                                             @RequestParam("StreamEvent") String streamEvent,
                                             @RequestParam(value = "CallSid", required = false) String callSid,
                                             @RequestParam(value = "StreamError", required = false) String streamError) {
        return handleStreamStatus(streamSid, streamEvent, callSid, streamError);
    }

    @PostMapping(value = "/trial/stream-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> trialStreamStatus(@RequestParam("StreamSid") String streamSid,
                                                  @RequestParam("StreamEvent") String streamEvent,
                                                  @RequestParam(value = "CallSid", required = false) String callSid,
                                                  @RequestParam(value = "StreamError", required = false) String streamError) {
        if (!trial.isEnabled()) return ResponseEntity.status(403).build();
        return handleStreamStatus(streamSid, streamEvent, callSid, streamError);
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
            // A delayed callback for an unknown call is idempotently ignored.
        }
        return ResponseEntity.noContent().build();
    }
}
