package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTraceService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Primary
public class CertificationGuardedRealtimeToolService extends RealtimeToolService {
    private final CallSessionRepository calls;
    private final CallActionRepository actions;
    private final CustomerRepository customers;
    private final CallTraceService trace;

    public CertificationGuardedRealtimeToolService(BusinessRepository businesses,
                                                    CustomerRepository customers,
                                                    ServiceItemRepository services,
                                                    KnowledgeItemRepository knowledge,
                                                    BookingRepository bookings,
                                                    CallSessionRepository calls,
                                                    BusinessScheduleService schedule,
                                                    BusinessRequestService requests,
                                                    UnansweredQuestionService unansweredQuestions,
                                                    CallActionRepository actions,
                                                    CallTraceService trace) {
        super(businesses, customers, services, knowledge, bookings, calls, schedule, requests, unansweredQuestions);
        this.calls = calls;
        this.actions = actions;
        this.customers = customers;
        this.trace = trace;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String execute(RealtimeCallContext context, String toolName, String rawArguments) {
        CallSession call = calls.findByIdAndBusinessId(context.callId(), context.businessId()).orElse(null);
        if (call != null && call.isCertification()) {
            JSONObject blocked = switch (toolName) {
                case "create_booking" -> guardCreate(context, call);
                case "cancel_booking" -> guardCancel(context, rawArguments);
                default -> null;
            };
            if (blocked != null) {
                trace.recordTool(context.businessId(), context.callId(), toolName, blocked);
                return blocked.toString();
            }
        }
        return super.execute(context, toolName, rawArguments);
    }

    private JSONObject guardCreate(RealtimeCallContext context, CallSession call) {
        boolean customerKnown = call.getCustomerId() != null
                || (context.callerNumber() != null
                && !context.callerNumber().isBlank()
                && customers.findFirstByBusinessIdAndPhone(context.businessId(), context.callerNumber()).isPresent());
        if (!customerKnown) {
            return error("CERTIFICATION_CUSTOMER_REQUIRED",
                    "La certificación exige identificar o registrar al cliente antes de crear la reserva.");
        }

        List<CallAction> history = actions.findAllByCallIdOrderByCreatedAtAsc(context.callId());
        int catalogIndex = firstSuccessful(history, "SERVICES_LISTED", 0);
        if (catalogIndex < 0) {
            return error("CERTIFICATION_CATALOG_REQUIRED",
                    "La certificación exige consultar el catálogo antes de crear la reserva.");
        }

        int availabilityIndex = firstSuccessfulAvailability(history, catalogIndex + 1);
        if (availabilityIndex < 0) {
            return error("CERTIFICATION_AVAILABILITY_REQUIRED",
                    "La certificación exige comprobar disponibilidad después de consultar el catálogo.");
        }
        return null;
    }

    private JSONObject guardCancel(RealtimeCallContext context, String rawArguments) {
        UUID bookingId;
        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            bookingId = UUID.fromString(args.optString("bookingId", ""));
        } catch (Exception e) {
            return null;
        }

        boolean createdInThisCall = actions.findAllByCallIdOrderByCreatedAtAsc(context.callId()).stream()
                .anyMatch(action -> action.isSuccess()
                        && "BOOKING_CREATED".equals(action.getActionType())
                        && bookingId.equals(action.getEntityId()));
        if (!createdInThisCall) {
            return error("CERTIFICATION_BOOKING_REQUIRED",
                    "La certificación solo puede cancelar la reserva creada con éxito en esta misma llamada.");
        }
        return null;
    }

    private static int firstSuccessful(List<CallAction> history, String actionType, int start) {
        for (int i = Math.max(0, start); i < history.size(); i++) {
            CallAction action = history.get(i);
            if (action.isSuccess() && actionType.equals(action.getActionType())) return i;
        }
        return -1;
    }

    private static int firstSuccessfulAvailability(List<CallAction> history, int start) {
        for (int i = Math.max(0, start); i < history.size(); i++) {
            CallAction action = history.get(i);
            if (!action.isSuccess()) continue;
            if ("AVAILABILITY_CHECKED".equals(action.getActionType())
                    || "AVAILABILITY_LISTED".equals(action.getActionType())) return i;
        }
        return -1;
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}
