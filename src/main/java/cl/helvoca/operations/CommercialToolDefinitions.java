package cl.helvoca.operations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

/** Shared commercial tool schema for voice and messaging providers. */
public final class CommercialToolDefinitions {
    private CommercialToolDefinitions() {}

    public static JSONArray all() {
        JSONObject itemProperties = new JSONObject()
                .put("catalogItemId", string("UUID exacto del producto o servicio devuelto por list_catalog"))
                .put("quantity", integer("Cantidad solicitada, entre 1 y 100"))
                .put("notes", string("Modificadores u observaciones del ítem, por ejemplo sin cebolla"));
        JSONObject itemSchema = object()
                .put("properties", itemProperties)
                .put("required", new JSONArray().put("catalogItemId").put("quantity"));
        JSONObject itemsArray = new JSONObject()
                .put("type", "array")
                .put("items", itemSchema);

        JSONObject orderProperties = new JSONObject()
                .put("items", itemsArray)
                .put("fulfillmentType", string("PICKUP o DELIVERY"))
                .put("deliveryZoneId", string("UUID opcional devuelto por validate_delivery_address; el backend vuelve a comprobar que coincida con la dirección"))
                .put("address", string("Dirección de entrega obligatoria cuando sea DELIVERY"));

        JSONObject quoteOrderParams = object()
                .put("properties", orderProperties)
                .put("required", new JSONArray().put("items").put("fulfillmentType"));

        JSONObject createOrderProperties = new JSONObject(orderProperties.toString())
                .put("expectedTotal", number("Total exacto previamente devuelto por quote_order y confirmado por el cliente"))
                .put("contactName", string("Nombre del cliente si lo entregó"))
                .put("notes", string("Notas generales del pedido"));
        JSONObject createOrderParams = object()
                .put("properties", createOrderProperties)
                .put("required", new JSONArray().put("items").put("fulfillmentType").put("expectedTotal"));

        JSONObject quoteProperties = new JSONObject()
                .put("title", string("Resumen corto de lo que se debe cotizar"))
                .put("description", string("Detalle de la necesidad del cliente"))
                .put("items", itemsArray)
                .put("contactName", string("Nombre del cliente si lo entregó"));

        return new JSONArray()
                .put(function("list_catalog",
                        "Lista el catálogo universal activo del negocio con productos y servicios, precios y moneda. Úsala antes de recomendar, cotizar o armar un pedido.",
                        object()))
                .put(function("list_delivery_zones",
                        "Lista las zonas de despacho configuradas, su costo y compra mínima. No inventes cobertura ni costo de despacho.",
                        object()))
                .put(function("validate_delivery_address",
                        "Valida en backend si una dirección pertenece a una zona de despacho configurada y devuelve la zona, costo y compra mínima. Úsala antes de prometer que existe despacho a una dirección.",
                        object().put("properties", new JSONObject()
                                        .put("address", string("Dirección completa entregada por el cliente")))
                                .put("required", new JSONArray().put("address"))))
                .put(function("quote_order",
                        "Calcula en backend el subtotal, despacho y total de un pedido usando precios actuales del catálogo. Para DELIVERY requiere una dirección cubierta; el backend resuelve y valida la zona. No confirma ni crea el pedido.",
                        quoteOrderParams))
                .put(function("create_order",
                        "Crea un pedido real y confirmado. Debes llamar quote_order primero y solo usar create_order después de que el cliente confirme el total exacto. El backend recalcula precios y cobertura y rechaza totales desactualizados o inventados.",
                        createOrderParams))
                .put(function("get_order_status",
                        "Consulta uno o los pedidos recientes del cliente actual. Si conoces un orderId puedes enviarlo; si no, devuelve los pedidos recientes asociados al cliente o teléfono verificado.",
                        object().put("properties", new JSONObject()
                                .put("orderId", string("UUID opcional del pedido")))))
                .put(function("cancel_order",
                        "Cancela un pedido del cliente actual cuando todavía admite cancelación. Nunca canceles pedidos de otro cliente ni confirmes la cancelación antes de success=true.",
                        object().put("properties", new JSONObject()
                                        .put("orderId", string("UUID exacto del pedido")))
                                .put("required", new JSONArray().put("orderId"))))
                .put(function("create_quote",
                        "Registra una cotización estructurada. Si se entregan ítems del catálogo, el backend calcula su monto. Si la necesidad requiere evaluación humana, la cotización queda REQUESTED sin inventar un precio final.",
                        object().put("properties", quoteProperties)
                                .put("required", new JSONArray().put("title"))))
                .put(function("create_lead",
                        "Registra un lead comercial estructurado usando el teléfono verificado de la conversación. Úsala cuando el objetivo sea captar y dar seguimiento a un potencial cliente, no para reemplazar un pedido o una reserva.",
                        object().put("properties", new JSONObject()
                                        .put("name", string("Nombre del interesado"))
                                        .put("email", string("Correo opcional"))
                                        .put("interest", string("Qué producto, servicio o solución le interesa"))
                                        .put("budget", number("Presupuesto opcional informado por el cliente"))
                                        .put("notes", string("Contexto adicional útil para seguimiento")))
                                .put("required", new JSONArray().put("name").put("interest"))));
    }

