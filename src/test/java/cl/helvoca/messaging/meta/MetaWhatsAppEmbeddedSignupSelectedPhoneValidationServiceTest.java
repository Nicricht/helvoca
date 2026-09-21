package cl.helvoca.messaging.meta;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupSelectedPhoneValidationServiceTest {

    @Test
    void validatesSelectedPhoneFromFirstPageAfterSubscriptionIsConfirmed() {
        UUID businessId = UUID.randomUUID();
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED", true, false, true));
        when(phones.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(
                                phone("1913623884432103", "+56 9 3333 4444"),
                                phone("1999999999999999", "+56 9 5555 6666")),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta, tenantProvider, audit);

        var result = service.validate("1906385232743451", "1913623884432103");

        assertEquals("PHONE_NUMBER_VALIDATED", result.state());
        assertEquals("1913623884432103", result.phoneNumberId());
        assertEquals("+56 9 3333 4444", result.displayPhoneNumber());

        var ordered = inOrder(subscription, phones);
        ordered.verify(subscription).ensureSubscribed("1906385232743451");
        ordered.verify(phones).list("1906385232743451", "system-user-secret");
        verify(audit).humanSuccess(
                eq(businessId),
                eq("META_WHATSAPP_PHONE_VALIDATED"),
                eq("META_WHATSAPP_CONFIG"),
                eq(businessId),
                isNull(),
                eq(Map.of(
                        "phoneValidated", true,
                        "appSubscribed", true)));
    }

    @Test
    void validatesSelectedPhoneFromLaterPage() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED", true, true, true));
        when(phones.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(phone("1999999999999999", "+56 9 5555 6666")),
                        "next-cursor"));
        when(phones.list("1906385232743451", "system-user-secret", "next-cursor"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(phone("1913623884432103", "+56 9 3333 4444")),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta);

        var result = service.validate("1906385232743451", "1913623884432103");

        assertEquals("1913623884432103", result.phoneNumberId());
        verify(phones).list(
                "1906385232743451",
                "system-user-secret",
                "next-cursor");
    }

    @Test
    void rejectsPhoneThatDoesNotBelongToSelectedWaba() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        var tenantProvider = mock(TenantProvider.class);
        var audit = mock(AuditService.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED", true, false, true));
        when(phones.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(
                        List.of(phone("1999999999999999", "+56 9 5555 6666")),
                        null));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta, tenantProvider, audit);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.validate("1906385232743451", "1913623884432103"));

        assertEquals("META_EMBEDDED_SIGNUP_PHONE_NOT_IN_WABA", error.getMessage());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsInvalidIdsBeforeAnyNetworkDependentStep() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validate("../waba", "1913623884432103"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validate("1906385232743451", "../phone"));

        verifyNoInteractions(subscription, phones);
    }

    @Test
    void doesNotLookupPhonesWhenSubscriptionIsNotConfirmed() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIPTION_UNCONFIRMED", true, false, false));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.validate("1906385232743451", "1913623884432103"));

        assertEquals("META_EMBEDDED_SIGNUP_APP_NOT_SUBSCRIBED", error.getMessage());
        verifyNoInteractions(phones);
    }

    @Test
    void failsClosedOnRepeatedPaginationCursor() {
        var subscription = mock(MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService.class);
        var phones = mock(MetaWhatsAppEmbeddedSignupPhoneNumberClient.class);
        MetaWhatsAppProperties meta = readyProperties();

        when(subscription.ensureSubscribed("1906385232743451"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
                        "APP_SUBSCRIBED", true, false, true));
        when(phones.list("1906385232743451", "system-user-secret"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(List.of(), "same-cursor"));
        when(phones.list("1906385232743451", "system-user-secret", "same-cursor"))
                .thenReturn(new MetaWhatsAppEmbeddedSignupPhoneNumberPage(List.of(), "same-cursor"));

        var service = new MetaWhatsAppEmbeddedSignupSelectedPhoneValidationService(
                subscription, phones, meta);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validate("1906385232743451", "1913623884432103"));

        assertEquals(
                "Meta Embedded Signup phone number pagination repeated a cursor",
                error.getMessage());
    }

    private static MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber phone(
            String id,
            String displayPhoneNumber) {
        return new MetaWhatsAppEmbeddedSignupPhoneNumberPage.PhoneNumber(
                id,
                displayPhoneNumber,
                "RecepVoz Demo",
                "GREEN",
                "VERIFIED");
    }

    private static MetaWhatsAppProperties readyProperties() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupSystemUserAccessToken("system-user-secret");
        return meta;
    }
}
