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
    void resolvesFullRouteFromActiveEnabledMetaPhoneIdentity() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();

        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phone.getId()).thenReturn(phoneId);
        when(phone.getPhoneNumber()).thenReturn("+56955555555");

        when(repository.findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                "META_WHATSAPP_CLOUD",
                "PHONE-123"))
                .thenReturn(Optional.of(phone));

        var result = new MetaWhatsAppTenantResolver(repository, context)
                .resolveRoute("  PHONE-123  ");

        assertTrue(result.isPresent());
        assertEquals(businessId, result.orElseThrow().businessId());
        assertEquals(phoneId, result.orElseThrow().phoneNumberId());
        assertEquals("+56955555555", result.orElseThrow().recipientPhone());
    }

    @Test
    void businessIdCompatibilityLookupUsesResolvedRoute() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID businessId = UUID.randomUUID();

        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);

        when(repository.findByWhatsappProviderAndWhatsappExternalIdAndActiveTrueAndWhatsappEnabledTrue(
                "META_WHATSAPP_CLOUD",
                "PHONE-123"))
                .thenReturn(Optional.of(phone));

        assertEquals(
                Optional.of(businessId),
                new MetaWhatsAppTenantResolver(repository, context).resolveBusinessId("PHONE-123"));
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
                .resolveRoute("UNKNOWN")
                .isEmpty());
    }

    @Test
    void blankPhoneIdentityNeverQueriesDatabase() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();

        assertTrue(new MetaWhatsAppTenantResolver(repository, context)
                .resolveRoute(" ")
                .isEmpty());

        verifyNoInteractions(repository);
    }
}
