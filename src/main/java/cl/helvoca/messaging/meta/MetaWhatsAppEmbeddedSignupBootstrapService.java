package cl.helvoca.messaging.meta;

import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetaWhatsAppEmbeddedSignupBootstrapService {
    private final MetaWhatsAppProperties metaProperties;
    private final TenantProvider tenantProvider;

    @Autowired
    public MetaWhatsAppEmbeddedSignupBootstrapService(
            MetaWhatsAppProperties metaProperties,
            TenantProvider tenantProvider) {
        this.metaProperties = metaProperties;
        this.tenantProvider = tenantProvider;
    }

    MetaWhatsAppEmbeddedSignupBootstrapService(MetaWhatsAppProperties metaProperties) {
        this(metaProperties, null);
    }

    public MetaWhatsAppEmbeddedSignupBootstrapResponse bootstrap() {
        boolean enabled = metaProperties.isEmbeddedSignupEnabled();
        boolean available = enabled
                && metaProperties.hasEmbeddedSignupAppId()
                && metaProperties.hasEmbeddedSignupConfigId();

        return new MetaWhatsAppEmbeddedSignupBootstrapResponse(
                enabled,
                available,
                available ? metaProperties.getEmbeddedSignupAppId() : null,
                available ? metaProperties.getEmbeddedSignupConfigId() : null,
                metaProperties.getGraphApiVersion(),
                available && metaProperties.isCoexistenceEnabled()
                        && tenantMayUseCoexistence());
    }

    private boolean tenantMayUseCoexistence() {
        String allowedBusinessId = metaProperties.getCoexistenceAllowedBusinessId();
        String allowedPhone = metaProperties.getCoexistenceAllowedPhone();
        boolean hasBusinessAllowlist = !allowedBusinessId.isBlank();
        boolean hasPhoneAllowlist = !allowedPhone.isBlank();

        if (hasBusinessAllowlist != hasPhoneAllowlist) return false;
        if (hasBusinessAllowlist) {
            if (tenantProvider == null
                    || !allowedPhone.matches("^\\+[1-9][0-9]{7,14}$")) {
                return false;
            }
            try {
                return tenantProvider.requireBusinessId().toString().equals(allowedBusinessId);
            } catch (RuntimeException unavailableTenant) {
                return false;
            }
        }
        if (tenantProvider == null || !metaProperties.isCoexistenceSelfServiceEnabled()) {
            return false;
        }
        try {
            tenantProvider.requireBusinessId();
            return true;
        } catch (RuntimeException unavailableTenant) {
            return false;
        }
    }
}
