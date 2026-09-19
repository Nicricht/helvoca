package cl.helvoca.messaging.meta;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MetaWhatsAppTenantResolverTest {

    @Test
    void resolvesBusinessFromActiveEnabledMetaPhoneIdentity() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID businessId = UUID.randomUUID();

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);

        when(repository.findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                "META_WHATSAPP_CLOUD",
                "PHONE-123"))
                .thenReturn(Optional.of(phone));

        var result = new MetaWhatsAppTenantResolver(repository, context)
                .resolveBusinessId("  PHONE-123  ");

        assertEquals(Optional.of(businessId), result);
        verify(repository).findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                "META_WHATSAPP_CLOUD",
                "PHONE-123");
    }

    @Test
    void unknownMetaPhoneIdentityDoesNotFallbackToAnotherTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();

        when(repository.findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                "META_WHATSAPP_CLOUD",
                "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertTrue(new MetaWhatsAppTenantResolver(repository, context)
                .resolveBusinessId("UNKNOWN")
                .isEmpty());
    }

    @Test
    void blankPhoneIdentityNeverQueriesDatabase() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();

        assertTrue(new MetaWhatsAppTenantResolver(repository, context)
                .resolveBusinessId(" ")
                .isEmpty());

        verifyNoInteractions(repository);
    }
}
