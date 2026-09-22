package cl.helvoca.operations;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BookingReadinessStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BookingReadinessStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final ServiceItemRepository services;
    private final BusinessHourRepository hours;
    private final AiAgentService aiAgents;

    public BookingReadinessStartupRunner(
            @Value("${HELVOCA_BOOKING_READINESS_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_BOOKING_READINESS_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            ServiceItemRepository services,
            BusinessHourRepository hours,
            AiAgentService aiAgents) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.services = services;
        this.hours = hours;
        this.aiAgents = aiAgents;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("BOOKING_READINESS skipped=invalid_business_id");
            return;
        }

        databaseContext.runAsTenant(tenantId, () -> log(evaluate(tenantId)));
    }

    Readiness evaluate(UUID businessId) {
        long activeServices = services.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(ServiceItem::isActive)
                .count();
        long businessHours = hours.countByBusinessId(businessId);
        boolean createBookingToolAllowed = aiAgents.toolAllowed(businessId, "create_booking");
        boolean ready = activeServices > 0 && businessHours > 0 && createBookingToolAllowed;
        return new Readiness(ready, activeServices, businessHours, createBookingToolAllowed);
    }

    private void log(Readiness readiness) {
        log.info(
                "BOOKING_READINESS ready={} activeServices={} businessHours={} createBookingToolAllowed={}",
                readiness.ready(),
                readiness.activeServices(),
                readiness.businessHours(),
                readiness.createBookingToolAllowed());
    }

    record Readiness(
            boolean ready,
            long activeServices,
            long businessHours,
            boolean createBookingToolAllowed
    ) {}
}
