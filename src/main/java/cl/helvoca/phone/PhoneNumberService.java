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

    public PhoneNumberService(PhoneNumberRepository repository,
                              TenantProvider tenantProvider) {
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

        PhoneNumber existing = repository.findByPhoneNumber(number).orElse(null);
        if (existing != null) {
            if (!existing.getBusinessId().equals(businessId)) {
                throw new ConflictException("Este número ya está conectado a otro negocio");
            }

            String externalId = blankToNull(request.externalId());
            if (externalId != null) existing.setExternalId(externalId);
            existing.setActive(request.active() == null || request.active());
            if (!existing.isActive()) {
                existing.setWhatsappEnabled(false);
                existing.setWhatsappCertifiedAt(null);
            }
            return PhoneNumberResponse.from(repository.save(existing));
        }

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber(number);
        phone.setExternalId(blankToNull(request.externalId()));
        phone.setActive(request.active() == null || request.active());
        phone.setWhatsappEnabled(false);
        return PhoneNumberResponse.from(repository.save(phone));
    }

    @Transactional
    public PhoneNumberResponse setActive(UUID id, boolean active) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber phone = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));
        phone.setActive(active);
        if (!active) {
            phone.setWhatsappEnabled(false);
            phone.setWhatsappCertifiedAt(null);
        }
        return PhoneNumberResponse.from(phone);
    }

    @Transactional
    public PhoneNumberResponse setWhatsappEnabled(UUID id, boolean enabled) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber phone = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));

        if (enabled) {
            if (!phone.isActive()) {
                throw new ConflictException("Activa el número antes de habilitarlo para WhatsApp");
            }
            boolean anotherSender = repository
                    .findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId)
                    .stream()
                    .anyMatch(existing -> !existing.getId().equals(phone.getId()));
            if (anotherSender) {
                throw new ConflictException("El negocio ya tiene otro número habilitado como remitente de WhatsApp");
            }
        }

        phone.setWhatsappEnabled(enabled);
        if (!enabled) phone.setWhatsappCertifiedAt(null);
        return PhoneNumberResponse.from(repository.save(phone));
    }

    @Transactional
    public void detach(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber phone = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));
        repository.delete(phone);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
