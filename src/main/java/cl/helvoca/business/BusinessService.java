package cl.helvoca.business;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class BusinessService {
    private static final Set<String> APPEARANCE_THEMES =
            Set.of("cyan", "blue", "emerald", "violet", "amber");

    private final BusinessRepository businesses;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public BusinessService(
            BusinessRepository businesses,
            TenantProvider tenantProvider,
            AuditService auditService
    ) {
        this.businesses = businesses;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BusinessResponse current() {
        var id = tenantProvider.requireBusinessId();
        return BusinessResponse.from(businesses.findById(id)
                .orElseThrow(() -> new NotFoundException("Business not found")));
    }

    @Transactional
    public BusinessResponse update(UpdateBusinessRequest request) {
        var id = tenantProvider.requireBusinessId();
        var business = businesses.findById(id)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        business.setName(request.name());
        business.setTimezone(request.timezone());
        business.setLanguage(request.language());
        business.setHumanTransferPhone(normalizeTransferPhone(request.humanTransferPhone()));
        auditService.success(id, "BUSINESS_UPDATE", "BUSINESS", id);
        return BusinessResponse.from(business);
    }

    @Transactional
    public BusinessAppearanceResponse updateAppearance(String theme) {
        var id = tenantProvider.requireBusinessId();
        var business = businesses.findById(id)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        String normalized = normalizeAppearanceTheme(theme);
        business.setAppearanceTheme(normalized);
        auditService.success(id, "BUSINESS_APPEARANCE_UPDATE", "BUSINESS", id);
        return new BusinessAppearanceResponse(normalized);
    }

    private static String normalizeAppearanceTheme(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!APPEARANCE_THEMES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "El tema debe ser cyan, blue, emerald, violet o amber.");
        }
        return normalized;
    }

    private static String normalizeTransferPhone(String value) {
        if (value == null || value.isBlank()) return null;
        String phone = value.trim();
        if (!phone.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException(
                    "El teléfono de transferencia debe usar formato E.164, por ejemplo +56912345678.");
        }
        return phone;
    }
}
