package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.telephony.twilio.trial.TrialConversationStateService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceConversationService;
import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import cl.helvoca.telephony.twilio.trial.TrialVoiceReply;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioVoiceControllerTest {

    @Test
    void readyLiveSipRoutesInboundWithoutOpeningMediaStream() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);
        when(liveSip.isReady()).thenReturn(true);
        when(liveSip.twiml("+14355652512", "+56911111111"))
                .thenReturn("<Response><Dial><Sip>sip:proj_test@sip.api.openai.com</Sip></Dial></Response>");

        var response = controller.incoming("CA-LIVE", "+56911111111", "+14355652512");

        assertEquals(200, response.getStatusCode().value());
        verify(liveSip).twiml("+14355652512", "+56911111111");
        verify(calls, never()).startInboundCall(anyString(), anyString(), anyString());
    }

    @Test
    void terminalStatusGeneratesSummaryForPersistedCall() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        when(calls.updateStatus("CA-TERMINAL", "completed", 42)).thenReturn(callId);

        var response = controller.status("CA-TERMINAL", "completed", 42);

        assertEquals(204, response.getStatusCode().value());
        verify(calls).updateStatus("CA-TERMINAL", "completed", 42);
        verify(summaries).generate(callId);
    }

    @Test
    void nonTerminalStatusDoesNotGenerateSummary() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        when(calls.updateStatus("CA-ACTIVE", "in-progress", null)).thenReturn(callId);

        var response = controller.status("CA-ACTIVE", "in-progress", null);

        assertEquals(204, response.getStatusCode().value());
        verify(calls).updateStatus("CA-ACTIVE", "in-progress", null);
        verifyNoInteractions(summaries);
    }

    @Test
    void trialGatherBridgesToTrustedHumanTargetWhenTransferWasRequested() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+17372508034", "trial:CA-TRANSFER");
        when(trial.isEnabled()).thenReturn(true);
        when(calls.getTrialContext("CA-TRANSFER")).thenReturn(context);
        when(conversation.reply(context, "quiero hablar con una persona"))
                .thenReturn(new TrialVoiceReply("Listo.", false));
        when(state.consumeHumanTransferTarget(callId)).thenReturn("+56922222222");
        when(twiml.trialTransfer("Claro, te comunico con una persona del negocio.", "+56922222222"))
                .thenReturn("<Response><Dial/></Response>");

        var response = controller.trialGather("CA-TRANSFER", "quiero hablar con una persona");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<Response><Dial/></Response>", response.getBody());
        verify(twiml).trialTransfer("Claro, te comunico con una persona del negocio.", "+56922222222");
        verify(twiml, never()).trialGather(anyString());
    }

    @Test
    void failedHumanDialReturnsCallerToHelvoca() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "+56911111111", "+17372508034", "trial:CA-DIAL");
        when(trial.isEnabled()).thenReturn(true);
        when(calls.getTrialContext("CA-DIAL")).thenReturn(context);
        when(twiml.trialGather("No pude comunicarte con una persona en este momento. Puedo seguir ayudándote por aquí."))
                .thenReturn("<Response><Gather/></Response>");

        var response = controller.trialTransferResult("CA-DIAL", "no-answer");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<Response><Gather/></Response>", response.getBody());
        verify(calls, never()).markTrialEnded(anyString());
        verifyNoInteractions(summaries);
    }

    @Test
    void completedHumanDialEndsAiSessionAndGeneratesSummary() {
        TwilioCallService calls = mock(TwilioCallService.class);
        TwimlFactory twiml = mock(TwimlFactory.class);
        OpenAiLiveSipService liveSip = mock(OpenAiLiveSipService.class);
        TrialVoiceProperties trial = mock(TrialVoiceProperties.class);
        TrialVoiceConversationService conversation = mock(TrialVoiceConversationService.class);
        TrialConversationStateService state = mock(TrialConversationStateService.class);
        CallSummaryService summaries = mock(CallSummaryService.class);
        TwilioVoiceController controller = new TwilioVoiceController(calls, twiml, liveSip, trial, conversation, state, summaries);

        UUID callId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, UUID.randomUUID(), null, "+56911111111", "+17372508034", "trial:CA-DIAL");
        when(trial.isEnabled()).thenReturn(true);
        when(calls.getTrialContext("CA-DIAL")).thenReturn(context);
        when(twiml.trialSayAndHangup("Gracias por comunicarte con nosotros. Hasta luego."))
                .thenReturn("<Response><Hangup/></Response>");

        var response = controller.trialTransferResult("CA-DIAL", "completed");

        assertEquals(200, response.getStatusCode().value());
        verify(calls).markTrialEnded("CA-DIAL");
        verify(state).clear(callId);
        verify(summaries).generate(callId);
    }
}
