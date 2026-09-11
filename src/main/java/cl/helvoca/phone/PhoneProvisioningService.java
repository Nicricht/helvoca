package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<AvailablePhoneNumberResponse> available(String country, String areaCode, int limit) {
        tenantProvider.requireBusinessId();
        return twilio.searchAvailable(country, areaCode, limit);
    }

    @Transactional
    public PhoneNumberResponse provision(ProvisionPhoneNumberRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        if (!Boolean.TRUE.equals(request.confirmPurchase())) {
            throw new IllegalArgumentException("Debes confirmar explícitamente la compra del número.");
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
            throw new ConflictException("Este número ya está registrado en Helvoca y no se comprará de nuevo.");
        }

        TwilioPhoneProvisioningClient.PurchasedPhoneNumber purchased = twilio.purchase(requestedNumber);
        String actualNumber = purchased.phoneNumber();
        if (!requestedNumber.equals(actualNumber)) {
            twilio.releaseQuietly(purchased.sid());
            throw new IllegalStateException("Twilio devolvió un número distinto al solicitado. La compra fue revertida.");
        }

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setExternalId(purchased.sid());
        phone.setPhoneNumber(actualNumber);
        phone.setActive(true);

        try {
            return PhoneNumberResponse.from(repository.saveAndFlush(phone));
        } catch (DataIntegrityViolationException ex) {
            twilio.releaseQuietly(purchased.sid());
            throw new ConflictException("El número se compró mientras otra operación lo registraba. La compra fue revertida.");
        } catch (RuntimeException ex) {
            twilio.releaseQuietly(purchased.sid());
            throw ex;
        }
    }
}
