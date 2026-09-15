package cl.helvoca.omnichannel;

import cl.helvoca.customer.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerIdentityService {
    private static final Set<CustomerIdentity.VerificationStatus> AUTO_LINK_STATUSES = EnumSet.of(
            CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED,
            CustomerIdentity.VerificationStatus.MANUAL_VERIFIED);

    private final CustomerIdentityRepository identities;
    private final CustomerRepository customers;

    public CustomerIdentityService(CustomerIdentityRepository identities,
                                   CustomerRepository customers) {
        this.identities = identities;
        this.customers = customers;
    }

    /**
     * Resolves a phone only when there is exactly one verified customer match
     * inside the same tenant. Zero or ambiguous matches fail closed.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolveVerifiedPhone(UUID businessId, String rawPhone) {
        if (businessId == null) return Optional.empty();
        String normalized = normalizePhone(rawPhone);
        if (normalized == null) return Optional.empty();

        LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
        identities.findAllByBusinessIdAndIdentityTypeAndNormalizedValueAndVerificationStatusIn(
                        businessId,
                        CustomerIdentity.Type.PHONE,
                        normalized,
                        AUTO_LINK_STATUSES)
                .forEach(identity -> {
                    if (customers.findByIdAndBusinessId(identity.getCustomerId(), businessId).isPresent()) {
                        candidates.add(identity.getCustomerId());
                    }
                });
        return candidates.size() == 1
                ? Optional.of(candidates.iterator().next())
                : Optional.empty();
    }

    @Transactional
    public Optional<CustomerIdentity> recordDeclaredPhone(UUID businessId,
                                                           UUID customerId,
                                                           String rawPhone,
                                                           String source) {
        return upsert(businessId, customerId, rawPhone,
                CustomerIdentity.VerificationStatus.UNVERIFIED, source, false);
    }

    @Transactional
    public Optional<CustomerIdentity> recordProviderAssertedPhone(UUID businessId,
                                                                   UUID customerId,
                                                                   String rawPhone,
                                                                   String source) {
        return upsert(businessId, customerId, rawPhone,
                CustomerIdentity.VerificationStatus.PROVIDER_ASSERTED, source, false);
    }

    @Transactional
    public CustomerIdentity verifyPhone(UUID businessId,
                                        UUID customerId,
                                        String rawPhone,
                                        CustomerIdentity.VerificationStatus verificationStatus,
                                        String source) {
        if (verificationStatus != CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED
                && verificationStatus != CustomerIdentity.VerificationStatus.MANUAL_VERIFIED) {
            throw new IllegalArgumentException("Only explicit verified statuses may enable automatic identity linking");
        }
        return upsert(businessId, customerId, rawPhone, verificationStatus, source, true)
                .orElseThrow(() -> new IllegalArgumentException("Phone must be a strict international number"));
    }

    private Optional<CustomerIdentity> upsert(UUID businessId,
                                              UUID customerId,
                                              String rawPhone,
                                              CustomerIdentity.VerificationStatus requestedStatus,
                                              String source,
                                              boolean requireStrictValue) {
        if (businessId == null || customerId == null) {
            if (requireStrictValue) throw new IllegalArgumentException("businessId and customerId are required");
            return Optional.empty();
        }
        if (customers.findByIdAndBusinessId(customerId, businessId).isEmpty()) {
            if (requireStrictValue) throw new IllegalArgumentException("Customer does not belong to tenant");
            return Optional.empty();
        }
        String normalized = normalizePhone(rawPhone);
        if (normalized == null) {
            if (requireStrictValue) throw new IllegalArgumentException("Phone must be a strict international number");
            return Optional.empty();
        }

        CustomerIdentity identity = identities
                .findByBusinessIdAndCustomerIdAndIdentityTypeAndNormalizedValue(
                        businessId, customerId, CustomerIdentity.Type.PHONE, normalized)
                .orElseGet(CustomerIdentity::new);

        if (identity.getId() == null) {
            identity.setBusinessId(businessId);
            identity.setCustomerId(customerId);
            identity.setIdentityType(CustomerIdentity.Type.PHONE);
            identity.setNormalizedValue(normalized);
        }

        CustomerIdentity.VerificationStatus current = identity.getVerificationStatus();
        if (current == null || trustRank(requestedStatus) > trustRank(current)) {
            identity.setVerificationStatus(requestedStatus);
            identity.setSource(normalizeSource(source));
            if (requestedStatus.permitsAutomaticLinking()) identity.setVerifiedAt(Instant.now());
        } else if (identity.getSource() == null || identity.getSource().isBlank()) {
            identity.setSource(normalizeSource(source));
        }
        return Optional.of(identities.saveAndFlush(identity));
    }

    /**
     * Strict generic E.164-like normalization. No country code is guessed.
     * Accepted forms must already contain '+' (or international 00 prefix).
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null) return null;
        String clean = rawPhone.trim();
        if (clean.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            clean = clean.substring(9).trim();
        }
        if (clean.startsWith("00")) clean = "+" + clean.substring(2);
        if (!clean.startsWith("+")) return null;

        String digits = clean.substring(1).replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15) return null;
        return "+" + digits;
    }

    private static int trustRank(CustomerIdentity.VerificationStatus status) {
        if (status == null) return -1;
        return switch (status) {
            case UNVERIFIED -> 0;
            case PROVIDER_ASSERTED -> 1;
            case CUSTOMER_VERIFIED -> 2;
            case MANUAL_VERIFIED -> 3;
        };
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "UNKNOWN";
        String normalized = source.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_:-]", "_");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }
}
