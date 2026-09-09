package cl.helvoca.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, UUID> {
    List<BusinessHour> findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(UUID businessId);
    List<BusinessHour> findAllByBusinessIdAndDayOfWeekOrderByOpenTimeAsc(UUID businessId, int dayOfWeek);
    long countByBusinessId(UUID businessId);
    void deleteAllByBusinessId(UUID businessId);
}
