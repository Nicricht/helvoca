package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TwilioCertificationCommandCallLauncherTest {

    @Test
    void validConfigurationRequiresExactAllowedTarget() {
        TwilioProperties properties = properties();
        TwilioCertificationCommandCallLauncher launcher = new TwilioCertificationCommandCallLauncher(
                properties,
                "+14355652512",
                "+56966939611",
                "+56966939611",
                "+56975856664",
                150,
                mock(TwilioCallControl.class));

        assertTrue(launcher.validConfiguration());
    }

    @Test
    void forbiddenTargetFailsClosed() {
        TwilioCertificationCommandCallLauncher launcher = new TwilioCertificationCommandCallLauncher(
                properties(),
                "+14355652512",
                "+56975856664",
                "+56975856664",
                "+56975856664",
                150,
                mock(TwilioCallControl.class));

        assertFalse(launcher.validConfiguration());
    }

    @Test
    void missingProviderCredentialsFailClosed() {
        TwilioProperties properties = new TwilioProperties();
        properties.setPublicBaseUrl("https://recepvoz.example");
        TwilioCertificationCommandCallLauncher launcher = new TwilioCertificationCommandCallLauncher(
                properties,
                "+14355652512",
                "+56966939611",
                "+56966939611",
                "+56975856664",
                150,
                mock(TwilioCallControl.class));

        assertFalse(launcher.validConfiguration());
    }

    private static TwilioProperties properties() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid("test-account");
        properties.setAuthToken("test-secret");
        properties.setPublicBaseUrl("https://recepvoz.example");
        return properties;
    }
}
