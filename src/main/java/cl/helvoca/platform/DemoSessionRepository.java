package cl.helvoca.platform;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {
    Optional<DemoSession> findFirstByRuntimeBusinessIdOrderByCreatedAtDesc(UUID runtimeBusinessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DemoSession d where d.id = :id")
    Optional<DemoSession> findByIdForUpdate(@Param("id") UUID id);

    Optional<DemoSession> findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
            UUID runtimeBusinessId,
            List<DemoSessionState> states);

    default Optional<DemoSession> findPreparedForRuntime(UUID runtimeBusinessId) {
        return findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
                runtimeBusinessId,
                List.of(DemoSessionState.PREPARING, DemoSessionState.READY, DemoSessionState.ACTIVE));
    }
}
