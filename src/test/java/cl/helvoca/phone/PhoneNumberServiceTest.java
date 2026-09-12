package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

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
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(false);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);
        PhoneNumberResponse response = service.create(new PhoneNumberRequest("+14355652512", null, true));

        assertTrue(response.active());
        verify(repository).save(phone);
    }

    @Test
    void cannotBindNumberOwnedByAnotherTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID otherBusiness = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(otherBusiness);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);
        ConflictException error = assertThrows(ConflictException.class,
                () -> service.create(new PhoneNumberRequest("+14355652512", null, true)));

        assertEquals("Este número ya está conectado a otro negocio", error.getMessage());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }
}
