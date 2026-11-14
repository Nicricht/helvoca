package cl.helvoca.ai.gemini;

import org.json.JSONObject;

import java.util.Map;

/**
 * Voice-only compact descriptions for Gemini Live. The original tool names,
 * parameter keys, types, required fields, backend validation and tenant
 * authorization remain unchanged. Unknown tools keep their own descriptions.
 *
 * This is deliberately prose-only compression: never drop a schema field
 * to save tokens or rely on prompt text as an authorization boundary.
 */
final class GeminiLiveCompactToolDescriptions {
    private GeminiLiveCompactToolDescriptions() { }

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("get_business_information",
                    "Nombre, hora local y zona horaria oficiales del negocio."),
            Map.entry("list_services",
                    "Servicios activos, serviceId, precio y duración. Obtén UUID real antes de reservar."),
            Map.entry("search_knowledge",
                    "Busca información oficial del negocio; no inventes respuestas."),
            Map.entry("find_caller",
                    "Identifica al cliente del teléfono verificado antes de reservar. Si found=false, solicita nombre y usa register_caller."),
            Map.entry("register_caller",
                    "Registra o corrige el nombre confirmado del cliente actual. El backend fija el teléfono."),
            Map.entry("verify_caller_whatsapp",
                    "Marca este número como WhatsApp solo tras confirmación explícita del cliente."),
            Map.entry("list_available_slots",
                    "Horarios reales por serviceId y date. Envía requestedWeekday y requestedWeekScope si se mencionaron. "
                    + "Ofrece solo startAt o nextAvailableSlots futuros. Si no hay cupos, usa customerSafeExplanation; "
                    + "si unavailabilityReasonKnown=false, no inventes causas. Un startAt listado no requiere otra consulta."),
            Map.entry("check_booking_availability",
                    "Consulta hora exacta aún no listada. Usa serviceId y hora local o startAt real; respeta día y semana. "
                    + "Si available=false, usa customerSafeExplanation sin inventar causa; si true, copia startAt."),
            Map.entry("create_booking",
                    "DOS fases. FASE 1: serviceId y startAt reales, día/semana si el cliente los dijo; "
                    + "requiresConfirmation=true, operationId y confirmationToken NO son reserva. "
                    + "Pide UNA confirmación; un sí basta. FASE 2: SOLO operationId y confirmationToken literales. "
                    + "Afirma reserva solo con success=true y bookingId; pronuncia fecha real del resultado."),
            Map.entry("list_customer_bookings",
                    "Lista reservas del cliente identificado en esta llamada; no accedas a otros clientes."),
            Map.entry("reschedule_booking",
                    "Reprograma solo bookingId solicitado con nueva hora disponible. Respeta requestedWeekday, "
                    + "requestedWeekScope y dayOfWeek del backend. Afirma cambio solo con success=true."),
            Map.entry("cancel_booking",
                    "Cancela bookingId real solo cuando el cliente lo pide; no canceles otras reservas. "
                    + "Nunca uses operationId como bookingId. Confirma cancelación solo con success=true."),
            Map.entry("create_request",
                    "Crea solicitud de seguimiento si no es una reserva. Confirma al cliente solo con success=true."),
            Map.entry("record_unanswered_question",
                    "Registra una pregunta sin respuesta oficial, solo después de search_knowledge."),
            Map.entry("transfer_to_human",
                    "Transfiere a humano cuando se solicite o no se pueda resolver; destino seguro del negocio.")
            // end_call and send_whatsapp_operation intentionally retain the
            // full existing contract: closing and handoff are high-risk.
    );

    private static final Map<String, String> ARGUMENTS = Map.ofEntries(
            Map.entry("serviceId", "UUID literal de list_services; nunca inventar."),
            Map.entry("startAt", "ISO-8601 literal devuelto por disponibilidad."),
            Map.entry("newStartAt", "Nuevo instante ISO-8601 devuelto por disponibilidad."),
            Map.entry("date", "Fecha local YYYY-MM-DD."),
            Map.entry("requestedWeekScope", "NEXT_WEEK próximo/semana siguiente; THIS_WEEK este/esta semana. Omitir sin semana explícita y en FASE 2."),
            Map.entry("requestedWeekday", "Día MONDAY..SUNDAY solo si se indicó; copiar dayOfWeek del backend."),
            Map.entry("localDate", "Fecha local YYYY-MM-DD."),
            Map.entry("localTime", "Hora local HH:mm."),
            Map.entry("newLocalDate", "Nueva fecha local YYYY-MM-DD."),
            Map.entry("newLocalTime", "Nueva hora local HH:mm."),
            Map.entry("bookingId", "UUID confirmado por FASE 2 o list_customer_bookings; no operationId."),
            Map.entry("operationId", "FASE 2: operationId exacto de FASE 1."),
            Map.entry("confirmationToken", "FASE 2: confirmationToken exacto de FASE 1."),
            Map.entry("customerName", "Nombre confirmado explícitamente por el cliente."),
            Map.entry("notes", "Notas opcionales FASE 1."),
            Map.entry("name", "Nombre del cliente confirmado en la llamada."),
            Map.entry("email", "Correo opcional del cliente."),
            Map.entry("query", "Pregunta o texto a buscar."),
            Map.entry("question", "Pregunta sin respuesta oficial."),
            Map.entry("confirmedSameNumber", "true SOLO tras confirmación explícita de WhatsApp."),
            Map.entry("requestType", "Tipo de seguimiento."),
            Map.entry("title", "Título breve de solicitud."),
            Map.entry("description", "Detalle de solicitud."),
            Map.entry("priority", "LOW, NORMAL, HIGH o URGENT."),
            Map.entry("detailsJson", "Datos opcionales JSON serializado.")
    );

    static void compact(JSONObject declaration) {
        String description = DESCRIPTIONS.get(declaration.getString("name"));
        if (description == null) return;
        declaration.put("description", description);
        JSONObject fields = declaration.getJSONObject("parameters").getJSONObject("properties");
        for (Map.Entry<String, String> entry : ARGUMENTS.entrySet()) {
            JSONObject field = fields.optJSONObject(entry.getKey());
            if (field != null) field.put("description", entry.getValue());
        }
    }
}
