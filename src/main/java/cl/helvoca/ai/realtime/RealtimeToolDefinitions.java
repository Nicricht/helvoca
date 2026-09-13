package cl.helvoca.ai.realtime;

import org.json.JSONArray;
import org.json.JSONObject;

public final class RealtimeToolDefinitions {
    private RealtimeToolDefinitions() {}

    public static JSONArray all() {
        return new JSONArray()
                .put(function("get_business_information", "Obtiene nombre, idioma y zona horaria del negocio.", object()))
                .put(function("list_services", "Lista servicios activos, precios y duración. Úsala antes de cualquier operación de reserva si todavía no tienes un serviceId real del catálogo.", object()))
                .put(function("search_knowledge", "Busca información oficial configurada por el negocio.",
                        object().put("properties", new JSONObject().put("query", string("Texto a buscar")))
                                .put("required", new JSONArray().put("query"))))
                .put(function("find_caller", "Obtiene el cliente asociado al teléfono de esta llamada. Para una reserva, ejecuta esta herramienta antes de create_booking. Si found=false, debes ejecutar register_caller antes de intentar crear la reserva.", object()))
                .put(function("register_caller", "Registra o actualiza al cliente de esta llamada. El teléfono se toma del contexto verificado y nunca de argumentos del modelo. Cuando find_caller devuelve found=false, completa este paso antes de create_booking.",
                        object().put("properties", new JSONObject()
                                        .put("name", string("Nombre del cliente"))
                                        .put("email", string("Correo opcional")))
                                .put("required", new JSONArray().put("name"))))
                .put(function("list_available_slots", "Lista horarios realmente disponibles para un servicio en una fecha local del negocio. Si no tienes un serviceId devuelto literalmente por list_services, llama list_services primero. Nunca inventes UUID. Si el horario solicitado no está disponible, usa literalmente uno de los startAt devueltos. No ejecutes create_booking con una alternativa que no haya sido devuelta por esta herramienta.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID exacto del servicio devuelto por list_services; nunca inventarlo"))
                                        .put("date", string("Fecha local del negocio en formato YYYY-MM-DD")))
                                .put("required", new JSONArray().put("serviceId").put("date"))))
                .put(function("check_booking_availability", "Comprueba una hora exacta antes de prometer o crear una reserva. El serviceId debe provenir literalmente de list_services. Si todavía no lo tienes, llama list_services primero. Nunca inventes UUID. Solo si devuelve available=true puedes usar ese mismo startAt en create_booking. Si available=false, llama list_available_slots antes de intentar crear la reserva.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID exacto del servicio devuelto por list_services; nunca inventarlo"))
                                        .put("startAt", string("Fecha y hora ISO-8601 con zona u offset")))
                                .put("required", new JSONArray().put("serviceId").put("startAt"))))
                .put(function("create_booking", "Crea una reserva real. PRECONDICIONES OBLIGATORIAS antes de invocarla: (1) ejecutar find_caller y, si found=false, completar register_caller; (2) ejecutar list_services y conservar literalmente el serviceId del catálogo; (3) verificar el horario con check_booking_availability y obtener available=true, o si no está disponible ejecutar list_available_slots y usar literalmente un startAt devuelto. Nunca llames create_booking antes de completar esas precondiciones. Solo se considera confirmada cuando esta herramienta devuelve success=true. Conserva el bookingId devuelto para cancelación o reprogramación posterior.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID exacto del servicio devuelto por list_services; nunca inventarlo"))
                                        .put("startAt", string("Fecha y hora previamente validada por check_booking_availability o devuelta por list_available_slots"))
                                        .put("notes", string("Notas opcionales")))
                                .put("required", new JSONArray().put("serviceId").put("startAt"))))
                .put(function("list_customer_bookings", "Lista las próximas reservas confirmadas del cliente identificado por esta llamada. El cliente y negocio se obtienen del contexto verificado.", object()))
                .put(function("reschedule_booking", "Reprograma una reserva del cliente de esta llamada. Solo comunica el cambio cuando success=true. El backend vuelve a validar horario y solapamientos.",
                        object().put("properties", new JSONObject()
                                        .put("bookingId", string("UUID de la reserva obtenido desde list_customer_bookings o desde create_booking en esta misma llamada"))
                                        .put("newStartAt", string("Nueva fecha y hora ISO-8601 con zona u offset")))
                                .put("required", new JSONArray().put("bookingId").put("newStartAt"))))
                .put(function("cancel_booking", "Cancela una reserva del cliente de esta llamada. Solo comunica la cancelación cuando success=true. Si acabas de crear la reserva en esta misma llamada, usa literalmente el bookingId devuelto por create_booking después de que create_booking haya devuelto success=true; nunca inventes otro identificador.",
                        object().put("properties", new JSONObject()
                                        .put("bookingId", string("UUID exacto de la reserva devuelto por create_booking o list_customer_bookings")))
                                .put("required", new JSONArray().put("bookingId"))))
                .put(function("create_request", "Crea una solicitud real de seguimiento cuando la necesidad del cliente no corresponde a una reserva. Sirve para cotizaciones, soporte, visitas, leads, urgencias u otros casos configurables. Solo confirma al cliente cuando success=true.",
                        object().put("properties", new JSONObject()
                                        .put("requestType", string("Tipo breve, por ejemplo cotización, soporte, visita, urgencia o contacto"))
                                        .put("title", string("Resumen corto de la solicitud"))
                                        .put("description", string("Descripción clara de lo que necesita el cliente"))
                                        .put("priority", string("LOW, NORMAL, HIGH o URGENT"))
                                        .put("detailsJson", string("Detalles estructurados opcionales en JSON serializado")))
                                .put("required", new JSONArray().put("requestType").put("title"))))
                .put(function("record_unanswered_question", "Registra una pregunta del cliente que no pudo resolverse con información oficial. Úsala solo después de buscar conocimiento y comprobar que la respuesta no está configurada.",
                        object().put("properties", new JSONObject()
                                        .put("question", string("Pregunta exacta o fielmente resumida del cliente")))
                                .put("required", new JSONArray().put("question"))))
                .put(function("transfer_to_human", "Solicita transferir la llamada a una persona del negocio cuando el cliente lo pida o la atención automática no pueda resolver su necesidad. El destino se obtiene de la configuración segura del negocio, nunca de argumentos del modelo.", object()));
    }

    private static JSONObject function(String name, String description, JSONObject parameters) {
        return new JSONObject()
                .put("type", "function")
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters);
    }

    private static JSONObject object() {
        // Keep the shared tool contract inside the JSON Schema subset accepted by
        // Gemini Live FunctionDeclaration. The backend still validates tenant,
        // identifiers and business rules, so provider-side additionalProperties
        // is not a security boundary.
        return new JSONObject().put("type", "object")
                .put("properties", new JSONObject());
    }

    private static JSONObject string(String description) {
        return new JSONObject().put("type", "string").put("description", description);
    }
}
