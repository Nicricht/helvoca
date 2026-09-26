package cl.helvoca.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PilotActivationConfirmationRepository extends JpaRepository<PilotActivationConfirmation, UUID> {}
