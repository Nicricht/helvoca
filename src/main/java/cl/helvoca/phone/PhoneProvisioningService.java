package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
            message = "El aprovisionamiento automático está deshabilitado. Puedes conectar un número existente manualmente.";
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

    public PhoneNumberResponse provision(ProvisionPhoneNumberRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("Debes confirmar explícitamente el aprovisionamiento del número.");
        }

        String requestedNumber = request.phoneNumber().trim();
        PhoneNumber existing = repository.findByPhoneNumber(requestedNumber).orElse(null);
        if (existing != null) {
            if (existing.getBusinessId().equals(businessId)
                    && "TWILIO".equalsIgnoreCase(existing.getProvider())
                    && existing.getExternalId() != null
                    && existing.getExternalId().startsWith("PN")) {
                existing.setActive(true);
                return PhoneNumberResponse.from(repository.save(existing));
            }
            throw new ConflictException("Este número ya está registrado en Helvoca y no se aprovisionará de nuevo.");
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
        phone.setActive(true);

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
