package cl.helvoca.business;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessProfileService {
    private final BusinessProfileRepository profiles;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public BusinessProfileService(BusinessProfileRepository profiles,
                                  TenantProvider tenantProvider,
                                  AuditService auditService) {
        this.profiles = profiles;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse current() {
        UUID businessId = tenantProvider.requireBusinessId();
        return profiles.findById(businessId)
                .map(BusinessProfileResponse::from)
                .orElseGet(() -> BusinessProfileResponse.empty(businessId));
    }

    @Transactional
    public BusinessProfileResponse upsert(BusinessProfileRequest request) {
        if (request == null) throw new IllegalArgumentException("Business profile is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessProfile profile = profiles.findById(businessId).orElse(null);
        Map<String, Object> before = profile == null ? null : snapshot(profile);

        if (profile == null) {
            profile = new BusinessProfile();
            profile.setBusinessId(businessId);
        }

        profile.setPresetKey(normalizePreset(request.presetKey()));
        profile.setPublicDescription(blankToNull(request.publicDescription()));
        profile.setPublicPhone(normalizePhone(request.publicPhone()));
        profile.setPublicEmail(normalizeEmail(request.publicEmail()));
        profile.setWebsiteUrl(blankToNull(request.websiteUrl()));
        profile.setAddressLine(blankToNull(request.addressLine()));
        profile.setCommune(blankToNull(request.commune()));
        profile.setCity(blankToNull(request.city()));
        profile.setRegion(blankToNull(request.region()));
        profile.setCountryCode(normalizeCountry(request.countryCode()));
        profile.setDefaultCurrency(normalizeCurrency(request.defaultCurrency()));
        profile.setSellsProducts(request.sellsProducts());
        profile.setSellsServices(request.sellsServices());
        profile.setUsesReservations(request.usesReservations());

        BusinessProfile saved = profiles.saveAndFlush(profile);
        auditService.humanSuccess(
                businessId,
                "BUSINESS_PROFILE_UPSERT",
                "BUSINESS_PROFILE",
                businessId,
                before,
                snapshot(saved)
        );
        return BusinessProfileResponse.from(saved);
    }

    private static String normalizePreset(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9_-]{1,60}$")) {
            throw new IllegalArgumentException("presetKey contains invalid characters");
        }
        return normalized;
    }

    private static String normalizePhone(String value) {
        String phone = blankToNull(value);
        if (phone == null) return null;
        if (!phone.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("publicPhone must use E.164 format");
        }
        return phone;
    }

    private static String normalizeEmail(String value) {
        String email = blankToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCountry(String value) {
        String country = blankToNull(value);
        if (country == null) return null;
        country = country.toUpperCase(Locale.ROOT);
        if (!country.matches("^[A-Z]{2}$")) throw new IllegalArgumentException("countryCode must contain two letters");
        return country;
    }

    private static String normalizeCurrency(String value) {
        String currency = blankToNull(value);
        if (currency == null) return "CLP";
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) throw new IllegalArgumentException("defaultCurrency must contain three letters");
        return currency;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> snapshot(BusinessProfile profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, "presetKey", profile.getPresetKey());
        put(out, "publicDescription", profile.getPublicDescription());
        put(out, "publicPhone", profile.getPublicPhone());
        put(out, "publicEmail", profile.getPublicEmail());
        put(out, "websiteUrl", profile.getWebsiteUrl());
        put(out, "addressLine", profile.getAddressLine());
        put(out, "commune", profile.getCommune());
        put(out, "city", profile.getCity());
        put(out, "region", profile.getRegion());
        put(out, "countryCode", profile.getCountryCode());
        put(out, "defaultCurrency", profile.getDefaultCurrency());
        put(out, "sellsProducts", profile.getSellsProducts());
        put(out, "sellsServices", profile.getSellsServices());
        put(out, "usesReservations", profile.getUsesReservations());
        return out;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }
}
