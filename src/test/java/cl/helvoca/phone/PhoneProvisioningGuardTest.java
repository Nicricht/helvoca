package cl.helvoca.phone;

import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhoneProvisioningGuardTest {
    @Test
    void statusReflectsDisabledAutomaticProvisioning() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(twilio.enabled()).thenReturn(false);
        when(twilio.configured()).thenReturn(true);

        PhoneProvisioningStatusResponse response = new PhoneProvisioningService(repository, tenant, twilio).status();

        assertFalse(response.enabled());
        assertTrue(response.configured());
        assertFalse(response.purchaseAvailable());
    }

    @Test
    void provisioningRequiresConfirmationBeforeProviderCall() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);

        assertThrows(IllegalArgumentException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest("+12025550123", false)));
        verifyNoInteractions(twilio);
    }
}
