package cl.helvoca.telephony.twilio;

import cl.helvoca.common.ExternalProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioPhoneOwnershipVerificationTest {
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String PHONE_SID = "PN" + "b".repeat(32);
    private static final String PHONE = "+12025550123";
    private TwilioProperties properties;
    private MockRestServiceServer server;
    private TwilioPhoneProvisioningClient client;

    @BeforeEach
    void setup() {
        properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT);
        properties.setAuthToken("mock-test-only");
        properties.setPublicBaseUrl("https://recepvoz.cl");
        properties.setProvisioningEnabled(true);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.twilio.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwilioPhoneProvisioningClient(properties, builder.build());
    }

    private static String validBody() {
        return """
                {"incoming_phone_numbers":[{
                "sid":"PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "phone_number":"+12025550123",
                "account_sid":"ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "capabilities":{"voice":true},
                "voice_url":"https://recepvoz.cl/webhooks/v1/twilio/voice",
                "voice_method":"POST",
                "status_callback":"https://recepvoz.cl/webhooks/v1/twilio/status",
                "status_callback_method":"POST",
                "voice_application_sid":"",
                "trunk_sid":""}]}
                """;
    }

    private void expect(String response) {
        server.expect(once(), requestTo("https://api.twilio.com/2010-04-01/Accounts/"
                        + ACCOUNT + "/IncomingPhoneNumbers.json?PhoneNumber=%2B12025550123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @Test
    void carrierAttestsExactOwnerNumberSidAndVoiceRoutesWithoutWrites() {
        expect(validBody());
        TwilioPhoneProvisioningClient.VerifiedPhoneNumber verified = client.verifyManagedPhone(PHONE);
        assertEquals(PHONE_SID, verified.sid());
        assertEquals(PHONE, verified.phoneNumber());
        server.verify();
    }

    @Test
    void credentialsMustBeConfiguredBeforeLookup() {
        properties.setAuthToken("");
        assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE));
        server.verify();
    }

    @Test
    void malformedNumberIsRejectedBeforeLookup() {
        assertThrows(IllegalArgumentException.class, () -> client.verifyManagedPhone("not-a-number"));
        server.verify();
    }

    @Test
    void rejectsMissingOrAmbiguousProviderRecords() {
        for (String body : new String[]{
                "null",
                "{}",
                "{\"incoming_phone_numbers\":[]}",
                "{\"incoming_phone_numbers\":[1]}",
                "{\"incoming_phone_numbers\":[{},{}]}"
        }) {
            expect(body);
            assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE), body);
        }
        server.verify();
    }

    @Test
    void rejectsEveryInconsistentIdentityOrCapability() {
        String good = validBody();
        String[][] cases = {
                {"\"PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"", "null"},
                {"\"PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"", "\"PNinvalid\""},
                {"\"+12025550123\"", "\"+12025550124\""},
                {"\"ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", "\"ACcccccccccccccccccccccccccccccccc\""},
                {"\"voice\":true", "\"voice\":false"},
                {"\"capabilities\":{\"voice\":true}", "\"capabilities\":null"}
        };
        for (String[] replacement : cases) {
            expect(good.replace(replacement[0], replacement[1]));
            assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE));
        }
        server.verify();
    }

    @Test
    void rejectsWrongWebhookOrForeignVoiceApplicationOrTrunk() {
        String good = validBody();
        String[][] cases = {
                {"https://recepvoz.cl/webhooks/v1/twilio/voice", "https://attacker.example/voice"},
                {"\"voice_method\":\"POST\"", "\"voice_method\":\"GET\""},
                {"https://recepvoz.cl/webhooks/v1/twilio/status", "https://attacker.example/status"},
                {"\"status_callback_method\":\"POST\"", "\"status_callback_method\":\"GET\""},
                {"\"voice_application_sid\":\"\"", "\"voice_application_sid\":\"APowner\""},
                {"\"trunk_sid\":\"\"", "\"trunk_sid\":\"TKowner\""}
        };
        for (String[] replacement : cases) {
            expect(good.replace(replacement[0], replacement[1]));
            assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE));
        }
        server.verify();
    }

    @Test
    void emptyOptionalRoutingResourcesDoNotOverrideWebhook() {
        expect(validBody().replace("\"trunk_sid\":\"\"", "\"trunk_sid\":null"));
        assertEquals(PHONE_SID, client.verifyManagedPhone(PHONE).sid());
        server.verify();
    }

    @Test
    void handlesTwilioHttpFailuresWithoutClaimingOwnership() {
        server.expect(once(), requestTo("https://api.twilio.com/2010-04-01/Accounts/"
                        + ACCOUNT + "/IncomingPhoneNumbers.json?PhoneNumber=%2B12025550123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE));
        server.verify();
    }

    @Test
    void handlesNetworkErrorsWithoutClaimingOwnership() {
        server.expect(once(), requestTo("https://api.twilio.com/2010-04-01/Accounts/"
                        + ACCOUNT + "/IncomingPhoneNumbers.json?PhoneNumber=%2B12025550123"))
                .andRespond(request -> { throw new IOException("simulated transport outage"); });
        assertThrows(ExternalProviderException.class, () -> client.verifyManagedPhone(PHONE));
        server.verify();
    }
}
