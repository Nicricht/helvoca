package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PhoneProvisioningService {
    private final PhoneNumberRepository repository;
    private final TenantProvider tenantProvider;
    private final TwilioPhoneProvisioningClient twilio;

    public PhoneProvisioningService(PhoneNumberRepository repository,
                                    TenantProvider tenantProvider,
                                    TwilioPhoneProvisioningClient twilio) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.twilio = twilio;
    }

    public PhoneProvisioningStatusResponse status() {
        tenantProvider.requireBusinessId();
        boolean enabled = twilio.enabled();
        boolean configured = twilio.configured();
        String message;
        if (!enabled) {
            message = "El aprovisionamiento automático está deshabilitado. Solo puedes reconectar números previamente asignados y verificados en Twilio.";
        } else if (!configured) {
            message = "El proveedor telefónico aún no tiene toda la configuración necesaria para aprovisionar números.";
        } else {
            message = "Puedes buscar números disponibles y confirmar explícitamente el aprovisionamiento.";
        }
        return new PhoneProvisioningStatusResponse(enabled, configured, enabled && configured, "TWILIO", message);
    }

    public List<AvailablePhoneNumberResponse> available(String country, String areaCode, int limit) {
        tenantProvider.requireBusinessId();
        return twilio.searchAvailable(country, areaCode, limit);
    }

    /**
     * A self-service tenant may only re-connect a number already assigned to itself.
     * Twilio must independently attest account, SID, voice capability and webhooks.
     * Unassigned/BYON numbers require a separate verified-ownership enrollment.
     */
    public PhoneNumberResponse connectExisting(PhoneNumberRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (request.externalId() != null && !request.externalId().isBlank()) {
            throw new ConflictException("El SID debe verificarse con Twilio, no ingresarse manualmente.");
        }
        if (Boolean.FALSE.equals(request.active())) {
            throw new ConflictException("Para desactivar un número utiliza su control de estado.");
        }
        String number = request.phoneNumber().trim();
        PhoneNumber existing = repository.findByPhoneNumber(number)
                .orElseThrow(() -> new ConflictException(
                        "El número no está asignado a este negocio. Utiliza aprovisionamiento verificado."));
        if (!businessId.equals(existing.getBusinessId())
                || !"TWILIO".equalsIgnoreCase(existing.getProvider())) {
            throw new ConflictException("El número no está asignado a este negocio en Twilio.");
        }
        requireManagedAssignment(existing);
        TwilioPhoneProvisioningClient.VerifiedPhoneNumber verified = twilio.verifyManagedPhone(number);
        if (!existing.getExternalId().equals(verified.sid())) {
            throw new ConflictException("El SID verificado no coincide con el registro existente.");
        }
        existing.setExternalId(verified.sid());
        existing.setVoiceVerifiedAt(Instant.now());
        existing.setActive(true);
        return PhoneNumberResponse.from(repository.saveAndFlush(existing));
    }

    /**
     * Existing inactive numbers cannot be reactivated from a toggle until Twilio
     * confirms the exact provider SID, managed account and live voice callbacks.
     */
    public PhoneNumberResponse activateVerified(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        PhoneNumber existing = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ConflictException("El número no pertenece a este negocio."));
        if (!"TWILIO".equalsIgnoreCase(existing.getProvider())) {
            throw new ConflictException("El proveedor de este número no está habilitado para voz Twilio.");
        }
        requireManagedAssignment(existing);
        TwilioPhoneProvisioningClient.VerifiedPhoneNumber verified =
                twilio.verifyManagedPhone(existing.getPhoneNumber());
        if (!existing.getExternalId().equals(verified.sid())) {
            throw new ConflictException("El SID del proveedor cambió; no se activó el número.");
        }
        existing.setExternalId(verified.sid());
        existing.setVoiceVerifiedAt(Instant.now());
        existing.setActive(true);
        return PhoneNumberResponse.from(repository.saveAndFlush(existing));
    }

    private void requireManagedAssignment(PhoneNumber phone) {
        String account = twilio.managedAccountSid();
        if (!"MANAGED_PURCHASE".equals(phone.getTwilioBindingSource())
                || account == null || account.isBlank()
                || !account.equals(phone.getTwilioAccountSid())
                || phone.getExternalId() == null || phone.getExternalId().isBlank()) {
            throw new ConflictException(
                    "Esta fila no tiene evidencia de aprovisionamiento para este tenant y cuenta Twilio. Requiere conciliación segura.");
        }
    }

    public PhoneNumberResponse provision(ProvisionPhoneNumberRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("Debes confirmar explícitamente el aprovisionamiento del número.");
        }

        String requestedNumber = request.phoneNumber().trim();
        PhoneNumber existing = repository.findByPhoneNumber(requestedNumber).orElse(null);
        if (existing != null) {
            if (!businessId.equals(existing.getBusinessId())
                    || !"TWILIO".equalsIgnoreCase(existing.getProvider())) {
                throw new ConflictException("Este número ya pertenece a otro negocio o proveedor.");
            }
            requireManagedAssignment(existing);
            TwilioPhoneProvisioningClient.VerifiedPhoneNumber verified = twilio.verifyManagedPhone(requestedNumber);
            if (!existing.getExternalId().equals(verified.sid())) {
                throw new ConflictException("El SID registrado difiere del SID propietario en Twilio.");
            }
            existing.setExternalId(verified.sid());
            existing.setVoiceVerifiedAt(Instant.now());
            existing.setActive(true);
            return PhoneNumberResponse.from(repository.saveAndFlush(existing));
        }

        TwilioPhoneProvisioningClient.ProvisionedPhoneNumber provisioned = twilio.provision(requestedNumber);
        String actualNumber = provisioned.phoneNumber();
        if (!requestedNumber.equals(actualNumber)) {
            twilio.releaseQuietly(provisioned.sid());
            throw new IllegalStateException("El proveedor devolvió un número distinto al solicitado. La operación fue revertida.");
        }

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setExternalId(provisioned.sid());
        phone.setPhoneNumber(actualNumber);
        phone.setTwilioBindingSource("MANAGED_PURCHASE");
        phone.setTwilioAccountSid(twilio.managedAccountSid());
        // A purchased number is not live-ready until the carrier GET attests
        // identity AND the deployed voice/status webhooks.
        phone.setActive(false);

        try {
            return PhoneNumberResponse.from(repository.saveAndFlush(phone));
        } catch (DataIntegrityViolationException ex) {
            twilio.releaseQuietly(provisioned.sid());
            throw new ConflictException("El número se registró mientras otra operación lo estaba procesando. La operación externa fue revertida.");
        } catch (RuntimeException ex) {
            twilio.releaseQuietly(provisioned.sid());
            throw ex;
        }
    }
}
