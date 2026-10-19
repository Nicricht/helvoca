package cl.helvoca.telephony.twilio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TwilioCertificationIngressTokenGate {
    private final TwilioCertificationCommandStore commands;
    private final String expectedFrom;
    private final String allowedTo;
    private final String forbiddenTo;
    private final TwilioCertificationDemoBindingGate demoBinding;

    @Autowired
    public TwilioCertificationIngressTokenGate(
            TwilioCertificationCommandStore commands,
            @Value("${TWILIO_TEST_FROM:}") String expectedFrom,
            @Value("${TWILIO_CERTIFICATION_ALLOWED_TO:}") String allowedTo,
            @Value("${TWILIO_CERTIFICATION_FORBIDDEN_TO:}") String forbiddenTo,
            TwilioCertificationDemoBindingGate demoBinding) {
        this.commands = commands;
        this.expectedFrom = expectedFrom;
        this.allowedTo = allowedTo;
        this.forbiddenTo = forbiddenTo;
        this.demoBinding = demoBinding;
    }

    TwilioCertificationIngressTokenGate(TwilioCertificationCommandStore commands,
            String expectedFrom, String allowedTo, String forbiddenTo) {
        this(commands, expectedFrom, allowedTo, forbiddenTo, null);
    }

    public boolean authorize(String token, String callSid, String from, String to) {
        if (!TwilioCertificationCommandStore.validToken(token)
                || !TwilioCertificationCommandStore.validCallSid(callSid)) {
            return false;
        }
        if (expectedFrom == null || !expectedFrom.trim().equals(from == null ? "" : from.trim())) {
            return false;
        }
        if (!TwilioCertificationStartupRunner.isAllowedTarget(to, allowedTo)
                || TwilioCertificationStartupRunner.isForbiddenTarget(to, forbiddenTo)) {
            return false;
        }
        if (demoBinding != null) {
            var businessId = demoBinding.pinnedBusinessId();
            return demoBinding.allows(businessId)
                    && commands.consumeIngressToken(token.trim(), callSid.trim(), businessId);
        }
        return commands.consumeIngressToken(token.trim(), callSid.trim());
    }
}
