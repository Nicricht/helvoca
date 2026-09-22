package cl.helvoca.phone;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioSubaccountVoiceBootstrapRunnerTest {
    private static final String PARENT_SID = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SUBACCOUNT_SID = "ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String PHONE_SID = "PNcccccccccccccccccccccccccccccccc";
    private static final String PHONE = "+14705331828";
    private static final String TENANT_PHONE = "+14355652512";

    @Test
    void configuresSubaccountVoiceAndAttachesNumberToSandboxTenant() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid(PARENT_SID);
        twilio.setAuthToken("parent-token");
        twilio.setPublicBaseUrl("https://recepvoz.example");

        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setSandboxTenantPhone(TENANT_PHONE);

        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        PhoneNumber anchor = new PhoneNumber();
        UUID businessId = UUID.randomUUID();
        anchor.setBusinessId(businessId);
        anchor.setPhoneNumber(TENANT_PHONE);
        anchor.setActive(true);
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));
        when(phones.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> lookup = mock(HttpResponse.class);
        when(lookup.statusCode()).thenReturn(200);
        when(lookup.body()).thenReturn("""
                {"incoming_phone_numbers":[{
                  "sid":"PNcccccccccccccccccccccccccccccccc",
                  "account_sid":"ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "phone_number":"+14705331828"
                }]}
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> update = mock(HttpResponse.class);
        when(update.statusCode()).thenReturn(200);
        when(update.body()).thenReturn("""
                {"account_sid":"ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(lookup, update);

        TwilioSubaccountVoiceBootstrapRunner runner = new TwilioSubaccountVoiceBootstrapRunner(
                true,
                SUBACCOUNT_SID,
                PHONE,
                twilio,
                whatsApp,
                phones,
                http);

        runner.run(null);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest lookupRequest = requestCaptor.getAllValues().get(0);
        HttpRequest updateRequest = requestCaptor.getAllValues().get(1);

        assertEquals("GET", lookupRequest.method());
        assertTrue(lookupRequest.uri().toString().contains("/Accounts/" + SUBACCOUNT_SID + "/IncomingPhoneNumbers.json"));
        assertEquals("POST", updateRequest.method());
        assertEquals(
                "https://api.twilio.com/2010-04-01/Accounts/" + SUBACCOUNT_SID
                        + "/IncomingPhoneNumbers/" + PHONE_SID + ".json",
                updateRequest.uri().toString());

        var phoneCaptor = org.mockito.ArgumentCaptor.forClass(PhoneNumber.class);
        verify(phones).save(phoneCaptor.capture());
        PhoneNumber saved = phoneCaptor.getValue();
        assertEquals(businessId, saved.getBusinessId());
        assertEquals(PHONE, saved.getPhoneNumber());
        assertEquals(PHONE_SID, saved.getExternalId());
        assertEquals("TWILIO", saved.getProvider());
        assertTrue(saved.isActive());
        assertFalse(saved.isWhatsappEnabled());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        WhatsAppProperties whatsApp = new WhatsAppProperties();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        HttpClient http = mock(HttpClient.class);

        new TwilioSubaccountVoiceBootstrapRunner(
                false,
                SUBACCOUNT_SID,
                PHONE,
                twilio,
                whatsApp,
                phones,
                http).run(null);

        verifyNoInteractions(phones, http);
    }

    @Test
    void rejectsPhoneReturnedFromDifferentAccount() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid(PARENT_SID);
        twilio.setAuthToken("parent-token");
        twilio.setPublicBaseUrl("https://recepvoz.example");

        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setSandboxTenantPhone(TENANT_PHONE);

        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        PhoneNumber anchor = new PhoneNumber();
        anchor.setBusinessId(UUID.randomUUID());
        anchor.setPhoneNumber(TENANT_PHONE);
        anchor.setActive(true);
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> lookup = mock(HttpResponse.class);
        when(lookup.statusCode()).thenReturn(200);
        when(lookup.body()).thenReturn("""
                {"incoming_phone_numbers":[{
                  "sid":"PNcccccccccccccccccccccccccccccccc",
                  "account_sid":"ACdddddddddddddddddddddddddddddddd",
                  "phone_number":"+14705331828"
                }]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(lookup);

        TwilioSubaccountVoiceBootstrapRunner runner = new TwilioSubaccountVoiceBootstrapRunner(
                true,
                SUBACCOUNT_SID,
                PHONE,
                twilio,
                whatsApp,
                phones,
                http);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals("Twilio returned phone ownership for a different account", error.getMessage());
        verify(phones, never()).save(any());
    }
}
