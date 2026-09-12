package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TwilioSignatureValidationFilterTest {

    @Test
    void outboundTestRequiresTwilioSignatureValidation() {
        TwilioSignatureValidationFilter filter =
                new TwilioSignatureValidationFilter(mock(TwilioSignatureValidator.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/webhooks/v1/twilio/outbound-test");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void productionVoiceRequiresTwilioSignatureValidation() {
        TwilioSignatureValidationFilter filter =
                new TwilioSignatureValidationFilter(mock(TwilioSignatureValidator.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/webhooks/v1/twilio/voice");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void unrelatedRoutesAreNotFiltered() {
        TwilioSignatureValidationFilter filter =
                new TwilioSignatureValidationFilter(mock(TwilioSignatureValidator.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertTrue(filter.shouldNotFilter(request));
    }
}
