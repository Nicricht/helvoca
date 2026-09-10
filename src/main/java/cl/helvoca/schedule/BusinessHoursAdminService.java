package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessHoursAdminService {
    private final BusinessHourRepository hours;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public BusinessHoursAdminService(BusinessHourRepository hours,
                                     TenantProvider tenantProvider,
                                     AuditService auditService) {
        this.hours = hours;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BusinessHourResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)
                .stream().map(BusinessHourResponse::from).toList();
    }

    @Transactional
    public List<BusinessHourResponse> replace(BusinessHoursRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(request.hours());
        hours.deleteAllByBusinessId(businessId);

        List<BusinessHour> saved = new ArrayList<>();
        for (BusinessHoursRequest.Interval interval : request.hours()) {
            BusinessHour hour = new BusinessHour();
            hour.setBusinessId(businessId);
            hour.setDayOfWeek(interval.dayOfWeek());
            hour.setOpenTime(interval.openTime());
            hour.setCloseTime(interval.closeTime());
            saved.add(hours.save(hour));
        }
        auditService.success(businessId, "BUSINESS_HOURS_REPLACE", "BUSINESS", businessId);
        return saved.stream()
                .sorted(Comparator.comparingInt(BusinessHour::getDayOfWeek).thenComparing(BusinessHour::getOpenTime))
                .map(BusinessHourResponse::from)
                .toList();
    }

    static void validate(List<BusinessHoursRequest.Interval> intervals) {
        for (BusinessHoursRequest.Interval interval : intervals) {
            if (!interval.openTime().isBefore(interval.closeTime())) {
                throw new IllegalArgumentException("Opening time must be before closing time");
            }
        }
        for (int day = 1; day <= 7; day++) {
            final int currentDay = day;
            List<BusinessHoursRequest.Interval> sameDay = intervals.stream()
                    .filter(i -> i.dayOfWeek() == currentDay)
                    .sorted(Comparator.comparing(BusinessHoursRequest.Interval::openTime))
                    .toList();
            for (int i = 1; i < sameDay.size(); i++) {
                if (sameDay.get(i).openTime().isBefore(sameDay.get(i - 1).closeTime())) {
                    throw new IllegalArgumentException("Business hour intervals cannot overlap");
                }
            }
        }
    }
}
