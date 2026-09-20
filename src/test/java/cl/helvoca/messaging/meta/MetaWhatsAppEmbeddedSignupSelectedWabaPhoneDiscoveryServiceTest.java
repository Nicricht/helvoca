package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryServiceTest {

    @Test
    void listsPhoneCandidatesOnlyAfterSelectedWabaIsSubscribed() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED",
                        true,
                        false,
                        true));
        when(phoneNumbers.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(new MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber(
                                "1913623884432103",
                                "+56 9 3333 4444",
                                "RecepVoz Demo",
                                "GREEN",
                                "VERIFIED")),
                        "next-phone-cursor"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService(
                subscription,
                phoneNumbers,
                meta);

        var result = service.discover("1906385232743451");

        assertEquals("PHONE_NUMBERS_DISCOVERED", result.state());
        assertTrue(result.appSubscribed());
        assertEquals(1, result.phoneNumbers().size());
        assertEquals("1913623884432103", result.phoneNumbers().get(0).id());
        assertEquals("+56 9 3333 4444", result.phoneNumbers().get(0).displayPhoneNumber());
        assertEquals("next-phone-cursor", result.afterCursor());

        var ordered = inOrder(subscription, phoneNumbers);
        ordered.verify(subscription).ensureSubscribed("1906385232743451");
        ordered.verify(phoneNumbers).list(
                "1906385232743451",
                "system-user-secret");
    }

    @Test
    void acceptsEmptyPhoneCandidateListWithoutPersistingAnything() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED",
                        true,
                        true,
                        true));
        when(phoneNumbers.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService(
                subscription,
                phoneNumbers,
                meta);

        var result = service.discover("1906385232743451");

        assertTrue(result.phoneNumbers().isEmpty());
        assertNull(result.afterCursor());
        assertTrue(result.appSubscribed());
    }

    @Test
    void doesNotListPhonesWhenSubscriptionIsNotConfirmed() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIPTION_UNCONFIRMED",
                        true,
                        false,
                        false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService(
                subscription,
                phoneNumbers,
                meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.discover("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_APP_NOT_SUBSCRIBED", error.getMessage());
        verifyNoInteractions(phoneNumbers);
    }

    @Test
    void doesNotListPhonesWhenSubscriptionPreconditionFails() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenThrow(new ConflictException("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService(
                subscription,
                phoneNumbers,
                meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.discover("1906385232743451"));

        assertEquals("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED", error.getMessage());
        verifyNoInteractions(phoneNumbers);
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        return meta;
    }
}
