package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class PublicBusinessSourceServiceTest {

    @Test
    void normalizesBareDomainsToHttps() {
        assertEquals("https://example.com", PublicBusinessSourceService.normalize("example.com").toString());
    }

    @Test
    void rejectsLoopbackAndPrivateTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> PublicBusinessSourceService.validatePublicTarget(URI.create("http://127.0.0.1")));
        assertThrows(IllegalArgumentException.class,
                () -> PublicBusinessSourceService.validatePublicTarget(URI.create("http://10.0.0.8")));
        assertThrows(IllegalArgumentException.class,
                () -> PublicBusinessSourceService.validatePublicTarget(URI.create("http://192.168.1.10")));
    }

    @Test
    void rejectsCredentialsAndNonWebPorts() {
        assertThrows(IllegalArgumentException.class,
                () -> PublicBusinessSourceService.validatePublicTarget(URI.create("https://user:pass@example.com")));
        assertThrows(IllegalArgumentException.class,
                () -> PublicBusinessSourceService.validatePublicTarget(URI.create("https://example.com:8080")));
    }

    @Test
    void stripsArbitraryScriptsAndStylesButKeepsVisibleText() {
        String html = "<html><style>.x{color:red}</style><script>alert(1)</script><body><h1>Don Pepe</h1><p>Abierto &amp; feliz</p></body></html>";
        String text = PublicBusinessSourceService.extractText(html);
        assertEquals("Don Pepe Abierto & feliz", text);
        assertFalse(text.contains("alert"));
        assertFalse(text.contains("color:red"));
    }

    @Test
    void keepsTitleDescriptionsAndJsonLdBusinessFacts() {
        String html = """
                <html>
                  <head>
                    <title>Restaurante Don Pepe</title>
                    <meta name="description" content="Cocina chilena en Santiago">
                    <meta property="og:description" content="Reservas todos los días">
                    <script type="application/ld+json">
                      {"@type":"Restaurant","name":"Don Pepe","openingHours":"Mo-Sa 12:00-23:00","address":"Providencia"}
                    </script>
                    <script>window.secret = 'no incluir';</script>
                  </head>
                  <body><p>Bienvenidos.</p></body>
                </html>
                """;

        String text = PublicBusinessSourceService.extractText(html);

        assertTrue(text.contains("Restaurante Don Pepe"));
        assertTrue(text.contains("Cocina chilena en Santiago"));
        assertTrue(text.contains("Reservas todos los días"));
        assertTrue(text.contains("openingHours"));
        assertTrue(text.contains("Mo-Sa 12:00-23:00"));
        assertTrue(text.contains("Providencia"));
        assertFalse(text.contains("window.secret"));
    }
}
