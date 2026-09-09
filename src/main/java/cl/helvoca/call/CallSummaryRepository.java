package cl.helvoca.call;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CallSummaryRepository extends JpaRepository<CallSummary, UUID> {
    Optional<CallSummary> findByCallId(UUID callId);
}
