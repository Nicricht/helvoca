package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupAuthorizationCodeRequestTest {
    @Test
    void trimsNonNullValuesAndRedactsTheAuthorizationCode() {
        var request = new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
                "  temporary-code  ", " 1906385232743451 ");

        assertEquals("temporary-code", request.code());
        assertEquals("1906385232743451", request.wabaId());
        assertFalse(request.toString().contains("temporary-code"));
    }

    @Test
    void preservesNullFieldsWithoutDereferencingThem() {
        var request = new MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(null, null);

        assertNull(request.code());
        assertNull(request.wabaId());
        assertTrue(request.toString().contains("wabaId=null"));
    }
}
