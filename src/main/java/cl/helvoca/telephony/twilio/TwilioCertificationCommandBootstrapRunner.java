package cl.helvoca.telephony.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TwilioCertificationCommandBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationCommandBootstrapRunner.class);

    private final String runId;
    private final TwilioCertificationCommandStore commands;

    public TwilioCertificationCommandBootstrapRunner(
            @Value("${TWILIO_CERTIFICATION_COMMAND_BOOTSTRAP_RUN_ID:}") String runId,
            TwilioCertificationCommandStore commands) {
        this.runId = runId;
        this.commands = commands;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (runId == null || runId.isBlank()) return;
        String normalized = runId.trim();
        if (!TwilioCertificationCommandStore.validRunId(normalized)) {
            log.error("TWILIO_CERTIFICATION_COMMAND bootstrap blocked: invalid run id");
            return;
        }
        boolean inserted = commands.enqueue(normalized, "startup-bootstrap");
        log.info("TWILIO_CERTIFICATION_COMMAND bootstrap run={} inserted={}", normalized, inserted);
    }
}
