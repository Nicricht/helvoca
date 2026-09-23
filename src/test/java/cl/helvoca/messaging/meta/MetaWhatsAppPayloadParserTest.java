package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaWhatsAppPayloadParserTest {

    @Test
    void extractsTextMessageIdentityAndDestination() throws Exception {
        byte[] body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "WABA-1",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": {
                          "display_phone_number": "15551234567",
                          "phone_number_id": "PHONE-123"
                        },
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.MESSAGE-1",
                          "timestamp": "1720000000",
                          "text": {"body": "Hola, quiero reservar"},
                          "type": "text"
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var messages = MetaWhatsAppPayloadParser.parseTextMessages(body);

        assertEquals(1, messages.size());
        assertEquals("wamid.MESSAGE-1", messages.getFirst().messageId());
        assertEquals("PHONE-123", messages.getFirst().phoneNumberId());
        assertEquals("56911111111", messages.getFirst().from());
        assertEquals("Hola, quiero reservar", messages.getFirst().text());
    }

    @Test
    void ignoresStatusEventsAndNonTextMessages() throws Exception {
        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "statuses": [{"id": "wamid.STATUS-1", "status": "delivered"}],
                        "messages": [
                          {"from": "56911111111", "id": "wamid.IMAGE-1", "type": "image"}
                        ]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertTrue(MetaWhatsAppPayloadParser.parseTextMessages(body).isEmpty());
    }

    @Test
    void extractsMultipleTextMessagesAcrossEntries() throws Exception {
        byte[] body = """
                {
                  "entry": [
                    {
                      "changes": [{
                        "value": {
                          "metadata": {"phone_number_id": "PHONE-A"},
                          "messages": [{
                            "from": "56910000001",
                            "id": "wamid.A",
                            "type": "text",
                            "text": {"body": "Uno"}
                          }]
                        }
                      }]
                    },
                    {
                      "changes": [{
                        "value": {
                          "metadata": {"phone_number_id": "PHONE-B"},
                          "messages": [{
                            "from": "56910000002",
                            "id": "wamid.B",
                            "type": "text",
                            "text": {"body": "Dos"}
                          }]
                        }
                      }]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var messages = MetaWhatsAppPayloadParser.parseTextMessages(body);

        assertEquals(2, messages.size());
        assertEquals("PHONE-A", messages.get(0).phoneNumberId());
        assertEquals("PHONE-B", messages.get(1).phoneNumberId());
    }

    @Test
    void missingRequiredFieldsAreIgnored() throws Exception {
        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "type": "text",
                          "text": {"body": "Sin ID"}
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertTrue(MetaWhatsAppPayloadParser.parseTextMessages(body).isEmpty());
    }
    @Test
    void extractsAudioMessageMediaIdentityAndMimeType() throws Exception {
        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.AUDIO-1",
                          "type": "audio",
                          "audio": {
                            "id": "123456789012345",
                            "mime_type": "audio/ogg; codecs=opus",
                            "voice": true
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var messages = MetaWhatsAppPayloadParser.parseAudioMessages(body);

        assertEquals(1, messages.size());
        assertEquals("wamid.AUDIO-1", messages.getFirst().messageId());
        assertEquals("PHONE-123", messages.getFirst().phoneNumberId());
        assertEquals("56911111111", messages.getFirst().from());
        assertEquals("123456789012345", messages.getFirst().mediaId());
        assertEquals("audio/ogg; codecs=opus", messages.getFirst().mimeType());
    }

}
