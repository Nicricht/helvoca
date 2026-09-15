package cl.helvoca.messaging;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.operations.CommercialToolDefinitions;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestSource;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class UniversalWhatsAppToolService extends WhatsAppToolService {
    private final CommercialOperationToolService commercial;
    private final BusinessOperationCapabilityService capabilities;
    private final BusinessRequestService requests;
    private final CustomerRepository customers;

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
                                        BusinessOperationCapabilityService capabilities) {
        super(businesses, customers, services, knowledge, bookings, schedule, requests,
                unansweredQuestions, conversations, jdbc);
        this.commercial = commercial;
        this.capabilities = capabilities;
        this.requests = requests;
        this.customers = customers;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String execute(MessagingConversation conversation, String toolName, String rawArguments) {
        if ("create_request".equals(toolName)) {
            return createRequestWithConversationContext(conversation, rawArguments).toString();
        }
        if (!commercial.supports(toolName)) return super.execute(conversation, toolName, rawArguments);
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

    @Override
    @Transactional(readOnly = true)
    public String buildInstructions(MessagingConversation conversation) {
        return super.buildInstructions(conversation)
                + CommercialToolDefinitions.instructions(capabilities.enabled(conversation.getBusinessId()));
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
