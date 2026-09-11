package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PhoneNumberService {
    private static final String TRIAL_PROVIDER = "TWILIO_TRIAL";
    private static final String TRIAL_EXTERNAL_ID = "TWILIO_TRIAL";

    private final PhoneNumberRepository repository;
    private final TenantProvider tenantProvider;
    private final AppUserRepository users;
    private final TrialVoiceProperties trialProperties;

    public PhoneNumberService(PhoneNumberRepository repository,
                              TenantProvider tenantProvider,
                              AppUserRepository users,
                              TrialVoiceProperties trialProperties) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.users = users;
        this.trialProperties = trialProperties;
    }

    @Transactional(readOnly = true)
    public List<PhoneNumberResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().map(PhoneNumberResponse::from).toList();
    }

    @Transactional
    public PhoneNumberResponse create(PhoneNumberRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        String number = request.phoneNumber().trim();

        PhoneNumber existing = repository.findByPhoneNumber(number).orElse(null);
        if (existing != null) {
            if (!existing.getBusinessId().equals(businessId)) {
                if (!canClaimSeededTrialNumber(existing, number)) {
                    throw new ConflictException("Este número ya está conectado a otro negocio");
                }
                existing.setBusinessId(businessId);
            }

            String externalId = blankToNull(request.externalId());
            if (externalId != null) existing.setExternalId(externalId);
            existing.setActive(request.active() == null || request.active());
            return PhoneNumberResponse.from(repository.save(existing));
        }

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber(number);
        phone.setExternalId(blankToNull(request.externalId()));
        phone.setActive(request.active() == null || request.active());
        return PhoneNumberResponse.from(repository.save(phone));
    }

    private boolean canClaimSeededTrialNumber(PhoneNumber existing, String requestedNumber) {
        if (!trialProperties.isEnabled() || !trialProperties.hasPhoneNumber()) return false;
        if (!trialProperties.getPhoneNumber().equals(requestedNumber)) return false;
        if (!TRIAL_PROVIDER.equals(existing.getProvider())) return false;
        if (!TRIAL_EXTERNAL_ID.equals(existing.getExternalId())) return false;
        return users.findAllByBusinessIdOrderByName(existing.getBusinessId()).isEmpty();
    }

    @Transactional
    public PhoneNumberResponse setActive(UUID id, boolean active) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber phone = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));
        phone.setActive(active);
        return PhoneNumberResponse.from(phone);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
