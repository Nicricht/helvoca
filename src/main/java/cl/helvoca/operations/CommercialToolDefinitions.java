package cl.helvoca.operations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

/** Shared commercial tool schema for voice and messaging providers. */
public final class CommercialToolDefinitions {
    private CommercialToolDefinitions() {}

    public static JSONArray all() {
        JSONObject modifiers = new JSONObject()
                .put("type", "object")
                .put("description", "Modificadores estructurados del ítem, por ejemplo {remove:[\"cebolla\"], options:{size:\"grande\"}}")
                .put("additionalProperties", true);
        JSONObject itemProperties = new JSONObject()
                .put("catalogItemId", string("UUID exacto del producto o servicio devuelto por list_catalog"))
                .put("quantity", integer("Cantidad solicitada, entre 1 y 100"))
                .put("modifiers", modifiers)
                .put("notes", string("Observaciones libres adicionales del ítem"));
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
                .put("address", string("Dirección de entrega obligatoria cuando sea DELIVERY"))
                .put("contactName", string("Nombre del cliente si lo entregó"));

        JSONObject quoteOrderParams = object()
                .put("properties", orderProperties)
                .put("required", new JSONArray().put("items").put("fulfillmentType"));

        JSONObject updateOrderProperties = new JSONObject(orderProperties.toString())
                .put("operationId", string("UUID exacto del borrador devuelto por quote_order o update_order"));
        JSONObject updateOrderParams = object()
                .put("properties", updateOrderProperties)
                .put("required", new JSONArray().put("operationId").put("items").put("fulfillmentType"));

        JSONObject createOrderParams = object()
                .put("properties", new JSONObject()
                        .put("operationId", string("UUID exacto del borrador más reciente"))
                        .put("confirmationToken", string("Token exacto de la última cotización devuelta por quote_order o update_order"))
                        .put("notes", string("Notas generales finales del pedido")))
                .put("required", new JSONArray().put("operationId").put("confirmationToken"));

        JSONObject deliveryProperties = new JSONObject()
                .put("address", string("Dirección completa del despacho autónomo"))
                .put("deliveryZoneId", string("UUID opcional de la zona ya validada; el backend vuelve a resolver la cobertura"))
                .put("orderId", string("UUID opcional de un pedido existente del mismo cliente para vincularlo y verificar compra mínima"))
                .put("contactName", string("Nombre del cliente si lo entregó"))
                .put("notes", string("Instrucciones operativas del despacho"));
        JSONObject quoteDeliveryParams = object()
                .put("properties", deliveryProperties)
                .put("required", new JSONArray().put("address"));
        JSONObject updateDeliveryProperties = new JSONObject(deliveryProperties.toString())
                .put("operationId", string("UUID exacto del borrador devuelto por quote_delivery o update_delivery"));
        JSONObject updateDeliveryParams = object()
                .put("properties", updateDeliveryProperties)
                .put("required", new JSONArray().put("operationId").put("address"));
        JSONObject createDeliveryParams = object()
                .put("properties", new JSONObject()
                        .put("operationId", string("UUID exacto del borrador de despacho más reciente"))
                        .put("confirmationToken", string("Token exacto de la última versión devuelta por quote_delivery o update_delivery")))
                .put("required", new JSONArray().put("operationId").put("confirmationToken"));

        JSONObject quoteProperties = new JSONObject()
                .put("title", string("Resumen corto de lo que se debe cotizar"))
                .put("description", string("Detalle de la necesidad del cliente"))
                .put("items", itemsArray)
                .put("contactName", string("Nombre del cliente si lo entregó"));

        JSONObject quotePaymentParams = object()
                .put("properties", new JSONObject()
                        .put("targetOperationId", string("UUID exacto de la operación CONFIRMED que se quiere pagar. El backend obtiene monto y moneda; nunca envíes un monto inventado.")))
                .put("required", new JSONArray().put("targetOperationId"));
        JSONObject updatePaymentParams = object()
                .put("properties", new JSONObject()
                        .put("operationId", string("UUID exacto del borrador PAYMENT devuelto por quote_payment o update_payment"))
                        .put("targetOperationId", string("UUID exacto y vigente de la operación que se quiere pagar")))
                .put("required", new JSONArray().put("operationId").put("targetOperationId"));
        JSONObject createPaymentParams = object()
                .put("properties", new JSONObject()
                        .put("operationId", string("UUID exacto del borrador PAYMENT más reciente"))
                        .put("confirmationToken", string("Token exacto de la última versión devuelta por quote_payment o update_payment")))
                .put("required", new JSONArray().put("operationId").put("confirmationToken"));

        return new JSONArray()
                .put(function("list_catalog",
                        "Lista el catálogo universal activo del negocio con productos, servicios, precios, moneda y media comercial disponible. Cada ítem indica hasMedia y media[]. Usa los UUID exactos devueltos; nunca inventes productos, precios ni media.",
                        object()))
                .put(function("list_delivery_zones",
                        "Lista las zonas de despacho configuradas, su costo y compra mínima. No inventes cobertura ni costo de despacho.",
                        object()))
                .put(function("validate_delivery_address",
                        "Valida en backend si una dirección pertenece a una zona de despacho configurada y devuelve la zona, costo y compra mínima. Úsala antes de prometer que existe despacho a una dirección.",
                        object().put("properties", new JSONObject()
                                        .put("address", string("Dirección completa entregada por el cliente")))
                                .put("required", new JSONArray().put("address"))))
                .put(function("quote_delivery",
                        "Crea un borrador DELIVERY autónomo y devuelve operationId, revision, confirmationToken, zona y costo vigentes. No crea el despacho final. orderId es opcional y solo puede vincular un pedido del cliente actual.",
                        quoteDeliveryParams))
                .put(function("update_delivery",
                        "Reemplaza el estado del borrador DELIVERY con la dirección y datos más recientes. Cada corrección genera una revisión y confirmationToken nuevos e invalida la confirmación anterior.",
                        updateDeliveryParams))
                .put(function("create_delivery",
                        "Confirma un despacho autónomo ya cotizado. Llámala solo después de un sí explícito del cliente sobre la dirección, zona y costo más recientes. El backend vuelve a validar cobertura y costo y la confirmación es idempotente.",
                        createDeliveryParams))
                .put(function("get_delivery_status",
                        "Consulta un despacho o los despachos recientes del cliente actual. Si conoces deliveryId envíalo; si no, usa el contexto verificado del cliente o conversación.",
                        object().put("properties", new JSONObject()
                                .put("deliveryId", string("UUID opcional del despacho")))))
                .put(function("cancel_delivery",
                        "Cancela un despacho autónomo del cliente actual solo si todavía está en estado CONFIRMED. No confirmes la cancelación antes de success=true.",
                        object().put("properties", new JSONObject()
                                        .put("deliveryId", string("UUID exacto del despacho")))
                                .put("required", new JSONArray().put("deliveryId"))))
                .put(function("quote_order",
                        "Crea un borrador estructurado y calcula en backend subtotal, despacho y total usando precios actuales. Devuelve operationId, revision y confirmationToken. No crea el pedido final.",
                        quoteOrderParams))
                .put(function("update_order",
                        "Reemplaza el estado del borrador con la información más reciente del cliente y vuelve a cotizar. Usa el conjunto completo y actualizado de ítems. Cada cambio invalida el confirmationToken anterior.",
                        updateOrderParams))
                .put(function("create_order",
                        "Confirma un borrador ya cotizado. Llámala solo después de que el cliente confirme explícitamente el total más reciente, usando operationId y confirmationToken exactos de esa versión. El backend recalcula precios y cobertura y la operación es idempotente.",
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
                                .put("required", new JSONArray().put("name").put("interest"))))
                .put(function("quote_payment",
                        "Crea un borrador PAYMENT para una operación confirmada del cliente. El backend obtiene el saldo y la moneda desde la operación de origen; nunca acepta un monto decidido por la IA. Devuelve operationId, revision y confirmationToken y todavía no crea una intención en el proveedor.",
                        quotePaymentParams))
                .put(function("update_payment",
                        "Reemplaza el objetivo del borrador PAYMENT y recalcula el saldo backend-autoritativo. Cada corrección rota confirmationToken e invalida confirmaciones antiguas.",
                        updatePaymentParams))
                .put(function("create_payment",
                        "Crea la intención en el proveedor comercial configurado solo después de un sí explícito sobre el monto y moneda más recientes. Usa el último confirmationToken. Nunca pidas ni almacenes credenciales de tarjeta. Un checkoutUrl no significa pagado.",
                        createPaymentParams))
                .put(function("get_payment_status",
                        "Consulta el estado verificado de un pago del cliente actual. Solo considera el pago completado cuando status sea SUCCEEDED; REQUIRES_ACTION o PENDING todavía no significan pagado.",
                        object().put("properties", new JSONObject()
                                .put("paymentId", string("UUID opcional del pago")))))
                .put(function("cancel_payment",
                        "Solicita cancelación de una intención de pago REQUIRES_ACTION o PENDING del cliente actual. No intentes cancelar pagos SUCCEEDED ni confirmes la cancelación antes de success=true.",
                        object().put("properties", new JSONObject()
                                        .put("paymentId", string("UUID exacto del pago")))
                                .put("required", new JSONArray().put("paymentId"))));
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
            out.append("Usa list_catalog como fuente oficial de productos, servicios, precios y media comercial. No inventes ítems, precios, imágenes ni videos. ")
                    .append("Si el cliente pide ver productos por WhatsApp, elige como máximo 3 ítems con hasMedia=true. Asegura primero que el cliente esté identificado en el contexto; en voz usa find_caller y, si hace falta, register_caller. ")
                    .append("Luego crea una solicitud create_request de tipo product_showcase para obtener un operationId y usa send_whatsapp_operation con purpose PRODUCT_SHOWCASE y esos catalogItemIds exactos. ")
                    .append("La llamada y WhatsApp deben conservar ese mismo operationId.\n");
        }
        if (enabled.contains(BusinessOperationCapability.ORDER)) {
            out.append("Para pedidos: usa quote_order para crear el borrador y obtener el total real. ")
                    .append("Si el cliente corrige cantidades, productos, modificadores, retiro, despacho o dirección, usa update_order con el estado completo más reciente. ")
                    .append("Cada actualización invalida la confirmación anterior. Presenta el total vigente y solo después de un sí explícito usa create_order con el último confirmationToken. ")
                    .append("Un pedido final solo existe si create_order devuelve success=true.\n");
        }
        if (enabled.contains(BusinessOperationCapability.DELIVERY)) {
            out.append("Para un despacho autónomo: valida la dirección, usa quote_delivery y presenta zona y costo. ")
                    .append("Si cambia dirección, pedido vinculado o instrucciones, usa update_delivery con el estado completo más reciente. ")
                    .append("Cada cambio invalida la confirmación anterior; solo después de un sí explícito usa create_delivery con el último confirmationToken. ")
                    .append("Si el despacho solo forma parte de un ORDER que aún se está armando, conserva el flujo quote_order/update_order/create_order y no crees un DELIVERY separado salvo que el cliente realmente solicite una operación de despacho independiente.\n");
        }
        if (enabled.contains(BusinessOperationCapability.QUOTE)) {
            out.append("Para cotizaciones: usa create_quote. Si el backend no devuelve un monto, explica que quedó solicitada para evaluación; nunca inventes el precio.\n");
        }
        if (enabled.contains(BusinessOperationCapability.LEAD)) {
            out.append("Para potenciales clientes que requieren seguimiento comercial usa create_lead y conserva únicamente datos entregados por la persona.\n");
        }
        if (enabled.contains(BusinessOperationCapability.PAYMENT)) {
            out.append("Para pagos: nunca decidas el monto ni la moneda. Usa quote_payment con la operación CONFIRMED que el cliente quiere pagar. ")
                    .append("Presenta el monto backend-autoritativo y solo después de un sí explícito usa create_payment con el último confirmationToken. ")
                    .append("Si cambia la operación objetivo usa update_payment; la confirmación anterior queda inválida. ")
                    .append("Nunca solicites números de tarjeta, CVV ni credenciales de pago. Un checkoutUrl solo inicia el pago: considera pagado únicamente un status SUCCEEDED verificado por el proveedor.\n");
        }
        return out.toString();
    }

    private static JSONObject function(String name, String description, JSONObject parameters) {
        return new JSONObject().put("type", "function").put("name", name)
                .put("description", description).put("parameters", parameters);
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
