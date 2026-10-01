package cl.helvoca.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {
    Optional<DemoSession> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select s from DemoSession s
            where s.state in (
                cl.helvoca.platform.DemoSessionState.PREPARING,
                cl.helvoca.platform.DemoSessionState.READY,
                cl.helvoca.platform.DemoSessionState.ACTIVE
            )
            order by s.createdAt desc
            """)
    Optional<DemoSession> findFirstOpenSession();
}
