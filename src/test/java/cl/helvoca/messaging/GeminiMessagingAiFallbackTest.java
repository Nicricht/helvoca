package cl.helvoca.messaging;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiMessagingAiFallbackTest {

    @Test
    void returnsSanitizedGeminiTextWithoutPuttingApiKeyInUrl() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {"text": "Hola **cliente**"}
                        ]
                      }
                    }
                  ]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        String answer = fallback.respond(
                "Responde en español.",
                List.of(new MessagingAiClient.Turn("user", "Hola")),
                Set.of(),
                (name, args) -> "{}");

        assertEquals("Hola cliente", answer);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertEquals(
                "https://example.test/v1beta/models/gemini-3.8-flash:generateContent",
                request.uri().toString());
        assertFalse(request.uri().toString().contains("secret-key"));
        assertEquals("secret-key", request.headers().firstValue("x-goog-api-key").orElseThrow());
    }

    @Test
    void failsFastWithoutRetryWhenGeminiRequestTimesOut() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timed out"));

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fallback.respond(
                        "Responde en español.",
                        List.of(new MessagingAiClient.Turn("user", "Quiero reservar")),
                        Set.of(),
                        (name, args) -> "{}"));

        assertTrue(error.getMessage().contains("timed out"));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void defaultsEachGeminiMessagingRequestToTwelveSeconds() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Hola"}]}}]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        fallback.respond(
                "Responde en español.",
                List.of(new MessagingAiClient.Turn("user", "Hola")),
                Set.of(),
                (name, args) -> "{}");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(Duration.ofSeconds(12), requestCaptor.getValue().timeout().orElseThrow());
    }

    @Test
    void completesBookingConversationAfterFourToolCalls() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> services = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"search_services","id":"call_1","args":{}}}]}}]}
                """);
        HttpResponse<String> availability = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"check_booking_availability","id":"call_2","args":{}}}]}}]}
                """);
        HttpResponse<String> proposal = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"create_booking","id":"call_3","args":{"serviceId":"service-1","startAt":"2026-09-24T10:00:00-03:00"}}}]}}]}
                """);
        HttpResponse<String> confirmation = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"create_booking","id":"call_4","args":{"operationId":"operation-1","confirmationToken":"token-1"}}}]}}]}
                """);
        HttpResponse<String> finalReply = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Reserva confirmada"}]}}]}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(services, availability, proposal, confirmation, finalReply);

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        List<String> calledTools = new ArrayList<>();
        String answer = fallback.respond(
                "Gestiona la reserva y confirma el resultado final al cliente.",
                List.of(new MessagingAiClient.Turn("user", "Quiero reservar una hora")),
                Set.of("search_services", "check_booking_availability", "create_booking"),
                (name, args) -> {
                    calledTools.add(name);
                    return "{\"success\":true}";
                });

        assertEquals("Reserva confirmada", answer);
        assertEquals(
                List.of("search_services", "check_booking_availability", "create_booking", "create_booking"),
                calledTools);
        verify(http, times(5)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void allowsFiveToolCallsBeforeReturningTheCustomerReply() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> services = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"list_services","id":"call_1","args":{}}}]}}]}
                """);
        HttpResponse<String> slots = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"list_available_slots","id":"call_2","args":{"serviceId":"service-1","date":"2026-09-23"}}}]}}]}
                """);
        HttpResponse<String> caller = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"find_caller","id":"call_3","args":{}}}]}}]}
                """);
        HttpResponse<String> register = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"register_caller","id":"call_4","args":{"name":"bdko gonzaliz"}}}]}}]}
                """);
        HttpResponse<String> proposal = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"create_booking","id":"call_5","args":{"serviceId":"service-1","startAt":"2026-09-23T16:00:00-04:00"}}}]}}]}
                """);
        HttpResponse<String> finalReply = response("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Perfecto. ¿Confirmas la reserva para las 16:00?"}]}}]}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(services, slots, caller, register, proposal, finalReply);

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        List<String> calledTools = new ArrayList<>();
        String answer = fallback.respond(
                "Gestiona la reserva por WhatsApp.",
                List.of(new MessagingAiClient.Turn("user", "A las 16:00 a nombre de bdko gonzaliz")),
                Set.of("list_services", "list_available_slots", "find_caller", "register_caller", "create_booking"),
                (name, args) -> {
                    calledTools.add(name);
                    return "{\"success\":true}";
                });

        assertEquals("Perfecto. ¿Confirmas la reserva para las 16:00?", answer);
        assertEquals(
                List.of("list_services", "list_available_slots", "find_caller", "register_caller", "create_booking"),
                calledTools);
        verify(http, times(6)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void preservesGeminiFunctionCallIdInFunctionResponse() {
        var part = GeminiMessagingAiFallback.functionResponsePart(
                "lookup_booking",
                "call_123",
                "{\"success\":true}");

        var functionResponse = part.getJSONObject("functionResponse");
        assertEquals("lookup_booking", functionResponse.getString("name"));
        assertEquals("call_123", functionResponse.getString("id"));
        assertTrue(functionResponse.getJSONObject("response").getBoolean("success"));
    }

    @Test
    void recognizesNestedOpenAiRateLimit() {
        RuntimeException wrapped = new RuntimeException(new RateLimitException());
        assertTrue(OpenAiMessagingAiClient.isRateLimit(wrapped));
        assertFalse(OpenAiMessagingAiClient.isRateLimit(new IllegalStateException("other")));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static final class RateLimitException extends RuntimeException {
    }
}
