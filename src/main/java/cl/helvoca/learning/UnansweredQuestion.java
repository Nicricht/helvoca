package cl.helvoca.learning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unanswered_question")
public class UnansweredQuestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "call_id") private UUID callId;
    @Column(name = "customer_id") private UUID customerId;
    @Column(nullable = false, columnDefinition = "text") private String question;
    @Column(name = "normalized_question", length = 500) private String normalizedQuestion;
    @Column(nullable = false) private int occurrences = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private QuestionStatus status = QuestionStatus.OPEN;
    @Column(columnDefinition = "text") private String answer;
    @Column(name = "knowledge_item_id") private UUID knowledgeItemId;
    @Column(name = "first_seen_at", nullable = false) private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "answered_at") private Instant answeredAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() { var now = Instant.now(); if (firstSeenAt == null) firstSeenAt = now; if (lastSeenAt == null) lastSeenAt = now; createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getCallId() { return callId; }
    public void setCallId(UUID v) { callId = v; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID v) { customerId = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { question = v; }
    public String getNormalizedQuestion() { return normalizedQuestion; }
    public void setNormalizedQuestion(String v) { normalizedQuestion = v; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int v) { occurrences = v; }
    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus v) { status = v; }
    public String getAnswer() { return answer; }
    public void setAnswer(String v) { answer = v; }
    public UUID getKnowledgeItemId() { return knowledgeItemId; }
    public void setKnowledgeItemId(UUID v) { knowledgeItemId = v; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant v) { firstSeenAt = v; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant v) { lastSeenAt = v; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant v) { answeredAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
