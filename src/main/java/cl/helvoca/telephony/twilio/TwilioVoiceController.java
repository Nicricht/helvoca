package cl.helvoca.telephony.twilio;

import cl.helvoca.common.NotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/v1/twilio")
public class TwilioVoiceController {
    private final TwilioCallService calls;
    private final TwimlFactory twiml;

    public TwilioVoiceController(TwilioCallService calls, TwimlFactory twiml) {
        this.calls = calls;
        this.twiml = twiml;
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

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestParam("CallSid") String callSid,
                                       @RequestParam("CallStatus") String callStatus,
                                       @RequestParam(value = "CallDuration", required = false) Integer callDuration) {
        try {
            calls.updateStatus(callSid, callStatus, callDuration);
        } catch (NotFoundException ignored) {
            // A delayed callback for an unknown call is idempotently ignored.
        }
        return ResponseEntity.noContent().build();
    }
}
