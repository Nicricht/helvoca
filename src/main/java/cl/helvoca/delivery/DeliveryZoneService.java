package cl.helvoca.delivery;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeliveryZoneService {
    private final DeliveryZoneRepository repository;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    @Autowired
    public DeliveryZoneService(DeliveryZoneRepository repository,
                               TenantProvider tenantProvider,
                               AuditService auditService) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    public DeliveryZoneService(DeliveryZoneRepository repository, TenantProvider tenantProvider) {
        this(repository, tenantProvider, null);
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
        DeliveryZone saved = repository.saveAndFlush(zone);
        if (auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "DELIVERY_ZONE_CREATE",
                    "DELIVERY_ZONE",
                    saved.getId(),
                    null,
                    snapshot(saved));
        }
        return ZoneView.from(saved);
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
        Map<String, Object> before = snapshot(zone);
        apply(zone, input);
        DeliveryZone saved = repository.saveAndFlush(zone);
        if (auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "DELIVERY_ZONE_UPDATE",
                    "DELIVERY_ZONE",
                    saved.getId(),
                    before,
                    snapshot(saved));
        }
        return ZoneView.from(saved);
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        DeliveryZone zone = require(id, businessId);
        Map<String, Object> before = snapshot(zone);
        boolean wasActive = zone.isActive();
        zone.setActive(false);
        DeliveryZone saved = repository.save(zone);
        if (wasActive && auditService != null) {
            auditService.humanSuccess(
                    businessId,
                    "DELIVERY_ZONE_DEACTIVATE",
                    "DELIVERY_ZONE",
                    saved.getId(),
                    before,
                    snapshot(saved));
        }
    }

    private DeliveryZone require(UUID id, UUID businessId) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Delivery zone not found"));
    }

    private static Map<String, Object> snapshot(DeliveryZone zone) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", zone.getName());
        snapshot.put("fee", zone.getFee());
        snapshot.put("minimumOrder", zone.getMinimumOrder());
        snapshot.put("active", zone.isActive());
        return snapshot;
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
