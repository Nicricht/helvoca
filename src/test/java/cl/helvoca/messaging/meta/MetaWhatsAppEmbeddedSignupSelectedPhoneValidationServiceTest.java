package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupSelectedPhoneValidationServiceTest {

    @Test
    void validatesPhoneFromFirstDiscoveredPage() {
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(discovery.discover("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
                        "PHONE_NUMBERS_DISCOVERED",
                        true,
                        List.of(phone(
                                "1913623884432103",
                                "+56 9 3333 4444",
                                "RecepVoz Demo")),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                discovery,
                phoneNumbers,
                meta);

        var result = service.validate(
                "1906385232743451",
                " 1913623884432103 ");

        assertEquals("PHONE_NUMBER_VALIDATED", result.state());
        assertEquals("1913623884432103", result.phoneNumberId());
        assertEquals("+56 9 3333 4444", result.displayPhoneNumber());
        assertEquals("RecepVoz Demo", result.verifiedName());
        verifyNoInteractions(phoneNumbers);
    }

    @Test
    void validatesPhoneFromLaterPageUsingSystemUserToken() {
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(discovery.discover("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
                        "PHONE_NUMBERS_DISCOVERED",
                        true,
                        List.of(phone(
                                "111111111111111",
                                "+56 9 1111 1111",
                                "First")),
                        "cursor-1"));
        when(phoneNumbers.list(
                "1906385232743451",
                "system-user-secret",
                "cursor-1"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(phone(
                                "1913623884432103",
                                "+56 9 3333 4444",
                                "Selected")),
                        "cursor-2"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                discovery,
                phoneNumbers,
                meta);

        var result = service.validate(
                "1906385232743451",
                "1913623884432103");

        assertEquals("1913623884432103", result.phoneNumberId());
        assertEquals("Selected", result.verifiedName());
        verify(phoneNumbers).list(
                "1906385232743451",
                "system-user-secret",
                "cursor-1");
        verifyNoMoreInteractions(phoneNumbers);
    }

    @Test
    void rejectsPhoneThatDoesNotBelongToSelectedWaba() {
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(discovery.discover("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
                        "PHONE_NUMBERS_DISCOVERED",
                        true,
                        List.of(),
                        "cursor-1"));
        when(phoneNumbers.list(
                "1906385232743451",
                "system-user-secret",
                "cursor-1"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(phone(
                                "111111111111111",
                                "+56 9 1111 1111",
                                "Other")),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                discovery,
                phoneNumbers,
                meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.validate(
                        "1906385232743451",
                        "1913623884432103"));

        assertEquals(
                "META_EMBEDDED_SIGNUP_PHONE_NOT_IN_SELECTED_WABA",
                error.getMessage());
    }

    @Test
    void rejectsInvalidPhoneIdBeforeDiscoveryOrNetworkCalls() {
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                discovery,
                phoneNumbers,
                readyProperties());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validate("1906385232743451", "../phone"));

        verifyNoInteractions(discovery, phoneNumbers);
    }

    @Test
    void rejectsRepeatedPaginationCursor() {
        var discovery = mock(MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryService.class);
        var phoneNumbers = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(discovery.discover("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaPhoneDiscoveryResult(
                        "PHONE_NUMBERS_DISCOVERED",
                        true,
                        List.of(),
                        "cursor-1"));
        when(phoneNumbers.list(
                "1906385232743451",
                "system-user-secret",
                "cursor-1"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(),
                        "cursor-1"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                discovery,
                phoneNumbers,
                meta);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validate(
                        "1906385232743451",
                        "1913623884432103"));

        assertEquals(
                "Meta Embedded Signup phone number pagination repeated a cursor",
                error.getMessage());
    }

    private static MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber phone(
            String id,
            String displayPhoneNumber,
            String verifiedName) {
        return new MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber(
                id,
                displayPhoneNumber,
                verifiedName,
                "GREEN",
                "VERIFIED");
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        return meta;
    }
}
