package cl.helvoca.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendHttpsSenderTest {
    private static SimpleMailMessage email() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("acceso@cuentas.recepvoz.cl");
        message.setTo("owner@example.test");
        message.setSubject("Confirmación");
        message.setText("Enlace privado de prueba");
        return message;
    }

    @Test
    void sendsOnePlainTextMessageToFixedHttpsEndpointWithBearerKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test-only"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"from\":\"acceso@cuentas.recepvoz.cl\",\"to\":[\"owner@example.test\"],\"subject\":\"Confirmación\",\"text\":\"Enlace privado de prueba\"}"))
                .andRespond(withSuccess("{\"id\":\"test-id\"}", MediaType.APPLICATION_JSON));
        ResendHttpsSender sender = new ResendHttpsSender("re_test-only", builder);
        assertTrue(sender.isConfigured());
        sender.send(email());
        server.verify();
    }

    @Test
    void refusesUnconfiguredSenderAndIncompleteMessage() {
        ResendHttpsSender sender = new ResendHttpsSender("", RestClient.builder());
        assertFalse(sender.isConfigured());
        assertThrows(IllegalStateException.class, () -> sender.send(email()));
        ResendHttpsSender ready = new ResendHttpsSender("re_test-only", RestClient.builder());
        assertThrows(IllegalArgumentException.class, () -> ready.send(new SimpleMailMessage()));
    }


    @Test
    void rejectsEveryIncompleteMailFieldWithoutIssuingHttpRequest() {
        ResendHttpsSender sender = new ResendHttpsSender("re_test-only", RestClient.builder());
        SimpleMailMessage noTo = new SimpleMailMessage();
        noTo.setFrom("acceso@cuentas.recepvoz.cl");
        noTo.setSubject("Subject");
        noTo.setText("Body");
        assertThrows(IllegalArgumentException.class, () -> sender.send(noTo));

        SimpleMailMessage twoRecipients = email();
        twoRecipients.setTo("one@example.test", "two@example.test");
        assertThrows(IllegalArgumentException.class, () -> sender.send(twoRecipients));

        SimpleMailMessage noSubject = email();
        noSubject.setSubject(null);
        assertThrows(IllegalArgumentException.class, () -> sender.send(noSubject));

        SimpleMailMessage noText = email();
        noText.setText(null);
        assertThrows(IllegalArgumentException.class, () -> sender.send(noText));

        SimpleMailMessage noFrom = email();
        noFrom.setFrom(null);
        assertThrows(IllegalArgumentException.class, () -> sender.send(noFrom));
    }

    @Test
    void whitespaceAndNullApiKeysAreBothUnconfigured() {
        assertFalse(new ResendHttpsSender(null, RestClient.builder()).isConfigured());
        assertFalse(new ResendHttpsSender("  ", RestClient.builder()).isConfigured());
        assertTrue(new ResendHttpsSender(" re_test-only ", RestClient.builder()).isConfigured());
    }

    @Test
    void rejectsProviderFailureWithoutExposingResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("sensitive provider details")
                        .contentType(MediaType.APPLICATION_JSON));
        ResendHttpsSender sender = new ResendHttpsSender("re_test-only", builder);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> sender.send(email()));
        assertEquals("Account email delivery failed", failure.getMessage());
        assertNull(failure.getCause());
        server.verify();
    }
}
