package cl.helvoca.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jobs")
public class PersistentJobProperties {
    private boolean enabled = false;
    private int leaseSeconds = 120;
    private int pollDelayMs = 1000;
    private int batchSize = 10;
    private int defaultMaxAttempts = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(int leaseSeconds) { this.leaseSeconds = Math.max(5, leaseSeconds); }
    public int getPollDelayMs() { return pollDelayMs; }
    public void setPollDelayMs(int pollDelayMs) { this.pollDelayMs = Math.max(250, pollDelayMs); }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = Math.max(1, Math.min(batchSize, 100)); }
    public int getDefaultMaxAttempts() { return defaultMaxAttempts; }
    public void setDefaultMaxAttempts(int defaultMaxAttempts) {
        this.defaultMaxAttempts = Math.max(1, Math.min(defaultMaxAttempts, 100));
    }
}
