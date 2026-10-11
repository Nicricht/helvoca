package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.voice.VoiceAiProvider;
import cl.helvoca.voice.VoiceAiProviderRegistry;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TwilioMediaStreamHandlerTest {

    @Test
    void signedBakeOffVoiceIsPropagatedAndTamperingIsRejected() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCallBindingGuard bindingGuard = mock(TwilioCallBindingGuard.class);
        when(bindingGuard.allowsMedia(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        WebSocketSession socket = mock(WebSocketSession.class);

        String streamSid = "MZ-bakeoff";
        String callSid = "CA-bakeoff";
        String accountSid = "AC-bakeoff";
        String business = "+14355652512";
        String caller = "+56966939611";
        long issuedAt = Instant.now().getEpochSecond();
        String route = signer.sign(business, caller, callSid, "gemini", "Sadachbia", issuedAt);
        UUID callId = UUID.randomUUID();
        RealtimeCallContext baseContext = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, caller, business, streamSid);

        when(socket.getId()).thenReturn("socket-bakeoff");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", callSid, caller, business)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return callId;
        });
        when(lifecycle.markStreamStarted(callId, callSid, streamSid, "gemini")).thenReturn(baseContext);
        when(provider.createSession(any(RealtimeCallContext.class), any(VoiceTransportSession.class))).thenReturn(ai);

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control, databaseContext, bindingGuard);
        handler.afterConnectionEstablished(socket);

        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("accountSid", accountSid)
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("voiceOverride", "Sadachbia")
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", route)));
        handler.handleTextMessage(socket, new TextMessage(start.toString()));

        ArgumentCaptor<RealtimeCallContext> contextCaptor = ArgumentCaptor.forClass(RealtimeCallContext.class);
        verify(provider).createSession(contextCaptor.capture(), any(VoiceTransportSession.class));
        assertEquals("Sadachbia", contextCaptor.getValue().voiceOverride());
        assertTrue(contextCaptor.getValue().voiceBakeOff());

        WebSocketSession tamperedSocket = mock(WebSocketSession.class);
        when(tamperedSocket.getId()).thenReturn("socket-tampered");
        handler.afterConnectionEstablished(tamperedSocket);
        JSONObject tampered = new JSONObject(start.toString());
        tampered.getJSONObject("start").getJSONObject("customParameters")
                .put("voiceOverride", "Leda");
        handler.handleTextMessage(tamperedSocket, new TextMessage(tampered.toString()));

        verify(tamperedSocket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
    }


    @Test
    void signedMediaStartFailureRestoresDeniedDatabaseScope() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCallBindingGuard bindingGuard = mock(TwilioCallBindingGuard.class);
        when(bindingGuard.allowsMedia(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        String callSid = "CA-db-scope";
        String streamSid = "MZ-db-scope";
        String business = "+14355652512";
        String caller = "+56911111111";
        long issuedAt = Instant.now().getEpochSecond();
        String route = signer.sign(business, caller, callSid, "gemini", issuedAt);

        when(socket.getId()).thenReturn("socket-db-failure");
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", callSid, caller, business))
                .thenAnswer(invocation -> {
                    assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
                    throw new IllegalStateException("database unavailable");
                });

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control, databaseContext, bindingGuard);
        handler.afterConnectionEstablished(socket);
        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("accountSid", "AC-db-scope")
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", route)));
        assertThrows(IllegalStateException.class,
                () -> handler.handleTextMessage(socket, new TextMessage(start.toString())));
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
        verify(provider, never()).createSession(any(), any());
    }

    @Test
    void signedMediaAiStartupFailureUpdatesStatusUnderSystemAndRestoresScope() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCallBindingGuard bindingGuard = mock(TwilioCallBindingGuard.class);
        when(bindingGuard.allowsMedia(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        String callSid = "CA-ai-start-failure";
        String streamSid = "MZ-ai-start-failure";
        String business = "+14355652512";
        String caller = "+56911111111";
        long issuedAt = Instant.now().getEpochSecond();
        String route = signer.sign(business, caller, callSid, "gemini", issuedAt);
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, caller, business, streamSid);
        when(socket.getId()).thenReturn("socket-ai-start-failure");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", callSid, caller, business)).thenReturn(callId);
        when(lifecycle.markStreamStarted(callId, callSid, streamSid, "gemini")).thenReturn(context);
        when(provider.createSession(eq(context), any(VoiceTransportSession.class))).thenReturn(ai);
        doThrow(new IllegalStateException("upstream unavailable")).when(ai).start();
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(lifecycle).updateStatus(eq(callId), eq("failed"), isNull());

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control, databaseContext, bindingGuard);
        handler.afterConnectionEstablished(socket);
        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("accountSid", "AC-ai-failure")
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", route)));
        assertThrows(IllegalStateException.class,
                () -> handler.handleTextMessage(socket, new TextMessage(start.toString())));
        verify(lifecycle).updateStatus(callId, "failed", null);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

    @Test
    void signedStartCreatesProviderSessionAndForwardsInboundAudio() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCallBindingGuard bindingGuard = mock(TwilioCallBindingGuard.class);
        when(bindingGuard.allowsMedia(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        VoiceAiProvider provider = mock(VoiceAiProvider.class);
        VoiceAiSession ai = mock(VoiceAiSession.class);
        WebSocketSession socket = mock(WebSocketSession.class);

        String streamSid = "MZ-test-stream";
        String callSid = "CA-test-call";
        String accountSid = "AC-test-account";
        String business = "+14355652512";
        String caller = "+56911111111";
        long issuedAt = Instant.now().getEpochSecond();
        String route = signer.sign(business, caller, callSid, "gemini", issuedAt);
        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, caller, business, streamSid);

        when(socket.getId()).thenReturn("socket-1");
        when(socket.isOpen()).thenReturn(true);
        when(providers.require("gemini")).thenReturn(provider);
        when(provider.id()).thenReturn("gemini");
        when(provider.configured()).thenReturn(true);
        when(lifecycle.startInboundCall("twilio", callSid, caller, business)).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return callId;
        });
        when(lifecycle.markStreamStarted(callId, callSid, streamSid, "gemini")).thenAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return context;
        });
        when(provider.createSession(eq(context), any(VoiceTransportSession.class))).thenReturn(ai);
        when(provider.certificationSession(context)).thenReturn(true);
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(lifecycle).markCertification(callId);
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(lifecycle).markAiModel(eq(callId), any());
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(lifecycle).markStreamStopped(streamSid);
        doAnswer(invocation -> {
            assertEquals(TenantDatabaseContext.Mode.SYSTEM, databaseContext.currentOrDenied().mode());
            return null;
        }).when(lifecycle).updateStatus(eq(callId), eq("failed"), isNull());

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control, databaseContext, bindingGuard);
        handler.afterConnectionEstablished(socket);

        JSONObject start = new JSONObject()
                .put("event", "start")
                .put("streamSid", streamSid)
                .put("start", new JSONObject()
                        .put("streamSid", streamSid)
                        .put("accountSid", accountSid)
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("issuedAt", String.valueOf(issuedAt))
                                .put("route", route)));
        handler.handleTextMessage(socket, new TextMessage(start.toString()));

        JSONObject media = new JSONObject()
                .put("event", "media")
                .put("streamSid", streamSid)
                .put("media", new JSONObject()
                        .put("track", "inbound")
                        .put("payload", "/////w=="));
        handler.handleTextMessage(socket, new TextMessage(media.toString()));

        verify(ai).start();
        verify(ai).acceptInboundAudio("/////w==");
        verify(lifecycle).markCertification(callId);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
        verify(lifecycle).startInboundCall("twilio", callSid, caller, business);
        verify(lifecycle).markStreamStarted(callId, callSid, streamSid, "gemini");

        ArgumentCaptor<VoiceTransportSession> transport = ArgumentCaptor.forClass(VoiceTransportSession.class);
        verify(provider).createSession(eq(context), transport.capture());
        assertNotNull(transport.getValue());
        assertEquals(streamSid, transport.getValue().id());

        when(control.hangup(accountSid, callSid)).thenReturn(true);
        assertTrue(transport.getValue().endAfterPlayback());

        ArgumentCaptor<TextMessage> outbound = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeastOnce()).sendMessage(outbound.capture());
        JSONObject markRequest = outbound.getAllValues().stream()
                .map(TextMessage::getPayload)
                .map(JSONObject::new)
                .filter(json -> "mark".equals(json.optString("event")))
                .findFirst()
                .orElseThrow();
        String markerName = markRequest.getJSONObject("mark").getString("name");

        JSONObject mark = new JSONObject()
                .put("event", "mark")
                .put("streamSid", streamSid)
                .put("mark", new JSONObject().put("name", markerName));
        handler.handleTextMessage(socket, new TextMessage(mark.toString()));

        verify(control).hangup(accountSid, callSid);
        handler.handleTransportError(socket, new IllegalStateException("stream disconnected"));
        verify(lifecycle, atLeastOnce()).updateStatus(callId, "failed", null);
        verify(lifecycle).markStreamStopped(streamSid);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }
    @Test
    void signedMediaRouteWithForeignTenantAccountIsRejectedBeforeAiAndDatabaseAccess() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        TwilioCallControl control = mock(TwilioCallControl.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        TwilioCallBindingGuard bindingGuard = mock(TwilioCallBindingGuard.class);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("foreign-tenant-socket");

        String callSid = "CA-foreign";
        String business = "+14355550101";
        String caller = "+56911111111";
        String account = "ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        long now = Instant.now().getEpochSecond();
        JSONObject message = new JSONObject()
                .put("event", "start")
                .put("streamSid", "MZ-foreign")
                .put("start", new JSONObject()
                        .put("accountSid", account)
                        .put("callSid", callSid)
                        .put("customParameters", new JSONObject()
                                .put("business", business)
                                .put("caller", caller)
                                .put("callSid", callSid)
                                .put("provider", "gemini")
                                .put("issuedAt", String.valueOf(now))
                                .put("route", signer.sign(business, caller, callSid, "gemini", now))));

        TwilioMediaStreamHandler handler = new TwilioMediaStreamHandler(
                signer, providers, health, lifecycle, control, databaseContext, bindingGuard);
        handler.afterConnectionEstablished(socket);
        handler.handleTextMessage(socket, new TextMessage(message.toString()));

        verify(bindingGuard).allowsMedia(callSid, business, caller, account);
        verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(lifecycle, providers, control);
        assertEquals(TenantDatabaseContext.Mode.DENIED, databaseContext.currentOrDenied().mode());
    }

}
