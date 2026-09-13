package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TwilioMediaStreamTwimlFactoryTest {

    @Test
    void mediaStreamUsesWssAndCustomParametersInsteadOfQueryString() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        properties.setPublicBaseUrl("https://helvoca.example");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        TwilioMediaStreamTwimlFactory factory = new TwilioMediaStreamTwimlFactory(properties, signer);

        String xml = factory.twiml(
                "+14355652512",
                "+56911111111",
                "CA0123456789abcdef0123456789abcdef",
                "gemini");

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList streams = document.getElementsByTagName("Stream");
        assertEquals(1, streams.getLength());
        Element stream = (Element) streams.item(0);
        assertEquals("wss://helvoca.example/ws/v1/twilio/media", stream.getAttribute("url"));
        assertFalse(stream.getAttribute("url").contains("?"));
        assertEquals("https://helvoca.example/webhooks/v1/twilio/stream-status",
                stream.getAttribute("statusCallback"));
        assertEquals("POST", stream.getAttribute("statusCallbackMethod"));

        NodeList parameters = document.getElementsByTagName("Parameter");
        assertEquals(6, parameters.getLength());
        assertTrue(hasParameter(parameters, "provider", "gemini"));
        assertTrue(hasParameter(parameters, "callSid", "CA0123456789abcdef0123456789abcdef"));
        assertTrue(hasParameter(parameters, "business", "+14355652512"));
        assertTrue(hasParameter(parameters, "caller", "+56911111111"));
        assertTrue(hasNonBlankParameter(parameters, "issuedAt"));
        assertTrue(hasNonBlankParameter(parameters, "route"));

        assertTrue(xml.contains("<Connect>"));
        assertFalse(xml.contains("<Say"));
        assertFalse(xml.contains("<Gather"));
        assertFalse(xml.toLowerCase().contains("polly"));
    }

    private static boolean hasParameter(NodeList list, String name, String value) {
        for (int i = 0; i < list.getLength(); i++) {
            Element item = (Element) list.item(i);
            if (name.equals(item.getAttribute("name")) && value.equals(item.getAttribute("value"))) return true;
        }
        return false;
    }

    private static boolean hasNonBlankParameter(NodeList list, String name) {
        for (int i = 0; i < list.getLength(); i++) {
            Element item = (Element) list.item(i);
            if (name.equals(item.getAttribute("name")) && !item.getAttribute("value").isBlank()) return true;
        }
        return false;
    }
}
