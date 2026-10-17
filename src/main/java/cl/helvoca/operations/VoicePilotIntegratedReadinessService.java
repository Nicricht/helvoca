package cl.helvoca.operations;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * An explicit operator-requested, fail-closed integration of the tenant-local
 * voice readiness and Twilio GET-only ownership checks.
 *
 * Neither local nor carrier readiness is a live-call or commercial release
 * certificate. No background polling or calls are initiated here.
 */
@Service
public class VoicePilotIntegratedReadinessService {
    private final VoiceRealCallPreflightService local;
    private final VoiceCarrierOwnershipPreflightService carrier;

    public VoicePilotIntegratedReadinessService(VoiceRealCallPreflightService local,
                                                VoiceCarrierOwnershipPreflightService carrier) {
        this.local = local;
        this.carrier = carrier;
    }

    public Result inspect(String destinationE164) {
        VoiceRealCallPreflightService.Result localResult = local.inspect(destinationE164);
        if (!localResult.localPreflightPassed()) {
            // Important: never make a carrier API request for unapproved pilots,
            // an inactive number, missing provenance or unconfigured Gemini.
            return new Result("BLOCKED_LOCAL", false, false, "NOT_CHECKED",
                    localResult.blockers(), false, false);
        }

        VoiceCarrierOwnershipPreflightService.Result carrierResult;
        try {
            carrierResult = carrier.inspect(destinationE164);
        } catch (RuntimeException exception) {
            // Untrusted upstream failures must not turn into a positive result or
            // expose provider exception bodies to the authenticated caller.
            return new Result("BLOCKED_CARRIER", true, false,
                    "CARRIER_CHECK_UNAVAILABLE", List.of(), false, false);
        }
        boolean verified = carrierResult.carrierOwnershipVerified()
                && carrierResult.localCallAdmissionAllowed();
        return new Result(verified ? "LOCAL_AND_CARRIER_PREFLIGHT_PASSED" : "BLOCKED_CARRIER",
                true, verified, carrierResult.carrierCode(), List.of(), false, false);
    }

    public record Result(String state,
                         boolean localPreflightPassed,
                         boolean carrierPreflightPassed,
                         String carrierCode,
                         List<String> localBlockers,
                         boolean liveCallCertified,
                         boolean commercialGo) {}
}
