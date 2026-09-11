package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.capability.BusinessCapabilityCode;
import cl.helvoca.capability.BusinessCapabilityService;
import cl.helvoca.learning.UnansweredQuestion;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.observability.CallToolEventService;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestPriority;
import cl.helvoca.request.BusinessRequestService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class RealtimeToolPolicyAspect {
    private final BusinessCapabilityService capabilities;
    private final BusinessRequestService requests;
    private final UnansweredQuestionService unanswered;
    private final CallToolEventService toolEvents;
    private final CallSessionRepository calls;

    public RealtimeToolPolicyAspect(BusinessCapabilityService capabilities,
                                    BusinessRequestService requests,
                                    UnansweredQuestionService unanswered,
                                    CallToolEventService toolEvents,
                                    CallSessionRepository calls) {
        this.capabilities = capabilities;
        this.requests = requests;
        this.unanswered = unanswered;
        this.toolEvents = toolEvents;
        this.calls = calls;
    }

    @Around("execution(public String cl.helvoca.ai.realtime.RealtimeToolService.execute(..)) && args(context,toolName,rawArguments)")
    public Object aroundExecute(ProceedingJoinPoint joinPoint,
                                RealtimeCallContext context,
                                String toolName,
                                String rawArguments) throws Throwable {
        long started = System.nanoTime();
        JSONObject result;
        try {
            BusinessCapabilityCode required = requiredCapability(toolName);
            if (required != null && !capabilities.isEnabled(context.businessId(), required)) {
                result = error("CAPABILITY_DISABLED", "Esta operación no está habilitada para este negocio.");
            } else if ("create_business_request".equals(toolName)) {
                result = createBusinessRequest(context, rawArguments);
            } else if ("record_unanswered_question".equals(toolName)) {
                result = recordUnansweredQuestion(context, rawArguments);
            } else {
                String rawResult = (String) joinPoint.proceed();
                result = new JSONObject(rawResult);
            }
        } catch (IllegalArgumentException e) {
            result = error("INVALID_ARGUMENT", e.getMessage());
        } catch (Exception e) {
            result = error("TOOL_EXECUTION_FAILED", "La operación no pudo completarse en el backend.");
        }

        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        try {
            toolEvents.record(context, toolName, result, durationMs);
        } catch (Exception ignored) {
            // La observabilidad nunca debe romper una acción del cliente.
        }
        return result.toString();
    }

    @Around("execution(public String cl.helvoca.ai.realtime.RealtimeToolService.buildInstructions(..)) && args(context)")
    public Object appendCapabilityInstructions(ProceedingJoinPoint joinPoint,
                                               RealtimeCallContext context) throws Throwable {
        String base = (String) joinPoint.proceed();
        Map<BusinessCapabilityCode, Boolean> enabled = capabilities.resolve(context.businessId());
        return base + "\nCapacidades habilitadas para este negocio: " + enabled + ".\n" +
                "Respeta esas capacidades estrictamente. Si BOOKINGS está deshabilitado, no ofrezcas crear, cambiar ni cancelar reservas. " +
                "Si REQUESTS está habilitado y el cliente necesita una gestión que no es una reserva, recopila los datos necesarios y usa create_business_request. " +
                "Si una pregunta factual no puede responderse después de search_knowledge, usa record_unanswered_question y explica que esa información no está confirmada. " +
                "Nunca inventes una respuesta para evitar registrar una pregunta pendiente.\n";
    }

    private JSONObject createBusinessRequest(RealtimeCallContext context, String rawArguments) {
        JSONObject args = arguments(rawArguments);
        CallSession call = trustedCall(context);
        String subject = required(args, "subject");
        String details = required(args, "details");
        String category = optional(args, "category");
        BusinessRequestPriority priority = priority(optional(args, "priority"));
        BusinessRequest created = requests.createFromCall(
                context.businessId(), call.getCustomerId(), call.getId(), category, subject, details, priority);
        call.setResolution("REQUEST_CREATED");
        calls.save(call);
        return success(new JSONObject()
                .put("requestId", created.getId().toString())
                .put("subject", created.getSubject())
                .put("status", created.getStatus().name())
                .put("priority", created.getPriority().name()));
    }

    private JSONObject recordUnansweredQuestion(RealtimeCallContext context, String rawArguments) {
        JSONObject args = arguments(rawArguments);
        CallSession call = trustedCall(context);
        UnansweredQuestion item = unanswered.recordFromCall(
                context.businessId(), call.getCustomerId(), call.getId(), required(args, "question"));
        return success(new JSONObject()
                .put("questionId", item.getId().toString())
                .put("status", item.getStatus().name())
                .put("occurrences", item.getOccurrences()));
    }

    private CallSession trustedCall(RealtimeCallContext context) {
        return calls.findByIdAndBusinessId(context.callId(), context.businessId())
                .filter(call -> context.callerNumber() == null || context.callerNumber().equals(call.getCallerNumber()))
                .orElseThrow(() -> new IllegalArgumentException("La llamada no corresponde al contexto verificado."));
    }

    private static BusinessCapabilityCode requiredCapability(String toolName) {
        return switch (toolName) {
            case "list_services" -> BusinessCapabilityCode.SERVICES;
            case "search_knowledge", "record_unanswered_question" -> BusinessCapabilityCode.INFORMATION;
            case "list_available_slots", "check_booking_availability", "create_booking",
                    "list_customer_bookings", "reschedule_booking", "cancel_booking" -> BusinessCapabilityCode.BOOKINGS;
            case "create_business_request" -> BusinessCapabilityCode.REQUESTS;
            case "transfer_to_human" -> BusinessCapabilityCode.HUMAN_TRANSFER;
            default -> null;
        };
    }

    private static JSONObject arguments(String rawArguments) {
        return rawArguments == null || rawArguments.isBlank() ? new JSONObject() : new JSONObject(rawArguments);
    }

    private static String required(JSONObject args, String name) {
        String value = args.optString(name, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Falta el dato obligatorio: " + name + ".");
        return value;
    }

    private static String optional(JSONObject args, String name) {
        if (!args.has(name) || args.isNull(name)) return null;
        String value = args.optString(name, "").trim();
        return value.isBlank() ? null : value;
    }

    private static BusinessRequestPriority priority(String value) {
        if (value == null) return BusinessRequestPriority.NORMAL;
        try {
            return BusinessRequestPriority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BusinessRequestPriority.NORMAL;
        }
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("code", code).put("message", message);
    }
}
