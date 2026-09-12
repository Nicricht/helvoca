package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.telephony.CallLifecycleService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OpenAiLiveSipTwimlContractTest {

    @Test
    void twimlIsWellFormedRequestsSrtpAndCarriesSignedCarrierCorrelation() throws Exception {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");

        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-test");

        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi,
                live,
                new OpenAiLiveRouteSigner(live),
                mock(CallLifecycleService.class),
                mock(RealtimeToolService.class),
                mock(OpenAiLiveSidebandManager.class));

        String callSid = "CA0123456789abcdef0123456789abcdef";
        String xml = service.twiml("+14355652512", "+56911111111", callSid);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        String sip = document.getElementsByTagName("Sip").item(0).getTextContent();

        assertTrue(sip.startsWith("sip:proj_test123@sip.api.openai.com;secure=true?"));
        assertFalse(sip.contains("transport=tls"), "secure=true is the Twilio SRTP contract, not TLS-only media");
        assertTrue(sip.contains("x-recepvoz-business=%2B14355652512"));
        assertTrue(sip.contains("x-recepvoz-caller=%2B56911111111"));
        assertTrue(sip.contains("x-recepvoz-call=" + callSid));
        assertTrue(sip.contains("x-recepvoz-issued-at="));
        assertTrue(sip.contains("x-recepvoz-route="));
        assertFalse(sip.endsWith("x-recepvoz-route="));
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.contains("<Stream"));
    }
}
