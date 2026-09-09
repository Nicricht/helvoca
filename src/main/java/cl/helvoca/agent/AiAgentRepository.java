package cl.helvoca.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiAgentRepository extends JpaRepository<AiAgent, UUID> {
    Optional<AiAgent> findByBusinessId(UUID businessId);
}
