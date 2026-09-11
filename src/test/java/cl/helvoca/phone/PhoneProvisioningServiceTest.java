package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneProvisioningServiceTest {

    @Test
    void purchaseRequiresExplicitConfirmation() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest("+12025550123", false)));

        assertTrue(ex.getMessage().contains("confirmar"));
        verifyNoInteractions(twilio);
    }

    @Test
    void purchasedNumberIsSavedForAuthenticatedTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByPhoneNumber("+12025550123")).thenReturn(Optional.empty());
        when(twilio.purchase("+12025550123"))
                .thenReturn(new TwilioPhoneProvisioningClient.PurchasedPhoneNumber(
                        "PNaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "+12025550123"));
        when(repository.saveAndFlush(any(PhoneNumber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);
        PhoneNumberResponse response = service.provision(
                new ProvisionPhoneNumberRequest("+12025550123", true));

        assertEquals("TWILIO", response.provider());
        assertEquals("PNaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", response.externalId());
        assertEquals("+12025550123", response.phoneNumber());
        assertTrue(response.active());

        verify(repository).saveAndFlush(argThat(phone ->
                businessId.equals(phone.getBusinessId())
                        && "+12025550123".equals(phone.getPhoneNumber())
                        && "PNaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".equals(phone.getExternalId())
                        && phone.isActive()));
        verify(twilio, never()).releaseQuietly(anyString());
    }

    @Test
    void alreadyProvisionedNumberForSameTenantIsIdempotent() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);

        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(businessId);
        existing.setProvider("TWILIO");
        existing.setExternalId("PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        existing.setPhoneNumber("+12025550123");
        existing.setActive(false);
        when(repository.findByPhoneNumber("+12025550123")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);
        PhoneNumberResponse response = service.provision(
                new ProvisionPhoneNumberRequest("+12025550123", true));

        assertTrue(response.active());
        verifyNoInteractions(twilio);
    }

    @Test
    void crossTenantNumberIsNeverPurchasedAgain() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(UUID.randomUUID());
        existing.setProvider("TWILIO");
        existing.setExternalId("PNcccccccccccccccccccccccccccccccc");
        existing.setPhoneNumber("+12025550123");
        when(repository.findByPhoneNumber("+12025550123")).thenReturn(Optional.of(existing));

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);

        assertThrows(ConflictException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest("+12025550123", true)));
        verifyNoInteractions(twilio);
    }

    @Test
    void persistenceFailureCompensatesByReleasingPurchasedNumber() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(repository.findByPhoneNumber("+12025550123")).thenReturn(Optional.empty());
        when(twilio.purchase("+12025550123"))
                .thenReturn(new TwilioPhoneProvisioningClient.PurchasedPhoneNumber(
                        "PNdddddddddddddddddddddddddddddddd", "+12025550123"));
        when(repository.saveAndFlush(any(PhoneNumber.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);

        assertThrows(ConflictException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest("+12025550123", true)));
        verify(twilio).releaseQuietly("PNdddddddddddddddddddddddddddddddd");
    }
}
