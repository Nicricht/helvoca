package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.telephony.twilio.trial.TrialVoiceReply;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/webhooks/v1/twilio")
public class TwilioVoiceController {
    private final TwilioCallService calls;
    private final TwimlFactory twiml;
    private final TrialVoiceProperties trial;
    private final TrialVoiceConversationService trialConversation;
    private final CallSummaryService summaries;

    public TwilioVoiceController(TwilioCallService calls,
                                 TwimlFactory twiml,
                                 TrialVoiceProperties trial,
                                 TrialVoiceConversationService trialConversation,
                                 CallSummaryService summaries) {
        this.calls = calls;
        this.twiml = twiml;
        this.trial = trial;
        this.trialConversation = trialConversation;
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
            return ResponseEntity.ok(twiml.trialSayAndHangup("No pude iniciar la demostración de Helvoca para este número."));
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
