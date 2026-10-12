package cl.helvoca.operations;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only, tenant-scoped LOCAL preflight. It does not call Twilio/Gemini,
 * authorize spending, change pilot flags, or certify an actual phone call.
 */
@Service
public class VoiceRealCallPreflightService {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern TWILIO_PHONE_SID = Pattern.compile("^PN[0-9a-fA-F]{32}$");

    private final TenantProvider tenants;
    private final PhoneNumberRepository numbers;
    private final ChannelRuntimeReadinessService runtime;
    private final ControlledPilotExternalEffectGuard pilot;

    public VoiceRealCallPreflightService(TenantProvider tenants,
                                        PhoneNumberRepository numbers,
                                        ChannelRuntimeReadinessService runtime,
                                        ControlledPilotExternalEffectGuard pilot) {
        this.tenants = tenants;
        this.numbers = numbers;
        this.runtime = runtime;
        this.pilot = pilot;
    }

    @Transactional(readOnly = true)
    public Result inspect(String destinationE164) {
        UUID tenantId = tenants.requireBusinessId();
        List<String> blockers = new ArrayList<>();

        boolean validPhone = destinationE164 != null && E164.matcher(destinationE164).matches();
        if (!validPhone) blockers.add("INVALID_DESTINATION_E164");

        PhoneNumber number = validPhone
                ? numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId).stream()
                        .filter(candidate -> destinationE164.equals(candidate.getPhoneNumber()))
                        .findFirst().orElse(null)
                : null;
        if (number == null || !number.isActive() || !"TWILIO".equals(number.getProvider())) {
            blockers.add("TENANT_TWILIO_PHONE_NOT_ACTIVE");
        }
        if (number == null || number.getExternalId() == null
                || !TWILIO_PHONE_SID.matcher(number.getExternalId()).matches()) {
            blockers.add("TWILIO_PHONE_SID_NOT_BOUND");
        }

        ChannelRuntimeReadinessService.ChannelRuntimeReadiness state = runtime.snapshot();
        if (!state.twilio().credentialsConfigured() || !state.twilio().securePublicBaseUrlConfigured()
                || !state.twilio().mediaStreamUrlConfigured()) {
            blockers.add("TWILIO_RUNTIME_NOT_READY");
        }
        boolean geminiReady = state.voice().ready()
                && "gemini".equals(state.voice().selectedProvider())
                && state.voice().providers().stream().anyMatch(provider ->
                    "gemini".equals(provider.providerId()) && provider.configured() && provider.available());
        if (!geminiReady) blockers.add("GEMINI_MEDIA_PROVIDER_NOT_READY");

        ControlledPilotExternalEffectGuard.Decision guard = pilot.evaluate(
                tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE);
        if (!guard.pilotManaged()) blockers.add("TENANT_NOT_PILOT_ENROLLED");
        if (!guard.allowed()) blockers.add("PILOT_VOICE_EFFECT_BLOCKED");

        // Never treat local readiness or a successful CI as evidence of a real call.
        return new Result(
                blockers.isEmpty() ? "LOCAL_PREFLIGHT_PASSED" : "BLOCKED",
                blockers.isEmpty(),
                false,
                false,
                List.copyOf(blockers));
    }

    public record Result(String state,
                         boolean localPreflightPassed,
                         boolean liveCallCertified,
                         boolean commercialGo,
                         List<String> blockers) { }
}
