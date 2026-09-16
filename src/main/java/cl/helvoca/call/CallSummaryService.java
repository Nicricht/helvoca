package cl.helvoca.call;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CallSummaryService {
    private static final Logger log = LoggerFactory.getLogger(CallSummaryService.class);
    private static final int MAX_SNIPPET = 220;

    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;
    private final CallActionRepository actions;
    private final CallSessionRepository calls;
    private final TenantDatabaseContext databaseContext;
    private final Set<UUID> summariesInFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    public CallSummaryService(CallTranscriptRepository transcripts,
                              CallSummaryRepository summaries,
                              CallActionRepository actions,
                              CallSessionRepository calls,
                              TenantDatabaseContext databaseContext) {
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.actions = actions;
        this.calls = calls;
        this.databaseContext = databaseContext;
    }

    // Retained for focused unit tests that do not bootstrap database RLS context.
    public CallSummaryService(CallTranscriptRepository transcripts,
                              CallSummaryRepository summaries,
                              CallActionRepository actions) {
        this(transcripts, summaries, actions, null, null);
    }

    @Async
    public void generate(UUID callId) {
        if (callId == null || !summariesInFlight.add(callId)) return;
        try {
            Thread.sleep(500);
            if (databaseContext == null || calls == null) {
                generateForTenant(callId);
                return;
            }

            UUID businessId = databaseContext.callAsSystem(() ->
                    calls.findById(callId).map(CallSession::getBusinessId).orElse(null));
            if (businessId == null) return;
            databaseContext.runAsTenant(businessId, () -> generateForTenant(callId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not generate summary for call {}: {}", callId, e.getMessage());
        } finally {
            summariesInFlight.remove(callId);
        }
    }

    private void generateForTenant(UUID callId) {
        if (summaries.findByCallId(callId).isPresent()) return;

        List<CallTranscript> items = transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId);
        List<CallAction> callActions = actions.findAllByCallIdOrderByCreatedAtAsc(callId);
        if (items.isEmpty() && callActions.isEmpty()) return;

        save(callId, buildLocalSummary(items, callActions));
    }

    private void save(UUID callId, String text) {
        int inserted = summaries.insertIfAbsent(UUID.randomUUID(), callId, text);
        if (inserted == 1) {
            log.info("Call summary persisted for call {} using local factual summarizer", callId);
        } else {
            log.debug("Call summary already persisted concurrently for call {}", callId);
        }
    }

    static String buildLocalSummary(List<CallTranscript> items, List<CallAction> actions) {
        StringBuilder out = new StringBuilder();
        out.append("La llamada registró ").append(items.size()).append(" intervenciones.");

        String firstUser = firstUserText(items);
        if (firstUser != null) {
            out.append(" Motivo inicial: ").append(snippet(firstUser)).append(".");
        }

        List<String> confirmed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        boolean unanswered = false;

        for (CallAction action : actions) {
            String type = action.getActionType() == null ? "ACCIÓN" : action.getActionType();
            if ("UNANSWERED_QUESTION_RECORDED".equals(type)) unanswered = true;

            String label = actionLabel(type);
            String detail = action.getDetail();
            String rendered = detail == null || detail.isBlank()
                    ? label
                    : label + " (" + snippet(detail) + ")";

            if (action.isSuccess()) {
                if (isMeaningfulConfirmedAction(type)) confirmed.add(rendered);
            } else {
                String error = action.getErrorCode();
                failed.add(error == null || error.isBlank()
                        ? rendered
                        : rendered + " [" + error + "]");
            }
        }

        if (!confirmed.isEmpty()) {
            out.append(" Acciones confirmadas: ").append(String.join("; ", confirmed)).append(".");
        } else {
            out.append(" No se registraron acciones de negocio confirmadas.");
        }

        if (!failed.isEmpty()) {
            out.append(" Acciones no completadas: ").append(String.join("; ", failed)).append(".");
        }
        if (unanswered) {
            out.append(" Quedó al menos una pregunta pendiente de respuesta del negocio.");
        }

        String lastUser = lastUserText(items);
        if (lastUser != null && !lastUser.equals(firstUser)) {
            out.append(" Último mensaje del cliente: ").append(snippet(lastUser)).append(".");
        }
        return out.toString();
    }

    private static boolean isMeaningfulConfirmedAction(String type) {
        return switch (type) {
            case "CUSTOMER_REGISTERED", "BOOKING_CREATED", "BOOKING_RESCHEDULED", "BOOKING_CANCELLED",
                    "REQUEST_CREATED", "HUMAN_TRANSFER", "UNANSWERED_QUESTION_RECORDED" -> true;
            default -> false;
        };
    }

    private static String actionLabel(String type) {
        return switch (type) {
            case "CUSTOMER_REGISTERED" -> "cliente registrado";
            case "BOOKING_CREATED" -> "reserva creada";
            case "BOOKING_RESCHEDULED" -> "reserva reprogramada";
            case "BOOKING_CANCELLED" -> "reserva cancelada";
            case "REQUEST_CREATED" -> "solicitud creada";
            case "HUMAN_TRANSFER", "TRANSFER_REQUESTED" -> "transferencia a humano";
            case "UNANSWERED_QUESTION_RECORDED" -> "pregunta pendiente registrada";
            case "AVAILABILITY_LISTED", "AVAILABILITY_CHECKED" -> "disponibilidad consultada";
            case "BUSINESS_INFORMATION" -> "información del negocio consultada";
            case "SERVICES_LISTED" -> "servicios consultados";
            case "KNOWLEDGE_SEARCH" -> "conocimiento consultado";
            case "CALLER_LOOKUP" -> "cliente consultado";
            case "BOOKINGS_LISTED" -> "reservas consultadas";
            default -> type.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String firstUserText(List<CallTranscript> items) {
        for (CallTranscript item : items) {
            if (isUser(item) && hasText(item)) return clean(item.getContent());
        }
        return null;
    }

    private static String lastUserText(List<CallTranscript> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            CallTranscript item = items.get(i);
            if (isUser(item) && hasText(item)) return clean(item.getContent());
        }
        return null;
    }

    private static boolean isUser(CallTranscript item) {
        return item != null && item.getSpeaker() != null
                && ("USER".equalsIgnoreCase(item.getSpeaker()) || "CALLER".equalsIgnoreCase(item.getSpeaker()));
    }

    private static boolean hasText(CallTranscript item) {
        return item.getContent() != null && !item.getContent().isBlank();
    }

    private static String clean(String value) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (cleaned.startsWith("[SIMULATED_CERTIFICATION]")) {
            cleaned = cleaned.substring("[SIMULATED_CERTIFICATION]".length()).trim();
        }
        return cleaned;
    }

    private static String snippet(String value) {
        String cleaned = clean(value);
        return cleaned.length() <= MAX_SNIPPET ? cleaned : cleaned.substring(0, MAX_SNIPPET - 1) + "…";
    }
}
