package cl.helvoca.messaging.outbound;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwilioWhatsAppProductionCertificationRunnerTest {
    @Test
    void sendsApprovedTemplateOnlyAfterClaimingRunId() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("wa-cert-20260918"), eq("whatsapp")))
                .thenReturn(1);

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {"contents":[
                  {"sid":"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "friendly_name":"helvoca_setup_confirmation_v1_20260918",
                   "language":"es",
                   "approvals":{"whatsapp":{"status":"approved"}}}
                ]}
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> sendResponse = mock(HttpResponse.class);
        when(sendResponse.statusCode()).thenReturn(201);
        when(sendResponse.body()).thenReturn("""
                {"sid":"SMaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","status":"queued"}
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> statusResponse = mock(HttpResponse.class);
        when(statusResponse.statusCode()).thenReturn(200);
        when(statusResponse.body()).thenReturn("""
                {"sid":"SMaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","status":"delivered"}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, sendResponse, statusResponse);

        TwilioWhatsAppProductionCertificationRunner runner =
                new TwilioWhatsAppProductionCertificationRunner(
                        true,
                        "wa-cert-20260918",
                        "+14355652512",
                        "+56966939611",
                        1,
                        1,
                        props,
                        jdbc,
                        http);

        var result = runner.executeOnce();

        assertEquals("delivered", result.messageStatus());
        assertEquals("SMaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.messageSid());
        verify(jdbc).update(anyString(), eq("wa-cert-20260918"), eq("whatsapp"));
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void createsTemplateAndSubmitsApprovalUsingLowercaseWhatsappPath() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("{\"contents\":[]}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(201);
        when(createResponse.body()).thenReturn(
                "{\"sid\":\"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> approvalResponse = mock(HttpResponse.class);
        when(approvalResponse.statusCode()).thenReturn(201);
        when(approvalResponse.body()).thenReturn("{\"status\":\"received\"}");

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, createResponse, approvalResponse);

        TwilioWhatsAppProductionCertificationRunner runner =
                new TwilioWhatsAppProductionCertificationRunner(
                        true,
                        "wa-cert-20260918",
                        "+14355652512",
                        "+56966939611",
                        1,
                        1,
                        props,
                        jdbc,
                        http);

        var result = runner.executeOnce();

        assertEquals("received", result.approvalStatus());
        assertEquals("awaiting_approval", result.messageStatus());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(
                "https://content.twilio.com/v1/Content/HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/ApprovalRequests/whatsapp",
                captor.getAllValues().get(2).uri().toString());
    }

    @Test
    void fetchesExistingApprovalStatusWithoutResubmitting() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {"contents":[
                  {"sid":"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "friendly_name":"helvoca_setup_confirmation_v1_20260918",
                   "language":"es"}
                ]}
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> approvalResponse = mock(HttpResponse.class);
        when(approvalResponse.statusCode()).thenReturn(200);
        when(approvalResponse.body()).thenReturn("""
                {"sid":"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "whatsapp":{"status":"received","category":"UTILITY","name":"helvoca_setup_confirmation_v1_20260918"}}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, approvalResponse);

        TwilioWhatsAppProductionCertificationRunner runner =
                new TwilioWhatsAppProductionCertificationRunner(
                        true,
                        "wa-cert-20260918",
                        "+14355652512",
                        "+56966939611",
                        1,
                        1,
                        props,
                        jdbc,
                        http);

        var result = runner.executeOnce();

        assertEquals("received", result.approvalStatus());
        assertEquals("awaiting_approval", result.messageStatus());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("GET", captor.getAllValues().get(1).method());
        assertEquals(
                "https://content.twilio.com/v1/Content/HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/ApprovalRequests",
                captor.getAllValues().get(1).uri().toString());
    }

    @Test
    void blocksDuplicateRealSendWhenRunIdWasAlreadyConsumed() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("wa-cert-20260918"), eq("whatsapp")))
                .thenReturn(0);

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {"contents":[
                  {"sid":"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "friendly_name":"helvoca_setup_confirmation_v1_20260918",
                   "language":"es",
                   "approvals":{"whatsapp":{"status":"approved"}}}
                ]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse);

        TwilioWhatsAppProductionCertificationRunner runner =
                new TwilioWhatsAppProductionCertificationRunner(
                        true,
                        "wa-cert-20260918",
                        "+14355652512",
                        "+56966939611",
                        1,
                        1,
                        props,
                        jdbc,
                        http);

        var result = runner.executeOnce();

        assertEquals("blocked_duplicate", result.messageStatus());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
