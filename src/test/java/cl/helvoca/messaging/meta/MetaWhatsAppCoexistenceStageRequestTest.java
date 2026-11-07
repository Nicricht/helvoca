package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaWhatsAppCoexistenceStageRequestTest {
    @Test
    void storesTheSelectedWabaAndPhoneNumberIds() {
        var request = new MetaWhatsAppCoexistenceStageRequest(
                "1906385232743451", "1913623884432103");

        assertEquals("1906385232743451", request.wabaId());
        assertEquals("1913623884432103", request.phoneNumberId());
    }
}
