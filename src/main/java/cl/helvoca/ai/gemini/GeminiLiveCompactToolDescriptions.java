package cl.helvoca.ai.gemini;

import org.json.JSONObject;

import java.util.Map;

/**
 * Voice-only, opt-in setup reduction. This does not change backend tool
 * definitions, parameters, type validation, tenant isolation or tool results.
 * Unknown tools retain their original description.
 */
final class GeminiLiveCompactToolDescriptions {
    private GeminiLiveCompactToolDescriptions() { }

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("list_services",
                    "Lista servicios activos con serviceId real, precio y duración. Antes de reservar, consulta el catálogo si no tienes el UUID literal."),
            Map.entry("find_caller",
                    "Obtiene el cliente del teléfono verificado. Antes de crear reserva busca al cliente; si found=false, pide el nombre y usa register_caller."),
            Map.entry("register_caller",
                    "Registra el nombre del cliente de esta llamada cuando sea necesario. El teléfono lo fija el backend, nunca la IA."),
            Map.entry("list_available_slots",
                    "Da horarios reales para serviceId y fecha local date. Enviar requestedWeekday si el cliente dijo el día; requestedWeekScope para este/próximo. "
                    + "Nunca inventes UUID, fecha ni startAt. Solo ofrece slots/nextAvailableSlots recibidos. Si no hay cupos, usa customerSafeExplanation; "
                    + "si unavailabilityReasonKnown=false, NO inventes causas. Un startAt listado pasa directo a create_booking FASE 1, sin reconsulta."),
            Map.entry("check_booking_availability",
                    "Consulta hora exacta NO obtenida ya de list_available_slots, con serviceId real y hora local o startAt literal. "
                    + "No vuelvas a consultar horas listadas. Si available=false, usa solo customerSafeExplanation, sin inventar causa. "
                    + "Si available=true, pasa startAt literal a create_booking. Respeta requestedWeekday y requestedWeekScope del cliente."),
            Map.entry("create_booking",
                    "Reserva en DOS fases. FASE 1: serviceId real más startAt exacto disponible; opcional requestedWeekday copiado de dayOfWeek "
                    + "y requestedWeekScope solo si el cliente lo indicó. La respuesta operationId, confirmationToken, requiresConfirmation=true "
                    + "NO es reserva: NO digas confirmada. Pregunta UNA vez; un sí claro basta. FASE 2: envía SOLO operationId y confirmationToken "
                    + "exactos. Solo con success=true y bookingId real di que está confirmada. Nunca inventes identificadores ni horarios."),
            Map.entry("reschedule_booking",
                    "Cambia solo la reserva identificada por bookingId que el cliente pidió modificar. Usa hora nueva devuelta por disponibilidad, "
                    + "sin inventarla. Respeta requestedWeekday/dayOfWeek del backend y requestedWeekScope. "
                    + "Solo comunica cambio si success=true; el backend valida capacidad."),
            Map.entry("cancel_booking",
                    "Cancela una reserva solo cuando el cliente lo pide explícitamente. Usa bookingId exacto de create_booking FASE 2 "
                    + "o list_customer_bookings, NUNCA operationId. Si hay varias, no canceles las otras sin petición. "
                    + "Confirma la cancelación solo con success=true.")
    );

    private static final Map<String, String> ARGUMENTS = Map.ofEntries(
            Map.entry("serviceId", "UUID literal obtenido de list_services; nunca inventarlo."),
            Map.entry("startAt", "Instante ISO-8601 exacto devuelto por disponibilidad; no recalcular offset."),
            Map.entry("newStartAt", "Nuevo instante ISO-8601 exacto devuelto por disponibilidad."),
            Map.entry("date", "Fecha local del negocio YYYY-MM-DD."),
            Map.entry("requestedWeekScope", "NEXT_WEEK para 'próximo' o 'semana que viene'; THIS_WEEK para 'este' o 'esta semana'. Omitir si no mencionó semana; omitir en FASE 2."),
            Map.entry("requestedWeekday", "Día solicitado MONDAY..SUNDAY; omitir si no lo mencionó; al confirmar, copiar dayOfWeek devuelto por el backend."),
            Map.entry("localDate", "Fecha local YYYY-MM-DD, cuando el cliente pidió hora local."),
            Map.entry("localTime", "Hora local HH:mm cuando corresponda."),
            Map.entry("newLocalDate", "Nueva fecha local YYYY-MM-DD cuando corresponda."),
            Map.entry("newLocalTime", "Nueva hora local HH:mm cuando corresponda."),
            Map.entry("bookingId", "UUID de reserva confirmada desde FASE 2 o list_customer_bookings; NUNCA operationId."),
            Map.entry("operationId", "Solo FASE 2: operationId literal devuelto en FASE 1."),
            Map.entry("confirmationToken", "Solo FASE 2: confirmationToken literal devuelto en FASE 1."),
            Map.entry("customerName", "Nombre confirmado explícitamente por el cliente, no reutilizar sin confirmar."),
            Map.entry("notes", "Notas opcionales de FASE 1.")
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
