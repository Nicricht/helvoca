package cl.helvoca.onboarding;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHoursAdminService;
import cl.helvoca.schedule.BusinessHoursRequest;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {
    private final BusinessRepository businesses;
    private final ServiceItemRepository services;
    private final KnowledgeItemRepository knowledge;
    private final PhoneNumberRepository phoneNumbers;
    private final BusinessHoursAdminService businessHours;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public OnboardingService(BusinessRepository businesses,
                             ServiceItemRepository services,
                             KnowledgeItemRepository knowledge,
                             PhoneNumberRepository phoneNumbers,
                             BusinessHoursAdminService businessHours,
                             TenantProvider tenantProvider,
                             AuditService auditService) {
        this.businesses = businesses;
        this.services = services;
        this.knowledge = knowledge;
        this.phoneNumbers = phoneNumbers;
        this.businessHours = businessHours;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse status() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = requireBusiness(businessId);
        return buildStatus(businessId, business);
    }

    @Transactional
    public OnboardingStatusResponse setup(OnboardingSetupRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = requireBusiness(businessId);
        validateTimezone(request.timezone());

        business.setName(request.businessName().trim());
        business.setTimezone(request.timezone().trim());
        business.setLanguage(request.language().trim().toLowerCase(Locale.ROOT));
        business.setHumanTransferPhone(normalizePhone(request.humanTransferPhone()));

        List<BusinessHoursRequest.Interval> intervals = request.hours().stream()
                .map(h -> new BusinessHoursRequest.Interval(h.dayOfWeek(), h.openTime(), h.closeTime()))
                .toList();
        businessHours.replace(new BusinessHoursRequest(intervals));

        syncServices(businessId, request.services());
        syncKnowledge(businessId, request.knowledge() == null ? List.of() : request.knowledge());
        businesses.saveAndFlush(business);
        auditService.success(businessId, "ONBOARDING_SETUP", "BUSINESS", businessId);
        return buildStatus(businessId, business);
    }

    private void syncServices(UUID businessId, List<OnboardingSetupRequest.ServiceInput> requested) {
        List<ServiceItem> current = services.findAllByBusinessIdOrderByNameAsc(businessId);
        Map<UUID, ServiceItem> byId = new HashMap<>();
        Map<String, ServiceItem> byName = new HashMap<>();
        for (ServiceItem item : current) {
            byId.put(item.getId(), item);
            byName.put(normalizedKey(item.getName()), item);
        }

        Set<UUID> retainedIds = new HashSet<>();
        Set<String> requestedNames = new HashSet<>();
        for (OnboardingSetupRequest.ServiceInput input : requested) {
            String key = normalizedKey(input.name());
            if (!requestedNames.add(key)) {
                throw new ConflictException("Service names must be unique");
            }

            ServiceItem item;
            if (input.id() != null) {
                item = byId.get(input.id());
                if (item == null) throw new NotFoundException("Service not found");
            } else {
                item = byName.get(key);
                if (item == null) {
                    item = new ServiceItem();
                    item.setBusinessId(businessId);
                }
            }

            ServiceItem nameOwner = byName.get(key);
            if (nameOwner != null && !Objects.equals(nameOwner.getId(), item.getId())) {
                throw new ConflictException("A service with that name already exists");
            }

            String oldKey = item.getId() == null ? null : normalizedKey(item.getName());
            item.setName(input.name().trim());
            item.setDescription(input.description() == null || input.description().isBlank() ? null : input.description().trim());
            item.setDurationMinutes(input.durationMinutes());
            item.setPrice(input.price());
            item.setActive(true);
            ServiceItem saved = services.save(item);
            if (saved.getId() != null) retainedIds.add(saved.getId());

            if (oldKey != null && !oldKey.equals(key) && byName.get(oldKey) == item) {
                byName.remove(oldKey);
            }
            byName.put(key, saved);
            if (saved.getId() != null) byId.put(saved.getId(), saved);
        }

        for (ServiceItem item : current) {
            if (item.isActive() && !retainedIds.contains(item.getId())) {
                item.setActive(false);
                services.save(item);
            }
        }
    }

    private void syncKnowledge(UUID businessId, List<OnboardingSetupRequest.KnowledgeInput> requested) {
        List<KnowledgeItem> current = knowledge.findAllByBusinessIdOrderByTitleAsc(businessId);
        Map<UUID, KnowledgeItem> byId = new HashMap<>();
        Map<String, KnowledgeItem> byTitle = new HashMap<>();
        for (KnowledgeItem item : current) {
            byId.put(item.getId(), item);
            byTitle.put(normalizedKey(item.getTitle()), item);
        }

        Set<UUID> retainedIds = new HashSet<>();
        Set<String> requestedTitles = new HashSet<>();
        for (OnboardingSetupRequest.KnowledgeInput input : requested) {
            String key = normalizedKey(input.title());
            if (!requestedTitles.add(key)) {
                throw new ConflictException("Knowledge titles must be unique");
            }

            KnowledgeItem item;
            if (input.id() != null) {
                item = byId.get(input.id());
                if (item == null) throw new NotFoundException("Knowledge item not found");
            } else {
                item = byTitle.get(key);
                if (item == null) {
                    item = new KnowledgeItem();
                    item.setBusinessId(businessId);
                }
            }

            KnowledgeItem titleOwner = byTitle.get(key);
            if (titleOwner != null && !Objects.equals(titleOwner.getId(), item.getId())) {
                throw new ConflictException("A knowledge item with that title already exists");
            }

            String oldKey = item.getId() == null ? null : normalizedKey(item.getTitle());
            item.setTitle(input.title().trim());
            item.setCategory(input.category() == null || input.category().isBlank() ? null : input.category().trim());
            item.setContent(input.content().trim());
            item.setActive(true);
            KnowledgeItem saved = knowledge.save(item);
            if (saved.getId() != null) retainedIds.add(saved.getId());

            if (oldKey != null && !oldKey.equals(key) && byTitle.get(oldKey) == item) {
                byTitle.remove(oldKey);
            }
            byTitle.put(key, saved);
            if (saved.getId() != null) byId.put(saved.getId(), saved);
        }

        for (KnowledgeItem item : current) {
            if (item.isActive() && !retainedIds.contains(item.getId())) {
                item.setActive(false);
                knowledge.save(item);
            }
        }
    }

    private OnboardingStatusResponse buildStatus(UUID businessId, Business business) {
        boolean profile = business.getName() != null && !business.getName().isBlank()
                && business.getTimezone() != null && !business.getTimezone().isBlank()
                && business.getLanguage() != null && !business.getLanguage().isBlank();
        boolean service = services.findAllByBusinessIdOrderByNameAsc(businessId).stream().anyMatch(ServiceItem::isActive);
        boolean schedule = businessHours.list().size() > 0;
        boolean kb = knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId).size() > 0;
        boolean transfer = business.getHumanTransferPhone() != null && !business.getHumanTransferPhone().isBlank();
        boolean phone = phoneNumbers.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream().anyMatch(p -> p.isActive());
        boolean ready = profile && service && schedule && phone;

        String nextStep;
        if (!profile) nextStep = "CONFIGURE_BUSINESS";
        else if (!service) nextStep = "ADD_SERVICE";
        else if (!schedule) nextStep = "CONFIGURE_HOURS";
        else if (!phone) nextStep = "CONNECT_PHONE_NUMBER";
        else if (!transfer) nextStep = "OPTIONAL_HUMAN_TRANSFER";
        else if (!kb) nextStep = "OPTIONAL_KNOWLEDGE";
        else nextStep = "READY";

        return new OnboardingStatusResponse(profile, service, schedule, kb, transfer, phone, ready, nextStep);
    }

    private Business requireBusiness(UUID businessId) {
        return businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid business timezone");
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return phone.trim();
    }

    private static String normalizedKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
