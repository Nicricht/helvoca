package cl.helvoca.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PersistentJobWorker {
    private final PersistentJobService jobs;
    private final PersistentJobProperties properties;
    private final String workerId = "helvoca-" + UUID.randomUUID();

    public PersistentJobWorker(PersistentJobService jobs, PersistentJobProperties properties) {
        this.jobs = jobs;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-delay-ms:1000}")
    public void poll() {
        if (!properties.isEnabled()) return;
        jobs.processBatch(workerId, properties.getBatchSize());
    }
}
