package cl.helvoca.publicbooking;

import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class PublicBookingService {
    private final BusinessProfileRepository profiles;
    private final TenantDatabaseContext databaseContext;
    private final PublicBookingTenantService tenantService;

    public PublicBookingService(BusinessProfileRepository profiles,
                                TenantDatabaseContext databaseContext,
                                PublicBookingTenantService tenantService) {
        this.profiles = profiles;
        this.databaseContext = databaseContext;
        this.tenantService = tenantService;
    }

    public PublicBookingController.PublicBookingPageResponse page(UUID key) {
        UUID businessId = resolveEnabledBusiness(key);
        return databaseContext.callAsTenant(businessId, () -> tenantService.page(businessId));
    }

    public PublicBookingController.PublicAvailabilityResponse availability(UUID key,
                                                                          UUID serviceId,
                                                                          LocalDate date) {
        UUID businessId = resolveEnabledBusiness(key);
        return databaseContext.callAsTenant(
                businessId,
                () -> tenantService.availability(businessId, serviceId, date));
    }

    public PublicBookingController.PublicBookingConfirmation create(
            UUID key,
            String idempotencyKey,
            PublicBookingController.PublicBookingRequest request) {
        UUID businessId = resolveEnabledBusiness(key);
        return databaseContext.callAsTenant(
                businessId,
                () -> tenantService.create(businessId, idempotencyKey, request));
    }

    private UUID resolveEnabledBusiness(UUID key) {
        if (key == null) throw new NotFoundException("Booking page not found");
        return databaseContext.callAsSystem(() ->
                profiles.findByPublicBookingKeyAndPublicBookingEnabledTrue(key)
                        .map(profile -> profile.getBusinessId())
                        .orElseThrow(() -> new NotFoundException("Booking page not found")));
    }
}
