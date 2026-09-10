package cl.helvoca.onboarding;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        upsertServices(businessId, request.services());
        upsertKnowledge(businessId, request.knowledge() == null ? List.of() : request.knowledge());
        businesses.saveAndFlush(business);
        auditService.success(businessId, "ONBOARDING_SETUP", "BUSINESS", businessId);
        return buildStatus(businessId, business);
    }

    private void upsertServices(UUID businessId, List<OnboardingSetupRequest.ServiceInput> requested) {
        Map<String, ServiceItem> existing = new HashMap<>();
        for (ServiceItem item : services.findAllByBusinessIdOrderByNameAsc(businessId)) {
            existing.put(item.getName().trim().toLowerCase(Locale.ROOT), item);
        }
        for (OnboardingSetupRequest.ServiceInput input : requested) {
            String key = input.name().trim().toLowerCase(Locale.ROOT);
            ServiceItem item = existing.getOrDefault(key, new ServiceItem());
            if (item.getId() == null) item.setBusinessId(businessId);
            item.setName(input.name().trim());
            item.setDescription(input.description());
            item.setDurationMinutes(input.durationMinutes());
            item.setPrice(input.price());
            item.setActive(true);
            services.save(item);
            existing.put(key, item);
        }
    }

    private void upsertKnowledge(UUID businessId, List<OnboardingSetupRequest.KnowledgeInput> requested) {
        Map<String, KnowledgeItem> existing = new HashMap<>();
        for (KnowledgeItem item : knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)) {
            existing.put(item.getTitle().trim().toLowerCase(Locale.ROOT), item);
        }
        for (OnboardingSetupRequest.KnowledgeInput input : requested) {
            String key = input.title().trim().toLowerCase(Locale.ROOT);
            KnowledgeItem item = existing.getOrDefault(key, new KnowledgeItem());
            if (item.getId() == null) item.setBusinessId(businessId);
            item.setTitle(input.title().trim());
            item.setCategory(input.category() == null || input.category().isBlank() ? null : input.category().trim());
            item.setContent(input.content().trim());
            item.setActive(true);
            knowledge.save(item);
            existing.put(key, item);
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
}
