package cl.helvoca.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {
    Optional<DemoSession> findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
            UUID runtimeBusinessId,
            List<DemoSessionState> states);

    default Optional<DemoSession> findPreparedForRuntime(UUID runtimeBusinessId) {
        return findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
                runtimeBusinessId,
                List.of(DemoSessionState.PREPARING, DemoSessionState.READY, DemoSessionState.ACTIVE));
    }
}
