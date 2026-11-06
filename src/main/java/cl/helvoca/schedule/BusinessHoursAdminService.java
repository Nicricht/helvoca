package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class BusinessHoursAdminService {
    private final BusinessHourRepository hours;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;
    private final BusinessRepository businesses;

    private static final Comparator<BusinessHoursRequest.Interval> INTERVAL_ORDER =
            Comparator.comparingInt(BusinessHoursRequest.Interval::dayOfWeek)
                    .thenComparing(BusinessHoursRequest.Interval::openTime)
                    .thenComparing(BusinessHoursRequest.Interval::closeTime);

    public BusinessHoursAdminService(BusinessHourRepository hours,
                                     TenantProvider tenantProvider,
                                     AuditService auditService,
                                     BusinessRepository businesses) {
        this.hours = hours;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
        this.businesses = businesses;
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
        lockBusiness(businessId);
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

    /**
     * Lock the stable tenant row before comparing a selected-day snapshot and
     * writing. All weekly replacement writers use the same lock.
     */
    @Transactional
    public List<BusinessHourResponse> mergeSelected(BusinessHoursSelectedRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(request.hours());
        validate(request.expectedSelectedDayHours());

        Set<Integer> days = request.hours().stream()
                .map(BusinessHoursRequest.Interval::dayOfWeek).collect(Collectors.toSet());
        if (days.isEmpty() || days.size() != request.hours().size()
                || request.expectedSelectedDayHours().stream().anyMatch(h -> !days.contains(h.dayOfWeek()))) {
            throw new IllegalArgumentException("Selecciona cada día una sola vez y limita la comparación a esos días.");
        }

        lockBusiness(businessId);
        List<BusinessHoursRequest.Interval> current = hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId)
                .stream().map(h -> new BusinessHoursRequest.Interval(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime()))
                .toList();
        List<BusinessHoursRequest.Interval> actualSelected = current.stream()
                .filter(h -> days.contains(h.dayOfWeek())).sorted(INTERVAL_ORDER).toList();
        List<BusinessHoursRequest.Interval> expectedSelected = request.expectedSelectedDayHours().stream()
                .sorted(INTERVAL_ORDER).toList();
        if (!actualSelected.equals(expectedSelected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Los horarios seleccionados cambiaron. Actualiza y vuelve a revisar antes de guardar.");
        }

        List<BusinessHoursRequest.Interval> combined = new ArrayList<>(current.stream()
                .filter(h -> !days.contains(h.dayOfWeek())).toList());
        combined.addAll(request.hours());
        validate(combined);
        return replace(new BusinessHoursRequest(combined));
    }

    private void lockBusiness(UUID businessId) {
        if (businesses.lockByIdForUpdate(businessId).isEmpty()) {
            throw new NotFoundException("Business not found");
        }
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
