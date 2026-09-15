package cl.helvoca.messaging;

import cl.helvoca.booking.BookingOperationSyncService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.operations.AutomationPolicyToolGate;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.operations.CommercialToolDefinitions;
import cl.helvoca.operations.SafeOperationRetryEngine;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestSource;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Set;
import java.util.UUID;

@Service
@Primary
public class UniversalWhatsAppToolService extends WhatsAppToolService {
    private static final Set<String> BOOKING_MUTATIONS = Set.of(
            "create_booking", "reschedule_booking", "cancel_booking");

    private final CommercialOperationToolService commercial;
    private final BusinessOperationCapabilityService capabilities;
    private final BusinessRequestService requests;
    private final CustomerRepository customers;
    private final BookingOperationSyncService bookingOperations;

    @Autowired(required = false)
    private AutomationPolicyToolGate automationPolicies;

    @Autowired(required = false)
    private SafeOperationRetryEngine retryEngine;

    public UniversalWhatsAppToolService(BusinessRepository businesses,
                                        CustomerRepository customers,
                                        ServiceItemRepository services,
                                        KnowledgeItemRepository knowledge,
                                        BookingRepository bookings,
                                        BusinessScheduleService schedule,
                                        BusinessRequestService requests,
                                        UnansweredQuestionService unansweredQuestions,
                                        MessagingConversationRepository conversations,
                                        JdbcTemplate jdbc,
                                        CommercialOperationToolService commercial,
                                        BusinessOperationCapabilityService capabilities,
                                        BookingOperationSyncService bookingOperations) {
        super(businesses, customers, services, knowledge, bookings, schedule, requests,
                unansweredQuestions, conversations, jdbc);
        this.commercial = commercial;
        this.capabilities = capabilities;
        this.requests = requests;
        this.customers = customers;
        this.bookingOperations = bookingOperations;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String execute(MessagingConversation conversation, String toolName, String rawArguments) {
        BusinessOperation.Type operationType = null;
        if (automationPolicies != null) {
            JSONObject policyBlock = automationPolicies.blockIfAutomationDisabled(
                    conversation.getBusinessId(), toolName);
            if (policyBlock != null) return policyBlock.toString();
            operationType = automationPolicies.operationType(toolName);
        }

        if (retryEngine != null && operationType != null) {
            BusinessOperation.Type retryType = operationType;
            return retryEngine.execute(
                    conversation.getBusinessId(),
                    retryType,
                    conversation.getId(),
                    toolName,
                    () -> executeOperationOnce(conversation, toolName, rawArguments));
        }
        return executeOperationOnce(conversation, toolName, rawArguments);
    }

    private String executeOperationOnce(MessagingConversation conversation,
                                        String toolName,
                                        String rawArguments) {
        if ("create_request".equals(toolName)) {
            return createRequestWithConversationContext(conversation, rawArguments).toString();
        }
        if (commercial.supports(toolName)) {
            if (!capabilities.isToolAllowed(conversation.getBusinessId(), toolName)) {
                return disabled().toString();
            }
            return commercial.execute(
                    conversation.getBusinessId(),
                    conversation.getCustomerId(),
                    conversation.getId(),
                    conversation.getSender(),
                    BusinessOrder.Source.WHATSAPP,
                    toolName,
                    rawArguments);
        }

        String result = super.execute(conversation, toolName, rawArguments);
        if (!BOOKING_MUTATIONS.contains(toolName)) return result;
        return synchronizeBookingMutation(conversation, toolName, result);
    }

    @Override
    @Transactional(readOnly = true)
    public String buildInstructions(MessagingConversation conversation) {
        return super.buildInstructions(conversation)
                + CommercialToolDefinitions.instructions(capabilities.enabled(conversation.getBusinessId()))
                + "\nSi una herramienta devuelve automation.fallbackAction, aplica esa alternativa con las herramientas disponibles antes de pedir intervención humana. No repitas manualmente una operación que automation ya reintentó.";
    }

    private String synchronizeBookingMutation(MessagingConversation conversation,
                                               String toolName,
                                               String rawResult) {
        JSONObject result = new JSONObject(rawResult);
        if (!result.optBoolean("success", false)) return rawResult;
        JSONObject data = result.optJSONObject("data");
        if (data == null || data.optString("bookingId", "").isBlank()) return rawResult;

        try {
            UUID bookingId = UUID.fromString(data.getString("bookingId"));
            BusinessOperation operation = bookingOperations.synchronize(
                    conversation.getBusinessId(),
                    bookingId,
                    conversation.getId(),
                    BusinessOrder.Source.WHATSAPP,
                    toolName);
            data.put("operationId", operation.getId().toString());
            data.put("operationRevision", operation.getRevision());
            return result.toString();
        } catch (RuntimeException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return error("BOOKING_OPERATION_SYNC_FAILED",
                    "La reserva no pudo sincronizarse de forma segura. No se aplicará el cambio.").toString();
        }
    }

    private JSONObject createRequestWithConversationContext(MessagingConversation conversation, String rawArguments) {
        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            Customer customer = currentCustomer(conversation);
            String detailsJson = optional(args, "detailsJson");
            if (detailsJson != null) new JSONObject(detailsJson);

            BusinessRequest request = requests.createFromAi(
                    conversation.getBusinessId(),
                    customer == null ? null : customer.getId(),
                    conversation.getId(),
                    required(args, "requestType"),
                    required(args, "title"),
                    optional(args, "description"),
                    customer == null ? null : customer.getName(),
                    conversation.getSender(),
                    requestPriority(optional(args, "priority")),
                    detailsJson,
                    RequestSource.AI_WHATSAPP);

            return success(new JSONObject()
                    .put("requestId", request.getId().toString())
                    .put("operationId", request.getOperationId().toString())
                    .put("status", request.getStatus().name())
                    .put("requestType", request.getRequestType())
                    .put("title", request.getTitle()));
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENT", e.getMessage());
        } catch (Exception e) {
            return error("REQUEST_OPERATION_FAILED", "La solicitud no pudo completarse.");
        }
    }

    private Customer currentCustomer(MessagingConversation conversation) {
        if (conversation.getCustomerId() != null) {
            return customers.findByIdAndBusinessId(conversation.getCustomerId(), conversation.getBusinessId()).orElse(null);
        }
        return customers.findFirstByBusinessIdAndPhone(
                conversation.getBusinessId(), conversation.getSender()).orElse(null);
    }

    private static RequestPriority requestPriority(String value) {
        if (value == null || value.isBlank()) return RequestPriority.NORMAL;
        try { return RequestPriority.valueOf(value.trim().toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("Prioridad inválida."); }
    }

    private static String required(JSONObject args, String key) {
        String value = args.optString(key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta el argumento " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) return null;
        String value = args.optString(key, null);
        return value == null || value.isBlank() ? null : value;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static JSONObject disabled() {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "TOOL_DISABLED")
                        .put("message", "Esta capacidad comercial no está habilitada para este negocio."));
    }
}
