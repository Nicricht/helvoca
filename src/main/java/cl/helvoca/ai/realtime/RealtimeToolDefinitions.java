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
                .put(function("check_booking_availability", "Comprueba una hora exacta antes de prometer o proponer una reserva. El serviceId debe provenir literalmente de list_services. Si el cliente expresa una hora local, usa localDate y localTime y deja que el backend aplique la zona horaria del negocio. Usa startAt solo cuando lo copies literalmente de list_available_slots. Si available=true, copia literalmente el startAt devuelto para create_booking.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID exacto del servicio devuelto por list_services; nunca inventarlo"))
                                        .put("startAt", string("Instante ISO-8601; úsalo solo si fue devuelto literalmente por una herramienta"))
                                        .put("localDate", string("Fecha local del negocio YYYY-MM-DD cuando el cliente habló en hora local"))
                                        .put("localTime", string("Hora local del negocio HH:mm cuando el cliente habló en hora local")))
                                .put("required", new JSONArray().put("serviceId"))))
                .put(function("create_booking", "Reserva en dos fases. FASE 1: usa un serviceId devuelto literalmente por list_services, nunca inventes UUID, y envía ese serviceId con startAt previamente validado, más notes opcional. El backend devolverá operationId, confirmationToken y requiresConfirmation=true, pero todavía NO existe una reserva. Presenta exactamente esas condiciones al cliente y pide confirmación explícita. FASE 2: solo tras esa confirmación, vuelve a llamar esta misma herramienta enviando únicamente operationId y confirmationToken devueltos por la fase 1. No mezcles condiciones nuevas con el token. Solo cuando la segunda fase devuelva success=true y bookingId existe se considera creada la reserva.",
                        object().put("properties", new JSONObject()
                                .put("serviceId", string("FASE 1: UUID exacto del servicio devuelto por list_services; nunca inventarlo"))
                                .put("startAt", string("FASE 1: startAt exacto devuelto por check_booking_availability o list_available_slots; no recalcular offset"))
                                .put("localDate", string("FASE 1 en WhatsApp: fecha local YYYY-MM-DD si la petición original fue expresada en hora local"))
                                .put("localTime", string("FASE 1 en WhatsApp: hora local HH:mm si la petición original fue expresada en hora local"))
                                .put("customerName", string("FASE 1 en WhatsApp: nombre exacto que el cliente confirmó explícitamente para la reserva; nunca reutilizar un nombre guardado sin confirmarlo"))
                                .put("notes", string("FASE 1: notas opcionales"))
                                .put("operationId", string("FASE 2: operationId exacto devuelto por la propuesta"))
                                .put("confirmationToken", string("FASE 2: confirmationToken exacto devuelto por la propuesta")))))
                .put(function("list_customer_bookings", "Lista las próximas reservas confirmadas del cliente identificado por esta llamada. El cliente y negocio se obtienen del contexto verificado.", object()))
                .put(function("reschedule_booking", "Reprograma una reserva del cliente de esta llamada. Solo comunica el cambio cuando success=true. El backend vuelve a validar horario y solapamientos.",
                        object().put("properties", new JSONObject()
                                        .put("bookingId", string("UUID de la reserva obtenido desde list_customer_bookings o desde create_booking después de la segunda fase confirmada"))
                                        .put("newStartAt", string("Nuevo instante ISO-8601; úsalo solo si fue devuelto literalmente por una herramienta"))
                                        .put("newLocalDate", string("Nueva fecha local YYYY-MM-DD cuando el cliente expresó la hora local"))
                                        .put("newLocalTime", string("Nueva hora local HH:mm cuando el cliente expresó la hora local")))
                                .put("required", new JSONArray().put("bookingId"))))
                .put(function("cancel_booking", "Cancela una reserva del cliente de esta llamada. Solo comunica la cancelación cuando success=true. Si acabas de crear la reserva en esta misma llamada, usa literalmente el bookingId devuelto por la segunda fase confirmada de create_booking; nunca uses operationId como bookingId.",
                        object().put("properties", new JSONObject()
                                        .put("bookingId", string("UUID exacto de la reserva devuelto por create_booking confirmado o list_customer_bookings")))
                                .put("required", new JSONArray().put("bookingId"))))
                .put(function("create_request", "Crea una solicitud real de seguimiento cuando la necesidad del cliente no corresponde a una reserva. También úsala para abrir una operación product_showcase antes de continuar esa misma operación por WhatsApp. Cuando success=true devuelve requestId y operationId; conserva literalmente ese operationId para el handoff.",
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
                .put(function("verify_caller_whatsapp",
                        "Verifica explícitamente que el número telefónico de la llamada actual es también el WhatsApp del cliente identificado. Úsala SOLO después de que el cliente lo confirme claramente. No la uses por inferencia ni para otro número.",
                        object().put("properties", new JSONObject()
                                        .put("confirmedSameNumber", new JSONObject()
                                                .put("type", "boolean")
                                                .put("description", "Debe ser true únicamente cuando el cliente confirmó explícitamente que el número actual de la llamada es su WhatsApp")))
                                .put("required", new JSONArray().put("confirmedSameNumber"))))
                .put(function("send_whatsapp_operation",
                        "Continúa por WhatsApp una operación backend ya existente del cliente actual. Para PRODUCT_SHOWCASE envía hasta 3 productos reales seleccionados desde list_catalog; el backend toma su media configurada y nunca acepta URLs inventadas. Usa literalmente un operationId devuelto por create_request, create_quote, quote_order, create_booking u otra herramienta backend. Solo di que el contenido quedó enviado o en cola cuando success=true. Si success=false, no afirmes que se envió.",
                        object().put("properties", new JSONObject()
                                        .put("operationId", string("UUID exacto de la operación backend que se quiere continuar por WhatsApp"))
                                        .put("purpose", string("PAYMENT_LINK, BOOKING_CONFIRMATION, MEETING_LINK, ORDER_STATUS, QUOTE, REMINDER, DELIVERY_STATUS o PRODUCT_SHOWCASE"))
                                        .put("catalogItemIds", new JSONObject()
                                                .put("type", "array")
                                                .put("description", "Solo para PRODUCT_SHOWCASE: entre 1 y 3 UUID exactos devueltos por list_catalog y con hasMedia=true")
                                                .put("items", string("UUID exacto de un producto del catálogo")))
                                        .put("recipientIdentityId", string("UUID opcional de una identidad telefónica verificada cuando el cliente tiene más de un teléfono verificado")))
                                .put("required", new JSONArray().put("operationId").put("purpose"))))
                .put(function("transfer_to_human", "Solicita transferir la llamada a una persona del negocio cuando el cliente lo pida o la atención automática no pueda resolver su necesidad. El destino se obtiene de la configuración segura del negocio, nunca de argumentos del modelo.", object()))
                .put(endCall());
    }

    public static JSONObject endCall() {
        return function("end_call",
                "Termina físicamente la llamada actual. Úsala solo cuando el cliente se despida claramente, diga que no necesita nada más, pida cortar/terminar la llamada o confirme que la atención terminó. Primero despídete de forma breve y luego invoca esta herramienta. No la uses por un silencio breve.",
                object());
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
