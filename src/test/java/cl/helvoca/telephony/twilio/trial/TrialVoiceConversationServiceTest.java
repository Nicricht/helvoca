package cl.helvoca.telephony.twilio.trial;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.call.CallTranscriptService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialVoiceConversationServiceTest {

    @Test
    void constructsWithStructuredConversationState() {
        TrialVoiceProperties trial = new TrialVoiceProperties();
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        CallTranscriptService transcriptWriter = mock(CallTranscriptService.class);
        CallTranscriptRepository transcriptRepository = mock(CallTranscriptRepository.class);
        TrialConversationStateService state = new TrialConversationStateService();

        TrialVoiceConversationService service = new TrialVoiceConversationService(
                trial,
                openAi,
                tools,
                transcriptWriter,
                transcriptRepository,
                state);

        assertNotNull(service);
    }

    @Test
    void processesTheConfiguredFinalUserTurnInsteadOfEndingEarly() {
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setMaxTurns(2);
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        CallTranscriptService transcriptWriter = mock(CallTranscriptService.class);
        CallTranscriptRepository transcriptRepository = mock(CallTranscriptRepository.class);
        when(transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(any()))
                .thenReturn(List.of(userTurn("uno"), userTurn("dos")));

        TrialVoiceConversationService service = new TrialVoiceConversationService(
                trial, openAi, tools, transcriptWriter, transcriptRepository,
                new TrialConversationStateService());

        TrialVoiceReply reply = service.reply(context(), "dos");

        assertFalse(reply.endCall());
        assertTrue(reply.text().contains("no puedo completar la consulta"));
    }

    @Test
    void endsOnlyAfterConfiguredUserTurnLimitIsExceeded() {
        TrialVoiceProperties trial = new TrialVoiceProperties();
        trial.setMaxTurns(2);
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        CallTranscriptService transcriptWriter = mock(CallTranscriptService.class);
        CallTranscriptRepository transcriptRepository = mock(CallTranscriptRepository.class);
        when(transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(any()))
                .thenReturn(List.of(userTurn("uno"), userTurn("dos"), userTurn("tres")));

        TrialVoiceConversationService service = new TrialVoiceConversationService(
                trial, openAi, tools, transcriptWriter, transcriptRepository,
                new TrialConversationStateService());

        TrialVoiceReply reply = service.reply(context(), "tres");

        assertTrue(reply.endCall());
        assertTrue(reply.text().contains("final de esta demostración"));
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+56222222222", "MZstream");
    }

    private static CallTranscript userTurn(String content) {
        CallTranscript transcript = new CallTranscript();
        transcript.setSpeaker("USER");
        transcript.setContent(content);
        return transcript;
    }
}
