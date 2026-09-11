package cl.helvoca.telephony.twilio;

import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialTwimlFactoryTest {

    @Test
    void productionMediaStreamUsesBidirectionalPcmuEndpointAndStatusCallback() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example/");
        twilio.setMediaStreamUrl("wss://helvoca.example/ws/twilio");
        TrialVoiceProperties trial = new TrialVoiceProperties();
        UUID callId = UUID.randomUUID();

        String xml = new TwimlFactory(twilio, trial).connectMediaStream(callId);

        assertTrue(xml.contains("<Connect><Stream url=\"wss://helvoca.example/ws/twilio\""));
        assertTrue(xml.contains("statusCallback=\"https://helvoca.example/webhooks/v1/twilio/stream-status\""));
        assertTrue(xml.contains("statusCallbackMethod=\"POST\""));
        assertTrue(xml.contains("<Parameter name=\"callId\" value=\"" + callId + "\"/>"));
    }

    @Test
    void trialGatherUsesSpeechAndSecretGatedHelvocaActionWithoutMediaStream() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example/");
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setLanguage("es-CL");
        trial.setTtsLanguage("es-MX");
        trial.setTtsVoice("Polly.Mía-Generative");
        trial.setWebhookSecret("test-secret");

        String xml = new TwimlFactory(twilio, trial).trialGather("Hola & bienvenido <Nico>");

        assertTrue(xml.contains("<Gather input=\"speech\""));
        assertTrue(xml.contains("action=\"https://helvoca.example/webhooks/v1/twilio/trial/gather?trialKey=test-secret\""));
        assertTrue(xml.contains("language=\"es-CL\""));
        assertTrue(xml.contains("voice=\"Polly.Mía-Generative\" language=\"es-MX\""));
        assertTrue(xml.contains("Hola &amp; bienvenido &lt;Nico&gt;"));
        assertFalse(xml.contains("<Stream"));
    }

    @Test
    void trialHangupUsesConfiguredGenerativeVoiceAndEscapesSpokenText() {
        TwilioProperties twilio = new TwilioProperties();
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setTtsLanguage("es-MX");
        trial.setTtsVoice("Polly.Mía-Generative");

        String xml = new TwimlFactory(twilio, trial).trialSayAndHangup("Reserva <confirmada> & lista");

        assertTrue(xml.contains("voice=\"Polly.Mía-Generative\" language=\"es-MX\""));
        assertTrue(xml.contains("Reserva &lt;confirmada&gt; &amp; lista"));
        assertTrue(xml.contains("<Hangup/>"));
    }

    @Test
    void trialTransferDialsConfiguredTargetAndUsesSecretGatedResultCallback() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example/");
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setTtsLanguage("es-MX");
        trial.setTtsVoice("Polly.Mía-Generative");
        trial.setWebhookSecret("test-secret");

        String xml = new TwimlFactory(twilio, trial)
                .trialTransfer("Te comunico.", "+56922222222");

        assertTrue(xml.contains("<Dial action=\"https://helvoca.example/webhooks/v1/twilio/trial/transfer-result?trialKey=test-secret\""));
        assertTrue(xml.contains("timeout=\"20\""));
        assertTrue(xml.contains("answerOnBridge=\"true\""));
        assertTrue(xml.contains("<Number>+56922222222</Number>"));
        assertTrue(xml.contains("Te comunico."));
        assertFalse(xml.contains("<Gather"));
    }
}
