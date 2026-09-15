package cl.helvoca.omnichannel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OmnichannelSessionRepository extends JpaRepository<OmnichannelSession, UUID> {
    Optional<OmnichannelSession> findFirstByBusinessIdAndCustomerIdAndStatusOrderByLastActivityAtDesc(
            UUID businessId,
            UUID customerId,
            OmnichannelSession.Status status);

    Optional<OmnichannelSession> findByIdAndBusinessId(UUID id, UUID businessId);
}
