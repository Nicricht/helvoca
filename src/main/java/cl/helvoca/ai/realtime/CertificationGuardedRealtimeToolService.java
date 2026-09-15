package cl.helvoca.ai.realtime;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingOperationSyncService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTraceService;
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
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Primary
public class CertificationGuardedRealtimeToolService extends RealtimeToolService {
    private static final Set<String> BOOKING_MUTATIONS = Set.of(
            "create_booking", "reschedule_booking", "cancel_booking");

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

    @Autowired(required = false)
    private CommercialOperationToolService commercialOperations;

    @Autowired(required = false)
    private BusinessOperationCapabilityService operationCapabilities;

    @Autowired
    private BookingOperationSyncService bookingOperations;

    @Autowired
    private BookingConfirmationWorkflowService bookingConfirmationWorkflow;

    @Autowired(required = false)
    private AutomationPolicyToolGate automationPolicies;

    @Autowired(required = false)
    private SafeOperationRetryEngine retryEngine;

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
        String instructions = RecepVozConversationPolicyService.appendTo(super.buildInstructions(context));
        if (operationCapabilities != null) {
            instructions += CommercialToolDefinitions.instructions(operationCapabilities.enabled(context.businessId()));
        }
        instructions += "\nPara create_booking usa siempre dos fases: primero llama con serviceId/startAt para obtener una propuesta, presenta esas condiciones y pide confirmación explícita; solo después vuelve a llamar create_booking con el operationId y confirmationToken devueltos. Una respuesta sin bookingId es solo una propuesta y NO significa que exista una reserva.";
        return instructions;
    }

    @Override
    @Transactional(readOnly = true)
    public JSONArray toolDefinitions(RealtimeCallContext context) {
        JSONArray definitions = super.toolDefinitions(context);

        if (operationCapabilities != null) {
            Set<String> allowedCommercial = operationCapabilities.allowedToolNames(context.businessId());
            JSONArray commercial = CommercialToolDefinitions.allowed(allowedCommercial);
            for (int i = 0; i < commercial.length(); i++) {
                JSONObject definition = commercial.getJSONObject(i);
                if (!containsTool(definitions, definition.getString("name"))) definitions.put(definition);
            }
        }

        if (!containsTool(definitions, "end_call")) definitions.put(RealtimeToolDefinitions.endCall());
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

        BusinessOperation.Type operationType = null;
        if (automationPolicies != null) {
            JSONObject policyBlock = automationPolicies.blockIfAutomationDisabled(context.businessId(), toolName);
            if (policyBlock != null) {
                trace.recordTool(context.businessId(), context.callId(), toolName, policyBlock);
                return policyBlock.toString();
            }
            operationType = automationPolicies.operationType(toolName);
        }

        if (retryEngine != null && operationType != null) {
            BusinessOperation.Type retryType = operationType;
            return retryEngine.execute(
                    context.businessId(),
                    retryType,
                    context.callId(),
                    toolName,
                    () -> executeOperationOnce(context, toolName, rawArguments));
        }
        return executeOperationOnce(context, toolName, rawArguments);
    }

    private String executeOperationOnce(RealtimeCallContext context,
                                        String toolName,
                                        String rawArguments) {
        if (commercialOperations != null && commercialOperations.supports(toolName)) {
            JSONObject result;
            if (operationCapabilities == null || !operationCapabilities.isToolAllowed(context.businessId(), toolName)) {
                result = error("TOOL_DISABLED", "Esta capacidad comercial no está habilitada para este negocio.");
            } else {
                CallSession call = calls.findByIdAndBusinessId(context.callId(), context.businessId()).orElse(null);
                UUID customerId = call == null ? null : call.getCustomerId();
                result = new JSONObject(commercialOperations.execute(
                        context.businessId(),
                        customerId,
                        context.callId(),
                        context.callerNumber(),
                        BusinessOrder.Source.VOICE,
                        toolName,
                        rawArguments));
            }
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

        if ("create_booking".equals(toolName)) {
            JSONObject args;
            try {
                args = rawArguments == null || rawArguments.isBlank()
                        ? new JSONObject()
                        : new JSONObject(rawArguments);
            } catch (Exception e) {
                JSONObject invalid = error("INVALID_ARGUMENT", "Los datos de la reserva no son válidos.");
                trace.recordTool(context.businessId(), context.callId(), toolName, invalid);
                return invalid.toString();
            }

            UUID customerId = call == null ? null : call.getCustomerId();
            if (customerId == null && context.callerNumber() != null && !context.callerNumber().isBlank()) {
                customerId = customers.findFirstByBusinessIdAndPhone(context.businessId(), context.callerNumber())
                        .map(customer -> customer.getId())
                        .orElse(null);
            }
            JSONObject result = bookingConfirmationWorkflow.execute(
                    context.businessId(),
                    customerId,
                    context.callId(),
                    context.callerNumber(),
                    BusinessOrder.Source.VOICE,
                    BookingSource.AI_CALL,
                    args);
            String synchronizedResult = synchronizeBookingMutation(context, toolName, result.toString());
            trace.recordTool(context.businessId(), context.callId(), toolName, new JSONObject(synchronizedResult));
            return synchronizedResult;
        }

        lockBookingMutation(context, toolName, rawArguments);
        String result = super.execute(context, toolName, rawArguments);
        if (!BOOKING_MUTATIONS.contains(toolName)) return result;
        return synchronizeBookingMutation(context, toolName, result);
    }

    private String synchronizeBookingMutation(RealtimeCallContext context,
                                               String toolName,
                                               String rawResult) {
        JSONObject result = new JSONObject(rawResult);
        if (!result.optBoolean("success", false)) return rawResult;
        JSONObject data = result.optJSONObject("data");
        if (data == null || data.optString("bookingId", "").isBlank()) return rawResult;

        try {
            UUID bookingId = UUID.fromString(data.getString("bookingId"));
            BusinessOperation operation = bookingOperations.synchronize(
                    context.businessId(),
                    bookingId,
                    context.callId(),
                    BusinessOrder.Source.VOICE,
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

    private static boolean containsTool(JSONArray definitions, String name) {
        for (int i = 0; i < definitions.length(); i++) {
            if (name.equals(definitions.getJSONObject(i).optString("name"))) return true;
        }
        return false;
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
