package cl.helvoca.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DemoProfileRepository extends JpaRepository<DemoProfile, UUID> {
    List<DemoProfile> findAllByOrderByUpdatedAtDesc();
}
