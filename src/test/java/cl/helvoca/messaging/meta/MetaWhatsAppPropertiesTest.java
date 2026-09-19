package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppPropertiesTest {

    @Test
    void defaultsAreFailClosed() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        assertFalse(properties.isEnabled());
        assertTrue(properties.isWebhookValidationEnabled());
        assertEquals("v26.0", properties.getGraphApiVersion());
        assertEquals("https://graph.facebook.com", properties.getGraphBaseUrl());
        assertEquals("https://graph.facebook.com/v26.0", properties.graphApiRoot());
        assertFalse(properties.hasVerifyToken());
        assertFalse(properties.hasAppSecret());
    }

    @Test
    void trimsConfiguredValuesAndNormalizesBaseUrl() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphApiVersion(" v26.0 ");
        properties.setGraphBaseUrl(" https://graph.facebook.com/ ");
        properties.setVerifyToken(" verify-me ");
        properties.setAppSecret(" secret ");

        assertEquals("v26.0", properties.getGraphApiVersion());
        assertEquals("https://graph.facebook.com", properties.getGraphBaseUrl());
        assertEquals("verify-me", properties.getVerifyToken());
        assertEquals("secret", properties.getAppSecret());
        assertTrue(properties.hasVerifyToken());
        assertTrue(properties.hasAppSecret());
    }
}
