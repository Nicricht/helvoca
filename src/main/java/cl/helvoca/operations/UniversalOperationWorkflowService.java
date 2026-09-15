package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestSource;
import cl.helvoca.request.RequestStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class UniversalOperationWorkflowService {
    private final CatalogItemRepository catalog;
    private final BusinessOperationRepository operations;
    private final BusinessOperationItemRepository operationItems;
    private final BusinessQuoteRepository quotes;
    private final BusinessLeadRepository leads;
    private final BusinessRequestRepository requests;
    private final OperationPolicyService policies;
    private final ConversationStateService conversationState;

    public UniversalOperationWorkflowService(CatalogItemRepository catalog,
                                             BusinessOperationRepository operations,
                                             BusinessOperationItemRepository operationItems,
                                             BusinessQuoteRepository quotes,
                                             BusinessLeadRepository leads,
                                             BusinessRequestRepository requests,
                                             OperationPolicyService policies,
                                             ConversationStateService conversationState) {
        this.catalog = catalog;
        this.operations = operations;
        this.operationItems = operationItems;
        this.quotes = quotes;
        this.leads = leads;
        this.requests = requests;
        this.policies = policies;
        this.conversationState = conversationState;
    }

    @Transactional
    public JSONObject createQuote(UUID businessId,
                                  UUID customerId,
                                  UUID sourceReferenceId,
                                  String trustedPhone,
                                  BusinessOrder.Source source,
                                  JSONObject args) {
        String title = required(args, "title").trim();
        if (title.isBlank()) throw new IllegalArgumentException("La cotización necesita un título.");

        QuoteCalculation calculation = calculateQuoteItems(businessId, args.optJSONArray("items"));
        BusinessOrder.Source safeSource = source == null ? BusinessOrder.Source.API : source;
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.QUOTE);

        BusinessOperation operation = baseOperation(
                businessId, customerId, sourceReferenceId, safeSource,
                BusinessOperation.Type.QUOTE,
                optional(args, "contactName"), trustedPhone,
                quoteMetadata(title, optional(args, "description"), calculation.items().size(), policy));
        operation.setSubtotal(calculation.amount());
        operation.setDeliveryFee(calculation.amount() == null ? null : BigDecimal.ZERO);
        operation.setTotal(calculation.amount());
        operation.setCurrency(calculation.currency());
        operation = operations.saveAndFlush(operation);
        persistItems(operation.getId(), calculation.items());

        BusinessQuote quote = new BusinessQuote();
        quote.setOperationId(operation.getId());
        quote.setBusinessId(businessId);
        quote.setCustomerId(customerId);
        quote.setSourceReferenceId(sourceReferenceId);
        quote.setContactName(optional(args, "contactName"));
        quote.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        quote.setTitle(title);
        quote.setDescription(optional(args, "description"));
        quote.setAmount(calculation.amount());
        quote.setCurrency(calculation.currency());
        quote.setStatus(calculation.amount() == null ? BusinessQuote.Status.REQUESTED : BusinessQuote.Status.READY);
        quote.setSource(safeSource);
        quote = quotes.saveAndFlush(quote);

        recordConversation(businessId, sourceReferenceId, safeSource, operation,
                Map.of(
                        "lastTool", "create_quote",
                        "quoteId", quote.getId().toString(),
                        "title", quote.getTitle(),
                        "confirmationPending", policy.requiresExplicitConfirmation()));

        return success(new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("revision", operation.getRevision())
                .put("quoteId", quote.getId().toString())
                .put("title", quote.getTitle())
                .put("status", quote.getStatus().name())
                .put("amount", nullable(quote.getAmount()))
                .put("currency", quote.getCurrency())
                .put("confirmationRequired", policy.requiresExplicitConfirmation()));
    }

    @Transactional
    public JSONObject createLead(UUID businessId,
                                 UUID customerId,
                                 UUID sourceReferenceId,
                                 String trustedPhone,
                                 BusinessOrder.Source source,
                                 JSONObject args) {
        String name = required(args, "name").trim();
        String interest = required(args, "interest").trim();
        if (name.isBlank() || interest.isBlank()) {
            throw new IllegalArgumentException("El lead necesita nombre e interés.");
        }

        BigDecimal budget = null;
        if (args.has("budget") && args.opt("budget") != JSONObject.NULL) {
            budget = money(args.get("budget"));
            if (budget.signum() < 0) throw new IllegalArgumentException("El presupuesto no puede ser negativo.");
        }

        BusinessOrder.Source safeSource = source == null ? BusinessOrder.Source.API : source;
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.LEAD);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "LEAD");
        metadata.put("name", name);
        put(metadata, "email", optional(args, "email"));
        metadata.put("interest", interest);
        put(metadata, "budget", budget);
        put(metadata, "notes", optional(args, "notes"));
        metadata.put("confirmationPending", policy.requiresExplicitConfirmation());

        BusinessOperation operation = baseOperation(
                businessId, customerId, sourceReferenceId, safeSource,
                BusinessOperation.Type.LEAD, name, trustedPhone, metadata);
        operation = operations.saveAndFlush(operation);

        BusinessLead lead = new BusinessLead();
        lead.setOperationId(operation.getId());
        lead.setBusinessId(businessId);
        lead.setCustomerId(customerId);
        lead.setSourceReferenceId(sourceReferenceId);
        lead.setName(name);
        lead.setPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        lead.setEmail(optional(args, "email"));
        lead.setInterest(interest);
        lead.setBudget(budget);
        lead.setNotes(optional(args, "notes"));
        lead.setStatus(BusinessLead.Status.NEW);
        lead.setSource(safeSource);
        lead = leads.saveAndFlush(lead);

        recordConversation(businessId, sourceReferenceId, safeSource, operation,
                Map.of(
                        "lastTool", "create_lead",
                        "leadId", lead.getId().toString(),
                        "name", lead.getName(),
                        "interest", lead.getInterest(),
                        "confirmationPending", policy.requiresExplicitConfirmation()));

        return success(new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("revision", operation.getRevision())
                .put("leadId", lead.getId().toString())
                .put("name", lead.getName())
                .put("interest", lead.getInterest())
                .put("status", lead.getStatus().name())
                .put("confirmationRequired", policy.requiresExplicitConfirmation()));
    }

    @Transactional
    public BusinessRequest createRequest(UUID businessId,
                                         UUID customerId,
                                         UUID sourceReferenceId,
                                         String requestType,
                                         String title,
                                         String description,
                                         String contactName,
                                         String contactPhone,
                                         RequestPriority priority,
                                         String detailsJson,
                                         RequestSource requestSource) {
        RequestSource safeRequestSource = requestSource == null ? RequestSource.AI_CALL : requestSource;
        BusinessOrder.Source source = operationSource(safeRequestSource);
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.REQUEST);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "REQUEST");
        metadata.put("requestType", clean(requestType, 80));
        metadata.put("title", clean(title, 200));
        put(metadata, "description", blankToNull(description));
        metadata.put("priority", (priority == null ? RequestPriority.NORMAL : priority).name());
        put(metadata, "details", blankToNull(detailsJson));
        metadata.put("confirmationPending", policy.requiresExplicitConfirmation());

        BusinessOperation operation = baseOperation(
                businessId, customerId, sourceReferenceId, source,
                BusinessOperation.Type.REQUEST, blankToNull(contactName), contactPhone, metadata);
        operation = operations.saveAndFlush(operation);

        BusinessRequest request = new BusinessRequest();
        request.setOperationId(operation.getId());
        request.setBusinessId(businessId);
        request.setCustomerId(customerId);
        request.setCallId(sourceReferenceId);
        request.setRequestType(clean(requestType, 80));
        request.setTitle(clean(title, 200));
        request.setDescription(blankToNull(description));
        request.setContactName(blankToNull(contactName));
        request.setContactPhone(blankToNull(contactPhone));
        request.setPriority(priority == null ? RequestPriority.NORMAL : priority);
        request.setStatus(RequestStatus.OPEN);
        request.setSource(safeRequestSource);
        request.setDetailsJson(blankToNull(detailsJson));
        request = requests.saveAndFlush(request);

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("lastTool", "create_request");
        patch.put("requestId", request.getId().toString());
        patch.put("requestType", request.getRequestType());
        patch.put("title", request.getTitle());
        patch.put("confirmationPending", policy.requiresExplicitConfirmation());
        recordConversation(businessId, sourceReferenceId, source, operation, patch);
        return request;
    }

    private BusinessOperation baseOperation(UUID businessId,
                                            UUID customerId,
                                            UUID sourceReferenceId,
                                            BusinessOrder.Source source,
                                            BusinessOperation.Type type,
                                            String contactName,
                                            String contactPhone,
                                            Map<String, Object> metadata) {
        OperationPolicyService.Policy policy = policies.resolve(type);
        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(type);
        operation.setSource(source == null ? BusinessOrder.Source.API : source);
        operation.setRevision(1);
        operation.setContactName(blankToNull(contactName));
        operation.setContactPhone(blankToNull(contactPhone));
        operation.setCurrency("CLP");
        operation.setMetadata(metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata));
        if (policy.requiresExplicitConfirmation()) {
            operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
            operation.setConfirmationToken(UUID.randomUUID());
        } else {
            operation.setStatus(BusinessOperation.Status.CONFIRMED);
            operation.setConfirmationToken(null);
        }
        return operation;
    }

    private QuoteCalculation calculateQuoteItems(UUID businessId, JSONArray items) {
        if (items == null || items.isEmpty()) return new QuoteCalculation(null, "CLP", List.of());
        BigDecimal subtotal = BigDecimal.ZERO;
        String currency = null;
        List<ItemSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject requested = items.optJSONObject(i);
            if (requested == null) throw new IllegalArgumentException("Cada ítem debe ser un objeto válido.");
            UUID itemId = uuid(required(requested, "catalogItemId"));
            int quantity = requested.optInt("quantity", 0);
            if (quantity < 1 || quantity > 100) {
                throw new IllegalArgumentException("La cantidad de cada ítem debe estar entre 1 y 100.");
            }
            CatalogItem item = catalog.findByIdAndBusinessId(itemId, businessId)
                    .filter(CatalogItem::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Un ítem del catálogo no existe o no está activo."));
            if (item.getPrice() == null) {
                throw new IllegalArgumentException("Un ítem de la cotización no tiene precio configurado.");
            }
            if (currency == null) currency = item.getCurrency();
            if (!Objects.equals(currency, item.getCurrency())) {
                throw new IllegalArgumentException("No se pueden mezclar monedas distintas en una misma operación.");
            }
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            JSONObject modifiers = requested.optJSONObject("modifiers");
            snapshots.add(new ItemSnapshot(
                    item, quantity, lineTotal,
                    modifiers == null ? null : modifiers.toMap(),
                    optional(requested, "notes")));
        }
        return new QuoteCalculation(subtotal, currency == null ? "CLP" : currency, snapshots);
    }

    private void persistItems(UUID operationId, List<ItemSnapshot> snapshots) {
        for (ItemSnapshot snapshot : snapshots) {
            BusinessOperationItem line = new BusinessOperationItem();
            line.setOperationId(operationId);
            line.setCatalogItemId(snapshot.item().getId());
            line.setItemName(snapshot.item().getName());
            line.setQuantity(snapshot.quantity());
            line.setUnitPrice(snapshot.item().getPrice());
            line.setLineTotal(snapshot.lineTotal());
            line.setModifiers(snapshot.modifiers());
            line.setNotes(snapshot.notes());
            operationItems.save(line);
        }
        if (!snapshots.isEmpty()) operationItems.flush();
    }

    private void recordConversation(UUID businessId,
                                    UUID sourceReferenceId,
                                    BusinessOrder.Source source,
                                    BusinessOperation operation,
                                    Map<String, Object> extra) {
        if (sourceReferenceId == null) return;
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("intent", operation.getType().name());
        patch.put("operationId", operation.getId().toString());
        patch.put("operationType", operation.getType().name());
        patch.put("operationStatus", operation.getStatus().name());
        patch.put("operationRevision", operation.getRevision());
        if (extra != null) patch.putAll(extra);
        conversationState.apply(businessId, sourceReferenceId, source, operation.getId(), patch);
    }

    private static Map<String, Object> quoteMetadata(String title,
                                                      String description,
                                                      int itemCount,
                                                      OperationPolicyService.Policy policy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "QUOTE");
        metadata.put("title", title);
        put(metadata, "description", description);
        metadata.put("itemCount", itemCount);
        metadata.put("confirmationPending", policy.requiresExplicitConfirmation());
        return metadata;
    }

    private static BusinessOrder.Source operationSource(RequestSource source) {
        return switch (source) {
            case AI_CALL -> BusinessOrder.Source.VOICE;
            case AI_WHATSAPP -> BusinessOrder.Source.WHATSAPP;
            case MANUAL -> BusinessOrder.Source.MANUAL;
            case WEB -> BusinessOrder.Source.API;
        };
    }

    private static String required(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) {
            throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        }
        String value = String.valueOf(args.get(key));
        if (value.isBlank()) throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) return null;
        String value = String.valueOf(args.get(key)).trim();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value.trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Se recibió un identificador inválido."); }
    }

    private static BigDecimal money(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalArgumentException("Se recibió un monto inválido."); }
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is missing");
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private record ItemSnapshot(CatalogItem item,
                                int quantity,
                                BigDecimal lineTotal,
                                Map<String, Object> modifiers,
                                String notes) {}
    private record QuoteCalculation(BigDecimal amount,
                                    String currency,
                                    List<ItemSnapshot> items) {}
}
