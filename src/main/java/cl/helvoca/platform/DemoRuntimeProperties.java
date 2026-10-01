package cl.helvoca.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoRuntimeProperties {
    private String runtimeBusinessId = "";

    public String getRuntimeBusinessId() {
        return runtimeBusinessId;
    }

    public void setRuntimeBusinessId(String runtimeBusinessId) {
        this.runtimeBusinessId = runtimeBusinessId == null ? "" : runtimeBusinessId.trim();
    }

    public UUID runtimeBusinessUuid() {
        if (runtimeBusinessId == null || runtimeBusinessId.isBlank()) return null;
        try {
            return UUID.fromString(runtimeBusinessId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
