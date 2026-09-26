package cl.helvoca.onboarding;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_activation_confirmation")
public class PilotActivationConfirmation {
    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "prices_confirmed", nullable = false)
    private boolean pricesConfirmed;
    @Column(name = "faq_reviewed", nullable = false)
    private boolean faqReviewed;
    @Column(name = "policies_approved", nullable = false)
    private boolean policiesApproved;
    @Column(name = "agent_instructions_approved", nullable = false)
    private boolean agentInstructionsApproved;
    @Column(name = "pilot_scope_approved", nullable = false)
    private boolean pilotScopeApproved;
    @Column(name = "conversation_test_completed", nullable = false)
    private boolean conversationTestCompleted;
    @Column(name = "mutation_tests_completed", nullable = false)
    private boolean mutationTestsCompleted;
    @Column(name = "human_handoff_tested", nullable = false)
    private boolean humanHandoffTested;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { Instant now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate() { updatedAt=Instant.now(); }

    public UUID getBusinessId(){return businessId;} public void setBusinessId(UUID v){businessId=v;}
    public boolean isPricesConfirmed(){return pricesConfirmed;} public void setPricesConfirmed(boolean v){pricesConfirmed=v;}
    public boolean isFaqReviewed(){return faqReviewed;} public void setFaqReviewed(boolean v){faqReviewed=v;}
    public boolean isPoliciesApproved(){return policiesApproved;} public void setPoliciesApproved(boolean v){policiesApproved=v;}
    public boolean isAgentInstructionsApproved(){return agentInstructionsApproved;} public void setAgentInstructionsApproved(boolean v){agentInstructionsApproved=v;}
    public boolean isPilotScopeApproved(){return pilotScopeApproved;} public void setPilotScopeApproved(boolean v){pilotScopeApproved=v;}
    public boolean isConversationTestCompleted(){return conversationTestCompleted;} public void setConversationTestCompleted(boolean v){conversationTestCompleted=v;}
    public boolean isMutationTestsCompleted(){return mutationTestsCompleted;} public void setMutationTestsCompleted(boolean v){mutationTestsCompleted=v;}
    public boolean isHumanHandoffTested(){return humanHandoffTested;} public void setHumanHandoffTested(boolean v){humanHandoffTested=v;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
