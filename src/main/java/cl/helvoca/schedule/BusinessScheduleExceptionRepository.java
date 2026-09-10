package cl.helvoca.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BusinessScheduleExceptionRepository extends JpaRepository<BusinessScheduleException, UUID> {
    Optional<BusinessScheduleException> findByBusinessIdAndExceptionDate(UUID businessId, LocalDate exceptionDate);
}
