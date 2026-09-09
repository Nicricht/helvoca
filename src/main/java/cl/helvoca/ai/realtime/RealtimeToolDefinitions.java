package cl.helvoca.ai.realtime;

import cl.helvoca.agent.AiCapability;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

public final class RealtimeToolDefinitions {
    private RealtimeToolDefinitions() {}

    public static JSONArray all() { return enabled(Set.of(AiCapability.values())); }

    public static JSONArray enabled(Set<AiCapability> capabilities) {
        Set<AiCapability> allowed = capabilities == null ? Set.of() : capabilities;
        JSONArray tools = new JSONArray();
        addIf(tools, allowed, AiCapability.GET_BUSINESS_INFORMATION,
                function("get_business_information", "Obtiene nombre, idioma, zona horaria y horario del negocio.", object()));
        addIf(tools, allowed, AiCapability.LIST_SERVICES,
                function("list_services", "Lista servicios activos, precios y duración.", object()));
        addIf(tools, allowed, AiCapability.SEARCH_KNOWLEDGE,
                function("search_knowledge", "Busca información oficial configurada por el negocio.",
                        object().put("properties", new JSONObject().put("query", string("Texto a buscar")))
                                .put("required", new JSONArray().put("query"))));
        addIf(tools, allowed, AiCapability.FIND_CALLER,
                function("find_caller", "Obtiene el cliente asociado al teléfono de esta llamada.", object()));
        addIf(tools, allowed, AiCapability.REGISTER_CALLER,
                function("register_caller", "Registra o actualiza al cliente de esta llamada. El teléfono se toma del contexto verificado y nunca de argumentos del modelo.",
                        object().put("properties", new JSONObject()
                                        .put("name", string("Nombre del cliente"))
                                        .put("email", string("Correo opcional")))
                                .put("required", new JSONArray().put("name"))));
        addIf(tools, allowed, AiCapability.CHECK_BOOKING_AVAILABILITY,
                function("check_booking_availability", "Comprueba disponibilidad real y horario del negocio antes de prometer una reserva.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID del servicio"))
                                        .put("startAt", string("Fecha y hora ISO-8601 con zona u offset")))
                                .put("required", new JSONArray().put("serviceId").put("startAt"))));
        addIf(tools, allowed, AiCapability.CREATE_BOOKING,
                function("create_booking", "Crea una reserva real. Solo se considera confirmada cuando esta herramienta devuelve success=true.",
                        object().put("properties", new JSONObject()
                                        .put("serviceId", string("UUID del servicio"))
                                        .put("startAt", string("Fecha y hora ISO-8601 con zona u offset"))
                                        .put("notes", string("Notas opcionales")))
                                .put("required", new JSONArray().put("serviceId").put("startAt"))));
        return tools;
    }

    private static void addIf(JSONArray out, Set<AiCapability> capabilities, AiCapability capability, JSONObject definition) {
        if (capabilities.contains(capability)) out.put(definition);
    }

    private static JSONObject function(String name, String description, JSONObject parameters) {
        return new JSONObject()
                .put("type", "function")
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters);
    }

    private static JSONObject object() {
        return new JSONObject().put("type", "object").put("additionalProperties", false).put("properties", new JSONObject());
    }

    private static JSONObject string(String description) {
        return new JSONObject().put("type", "string").put("description", description);
    }
}
