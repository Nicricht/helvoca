package cl.helvoca.learning;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unanswered_question")
public class UnansweredQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "call_id")
    private UUID callId;

    @Column(name = "question_key", nullable = false, length = 64)
    private String questionKey;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(columnDefinition = "text")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnansweredQuestionStatus status = UnansweredQuestionStatus.OPEN;

    @Column(nullable = false)
    private int occurrences = 1;

    @Column(name = "first_asked_at", nullable = false)
    private Instant firstAskedAt;

    @Column(name = "last_asked_at", nullable = false)
    private Instant lastAskedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (firstAskedAt == null) firstAskedAt = now;
        if (lastAskedAt == null) lastAskedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }
    public String getQuestionKey() { return questionKey; }
    public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public UnansweredQuestionStatus getStatus() { return status; }
    public void setStatus(UnansweredQuestionStatus status) { this.status = status; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public Instant getFirstAskedAt() { return firstAskedAt; }
    public void setFirstAskedAt(Instant firstAskedAt) { this.firstAskedAt = firstAskedAt; }
    public Instant getLastAskedAt() { return lastAskedAt; }
    public void setLastAskedAt(Instant lastAskedAt) { this.lastAskedAt = lastAskedAt; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
