package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PhoneNumberService {
    private final PhoneNumberRepository repository;
    private final TenantProvider tenantProvider;

    public PhoneNumberService(PhoneNumberRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
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
        if (repository.existsByPhoneNumber(number)) {
            throw new ConflictException("Phone number is already registered");
        }
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber(number);
        phone.setExternalId(blankToNull(request.externalId()));
        phone.setActive(request.active() == null || request.active());
        return PhoneNumberResponse.from(repository.save(phone));
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
