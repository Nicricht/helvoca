package cl.helvoca.messaging.meta;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class MetaWhatsAppTenantResolver {
    static final String PROVIDER = "META_WHATSAPP_CLOUD";

    private final PhoneNumberRepository phoneNumbers;
    private final TenantDatabaseContext databaseContext;

    public MetaWhatsAppTenantResolver(PhoneNumberRepository phoneNumbers,
                                      TenantDatabaseContext databaseContext) {
        this.phoneNumbers = phoneNumbers;
        this.databaseContext = databaseContext;
    }

    public Optional<MetaWhatsAppTenantRoute> resolveRoute(String phoneNumberId) {
        String externalId = normalize(phoneNumberId);
        if (externalId == null) {
            return Optional.empty();
        }

        return databaseContext.callAsSystem(() ->
                phoneNumbers
                        .findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                                PROVIDER,
                                externalId)
                        .map(phone -> new MetaWhatsAppTenantRoute(
                                phone.getBusinessId(),
                                phone.getId(),
                                phone.getPhoneNumber())));
    }

    public Optional<UUID> resolveBusinessId(String phoneNumberId) {
        return resolveRoute(phoneNumberId).map(MetaWhatsAppTenantRoute::businessId);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
