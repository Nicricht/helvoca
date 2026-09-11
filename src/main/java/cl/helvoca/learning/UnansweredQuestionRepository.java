package cl.helvoca.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, UUID> {
    List<UnansweredQuestion> findAllByBusinessIdOrderByLastAskedAtDesc(UUID businessId);
    List<UnansweredQuestion> findTop10ByBusinessIdAndStatusOrderByLastAskedAtDesc(UUID businessId, UnansweredQuestionStatus status);
    Optional<UnansweredQuestion> findByBusinessIdAndQuestionKey(UUID businessId, String questionKey);
    Optional<UnansweredQuestion> findByIdAndBusinessId(UUID id, UUID businessId);
    long countByBusinessIdAndStatus(UUID businessId, UnansweredQuestionStatus status);
}
