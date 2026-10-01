package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TwilioCertificationCommandWorker {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationCommandWorker.class);

    private final boolean enabled;
    private final TwilioCertificationCommandStore commands;
    private final TwilioCertificationCommandCallLauncher launcher;
    private final TenantDatabaseContext databaseContext;

    public TwilioCertificationCommandWorker(
            @Value("${TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED:false}") boolean enabled,
            TwilioCertificationCommandStore commands,
            TwilioCertificationCommandCallLauncher launcher,
            TenantDatabaseContext databaseContext) {
        this.enabled = enabled;
        this.commands = commands;
        this.launcher = launcher;
        this.databaseContext = databaseContext;
    }

    @Scheduled(
            initialDelayString = "${TWILIO_CERTIFICATION_COMMAND_INITIAL_DELAY_MS:5000}",
            fixedDelayString = "${TWILIO_CERTIFICATION_COMMAND_POLL_MS:1000}")
    public void poll() {
        if (!enabled) return;

        databaseContext.callAsSystem(commands::claimNext).ifPresent(command -> {
            try {
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
