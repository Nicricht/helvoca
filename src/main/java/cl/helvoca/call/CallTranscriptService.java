package cl.helvoca.call;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CallTranscriptService {
    private final CallTranscriptRepository transcripts;

    public CallTranscriptService(CallTranscriptRepository transcripts) {
        this.transcripts = transcripts;
    }

    @Transactional
    public synchronized void append(UUID callId, String speaker, String content) {
        if (content == null || content.isBlank()) return;
        CallTranscript transcript = new CallTranscript();
        transcript.setCallId(callId);
        transcript.setSpeaker(speaker);
        transcript.setContent(content.trim());
        transcript.setSequenceNumber(transcripts.maxSequenceNumber(callId) + 1);
        transcripts.save(transcript);
    }
}
