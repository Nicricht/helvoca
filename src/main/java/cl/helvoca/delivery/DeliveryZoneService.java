package cl.helvoca.delivery;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryZoneService {
    private final DeliveryZoneRepository repository;
    private final TenantProvider tenantProvider;

    public DeliveryZoneService(DeliveryZoneRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<ZoneView> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByNameAsc(businessId).stream().map(ZoneView::from).toList();
    }

    @Transactional
    public ZoneView create(ZoneInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(input);
        if (repository.existsByBusinessIdAndNameIgnoreCase(businessId, input.name().trim())) {
            throw new ConflictException("A delivery zone with that name already exists");
        }
        DeliveryZone zone = new DeliveryZone();
        zone.setBusinessId(businessId);
        apply(zone, input);
        return ZoneView.from(repository.saveAndFlush(zone));
    }

    @Transactional
    public ZoneView update(UUID id, ZoneInput input) {
        UUID businessId = tenantProvider.requireBusinessId();
        validate(input);
        DeliveryZone zone = require(id, businessId);
        if (!zone.getName().equalsIgnoreCase(input.name().trim())
                && repository.existsByBusinessIdAndNameIgnoreCase(businessId, input.name().trim())) {
            throw new ConflictException("A delivery zone with that name already exists");
        }
        apply(zone, input);
        return ZoneView.from(repository.saveAndFlush(zone));
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        DeliveryZone zone = require(id, businessId);
        zone.setActive(false);
        repository.save(zone);
    }

    private DeliveryZone require(UUID id, UUID businessId) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Delivery zone not found"));
    }

    private static void validate(ZoneInput input) {
        if (input == null || input.name() == null || input.name().isBlank()) {
            throw new IllegalArgumentException("Delivery zone name is required");
        }
        if (input.coverageTerms() == null || input.coverageTerms().isBlank()) {
            throw new IllegalArgumentException("Delivery coverage terms are required");
        }
        if (input.fee() == null || input.fee().signum() < 0) {
            throw new IllegalArgumentException("Delivery fee must be zero or greater");
        }
        if (input.minimumOrder() != null && input.minimumOrder().signum() < 0) {
            throw new IllegalArgumentException("Minimum order cannot be negative");
        }
    }

    private static void apply(DeliveryZone zone, ZoneInput input) {
        zone.setName(input.name().trim());
        zone.setCoverageTerms(input.coverageTerms().trim());
        zone.setFee(input.fee());
        zone.setMinimumOrder(input.minimumOrder());
        if (input.active() != null) zone.setActive(input.active());
    }

    public record ZoneInput(String name, String coverageTerms, BigDecimal fee, BigDecimal minimumOrder, Boolean active) {}

    public record ZoneView(UUID id, String name, String coverageTerms,
                           BigDecimal fee, BigDecimal minimumOrder, boolean active) {
        static ZoneView from(DeliveryZone zone) {
            return new ZoneView(zone.getId(), zone.getName(), zone.getCoverageTerms(),
                    zone.getFee(), zone.getMinimumOrder(), zone.isActive());
        }
    }
}
