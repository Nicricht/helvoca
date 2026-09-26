package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PilotLaunchControlRepository extends JpaRepository<PilotLaunchControl, UUID> {
}
