package cl.helvoca.telephony.twilio;

import cl.helvoca.phone.PhoneNumber;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier.AccountKind;
import static cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier.Code;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioExistingNumberOwnershipVerifierTest {
    private static final String MAIN = "AC" + "a".repeat(32);
    private static final String CHILD = "AC" + "c".repeat(32);
    private static final String FOREIGN = "AC" + "d".repeat(32);
    private static final String PN = "PN" + "b".repeat(32);
    private static final String OTHER_PN = "PN" + "e".repeat(32);
    private static final String NUMBER = "+12025550123";
    private static final String BASE = "https://api.twilio.com/2010-04-01/Accounts/";
    private static final String TOKEN = "unit-test-only";
    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString((MAIN + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));

    private TwilioProperties properties;
    private MockRestServiceServer server;
    private TwilioExistingNumberOwnershipVerifier verifier;

    @BeforeEach
    void setup() {
        properties = new TwilioProperties();
        properties.setAccountSid(MAIN);
        properties.setAuthToken(TOKEN);
        properties.setPublicBaseUrl("https://recepvoz.cl");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.twilio.com");
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new TwilioExistingNumberOwnershipVerifier(properties, CHILD, builder.build());
    }

    private String account(String owner) {
        return """
                {"sid":"%s","owner_account_sid":"%s","status":"active"}
                """.formatted(owner, MAIN);
    }

    private String phone(String owner) {
        return """
                {"sid":"%s","account_sid":"%s","phone_number":"%s",
                 "status":"in-use","voice_receive_mode":"voice",
                 "capabilities":{"voice":true},
                 "voice_url":"https://recepvoz.cl/webhooks/v1/twilio/voice",
                 "voice_method":"POST",
                 "status_callback":"https://recepvoz.cl/webhooks/v1/twilio/status",
                 "status_callback_method":"POST",
                 "voice_application_sid":null,"trunk_sid":null}
                """.formatted(PN, owner, NUMBER);
    }

    private String listing(String record) {
        return "{\"incoming_phone_numbers\":[" + record + "],\"next_page_uri\":null}";
    }

    private void expect(String url, String body) {
        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectAccount(String owner, String body) {
        expect(BASE + owner + ".json", body);
    }

    private void expectList(String owner, String body) {
        expect(BASE + owner + "/IncomingPhoneNumbers.json?PhoneNumber=%2B12025550123", body);
    }

    private void expectDetail(String owner, String body) {
        expect(BASE + owner + "/IncomingPhoneNumbers/" + PN + ".json", body);
    }

    private void expectAccountAndList(String owner, String list) {
        expectAccount(owner, account(owner));
        expectList(owner, list);
    }

    private void expectFull(String owner, String listPhone, String detailPhone) {
        expectAccountAndList(owner, listing(listPhone));
        expectDetail(owner, detailPhone);
    }

    private Code check(String owner, String sid) {
        return verifier.verify(NUMBER, owner, sid).code();
    }

    @Test
    void verifiesMainAccountWithFreshExactSidAndOnlyReadRequests() {
        expectFull(MAIN, phone(MAIN), phone(MAIN));
        var result = verifier.verify(NUMBER, MAIN, PN);
        assertEquals(Code.VERIFIED, result.code());
        assertTrue(result.verified());
        assertEquals(PN, result.evidence().phoneSid());
        assertEquals(MAIN, result.evidence().ownerAccountSid());
        assertEquals(AccountKind.MAIN, result.evidence().accountKind());
        assertEquals("TwilioEvidence[REDACTED]", result.evidence().toString());
        assertEquals("TwilioVerification[VERIFIED]", result.toString());
        server.verify();
    }

    @Test
    void verifiesExplicitlyAllowedSubaccountUsingParentAuthenticationWithUnknownLegacySid() {
        expectFull(CHILD, phone(CHILD), phone(CHILD));
        var result = verifier.verify(NUMBER, CHILD, null);
        assertTrue(result.verified());
        assertEquals(AccountKind.SUBACCOUNT, result.evidence().accountKind());
        assertEquals(CHILD, result.evidence().ownerAccountSid());
        server.verify();
    }

    @Test
    void evidenceIsHiddenFromJsonEvenIfAnOperatorAccidentallySerializesOutcome() throws Exception {
        expectFull(CHILD, phone(CHILD), phone(CHILD));
        var outcome = verifier.verify(NUMBER, CHILD, null);
        String json = new ObjectMapper().writeValueAsString(outcome);
        assertFalse(json.contains(PN));
        assertFalse(json.contains(CHILD));
        assertFalse(json.contains(NUMBER));
        assertFalse(outcome.toString().contains(PN));
        assertFalse(outcome.toString().contains(CHILD));
        server.verify();
    }

    @Test
    void refusesInvalidOrTenantSuppliedIdentifiersBeforeAnyCarrierLookup() {
        for (String number : new String[]{"+111", "12025550123", " +12025550123", "+12025550123 "}) {
            assertEquals(Code.INVALID_TARGET, verifier.verify(number, MAIN, null).code());
        }
        assertEquals(Code.INVALID_TARGET, verifier.verify(null, MAIN, null).code());
        assertEquals(Code.INVALID_TARGET, verifier.verify(NUMBER, null, null).code());
        assertEquals(Code.INVALID_TARGET, verifier.verify(NUMBER, "ACinvalid", null).code());
        assertEquals(Code.INVALID_TARGET, verifier.verify(NUMBER, MAIN, "PNinvalid").code());
        assertEquals(Code.ACCOUNT_NOT_ALLOWED, check(FOREIGN, null));
        assertEquals(Code.ACCOUNT_NOT_ALLOWED,
                new TwilioExistingNumberOwnershipVerifier(properties, null, null)
                        .verify(NUMBER, CHILD, null).code());
        assertEquals(Code.ACCOUNT_NOT_ALLOWED,
                new TwilioExistingNumberOwnershipVerifier(properties, "bad ", null)
                        .verify(NUMBER, CHILD, null).code());
        server.verify();
    }

    @Test
    void refusesIncompleteProviderConfigurationBeforeNetwork() {
        properties.setAuthToken("");
        assertEquals(Code.NOT_CONFIGURED, check(MAIN, null));
        properties.setAuthToken(TOKEN);
        properties.setPublicBaseUrl("http://insecure.example");
        assertEquals(Code.NOT_CONFIGURED, check(MAIN, null));
        properties.setPublicBaseUrl("https://recepvoz.cl");
        properties.setAccountSid("ACwrong");
        assertEquals(Code.NOT_CONFIGURED, check(MAIN, null));
        server.verify();
    }

    @Test
    void rejectsInactiveForeignOrIncompleteAccountAttestation() {
        String original = account(CHILD);
        for (String altered : new String[]{
                "null",
                "{}",
                original.replace(CHILD, FOREIGN),
                original.replace(MAIN, FOREIGN),
                original.replace("\"active\"", "\"suspended\"")
        }) {
            server.reset();
            expectAccount(CHILD, altered);
            assertEquals(Code.ACCOUNT_NOT_VERIFIED, check(CHILD, null));
            server.verify();
        }
    }

    @Test
    void rejectsEmptyDuplicateIncompleteAndPaginatedFilteredResults() {
        for (String page : new String[]{
                "{\"incoming_phone_numbers\":[],\"next_page_uri\":null}",
                "{\"incoming_phone_numbers\":[" + phone(MAIN) + "," + phone(MAIN) + "],\"next_page_uri\":null}",
                "null",
                "{}",
                "{\"incoming_phone_numbers\":[],\"next_page_uri\":\"/more\"}",
                "{\"incoming_phone_numbers\":null,\"next_page_uri\":null}",
                "{\"incoming_phone_numbers\":[true],\"next_page_uri\":null}"
        }) {
            server.reset();
            expectAccountAndList(MAIN, page);
            Code result = check(MAIN, null);
            if (page.contains("\"incoming_phone_numbers\":[]") && page.contains("\"next_page_uri\":null")) {
                assertEquals(Code.NUMBER_NOT_FOUND, result);
            } else if (page.contains("},{")) {
                assertEquals(Code.AMBIGUOUS_NUMBER, result);
            } else {
                assertEquals(Code.INCOMPLETE_RESPONSE, result);
            }
            server.verify();
        }
    }

    @Test
    void mismatchingExpectedSidOrInvalidCarrierSidNeverPromotesLegacyNumber() {
        for (String record : new String[]{
                phone(MAIN).replace(PN, "PNwrong"),
                phone(MAIN).replace("\"sid\":\"" + PN + "\"", "\"sid\":null")
        }) {
            server.reset();
            expectAccountAndList(MAIN, listing(record));
            assertEquals(Code.PHONE_IDENTITY_MISMATCH, check(MAIN, null));
            server.verify();
        }
        server.reset();
        expectAccountAndList(MAIN, listing(phone(MAIN)));
        assertEquals(Code.PHONE_IDENTITY_MISMATCH, check(MAIN, OTHER_PN));
        server.verify();
    }

    @Test
    void rejectsContradictoryIdentityCapabilityAndRoutingInListedResource() {
        String good = phone(CHILD);
        String[][] mutations = {
                { "\"account_sid\":\"" + CHILD + "\"", "\"account_sid\":\"" + FOREIGN + "\"", "PHONE_IDENTITY_MISMATCH" },
                { "\"phone_number\":\"" + NUMBER + "\"", "\"phone_number\":\"+12025550124\"", "PHONE_IDENTITY_MISMATCH" },
                { "\"status\":\"in-use\"", "\"status\":\"released\"", "VOICE_UNAVAILABLE" },
                { "\"voice_receive_mode\":\"voice\"", "\"voice_receive_mode\":\"fax\"", "VOICE_UNAVAILABLE" },
                { "\"capabilities\":{\"voice\":true}", "\"capabilities\":null", "VOICE_UNAVAILABLE" },
                { "\"voice\":true", "\"voice\":false", "VOICE_UNAVAILABLE" },
                { "https://recepvoz.cl/webhooks/v1/twilio/voice", "https://foreign.example/voice", "WEBHOOK_MISMATCH" },
                { "\"voice_method\":\"POST\"", "\"voice_method\":\"GET\"", "WEBHOOK_MISMATCH" },
                { "https://recepvoz.cl/webhooks/v1/twilio/status", "https://foreign.example/status", "WEBHOOK_MISMATCH" },
                { "\"status_callback_method\":\"POST\"", "\"status_callback_method\":\"GET\"", "WEBHOOK_MISMATCH" },
                { "\"voice_application_sid\":null", "\"voice_application_sid\":\"APforeign\"", "WEBHOOK_MISMATCH" },
                { "\"trunk_sid\":null", "\"trunk_sid\":\"TKforeign\"", "WEBHOOK_MISMATCH" },
                { "\"trunk_sid\":null", "\"trunk_sid\":123", "WEBHOOK_MISMATCH" }
        };
        for (String[] mutation : mutations) {
            server.reset();
            expectAccountAndList(CHILD, listing(good.replace(mutation[0], mutation[1])));
            assertEquals(Code.valueOf(mutation[2]), check(CHILD, null), mutation[0]);
            server.verify();
        }
    }

    @Test
    void rejectsContradictoryOrIncompleteSecondFetchEvenAfterMatchingList() {
        server.reset();
        expectFull(MAIN, phone(MAIN), "null");
        assertEquals(Code.INCOMPLETE_RESPONSE, check(MAIN, null));
        server.verify();

        for (String detail : new String[]{
                phone(MAIN).replace(PN, OTHER_PN),
                phone(MAIN).replace(MAIN, CHILD),
                phone(MAIN).replace(NUMBER, "+12025550124"),
                phone(MAIN).replace("\"voice\":true", "\"voice\":false"),
                phone(MAIN).replace("\"voice_method\":\"POST\"", "\"voice_method\":\"GET\"")
        }) {
            server.reset();
            expectFull(MAIN, phone(MAIN), detail);
            assertFalse(verifier.verify(NUMBER, MAIN, null).verified());
            server.verify();
        }
    }

    @Test
    void acceptsBlankUnconfiguredOverridesButNeverPopulatedApplicationOrTrunk() {
        String phone = phone(MAIN)
                .replace("\"voice_application_sid\":null", "\"voice_application_sid\":\" \"")
                .replace("\"trunk_sid\":null", "\"trunk_sid\":\"\"");
        expectFull(MAIN, phone, phone);
        assertEquals(Code.VERIFIED, check(MAIN, null));
        server.verify();
    }

    @Test
    void rejectsProviderHttpAndTransportOutagesWithoutLeakingDetailsOrPersisting() {
        server.expect(once(), requestTo(BASE + MAIN + ".json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"message\":\"credential SECRET MUST NOT LEAK\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        var result = verifier.verify(NUMBER, MAIN, null);
        assertEquals(Code.PROVIDER_UNAVAILABLE, result.code());
        assertFalse(result.verified());
        assertNull(result.evidence());
        assertFalse(result.toString().contains("SECRET"));
        server.verify();

        server.reset();
        expectAccount(MAIN, account(MAIN));
        server.expect(once(), requestTo(BASE + MAIN + "/IncomingPhoneNumbers.json?PhoneNumber=%2B12025550123"))
                .andRespond(req -> { throw new IOException("simulated token leak"); });
        assertEquals(Code.PROVIDER_UNAVAILABLE, check(MAIN, null));
        server.verify();

        server.reset();
        expectAccountAndList(MAIN, listing(phone(MAIN)));
        server.expect(once(), requestTo(BASE + MAIN + "/IncomingPhoneNumbers/" + PN + ".json"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals(Code.PROVIDER_UNAVAILABLE, check(MAIN, null));
        server.verify();
    }

    @Test
    void legacyDatabaseObjectIsNeverChangedByCarrierReadOnlyVerification() {
        PhoneNumber row = new PhoneNumber();
        row.setPhoneNumber(NUMBER);
        row.setExternalId(null);
        row.setTwilioBindingSource(null);
        row.setVoiceVerifiedAt(null);
        row.setActive(true);
        expectFull(MAIN, phone(MAIN), phone(MAIN));
        assertEquals(Code.VERIFIED, check(MAIN, row.getExternalId()));
        assertNull(row.getExternalId());
        assertNull(row.getTwilioBindingSource());
        assertNull(row.getVoiceVerifiedAt());
        assertTrue(row.isActive());
        server.verify();
    }

    @Test
    void noEvidenceMeansNotVerifiedAndDiagnosticsNeverPrintCarrierData() {
        var denied = verifier.verify(null, MAIN, PN);
        assertFalse(denied.verified());
        assertEquals("TwilioVerification[INVALID_TARGET]", denied.toString());
    }
}
