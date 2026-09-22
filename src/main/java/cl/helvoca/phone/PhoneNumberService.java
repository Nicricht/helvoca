package cl.helvoca.phone;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PhoneNumberService {
    private final PhoneNumberRepository repository;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public PhoneNumberService(PhoneNumberRepository repository,
                              TenantProvider tenantProvider,
                              AuditService auditService) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    PhoneNumberService(PhoneNumberRepository repository,
                       TenantProvider tenantProvider) {
        this(repository, tenantProvider, null);
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

            boolean wasCertified = existing.getWhatsappCertifiedAt() != null;
            boolean wasEnabled = existing.isWhatsappEnabled();

            String externalId = blankToNull(request.externalId());
            if (externalId != null) existing.setExternalId(externalId);
            existing.setActive(request.active() == null || request.active());
            if (!existing.isActive()) {
                existing.setWhatsappEnabled(false);
                existing.setWhatsappCertifiedAt(null);
            }

            PhoneNumber saved = repository.save(existing);
            if (!saved.isActive() && wasEnabled && auditService != null) {
                auditService.humanSuccess(
                        businessId,
                        "WHATSAPP_SENDER_DISABLED",
                        "WHATSAPP_SENDER",
                        businessId,
                        Map.of(
                                "whatsappEnabled", true,
                                "certified", wasCertified),
                        Map.of(
                                "whatsappEnabled", false,
                                "certified", false));
            }

            if (!saved.isActive() && wasCertified && auditService != null) {
                auditService.humanSuccess(
                        businessId,
                        "WHATSAPP_CERTIFICATION_CLEARED",
                        "WHATSAPP_SENDER",
                        businessId,
                        Map.of(
                                "whatsappEnabled", wasEnabled,
                                "certified", true),
                        Map.of(
                                "whatsappEnabled", false,
                                "certified", false));
            }
            return PhoneNumberResponse.from(saved);
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
        boolean wasCertified = phone.getWhatsappCertifiedAt() != null;
        boolean wasEnabled = phone.isWhatsappEnabled();

        phone.setActive(active);
        if (!active) {
            phone.setWhatsappEnabled(false);
            phone.setWhatsappCertifiedAt(null);
        }

        if (!active && wasEnabled && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_SENDER_DISABLED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", true,
                            "certified", wasCertified),
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", false));
        }

        if (!active && wasCertified && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_CERTIFICATION_CLEARED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", wasEnabled,
                            "certified", true),
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", false));
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

        boolean wasCertified = phone.getWhatsappCertifiedAt() != null;
        boolean wasEnabled = phone.isWhatsappEnabled();

        phone.setWhatsappEnabled(enabled);
        if (!enabled) phone.setWhatsappCertifiedAt(null);
        PhoneNumber saved = repository.save(phone);

        if (enabled && !wasEnabled && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_SENDER_ENABLED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", wasCertified),
                    Map.of(
                            "whatsappEnabled", true,
                            "certified", wasCertified));
        }

        if (!enabled && wasEnabled && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_SENDER_DISABLED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", true,
                            "certified", wasCertified),
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", false));
        }

        if (!enabled && wasCertified && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_CERTIFICATION_CLEARED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", wasEnabled,
                            "certified", true),
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", false));
        }

        return PhoneNumberResponse.from(saved);
    }

    @Transactional
    public void detach(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber phone = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Phone number not found"));
        boolean wasCertified = phone.getWhatsappCertifiedAt() != null;
        boolean wasEnabled = phone.isWhatsappEnabled();

        repository.delete(phone);

        if (wasCertified && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "WHATSAPP_CERTIFICATION_CLEARED",
                    "WHATSAPP_SENDER",
                    businessId,
                    Map.of(
                            "whatsappEnabled", wasEnabled,
                            "certified", true),
                    Map.of(
                            "whatsappEnabled", false,
                            "certified", false));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
