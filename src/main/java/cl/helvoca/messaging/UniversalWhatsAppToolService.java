package cl.helvoca.messaging;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.operations.BusinessOperationCapabilityService;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.operations.CommercialToolDefinitions;
import cl.helvoca.request.BusinessRequestService;
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
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String execute(MessagingConversation conversation, String toolName, String rawArguments) {
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

    private static JSONObject disabled() {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "TOOL_DISABLED")
                        .put("message", "Esta capacidad comercial no está habilitada para este negocio."));
    }
}
