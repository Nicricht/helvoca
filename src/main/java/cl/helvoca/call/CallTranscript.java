package cl.helvoca.call;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_transcript")
public class CallTranscript {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(nullable = false, length = 30)
    private String speaker;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public Instant getCreatedAt() { return createdAt; }
}
