package cl.helvoca.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {
    List<TeamInvitation> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<TeamInvitation> findByBusinessIdAndTokenHash(UUID businessId, String tokenHash);
    List<TeamInvitation> findAllByBusinessIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID businessId, String email);
}
