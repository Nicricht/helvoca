package cl.helvoca.call;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {
    Optional<CallSession> findByProviderCallId(String providerCallId);
    Optional<CallSession> findByStreamSid(String streamSid);
    Optional<CallSession> findByIdAndBusinessId(UUID id, UUID businessId);
    Page<CallSession> findAllByBusinessId(UUID businessId, Pageable pageable);
}
