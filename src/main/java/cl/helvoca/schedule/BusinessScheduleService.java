package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class BusinessScheduleService {
    private final BusinessHourRepository hours;
    private final BusinessScheduleExceptionRepository exceptions;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public BusinessScheduleService(BusinessHourRepository hours,
                                   BusinessScheduleExceptionRepository exceptions,
                                   TenantProvider tenantProvider,
                                   AuditService auditService) {
        this.hours = hours;
        this.exceptions = exceptions;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BusinessHour> listHours() {
        return hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(tenantProvider.requireBusinessId());
    }

    @Transactional
    public List<BusinessHour> replaceHours(List<HourInput> requested) {
        UUID businessId = tenantProvider.requireBusinessId();
        List<HourInput> normalized = requested == null ? List.of() : List.copyOf(requested);
        validatePeriods(normalized);
        hours.deleteAllByBusinessId(businessId);
        List<BusinessHour> saved = new ArrayList<>();
        for (HourInput input : normalized) {
            BusinessHour hour = new BusinessHour();
            hour.setBusinessId(businessId);
            hour.setDayOfWeek(input.dayOfWeek());
            hour.setOpenTime(input.openTime());
            hour.setCloseTime(input.closeTime());
            saved.add(hours.save(hour));
        }
        hours.flush();
        auditService.success(businessId, "BUSINESS_HOURS_REPLACE", "BUSINESS", businessId);
        return saved.stream()
                .sorted(Comparator.comparingInt(BusinessHour::getDayOfWeek).thenComparing(BusinessHour::getOpenTime))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessScheduleException> listExceptions() {
        return exceptions.findAllByBusinessIdOrderByExceptionDateAsc(tenantProvider.requireBusinessId());
    }

    @Transactional
    public BusinessScheduleException upsertException(ExceptionInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        validateException(input);
        BusinessScheduleException exception = exceptions.findByBusinessIdAndExceptionDate(businessId, input.date())
                .orElseGet(BusinessScheduleException::new);
        if (exception.getId() == null) {
            exception.setBusinessId(businessId);
            exception.setExceptionDate(input.date());
        }
        exception.setClosed(input.closed());
        exception.setOpenTime(input.closed() ? null : input.openTime());
        exception.setCloseTime(input.closed() ? null : input.closeTime());
        exception.setReason(input.reason() == null || input.reason().isBlank() ? null : input.reason().trim());
        BusinessScheduleException saved = exceptions.saveAndFlush(exception);
        auditService.success(businessId, "BUSINESS_SCHEDULE_EXCEPTION_UPSERT", "BUSINESS_SCHEDULE_EXCEPTION", saved.getId());
        return saved;
    }

    @Transactional
    public void deleteException(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessScheduleException exception = exceptions.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Schedule exception not found"));
        exceptions.delete(exception);
        auditService.success(businessId, "BUSINESS_SCHEDULE_EXCEPTION_DELETE", "BUSINESS_SCHEDULE_EXCEPTION", id);
    }

    private static void validatePeriods(List<HourInput> periods) {
        Map<Integer, List<HourInput>> byDay = new HashMap<>();
        for (HourInput input : periods) {
            if (input == null) throw new IllegalArgumentException("Schedule period cannot be null");
            if (input.dayOfWeek() < 1 || input.dayOfWeek() > 7) {
                throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
            }
            requireValidRange(input.openTime(), input.closeTime());
            byDay.computeIfAbsent(input.dayOfWeek(), ignored -> new ArrayList<>()).add(input);
        }
        for (List<HourInput> day : byDay.values()) {
            day.sort(Comparator.comparing(HourInput::openTime));
            for (int i = 1; i < day.size(); i++) {
                if (day.get(i).openTime().isBefore(day.get(i - 1).closeTime())) {
                    throw new IllegalArgumentException("Business hour periods cannot overlap");
                }
            }
        }
    }

    private static void validateException(ExceptionInput input) {
        if (input == null || input.date() == null) throw new IllegalArgumentException("date is required");
        if (input.closed()) {
            if (input.openTime() != null || input.closeTime() != null) {
                throw new IllegalArgumentException("Closed exceptions cannot include opening hours");
            }
            return;
        }
        requireValidRange(input.openTime(), input.closeTime());
    }

    private static void requireValidRange(LocalTime openTime, LocalTime closeTime) {
        if (openTime == null || closeTime == null) throw new IllegalArgumentException("openTime and closeTime are required");
        if (!openTime.isBefore(closeTime)) throw new IllegalArgumentException("openTime must be before closeTime");
    }

    public record HourInput(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {}
    public record ExceptionInput(LocalDate date, boolean closed, LocalTime openTime, LocalTime closeTime, String reason) {}
}
