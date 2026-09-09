package cl.helvoca.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessScheduleExceptionRepository extends JpaRepository<BusinessScheduleException, UUID> {
    List<BusinessScheduleException> findAllByBusinessIdOrderByExceptionDateAsc(UUID businessId);
    Optional<BusinessScheduleException> findByBusinessIdAndExceptionDate(UUID businessId, LocalDate exceptionDate);
    Optional<BusinessScheduleException> findByIdAndBusinessId(UUID id, UUID businessId);
}
