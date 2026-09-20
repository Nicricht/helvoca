package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppEmbeddedSignupBootstrapServiceTest {

    @Test
    void staysUnavailableAndDoesNotExposePartialIdsByDefault() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupAppId("123456789");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertFalse(response.enabled());
        assertFalse(response.available());
        assertNull(response.appId());
        assertNull(response.configId());
        assertEquals("v26.0", response.graphApiVersion());
    }

    @Test
    void exposesOnlySdkBootstrapIdentifiersWhenFullyEnabled() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");
        meta.setEmbeddedSignupConfigId("987654321");
        meta.setAppSecret("super-secret");
        meta.setVerifyToken("verify-secret");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertTrue(response.enabled());
        assertTrue(response.available());
        assertEquals("123456789", response.appId());
        assertEquals("987654321", response.configId());
        assertEquals("v26.0", response.graphApiVersion());
        assertFalse(response.toString().contains("super-secret"));
        assertFalse(response.toString().contains("verify-secret"));
    }

    @Test
    void doesNotExposeIdsWhenConfigurationIsIncomplete() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setEmbeddedSignupEnabled(true);
        meta.setEmbeddedSignupAppId("123456789");

        var response = new MetaWhatsAppEmbeddedSignupBootstrapService(meta).bootstrap();

        assertTrue(response.enabled());
        assertFalse(response.available());
        assertNull(response.appId());
        assertNull(response.configId());
    }
}
