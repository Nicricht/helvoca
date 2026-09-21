package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupPhoneRecordResolverServiceTest {

    @Test
    void reusesSameTenantPhoneAfterNormalizingMetaDisplayNumber() {
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        var phones = mock(PhoneNumberRepository.class);
        var tenant = mock(TenantProvider.class);
        PhoneNumber existing = mock(PhoneNumber.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumber("+56933334444")).thenReturn(Optional.of(existing));
        when(existing.getBusinessId()).thenReturn(businessId);
        when(existing.getId()).thenReturn(phoneId);

        var service = new MetaWhatsAppEmbeddedSignupPhoneRecordResolverService(phones, tenant);

        assertEquals(phoneId, service.resolve("+56 9 3333 4444"));
        verify(phones, never()).saveAndFlush(any());
    }

    @Test
    void createsWhatsappOnlyPhoneForCurrentTenantWhenMissing() {
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        var phones = mock(PhoneNumberRepository.class);
        var tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumber("+56933334444")).thenReturn(Optional.empty());
        when(phones.saveAndFlush(any(PhoneNumber.class))).thenAnswer(invocation -> {
            PhoneNumber submitted = invocation.getArgument(0);
            PhoneNumber saved = mock(PhoneNumber.class);
            when(saved.getId()).thenReturn(phoneId);
            assertEquals(businessId, submitted.getBusinessId());
            assertEquals(MetaWhatsAppMessagingProvider.ID, submitted.getProvider());
            assertEquals("+56933334444", submitted.getPhoneNumber());
            assertTrue(submitted.isActive());
            assertFalse(submitted.isWhatsappEnabled());
            assertEquals(MetaWhatsAppMessagingProvider.ID, submitted.getWhatsappProvider());
            return saved;
        });

        var service = new MetaWhatsAppEmbeddedSignupPhoneRecordResolverService(phones, tenant);

        assertEquals(phoneId, service.resolve("+56 9 3333 4444"));
    }

    @Test
    void refusesPhoneOwnedByAnotherTenant() {
        UUID businessId = UUID.randomUUID();
        var phones = mock(PhoneNumberRepository.class);
        var tenant = mock(TenantProvider.class);
        PhoneNumber existing = mock(PhoneNumber.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumber("+56933334444")).thenReturn(Optional.of(existing));
        when(existing.getBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppEmbeddedSignupPhoneRecordResolverService(phones, tenant);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.resolve("+56 9 3333 4444"));

        assertEquals("META_EMBEDDED_SIGNUP_PHONE_OWNED_BY_ANOTHER_TENANT", error.getMessage());
        verify(phones, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMalformedMetaDisplayNumber() {
        var phones = mock(PhoneNumberRepository.class);
        var tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        var service = new MetaWhatsAppEmbeddedSignupPhoneRecordResolverService(phones, tenant);

        assertThrows(IllegalArgumentException.class, () -> service.resolve("not-a-phone"));
        verifyNoInteractions(phones);
    }
}
