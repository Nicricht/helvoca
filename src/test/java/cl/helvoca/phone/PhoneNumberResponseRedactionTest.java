package cl.helvoca.phone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberResponseRedactionTest {
    @Test
    void tenantFacingResponseNeverExposesInternalTwilioSidOrAccountSid() throws Exception {
        PhoneNumber phone = new PhoneNumber();
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setExternalId("PN" + "a".repeat(32));
        phone.setTwilioAccountSid("AC" + "b".repeat(32));

        PhoneNumberResponse response = PhoneNumberResponse.from(phone);
        assertEquals("+14355652512", response.phoneNumber());
        assertNull(response.externalId());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains(phone.getExternalId()));
        assertFalse(json.contains(phone.getTwilioAccountSid()));
    }

    @Test
    void carrierIdentifierIsAlsoRedactedForNonTwilioProviders() {
        PhoneNumber phone = new PhoneNumber();
        phone.setProvider("OTHER");
        phone.setExternalId("opaque-private-provider-id");
        assertNull(PhoneNumberResponse.from(phone).externalId());
    }
}
