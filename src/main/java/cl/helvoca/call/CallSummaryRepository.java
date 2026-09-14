package cl.helvoca.call;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface CallSummaryRepository extends JpaRepository<CallSummary, UUID> {
    Optional<CallSummary> findByCallId(UUID callId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO call_summary (id, call_id, summary, created_at)
            VALUES (:id, :callId, :summary, CURRENT_TIMESTAMP)
            ON CONFLICT (call_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("callId") UUID callId,
                       @Param("summary") String summary);
}
