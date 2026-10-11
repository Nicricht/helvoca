package cl.helvoca.phone;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.security.TenantDatabaseContext;
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
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
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
                databaseContext,
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


    private TwilioProperties readyTwilio() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAccountSid(PARENT_SID);
        twilio.setAuthToken("parent-token");
        twilio.setPublicBaseUrl("https://recepvoz.example");
        return twilio;
    }

    private WhatsAppProperties tenantSettings() {
        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setSandboxTenantPhone(TENANT_PHONE);
        return whatsApp;
    }

    private HttpResponse<String> returnedPhone() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"incoming_phone_numbers":[{
                    "sid":"PNcccccccccccccccccccccccccccccccc",
                    "account_sid":"ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "phone_number":"+14705331828"
                }]}
                """);
        return response;
    }

    private HttpResponse<String> updatedPhone() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"account_sid":"ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                """);
        return response;
    }

    @Test
    void rejectsAnotherTenantBeforeAnyExternalTwilioRequest() throws Exception {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        PhoneNumber anchor = new PhoneNumber();
        anchor.setBusinessId(UUID.randomUUID());
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
            return Optional.of(anchor);
        });
        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(UUID.randomUUID());
        when(phones.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        HttpClient http = mock(HttpClient.class);

        var runner = new TwilioSubaccountVoiceBootstrapRunner(
                true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);
        assertEquals("Twilio subaccount phone is already attached to a different Helvoca tenant",
                assertThrows(IllegalStateException.class, () -> runner.run(null)).getMessage());
        verifyNoInteractions(http);
        verify(phones, never()).save(any());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void rejectsExistingWrongProviderBeforeAnyExternalTwilioRequest() throws Exception {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID tenant = UUID.randomUUID();
        PhoneNumber anchor = new PhoneNumber();
        anchor.setBusinessId(tenant);
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));
        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(tenant);
        existing.setProvider("OTHER");
        when(phones.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        HttpClient http = mock(HttpClient.class);

        var runner = new TwilioSubaccountVoiceBootstrapRunner(
                true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);
        assertEquals("Twilio subaccount phone is registered with a different provider",
                assertThrows(IllegalStateException.class, () -> runner.run(null)).getMessage());
        verifyNoInteractions(http);
        verify(phones, never()).save(any());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void existingOtherSidBlocksWebhookUpdateAfterReadOnlyLookup() throws Exception {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID tenant = UUID.randomUUID();
        PhoneNumber anchor = new PhoneNumber();
        anchor.setBusinessId(tenant);
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));
        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(tenant);
        existing.setExternalId("PNdddddddddddddddddddddddddddddddd");
        when(phones.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> lookupResponse = returnedPhone();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(lookupResponse);

        var runner = new TwilioSubaccountVoiceBootstrapRunner(
                true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);
        assertEquals("Twilio subaccount phone SID conflicts with existing Helvoca mapping",
                assertThrows(IllegalStateException.class, () -> runner.run(null)).getMessage());
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("GET", requests.getValue().method());
        verify(phones, never()).save(any());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void matchingTenantAndPhoneSidAllowsUpdateAndRestoresSystemScope() throws Exception {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID tenant = UUID.randomUUID();
        PhoneNumber anchor = new PhoneNumber();
        anchor.setBusinessId(tenant);
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));
        PhoneNumber existing = new PhoneNumber();
        existing.setBusinessId(tenant);
        existing.setPhoneNumber(PHONE);
        existing.setExternalId(PHONE_SID);
        when(phones.findByPhoneNumber(PHONE)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
            return Optional.of(existing);
        });
        when(phones.save(any())).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
            return invocation.getArgument(0);
        });
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> lookupResponse = returnedPhone();
        HttpResponse<String> updateResponse = updatedPhone();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(lookupResponse, updateResponse);

        var runner = new TwilioSubaccountVoiceBootstrapRunner(
                true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);
        runner.run(null);
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(phones).save(existing);
        assertEquals(tenant, existing.getBusinessId());
        assertEquals(PHONE_SID, existing.getExternalId());
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }


    @Test
    void existingTenantPhoneWithAbsentOrEmptySidIsSafelyLinkedToVerifiedSid() throws Exception {
        for (String previousSid : new String[] {null, ""}) {
            PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
            TenantDatabaseContext context = new TenantDatabaseContext();
            UUID tenant = UUID.randomUUID();
            PhoneNumber anchor = new PhoneNumber();
            anchor.setBusinessId(tenant);
            when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenReturn(Optional.of(anchor));
            PhoneNumber existing = new PhoneNumber();
            existing.setBusinessId(tenant);
            existing.setPhoneNumber(PHONE);
            existing.setExternalId(previousSid);
            when(phones.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
            when(phones.save(any())).thenAnswer(invocation -> {
                assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
                return invocation.getArgument(0);
            });
            HttpClient http = mock(HttpClient.class);
            HttpResponse<String> lookup = returnedPhone();
            HttpResponse<String> update = updatedPhone();
            when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(lookup, update);

            var runner = new TwilioSubaccountVoiceBootstrapRunner(
                    true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);
            runner.run(null);
            verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            verify(phones).save(existing);
            assertEquals(PHONE_SID, existing.getExternalId());
            assertEquals(tenant, existing.getBusinessId());
            assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
        }
    }


    @Test
    void missingOrInactiveTenantAnchorRejectsBootstrapBeforeAnyTwilioRequest() throws Exception {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext context = new TenantDatabaseContext();
        when(phones.findByPhoneNumberAndActiveTrue(TENANT_PHONE)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrDenied().mode());
            return Optional.empty();
        });
        HttpClient http = mock(HttpClient.class);
        var runner = new TwilioSubaccountVoiceBootstrapRunner(
                true, SUBACCOUNT_SID, PHONE, readyTwilio(), tenantSettings(), phones, context, http);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals("WhatsApp Sandbox tenant phone must resolve to an active Helvoca tenant before voice bootstrap",
                error.getMessage());
        verify(phones).findByPhoneNumberAndActiveTrue(TENANT_PHONE);
        verify(phones, never()).findByPhoneNumber(PHONE);
        verify(phones, never()).save(any());
        verifyNoInteractions(http);
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        WhatsAppProperties whatsApp = new WhatsAppProperties();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        HttpClient http = mock(HttpClient.class);

        new TwilioSubaccountVoiceBootstrapRunner(
                false,
                SUBACCOUNT_SID,
                PHONE,
                twilio,
                whatsApp,
                phones,
                databaseContext,
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
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
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
                databaseContext,
                http);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals("Twilio returned phone ownership for a different account", error.getMessage());
        verify(phones, never()).save(any());
    }
}
