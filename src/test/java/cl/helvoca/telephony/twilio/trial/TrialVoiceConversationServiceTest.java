package cl.helvoca.telephony.twilio.trial;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.call.CallTranscriptService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

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
}
