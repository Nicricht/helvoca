package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TwilioCertificationCommandBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationCommandBootstrapRunner.class);

    private final String runId;
    private final TwilioCertificationCommandStore commands;
    private final TenantDatabaseContext databaseContext;
    private final TwilioCertificationDemoBindingGate demoBinding;

    @Autowired
    public TwilioCertificationCommandBootstrapRunner(
            @Value("${TWILIO_CERTIFICATION_COMMAND_BOOTSTRAP_RUN_ID:}") String runId,
            TwilioCertificationCommandStore commands,
            TenantDatabaseContext databaseContext,
            TwilioCertificationDemoBindingGate demoBinding) {
        this.runId = runId;
        this.commands = commands;
        this.databaseContext = databaseContext;
        this.demoBinding = demoBinding;
    }

    TwilioCertificationCommandBootstrapRunner(String runId,
            TwilioCertificationCommandStore commands, TenantDatabaseContext databaseContext) {
        this(runId, commands, databaseContext, null);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (runId == null || runId.isBlank()) return;
        String normalized = runId.trim();
        if (!TwilioCertificationCommandStore.validRunId(normalized)) {
            log.error("TWILIO_CERTIFICATION_COMMAND bootstrap blocked: invalid run id");
            return;
        }
        if (demoBinding != null && !demoBinding.allows(demoBinding.pinnedBusinessId())) {
            log.error("TWILIO_CERTIFICATION_COMMAND bootstrap blocked: DEMO binding unavailable");
            return;
        }
        boolean inserted = databaseContext.callAsSystem(
                () -> demoBinding == null
                        ? commands.enqueue(normalized, "startup-bootstrap")
                        : commands.enqueue(normalized, "startup-bootstrap", demoBinding.pinnedBusinessId()));
        log.info("TWILIO_CERTIFICATION_COMMAND bootstrap run={} inserted={}", normalized, inserted);
    }
}
