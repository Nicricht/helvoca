package cl.helvoca.telephony.twilio;

import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialTwimlFactoryTest {

    @Test
    void trialGatherUsesSpeechAndSecretGatedHelvocaActionWithoutMediaStream() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example/");
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setLanguage("es-CL");
        trial.setWebhookSecret("test-secret");

        String xml = new TwimlFactory(twilio, trial).trialGather("Hola & bienvenido <Nico>");

        assertTrue(xml.contains("<Gather input=\"speech\""));
        assertTrue(xml.contains("action=\"https://helvoca.example/webhooks/v1/twilio/trial/gather?trialKey=test-secret\""));
        assertTrue(xml.contains("language=\"es-CL\""));
        assertTrue(xml.contains("Hola &amp; bienvenido &lt;Nico&gt;"));
        assertFalse(xml.contains("<Stream"));
    }

    @Test
    void trialHangupEscapesSpokenText() {
        TwilioProperties twilio = new TwilioProperties();
        TrialVoiceProperties trial = new TrialVoiceProperties();

        String xml = new TwimlFactory(twilio, trial).trialSayAndHangup("Reserva <confirmada> & lista");

        assertTrue(xml.contains("Reserva &lt;confirmada&gt; &amp; lista"));
        assertTrue(xml.contains("<Hangup/>"));
    }
}
