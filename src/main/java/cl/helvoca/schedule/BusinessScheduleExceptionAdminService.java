package cl.helvoca.schedule;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessScheduleExceptionAdminService {
    private final BusinessScheduleExceptionRepository exceptions;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public BusinessScheduleExceptionAdminService(BusinessScheduleExceptionRepository exceptions,
                                                 TenantProvider tenantProvider,
                                                 AuditService auditService) {
        this.exceptions = exceptions;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return exceptions.findAllByBusinessIdOrderByExceptionDateAsc(businessId)
                .stream()
                .map(View::from)
                .toList();
    }

    @Transactional
    public View upsert(LocalDate date, Update update) {
        if (date == null) throw new IllegalArgumentException("Exception date is required");
        if (update == null) throw new IllegalArgumentException("Schedule exception payload is required");
        validate(update);

        UUID businessId = tenantProvider.requireBusinessId();
        BusinessScheduleException value = exceptions.findByBusinessIdAndExceptionDate(businessId, date).orElse(null);
        Map<String, Object> before = value == null ? null : snapshot(value);
        boolean created = value == null;

        if (value == null) {
            value = new BusinessScheduleException();
            value.setBusinessId(businessId);
            value.setExceptionDate(date);
        }

        value.setClosed(update.closed());
        value.setOpenTime(update.closed() ? null : update.openTime());
        value.setCloseTime(update.closed() ? null : update.closeTime());
        value.setReason(normalizeReason(update.reason()));

        BusinessScheduleException saved = exceptions.saveAndFlush(value);
        auditService.humanSuccess(
                businessId,
                created ? "BUSINESS_SCHEDULE_EXCEPTION_CREATE" : "BUSINESS_SCHEDULE_EXCEPTION_UPDATE",
                "BUSINESS_SCHEDULE_EXCEPTION",
                saved.getId(),
                before,
                snapshot(saved));

        return View.from(saved);
    }

    @Transactional
    public void delete(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Exception date is required");

        UUID businessId = tenantProvider.requireBusinessId();
        BusinessScheduleException value = exceptions.findByBusinessIdAndExceptionDate(businessId, date)
                .orElseThrow(() -> new NotFoundException("Schedule exception not found"));
        Map<String, Object> before = snapshot(value);

        exceptions.delete(value);
        auditService.humanSuccess(
                businessId,
                "BUSINESS_SCHEDULE_EXCEPTION_DELETE",
                "BUSINESS_SCHEDULE_EXCEPTION",
                value.getId(),
                before,
                null);
    }

    static void validate(Update update) {
        if (update.closed()) {
            if (update.openTime() != null || update.closeTime() != null) {
                throw new IllegalArgumentException("Closed dates cannot include opening hours");
            }
            return;
        }

        if (update.openTime() == null || update.closeTime() == null) {
            throw new IllegalArgumentException("Special opening and closing times are required");
        }
        if (!update.openTime().isBefore(update.closeTime())) {
            throw new IllegalArgumentException("Opening time must be before closing time");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        return reason.trim();
    }

    private static Map<String, Object> snapshot(BusinessScheduleException value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("exceptionDate", value.getExceptionDate());
        snapshot.put("closed", value.isClosed());
        snapshot.put("openTime", value.getOpenTime());
        snapshot.put("closeTime", value.getCloseTime());
        return snapshot;
    }

    public record Update(
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime,
            @Size(max = 200) String reason
    ) {}

    public record View(
            UUID id,
            LocalDate exceptionDate,
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime,
            String reason
    ) {
        static View from(BusinessScheduleException value) {
            return new View(
                    value.getId(),
                    value.getExceptionDate(),
                    value.isClosed(),
                    value.getOpenTime(),
                    value.getCloseTime(),
                    value.getReason());
        }
    }
}
