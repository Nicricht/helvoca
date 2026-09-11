package cl.helvoca.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, UUID> {
    List<UnansweredQuestion> findAllByBusinessIdOrderByLastSeenAtDesc(UUID businessId);
    List<UnansweredQuestion> findAllByBusinessIdAndStatusOrderByLastSeenAtDesc(UUID businessId, QuestionStatus status);
    Optional<UnansweredQuestion> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<UnansweredQuestion> findFirstByBusinessIdAndNormalizedQuestionAndStatus(UUID businessId, String normalizedQuestion, QuestionStatus status);
}
