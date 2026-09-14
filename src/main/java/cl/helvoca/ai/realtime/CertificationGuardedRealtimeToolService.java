package cl.helvoca.ai.realtime;

import cl.helvoca.booking.Booking;
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
import cl.helvoca.telephony.twilio.TwilioCallControl;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final BookingRepository bookings;
    private final CallTraceService trace;
    private final JdbcTemplate jdbc;

    @Autowired(required = false)
    private TwilioCallControl twilioCallControl;

    @Autowired(required = false)
    private TwilioProperties twilioProperties;

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
                                                    CallTraceService trace,
                                                    JdbcTemplate jdbc) {
        super(businesses, customers, services, knowledge, bookings, calls, schedule, requests, unansweredQuestions);
        this.calls = calls;
        this.actions = actions;
        this.customers = customers;
        this.bookings = bookings;
        this.trace = trace;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public String buildInstructions(RealtimeCallContext context) {
        return RecepVozConversationPolicyService.appendTo(super.buildInstructions(context));
    }

    @Override
    @Transactional(readOnly = true)
    public JSONArray toolDefinitions(RealtimeCallContext context) {
        JSONArray definitions = super.toolDefinitions(context);
        boolean hasEndCall = false;
        for (int i = 0; i < definitions.length(); i++) {
            if ("end_call".equals(definitions.getJSONObject(i).optString("name"))) {
                hasEndCall = true;
                break;
            }
        }
        if (!hasEndCall) definitions.put(RealtimeToolDefinitions.endCall());
        return definitions;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String execute(RealtimeCallContext context, String toolName, String rawArguments) {
        if ("end_call".equals(toolName)) {
            JSONObject result = endCall(context);
            trace.recordTool(context.businessId(), context.callId(), toolName, result);
            return result.toString();
        }

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

        lockBookingMutation(context, toolName, rawArguments);
        return super.execute(context, toolName, rawArguments);
    }

    private JSONObject endCall(RealtimeCallContext context) {
        CallSession call = calls.findByIdAndBusinessId(context.callId(), context.businessId()).orElse(null);
        if (call == null || context.streamSid() == null || !context.streamSid().equals(call.getStreamSid())) {
            return error("CALL_CONTEXT_MISMATCH", "No pude verificar que esta llamada corresponda al contexto telefónico actual.");
        }
        if (call.getStatus() != null && call.getStatus().terminal()) {
            return success(new JSONObject().put("ended", true).put("alreadyEnded", true));
        }
        if (!"twilio".equalsIgnoreCase(call.getTelephonyProvider())) {
            return error("END_CALL_UNSUPPORTED", "El proveedor telefónico actual no admite cierre remoto desde este flujo.");
        }
        if (twilioCallControl == null || twilioProperties == null || !twilioProperties.hasAccountSid()) {
            return error("END_CALL_UNAVAILABLE", "El control telefónico no está disponible en este momento.");
        }
        String providerCallId = call.getProviderCallId();
        if (providerCallId == null || providerCallId.isBlank()) {
            return error("END_CALL_UNAVAILABLE", "La llamada no tiene un identificador telefónico válido para finalizarla.");
        }
        boolean accepted = twilioCallControl.hangup(twilioProperties.getAccountSid().trim(), providerCallId);
        if (!accepted) {
            return error("END_CALL_FAILED", "No pude finalizar la llamada desde el proveedor telefónico.");
        }
        return success(new JSONObject().put("ended", true).put("alreadyEnded", false));
    }

    private void lockBookingMutation(RealtimeCallContext context, String toolName, String rawArguments) {
        UUID serviceId = null;
        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            if ("create_booking".equals(toolName)) {
                serviceId = UUID.fromString(args.optString("serviceId", ""));
            } else if ("reschedule_booking".equals(toolName)) {
                UUID bookingId = UUID.fromString(args.optString("bookingId", ""));
                Booking booking = bookings.findByIdAndBusinessId(bookingId, context.businessId()).orElse(null);
                if (booking != null) serviceId = booking.getServiceId();
            }
        } catch (Exception ignored) {
            return;
        }
        if (serviceId == null) return;

        int businessKey = context.businessId().hashCode();
        int serviceKey = serviceId.hashCode();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessKey + "," + serviceKey + ")");
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

    private static JSONObject success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}
