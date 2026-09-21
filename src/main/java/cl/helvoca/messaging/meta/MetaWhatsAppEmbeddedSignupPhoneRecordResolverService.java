package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MetaWhatsAppEmbeddedSignupPhoneRecordResolverService {
    private final PhoneNumberRepository phones;
    private final TenantProvider tenantProvider;

    public MetaWhatsAppEmbeddedSignupPhoneRecordResolverService(
            PhoneNumberRepository phones,
            TenantProvider tenantProvider) {
        this.phones = phones;
        this.tenantProvider = tenantProvider;
    }

    public UUID resolve(String displayPhoneNumber) {
        UUID businessId = tenantProvider.requireBusinessId();
        String normalizedPhoneNumber = normalizeE164(displayPhoneNumber);

        PhoneNumber existing = phones.findByPhoneNumber(normalizedPhoneNumber).orElse(null);
        if (existing != null) {
            return requireSameTenant(existing, businessId);
        }

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider(MetaWhatsAppMessagingProvider.ID);
        phone.setPhoneNumber(normalizedPhoneNumber);
        phone.setActive(true);
        phone.setWhatsappEnabled(false);
        phone.setWhatsappProvider(MetaWhatsAppMessagingProvider.ID);
        phone.setWhatsappExternalId(null);
        phone.setWhatsappCertifiedAt(null);

        try {
            PhoneNumber saved = phones.saveAndFlush(phone);
            return saved.getId();
        } catch (DataIntegrityViolationException race) {
            PhoneNumber raced = phones.findByPhoneNumber(normalizedPhoneNumber)
                    .orElseThrow(() -> race);
            return requireSameTenant(raced, businessId);
        }
    }

    static String normalizeE164(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("Meta display phone number is required");
        }

        String digits = raw.replaceAll("[^0-9]", "");
        String normalized = "+" + digits;
        if (!normalized.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("Meta display phone number is not valid E.164");
        }
        return normalized;
    }

    private static UUID requireSameTenant(PhoneNumber phone, UUID businessId) {
        if (!businessId.equals(phone.getBusinessId())) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_PHONE_OWNED_BY_ANOTHER_TENANT");
        }
        return phone.getId();
    }
}
