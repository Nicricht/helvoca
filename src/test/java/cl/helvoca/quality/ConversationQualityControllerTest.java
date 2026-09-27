package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class ConversationQualityControllerTest {

    @Test
    void delegatesAnalysisAndReplayCapture() {
        ConversationQualityService quality = mock(ConversationQualityService.class);
        ConversationReplayCaptureService replay = mock(ConversationReplayCaptureService.class);
        ConversationQualityController controller = new ConversationQualityController(quality, replay);
        UUID callId = UUID.randomUUID();

        ConversationQualityService.CallQualityReport report =
                new ConversationQualityService.CallQualityReport(
                        callId, "COMPLETED", "DONE", 0, 0, 0, 0, 0,
                        new ConversationQualityEngine.Report(true, 0, 0, List.of()));
        ConversationReplayFixture fixture = new ConversationReplayFixture(
                1, "call", "fingerprint", List.of(), List.of(),
                new ConversationReplayFixture.Expected(true, List.of()));

        when(quality.analyze(callId)).thenReturn(report);
        when(replay.capture(callId)).thenReturn(fixture);

        assertSame(report, controller.analyze(callId));
        assertSame(fixture, controller.replay(callId));
        verify(quality).analyze(callId);
        verify(replay).capture(callId);
    }
}
