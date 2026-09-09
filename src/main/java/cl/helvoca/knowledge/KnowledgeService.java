package cl.helvoca.knowledge;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeService {
    private final KnowledgeItemRepository items;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public KnowledgeService(KnowledgeItemRepository items, TenantProvider tenantProvider, AuditService auditService) {
        this.items = items;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeItemResponse> list(boolean activeOnly) {
        UUID businessId = tenantProvider.requireBusinessId();
        var result = activeOnly
                ? items.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)
                : items.findAllByBusinessIdOrderByTitleAsc(businessId);
        return result.stream().map(KnowledgeItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeItemResponse get(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        return KnowledgeItemResponse.from(requireItem(id, businessId));
    }

    @Transactional
    public KnowledgeItemResponse create(KnowledgeItemRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        KnowledgeItem item = new KnowledgeItem();
        item.setBusinessId(businessId);
        apply(item, request);
        KnowledgeItem saved = items.save(item);
        auditService.success(businessId, "KNOWLEDGE_CREATE", "KNOWLEDGE_ITEM", saved.getId());
        return KnowledgeItemResponse.from(saved);
    }

    @Transactional
    public KnowledgeItemResponse update(UUID id, KnowledgeItemRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        KnowledgeItem item = requireItem(id, businessId);
        apply(item, request);
        auditService.success(businessId, "KNOWLEDGE_UPDATE", "KNOWLEDGE_ITEM", id);
        return KnowledgeItemResponse.from(item);
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        KnowledgeItem item = requireItem(id, businessId);
        item.setActive(false);
        auditService.success(businessId, "KNOWLEDGE_DEACTIVATE", "KNOWLEDGE_ITEM", id);
    }

    private KnowledgeItem requireItem(UUID id, UUID businessId) {
        return items.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Knowledge item not found"));
    }

    private static void apply(KnowledgeItem item, KnowledgeItemRequest request) {
        item.setTitle(request.title());
        item.setCategory(request.category());
        item.setContent(request.content());
        if (request.active() != null) item.setActive(request.active());
    }
}
