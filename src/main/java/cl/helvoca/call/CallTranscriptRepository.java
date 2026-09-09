package cl.helvoca.call;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CallTranscriptRepository extends JpaRepository<CallTranscript, UUID> {
    List<CallTranscript> findAllByCallIdOrderBySequenceNumberAsc(UUID callId);

    @Query("select coalesce(max(t.sequenceNumber), 0) from CallTranscript t where t.callId = :callId")
    int maxSequenceNumber(@Param("callId") UUID callId);
}
