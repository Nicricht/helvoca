package cl.helvoca.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {
    List<KnowledgeItem> findAllByBusinessIdOrderByTitleAsc(UUID businessId);
    List<KnowledgeItem> findAllByBusinessIdAndActiveTrueOrderByTitleAsc(UUID businessId);
    Optional<KnowledgeItem> findByIdAndBusinessId(UUID id, UUID businessId);
}
