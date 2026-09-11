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
    void authenticatedTenantCanClaimOnlyTheUnusedSeededTrialNumber() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID demoBusiness = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(demoBusiness);
        phone.setProvider("TWILIO_TRIAL");
        phone.setExternalId("TWILIO_TRIAL");
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);

        when(repository.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(phone));
        when(users.findAllByBusinessIdOrderByName(demoBusiness)).thenReturn(List.of());
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, users, trialProperties());
        PhoneNumberResponse response = service.create(new PhoneNumberRequest("+17372508034", null, true));

        assertEquals(currentBusiness, phone.getBusinessId());
        assertEquals("+17372508034", response.phoneNumber());
        assertTrue(response.active());
        verify(repository).save(phone);
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
    }

    private static TrialVoiceProperties trialProperties() {
        TrialVoiceProperties properties = new TrialVoiceProperties();
        properties.setEnabled(true);
        properties.setPhoneNumber("+17372508034");
        return properties;
    }
}
