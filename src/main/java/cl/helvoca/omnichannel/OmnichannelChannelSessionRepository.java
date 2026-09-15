package cl.helvoca.omnichannel;

import cl.helvoca.operations.BusinessOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OmnichannelChannelSessionRepository extends JpaRepository<OmnichannelChannelSession, UUID> {
    Optional<OmnichannelChannelSession> findByBusinessIdAndChannelAndSourceReferenceId(
            UUID businessId,
            BusinessOrder.Source channel,
            UUID sourceReferenceId);
}
