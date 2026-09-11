package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.user.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneNumberServiceTest {

    @Test
    void reconnectingNumberAlreadyOwnedBySameTenantIsIdempotent() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+17372508034");
        phone.setActive(false);
        when(repository.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, users, trialProperties());
        PhoneNumberResponse response = service.create(new PhoneNumberRequest("+17372508034", null, true));

        assertTrue(response.active());
        verify(repository).save(phone);
    }

    @Test
    void authenticatedTenantClaimsSeededTrialNumberWithoutReassigningHistoricalPhoneRow() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID demoBusiness = UUID.randomUUID();
        UUID historicalPhoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber existing = mock(PhoneNumber.class);
        when(existing.getId()).thenReturn(historicalPhoneId);
        when(existing.getBusinessId()).thenReturn(demoBusiness);
        when(existing.getProvider()).thenReturn("TWILIO_TRIAL");
        when(existing.getExternalId()).thenReturn("TWILIO_TRIAL");

        when(repository.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(existing));
        when(users.findAllByBusinessIdOrderByName(demoBusiness)).thenReturn(List.of());
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        when(repository.save(any(PhoneNumber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, users, trialProperties());
        PhoneNumberResponse response = service.create(new PhoneNumberRequest("+17372508034", null, true));

        verify(existing, never()).setBusinessId(any());
        verify(existing).setActive(false);
        verify(existing).setPhoneNumber(startsWith("archived-"));
        verify(existing).setExternalId("TWILIO_TRIAL_ARCHIVED_" + historicalPhoneId);
        verify(repository).saveAndFlush(existing);

        assertEquals("TWILIO_TRIAL", response.provider());
        assertEquals("TWILIO_TRIAL", response.externalId());
        assertEquals("+17372508034", response.phoneNumber());
        assertTrue(response.active());
    }

    @Test
    void cannotClaimTrialNumberIfItsOwnerHasARealUser() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID otherBusiness = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(otherBusiness);
        phone.setProvider("TWILIO_TRIAL");
        phone.setExternalId("TWILIO_TRIAL");
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);
        when(repository.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(phone));
        when(users.findAllByBusinessIdOrderByName(otherBusiness)).thenReturn(List.of(mock(cl.helvoca.user.AppUser.class)));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, users, trialProperties());
        assertThrows(ConflictException.class,
                () -> service.create(new PhoneNumberRequest("+17372508034", null, true)));
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void cannotBindNormalNumberOwnedByAnotherTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID otherBusiness = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(otherBusiness);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);
        when(repository.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, users, trialProperties());
        ConflictException error = assertThrows(ConflictException.class,
                () -> service.create(new PhoneNumberRequest("+17372508034", null, true)));

        assertEquals("Este número ya está conectado a otro negocio", error.getMessage());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    private static TrialVoiceProperties trialProperties() {
        TrialVoiceProperties properties = new TrialVoiceProperties();
        properties.setEnabled(true);
        properties.setPhoneNumber("+17372508034");
        return properties;
    }
}
