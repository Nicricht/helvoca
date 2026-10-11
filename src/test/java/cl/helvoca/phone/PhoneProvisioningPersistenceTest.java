package cl.helvoca.phone;

import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneProvisioningPersistenceTest {
    @Test
    void providerResultIsPersistedForCurrentTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(twilio.managedAccountSid()).thenReturn("AC" + "c".repeat(32));
        when(repository.findByPhoneNumber("+12025550123")).thenReturn(Optional.empty());
        when(twilio.provision("+12025550123")).thenReturn(
                new TwilioPhoneProvisioningClient.ProvisionedPhoneNumber(
                        "PNaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "+12025550123"));
        when(repository.saveAndFlush(any(PhoneNumber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhoneNumberResponse response = new PhoneProvisioningService(repository, tenant, twilio)
                .provision(new ProvisionPhoneNumberRequest("+12025550123", true));

        assertEquals("+12025550123", response.phoneNumber());
        assertEquals("TWILIO", response.provider());
        assertFalse(response.active(), "Newly purchased numbers remain pending carrier attestation");
        verify(repository).saveAndFlush(argThat(phone ->
                businessId.equals(phone.getBusinessId())
                        && "MANAGED_PURCHASE".equals(phone.getTwilioBindingSource())
                        && ("AC" + "c".repeat(32)).equals(phone.getTwilioAccountSid())
                        && phone.getVoiceVerifiedAt() == null
                        && !phone.isActive()));
    }
}
