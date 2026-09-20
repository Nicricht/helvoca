package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaWhatsAppDeliveryStatusParserTest {

    @Test
    void extractsStatusTimestampPhoneAndFailureCode() throws Exception {
        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "statuses": [
                          {
                            "id": "wamid.SENT-1",
                            "status": "sent",
                            "timestamp": "1720000000"
                          },
                          {
                            "id": "wamid.FAIL-1",
                            "status": "failed",
                            "timestamp": "1720000001",
                            "errors": [{"code": 131047}]
                          }
                        ]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var statuses = MetaWhatsAppPayloadParser.parseDeliveryStatuses(body);

        assertEquals(2, statuses.size());
        assertEquals("wamid.SENT-1", statuses.get(0).messageId());
        assertEquals("PHONE-123", statuses.get(0).phoneNumberId());
        assertEquals("sent", statuses.get(0).status());
        assertEquals(Instant.ofEpochSecond(1720000000L), statuses.get(0).occurredAt());
        assertEquals("131047", statuses.get(1).errorCode());
    }
}