    public static JSONArray allowed(Set<String> allowedToolNames) {
        Set<String> allowed = allowedToolNames == null ? Set.of() : allowedToolNames;
        JSONArray out = new JSONArray();
        JSONArray all = all();
        for (int i = 0; i < all.length(); i++) {
            JSONObject definition = all.getJSONObject(i);
            if (allowed.contains(definition.getString("name"))) out.put(definition);
        }
        return out;
    }

    public static String instructions(Set<BusinessOperationCapability> enabled) {
        if (enabled == null || enabled.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\nCAPACIDADES COMERCIALES ACTIVAS DEL NEGOCIO: ")
                .append(enabled).append(".\n");
        if (enabled.contains(BusinessOperationCapability.CATALOG)) {
            out.append("Usa list_catalog como fuente oficial de productos, servicios y precios comerciales. ")
                    .append("No inventes ítems ni precios.\n");
        }
        if (enabled.contains(BusinessOperationCapability.ORDER)) {
            out.append("Para pedidos: comprende los ítems y modificadores, usa quote_order para obtener el total real, ")
                    .append("repítelo al cliente y crea el pedido únicamente después de una confirmación explícita mediante create_order. ")
                    .append("Un pedido solo existe si create_order devuelve success=true.\n");
        }
        if (enabled.contains(BusinessOperationCapability.DELIVERY)) {
            out.append("Para despacho: pide la dirección exacta y usa validate_delivery_address antes de prometer cobertura. ")
                    .append("El backend vuelve a validar la dirección al cotizar y crear el pedido; nunca elijas cobertura o costo por intuición.\n");
        }
        if (enabled.contains(BusinessOperationCapability.QUOTE)) {
            out.append("Para cotizaciones: usa create_quote. Si el backend no devuelve un monto, explica que quedó solicitada para evaluación; nunca inventes el precio.\n");
        }
        if (enabled.contains(BusinessOperationCapability.LEAD)) {
            out.append("Para potenciales clientes que requieren seguimiento comercial usa create_lead y conserva únicamente datos entregados por la persona.\n");
        }
        return out.toString();
    }

    private static JSONObject function(String name, String description, JSONObject parameters) {
        return new JSONObject()
                .put("type", "function")
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters);
    }

    private static JSONObject object() {
        return new JSONObject().put("type", "object").put("properties", new JSONObject());
    }

    private static JSONObject string(String description) {
        return new JSONObject().put("type", "string").put("description", description);
    }

    private static JSONObject number(String description) {
        return new JSONObject().put("type", "number").put("description", description);
    }

    private static JSONObject integer(String description) {
        return new JSONObject().put("type", "integer").put("description", description);
    }
}
