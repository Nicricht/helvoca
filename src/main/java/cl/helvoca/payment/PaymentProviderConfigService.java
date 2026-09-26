package cl.helvoca.payment;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentProviderConfigService {
    private static final String MERCADO_PAGO = "mercadopago";
    private static final String MANAGED_SANDBOX_CREDENTIAL_REF = "RECEPVOZ_MP_SANDBOX";

    private final PaymentProviderConfigRepository configs;
    private final PaymentProviderCredentialResolver credentials;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public PaymentProviderConfigService(PaymentProviderConfigRepository configs,
                                        PaymentProviderCredentialResolver credentials,
                                        TenantProvider tenantProvider,
                                        AuditService auditService) {
        this.configs = configs;
        this.credentials = credentials;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    public PaymentProviderConfigService(PaymentProviderConfigRepository configs,
                                        PaymentProviderCredentialResolver credentials,
                                        TenantProvider tenantProvider) {
        this(configs, credentials, tenantProvider, null);
    }

    @Transactional(readOnly = true)
    public View current() {
        UUID businessId = tenantProvider.requireBusinessId();
        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        return view(config);
    }

    @Transactional
    public View replace(Update request) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (request == null) throw new IllegalArgumentException("Payment provider configuration is required.");

        String provider = normalizeProvider(request.provider());
        if (!MERCADO_PAGO.equals(provider)) {
            throw new IllegalArgumentException("Only Mercado Pago is supported by the current sandbox adapter.");
        }
        PaymentProviderConfig.Mode mode = request.mode() == null
                ? PaymentProviderConfig.Mode.SANDBOX
                : request.mode();
        if (mode != PaymentProviderConfig.Mode.SANDBOX) {
            throw new IllegalArgumentException("LIVE merchant payments are intentionally disabled.");
        }
        String credentialRef = normalizeCredentialRef(request.credentialRef());
        if (request.enabled() && credentials.resolve(credentialRef).isEmpty()) {
            throw new IllegalArgumentException("Sandbox merchant credentials are not configured for credentialRef.");
        }

        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        Map<String, Object> before = config == null ? null : snapshot(config);
        if (config == null) config = new PaymentProviderConfig();
        config.setBusinessId(businessId);
        config.setProvider(provider);
        config.setMode(mode);
        config.setCredentialRef(credentialRef);
        config.setEnabled(request.enabled());
        config = configs.saveAndFlush(config);
        if (auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "PAYMENT_PROVIDER_CONFIG_REPLACE",
                    "PAYMENT_PROVIDER_CONFIG",
                    businessId,
                    before,
                    snapshot(config));
        }
        return view(config);
    }

    @Transactional(readOnly = true)
    public ManagedSandboxView managedSandbox() {
        UUID businessId = tenantProvider.requireBusinessId();
        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        boolean available = credentials.resolve(MANAGED_SANDBOX_CREDENTIAL_REF).isPresent();
        return managedSandboxView(config, available);
    }

    @Transactional
    public ManagedSandboxView enableManagedSandbox() {
        UUID businessId = tenantProvider.requireBusinessId();
        if (credentials.resolve(MANAGED_SANDBOX_CREDENTIAL_REF).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Managed Mercado Pago Sandbox is not available.");
        }

        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        if (config != null
                && config.getCredentialRef() != null
                && !MANAGED_SANDBOX_CREDENTIAL_REF.equalsIgnoreCase(config.getCredentialRef())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This business already has a different payment provider configuration.");
        }

        Map<String, Object> before = config == null ? null : snapshot(config);
        if (config == null) config = new PaymentProviderConfig();
        config.setBusinessId(businessId);
        config.setProvider(MERCADO_PAGO);
        config.setMode(PaymentProviderConfig.Mode.SANDBOX);
        config.setCredentialRef(MANAGED_SANDBOX_CREDENTIAL_REF);
        config.setEnabled(true);
        PaymentProviderConfig saved = configs.saveAndFlush(config);

        auditManagedSandbox(
                businessId,
                "PAYMENT_PROVIDER_MANAGED_SANDBOX_ENABLE",
                before,
                snapshot(saved));
        return managedSandboxView(saved, true);
    }

    @Transactional
    public ManagedSandboxView disableManagedSandbox() {
        UUID businessId = tenantProvider.requireBusinessId();
        boolean available = credentials.resolve(MANAGED_SANDBOX_CREDENTIAL_REF).isPresent();
        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        if (config == null) return managedSandboxView(null, available);

        if (config.getCredentialRef() == null
                || !MANAGED_SANDBOX_CREDENTIAL_REF.equalsIgnoreCase(config.getCredentialRef())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Managed Sandbox cannot modify a different payment provider configuration.");
        }

        Map<String, Object> before = snapshot(config);
        config.setEnabled(false);
        PaymentProviderConfig saved = configs.saveAndFlush(config);
        auditManagedSandbox(
                businessId,
                "PAYMENT_PROVIDER_MANAGED_SANDBOX_DISABLE",
                before,
                snapshot(saved));
        return managedSandboxView(saved, available);
    }

    @Transactional(readOnly = true)
    public PaymentProviderConfig requireSandboxMercadoPago(UUID businessId) {
        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        if (config == null || !config.isEnabled()
                || config.getMode() != PaymentProviderConfig.Mode.SANDBOX
                || !MERCADO_PAGO.equalsIgnoreCase(config.getProvider())) return null;
        return config;
    }

    @Transactional(readOnly = true)
    public PaymentProviderConfig byWebhookKey(UUID webhookKey) {
        if (webhookKey == null) return null;
        PaymentProviderConfig config = configs.findByWebhookKey(webhookKey).orElse(null);
        if (config == null || !config.isEnabled()
                || config.getMode() != PaymentProviderConfig.Mode.SANDBOX
                || !MERCADO_PAGO.equalsIgnoreCase(config.getProvider())) return null;
        return config;
    }

    private void auditManagedSandbox(UUID businessId,
                                     String action,
                                     Map<String, Object> before,
                                     Map<String, Object> after) {
        if (auditService == null) return;
        auditService.humanSuccess(
                businessId,
                action,
                "PAYMENT_PROVIDER_CONFIG",
                businessId,
                before,
                after);
    }

    private ManagedSandboxView managedSandboxView(PaymentProviderConfig config, boolean available) {
        boolean managedConfig = config != null
                && MANAGED_SANDBOX_CREDENTIAL_REF.equalsIgnoreCase(config.getCredentialRef());
        boolean blockedByCustomConfiguration = config != null && !managedConfig;
        boolean enabled = available
                && managedConfig
                && config.isEnabled()
                && config.getMode() == PaymentProviderConfig.Mode.SANDBOX
                && MERCADO_PAGO.equalsIgnoreCase(config.getProvider());
        String webhookPath = managedConfig
                ? "/webhooks/v1/payments/mercadopago/" + config.getWebhookKey()
                : null;
        return new ManagedSandboxView(
                available,
                managedConfig,
                enabled,
                blockedByCustomConfiguration,
                managedConfig ? config.getProvider() : null,
                managedConfig ? config.getMode() : null,
                webhookPath);
    }

    private static Map<String, Object> snapshot(PaymentProviderConfig config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provider", config.getProvider());
        snapshot.put("mode", config.getMode() == null ? null : config.getMode().name());
        snapshot.put("enabled", config.isEnabled());
        snapshot.put("credentialReferenceConfigured",
                config.getCredentialRef() != null && !config.getCredentialRef().isBlank());
        return snapshot;
    }

    private View view(PaymentProviderConfig config) {
        if (config == null) return new View(null, null, false, false, null);
        boolean credentialReady = credentials.resolve(config.getCredentialRef()).isPresent();
        String webhookPath = "/webhooks/v1/payments/mercadopago/" + config.getWebhookKey();
        return new View(config.getProvider(), config.getMode(), config.isEnabled(), credentialReady, webhookPath);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) return MERCADO_PAGO;
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCredentialRef(String credentialRef) {
        if (credentialRef == null) throw new IllegalArgumentException("credentialRef is required.");
        String normalized = credentialRef.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,80}")) {
            throw new IllegalArgumentException("credentialRef may contain only A-Z, 0-9 and underscore.");
        }
        return normalized;
    }

    public record Update(String provider, PaymentProviderConfig.Mode mode, boolean enabled, String credentialRef) {}

    public record ManagedSandboxView(
            boolean available,
            boolean configured,
            boolean enabled,
            boolean blockedByCustomConfiguration,
            String provider,
            PaymentProviderConfig.Mode mode,
            String webhookPath) {}

    public record View(String provider,
                       PaymentProviderConfig.Mode mode,
                       boolean enabled,
                       boolean credentialsConfigured,
                       String webhookPath) {}
}
