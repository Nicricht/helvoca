package cl.helvoca.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class ApiRateLimitProperties {
    private boolean enabled = true;
    private Limit login = new Limit(20, 60);
    private Limit register = new Limit(5, 3600);
    private Limit publicApi = new Limit(120, 60);
    private Limit billing = new Limit(20, 60);
    private Limit authenticatedApi = new Limit(600, 60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Limit getLogin() { return login; }
    public void setLogin(Limit login) { this.login = sane(login, 20, 60); }
    public Limit getRegister() { return register; }
    public void setRegister(Limit register) { this.register = sane(register, 5, 3600); }
    public Limit getPublicApi() { return publicApi; }
    public void setPublicApi(Limit publicApi) { this.publicApi = sane(publicApi, 120, 60); }
    public Limit getBilling() { return billing; }
    public void setBilling(Limit billing) { this.billing = sane(billing, 20, 60); }
    public Limit getAuthenticatedApi() { return authenticatedApi; }
    public void setAuthenticatedApi(Limit authenticatedApi) { this.authenticatedApi = sane(authenticatedApi, 600, 60); }

    private static Limit sane(Limit value, int requests, int seconds) {
        if (value == null) return new Limit(requests, seconds);
        value.setRequests(Math.max(1, value.getRequests()));
        value.setWindowSeconds(Math.max(1, value.getWindowSeconds()));
        return value;
    }

    public static class Limit {
        private int requests;
        private int windowSeconds;

        public Limit() {}
        public Limit(int requests, int windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }
        public int getRequests() { return Math.max(1, requests); }
        public void setRequests(int requests) { this.requests = requests; }
        public int getWindowSeconds() { return Math.max(1, windowSeconds); }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    }
}
