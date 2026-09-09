package cl.helvoca.call;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CallTranscriptRepository extends JpaRepository<CallTranscript, UUID> {
    List<CallTranscript> findAllByCallIdOrderBySequenceNumberAsc(UUID callId);
}
