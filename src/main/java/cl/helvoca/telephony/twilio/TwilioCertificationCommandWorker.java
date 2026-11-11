package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TwilioCertificationCommandWorker {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationCommandWorker.class);

    private final boolean enabled;
    private final String onlyRunId;
    private final boolean exactRunConfigured;
    private final String environmentName;
    private final TwilioCertificationCommandStore commands;
    private final TwilioCertificationCommandCallLauncher launcher;
    private final TenantDatabaseContext databaseContext;
    private final TwilioCertificationDemoBindingGate demoBinding;

    @Autowired
    public TwilioCertificationCommandWorker(
            @Value("${TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED:false}") boolean enabled,
            @Value("${TWILIO_CERTIFICATION_COMMAND_ONLY_RUN_ID:}") String onlyRunId,
            @Value("${RAILWAY_ENVIRONMENT_NAME:}") String environmentName,
            TwilioCertificationCommandStore commands,
            TwilioCertificationCommandCallLauncher launcher,
            TenantDatabaseContext databaseContext,
            TwilioCertificationDemoBindingGate demoBinding) {
        this.enabled = enabled;
        this.exactRunConfigured = onlyRunId != null && !onlyRunId.isEmpty();
        this.onlyRunId = onlyRunId == null ? "" : onlyRunId.trim();
        this.environmentName = environmentName;
        this.commands = commands;
        this.launcher = launcher;
        this.databaseContext = databaseContext;
        this.demoBinding = demoBinding;
    }

    // Isolated legacy unit tests; Spring always injects the DEMO gate.
    TwilioCertificationCommandWorker(boolean enabled, TwilioCertificationCommandStore commands,
            TwilioCertificationCommandCallLauncher launcher, TenantDatabaseContext databaseContext) {
        this(enabled, "", "", commands, launcher, databaseContext, null);
    }

    TwilioCertificationCommandWorker(boolean enabled, TwilioCertificationCommandStore commands,
            TwilioCertificationCommandCallLauncher launcher, TenantDatabaseContext databaseContext,
            TwilioCertificationDemoBindingGate demoBinding) {
        this(enabled, "", "", commands, launcher, databaseContext, demoBinding);
    }

    @Scheduled(
            initialDelayString = "${TWILIO_CERTIFICATION_COMMAND_INITIAL_DELAY_MS:5000}",
            fixedDelayString = "${TWILIO_CERTIFICATION_COMMAND_POLL_MS:1000}")
    public void poll() {
        if (!enabled) return;

        boolean pinned = exactRunConfigured;
        if (pinned && (!"demo-isolated".equals(environmentName)
                || !TwilioCertificationCommandStore.validRunId(onlyRunId)
                || demoBinding == null || demoBinding.pinnedBusinessId() == null
                || !demoBinding.allows(demoBinding.pinnedBusinessId()))) {
            log.error("TWILIO_CERTIFICATION_COMMAND exact-run preflight blocked");
            return;
        }
        databaseContext.callAsSystem(() -> pinned
                ? commands.claimExactRunId(onlyRunId, demoBinding.pinnedBusinessId(), "startup-bootstrap")
                : commands.claimNext()).ifPresent(command -> {
            try {
                if (demoBinding != null && !demoBinding.allows(command.businessId())) {
                    throw new IllegalStateException("certification DEMO tenant/phone binding unavailable");
                }
                String callSid = launcher.launch(command.callbackToken());
                databaseContext.runAsSystem(
                        () -> commands.recordProviderCall(command.runId(), command.callbackToken(), callSid));
                log.info("TWILIO_CERTIFICATION_COMMAND CREATED run={} call={} target=fixed-allowlist",
                        command.runId(), callSid);
            } catch (Exception e) {
                databaseContext.runAsSystem(
                        () -> commands.markFailed(command.runId(), rootMessage(e)));
                log.error("TWILIO_CERTIFICATION_COMMAND FAILED run={} reason={}",
                        command.runId(), rootMessage(e));
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
