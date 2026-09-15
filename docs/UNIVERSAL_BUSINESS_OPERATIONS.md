# Universal Business Operations

## Objetivo

Helvoca separa el cerebro conversacional de las operaciones que cada tenant puede ejecutar. Un restaurante, taller, clínica, tienda, courier o inmobiliaria usa el mismo dominio; los verticales son presets de configuración y no ramas del tipo `if restaurant`, `if clinic`, etc.

El LLM interpreta lenguaje y reúne datos. El backend conserva autoridad sobre permisos, catálogo, precios, cobertura, estados, confirmaciones, importes, moneda y persistencia. En pagos, la IA tampoco maneja credenciales de tarjeta ni decide si un pago fue exitoso.

La dirección del producto es:

`Conversation -> structured state -> policy -> universal operation -> typed projection -> provider/integration -> audit`

## Autorización por tenant

`BusinessOperationCapability` es una capa de configuración/preset. La autorización efectiva de herramientas tiene una única fuente de verdad runtime: `AiAgent` + `AiCapability`, persistida en `ai_agent_capability`.

Presets comerciales actuales:

- `CATALOG`: catálogo universal.
- `ORDER`: cotizar, actualizar, confirmar, consultar y cancelar pedidos.
- `DELIVERY`: validar cobertura y ejecutar despachos autónomos versionados.
- `QUOTE`: cotizaciones estructuradas.
- `LEAD`: captura estructurada de potenciales clientes.
- `PAYMENT`: preparar, confirmar, consultar y cancelar intenciones de pago usando un adapter comercial configurado para el tenant.

Dependencias normalizadas:

- `ORDER` implica `CATALOG`.
- `QUOTE` implica `CATALOG`.
- `DELIVERY` es autónomo y no obliga a habilitar `ORDER` ni `CATALOG`.
- `PAYMENT` es autónomo como capability. Requiere una operación pagable confirmada y un provider adapter configurado para poder materializar una intención de pago.

Las capacidades comerciales son opt-in. V26 agregó las herramientas transaccionales de DELIVERY solo a tenants que ya tenían las capacidades de delivery previas. V27 **no concede PAYMENT a ningún tenant existente**: debe habilitarse explícitamente y el provider se resuelve fail-closed.

API de configuración:

- `GET /api/v1/business-capabilities`
- `PUT /api/v1/business-capabilities`

## Catálogo universal

`catalog_item` representa productos y servicios sin asumir que cada ítem debe convertirse en una reserva.

Tipos:

- `SERVICE`
- `PRODUCT`

Cada ítem puede contener nombre, descripción, precio, moneda, duración opcional y metadata. Los servicios legacy se sincronizan con el catálogo sin romper el dominio de booking existente.

API:

- `GET /api/v1/catalog`
- `POST /api/v1/catalog`
- `PUT /api/v1/catalog/{id}`
- `DELETE /api/v1/catalog/{id}`

## Business Operation Engine

Desde V23 existe `business_operation` como envolvente universal de una operación conversacional.

Tipos actuales:

- `ORDER`
- `QUOTE`
- `LEAD`
- `DELIVERY`
- `REQUEST`
- `BOOKING`
- `PAYMENT`

Estados universales:

- `DRAFT`
- `AWAITING_CONFIRMATION`
- `CONFIRMED`
- `CANCELLED`
- `EXPIRED`
- `FAILED`

La envolvente conserva tenant, cliente, referencia del canal, origen, revisión, token de confirmación cuando corresponde, datos de contacto, valores monetarios y `metadata_json` estructurada.

`business_operation_item` conserva snapshots estructurados de ítems de catálogo, cantidades, precios y modificadores.

### Proyecciones tipadas

La universalización es evolutiva, no un reemplazo destructivo. Las tablas especializadas siguen existiendo como proyecciones compatibles:

- `business_order`
- `business_quote`
- `business_lead`
- `business_request`
- `booking`
- `business_delivery`
- `business_payment`

V23 convirtió ORDER en una proyección 1:1 de `business_operation`.

V24 hizo lo mismo con QUOTE, LEAD y REQUEST y migró los registros existentes.

V25 incorporó BOOKING. Cada `booking` tiene un `operation_id` único y no nulo; las reservas históricas fueron migradas y las nuevas reservas se sincronizan dentro de la misma transacción.

V26 agregó `business_delivery` como proyección 1:1 de una operación `DELIVERY` autónoma. Los ORDER existentes con `fulfillment_type=DELIVERY` siguen siendo ORDER y no se duplican como entregas autónomas.

V27 agrega `business_payment` como proyección 1:1 de una operación `PAYMENT`. El pago apunta mediante `target_operation_id` a la operación comercial que se está pagando, conserva snapshot monetario, provider, referencia externa e idempotency key.

## Conversation State Engine

`conversation_operation_state` evita que el estado operativo dependa únicamente del historial textual del modelo.

La clave lógica es:

`business_id + channel + source_reference_id`

El estado incluye operación activa, JSON estructurado, revisión incremental y timestamps. Una corrección nueva reemplaza el valor anterior incompatible.

Ejemplos:

- ORDER reemplaza cantidades, dirección o fulfillment cuando el cliente corrige el pedido.
- DELIVERY reemplaza dirección, zona resuelta e instrucciones y rota su confirmación.
- BOOKING sincroniza creación, reprogramación y cancelación desde voz o WhatsApp.
- PAYMENT conserva operación objetivo, monto backend-autoritativo, moneda, confirmación vigente, `paymentId`, provider, `checkoutUrl`, `paymentStatus` y `paymentPending` cuando corresponda.

No se almacenan credenciales de tarjeta, CVV ni secretos de pago en Conversation State.

## Policy Engine

`OperationPolicyService` centraliza la política base de confirmación y revisión humana.

Política actual:

- `ORDER`, `DELIVERY`, `BOOKING` y `PAYMENT`: confirmación explícita.
- `QUOTE`, `LEAD` y `REQUEST`: no requieren confirmación transaccional adicional.
- todas pueden derivar a revisión humana ante fallo.

La política no la decide el LLM. Esta versión sigue siendo backend centralizado; todavía no existe una matriz configurable por tenant en base de datos.

BOOKING conserva por ahora sus guardas conversacionales y de certificación existentes. Declararlo `EXPLICIT` no significa que ya use exactamente el token/versionado de ORDER, DELIVERY o PAYMENT.

## Herramientas comerciales

Cuando el `AiAgent` del tenant las autoriza, voz y WhatsApp pueden publicar dinámicamente:

- `list_catalog`
- `list_delivery_zones`
- `validate_delivery_address`
- `quote_delivery`
- `update_delivery`
- `create_delivery`
- `get_delivery_status`
- `cancel_delivery`
- `quote_order`
- `update_order`
- `create_order`
- `get_order_status`
- `cancel_order`
- `create_quote`
- `create_lead`
- `quote_payment`
- `update_payment`
- `create_payment`
- `get_payment_status`
- `cancel_payment`

El servicio comercial vuelve a validar la capability exacta en runtime y falla cerrado aunque un adapter publique accidentalmente una herramienta no autorizada.

## ORDER

ORDER utiliza un flujo versionado:

1. `quote_order` crea un `business_operation` en `AWAITING_CONFIRMATION`.
2. El backend calcula precios, cantidades, moneda, cobertura, mínimo y despacho.
3. Devuelve `operationId`, `revision` y `confirmationToken`.
4. `update_order` reemplaza el estado completo más reciente, incrementa revisión y rota token.
5. El token anterior queda inválido.
6. Solo después de una confirmación explícita se invoca `create_order` con la última versión.
7. El backend recalcula antes de materializar.
8. Si cambió el total responde `ORDER_TOTAL_CHANGED`, rota revisión/token y no crea el pedido.
9. Si todo coincide crea una única proyección `business_order` y snapshots de líneas.
10. Un retry secuencial devuelve el mismo pedido como replay idempotente.

El token protege la versión del borrador, no demuestra criptográficamente que una persona dijo “sí”. La capa conversacional debe solicitar confirmación explícita antes de ejecutar la mutación final.

## BOOKING

BOOKING conserva la lógica existente para disponibilidad, solapamientos, ownership, horarios y guardas de certificación, pero `booking` es una proyección obligatoria de `business_operation`.

Las creaciones, reprogramaciones y cancelaciones sincronizan la operación universal y Conversation State. BOOKING aún no usa un draft tokenizado idéntico a ORDER/DELIVERY/PAYMENT.

## DELIVERY

DELIVERY autónomo utiliza un flujo estructurado:

1. `validate_delivery_address` comprueba cobertura sin crear una operación.
2. `quote_delivery` crea un borrador `DELIVERY` en `AWAITING_CONFIRMATION`.
3. La dirección se vuelve a resolver siempre en backend.
4. `update_delivery` reemplaza el estado vigente, incrementa revisión y rota token.
5. `create_delivery` solo debe ejecutarse tras confirmación explícita.
6. El backend recalcula cobertura/costo antes de materializar.
7. Si zona, costo o moneda cambian devuelve `DELIVERY_TERMS_CHANGED` y exige nueva confirmación.
8. Si siguen vigentes crea una única proyección `business_delivery`.
9. Un retry secuencial devuelve el mismo despacho como replay idempotente.

Un DELIVERY puede enlazarse opcionalmente a un `business_order` del mismo tenant y cliente. El costo de delivery es un hecho operativo y no implica por sí mismo un segundo cobro.

La proyección tiene estados `CONFIRMED`, `IN_TRANSIT`, `DELIVERED` y `CANCELLED`.

## PAYMENT

V27 introduce la base universal de PAYMENT sin acoplar el dominio a Mercado Pago, Stripe, WebPay u otro proveedor.

### Regla monetaria

El LLM **no envía ni decide `amount` o `currency`**. Las herramientas reciben `targetOperationId`; el backend carga la operación confirmada del mismo tenant/cliente y obtiene el monto desde `business_operation.total`.

Si existen pagos `SUCCEEDED` previos contra la misma operación, el backend calcula el saldo restante. Si el saldo ya es cero, responde `PAYMENT_ALREADY_SATISFIED`.

Solo pueden pagarse operaciones:

- del mismo tenant;
- verificadas como pertenecientes al cliente/conversación actual;
- en estado universal `CONFIRMED`;
- con `total > 0` y moneda válida;
- que no sean a su vez una operación PAYMENT.

### Flujo

1. `quote_payment(targetOperationId)` calcula el saldo real y crea un PAYMENT en `AWAITING_CONFIRMATION`.
2. Devuelve `operationId`, `revision`, `confirmationToken`, `amount` y `currency`.
3. Si cambia el objetivo, `update_payment` recalcula y rota el token.
4. `create_payment` solo debe llamarse después de un sí explícito sobre el monto y moneda más recientes.
5. Antes de contactar un provider se recalcula el target. Si cambió saldo, moneda u objetivo, responde `PAYMENT_TERMS_CHANGED` con nueva revisión/token y no crea ninguna intención externa.
6. El provider se resuelve por tenant mediante `PaymentProviderRegistry`. Cero o múltiples adapters compatibles fallan cerrado con `PAYMENT_PROVIDER_UNAVAILABLE`.
7. El adapter recibe una idempotency key estable: `payment-operation:<operationId>`.
8. Solo después de una respuesta válida del adapter se materializa `business_payment`.
9. Un retry secuencial devuelve la misma proyección como `idempotentReplay=true` y no vuelve a invocar el provider.
10. `get_payment_status` puede refrescar estados no terminales usando el adapter y conserva el último estado verificado si el provider temporalmente no responde.
11. `cancel_payment` solo intenta cancelación automática para `REQUIRES_ACTION` o `PENDING`; estados terminales requieren semántica específica o revisión humana.

Estados tipados:

- `REQUIRES_ACTION`
- `PENDING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `EXPIRED`
- `REFUNDED`

Un `checkoutUrl` **no significa que exista un pago exitoso**. Para lógica comercial, el pago solo se considera completado cuando el estado verificado sea `SUCCEEDED`.

### Provider adapters

`PaymentProviderAdapter` es la frontera externa. Una implementación concreta debe encargarse de:

- credenciales comerciales por tenant;
- creación idempotente de intentos;
- consulta de estado;
- cancelación cuando el proveedor la permita;
- validación criptográfica de webhooks/eventos;
- traducción de estados externos a los estados universales.

Las credenciales `MERCADOPAGO_*` existentes pertenecen al billing de suscripciones de Helvoca y **no se reutilizan** para cobrar a los clientes de los tenants.

V27 contiene la arquitectura universal y persiste los estados, pero no habilita un provider comercial real por defecto ni realiza cobros reales durante despliegue o pruebas.

### Concurrencia

V27 incorpora idempotency key estable y constraints únicas para operación, idempotency key y referencia externa del provider. Esto endurece retries y duplicados, pero la garantía exactamente-una-vez bajo dos confirmaciones verdaderamente simultáneas requiere un hardening posterior con locking y/o recuperación explícita de unique violations.

## QUOTE

`create_quote` pasa por `UniversalOperationWorkflowService`. Si se entregan ítems, el backend consulta el catálogo del tenant, calcula cantidades/precios/moneda y persiste snapshots. Si no hay un precio determinista, puede quedar `REQUESTED` sin inventar un monto.

## LEAD

`create_lead` crea una operación universal y su proyección `business_lead`. Nombre e interés son obligatorios; correo, presupuesto y notas son opcionales.

## REQUEST

`create_request`, tanto manual como desde IA, crea una operación universal y la proyección `business_request`. Voz conserva `call_id` para llamadas reales; WhatsApp usa `source_reference_id` y no mezcla IDs de conversación con la FK de llamadas.

## Consola/API operativa

Actualmente existen APIs operativas para pedidos, deliveries, cotizaciones y leads. PAYMENT en V27 queda disponible a través del dominio/herramientas compartidas y su proyección; la consola administrativa específica de pagos puede añadirse junto con el primer provider adapter comercial.

## Ejemplos de presets

- Restaurante: `CATALOG + ORDER + DELIVERY`, y opcionalmente `PAYMENT`.
- Tienda: `CATALOG + ORDER + DELIVERY + PAYMENT` cuando acepta cobro remoto.
- Courier/logística: `DELIVERY`, con `PAYMENT` opcional si cobra el servicio en línea.
- Taller: `CATALOG + QUOTE + BOOKING`, con `PAYMENT` para anticipos cuando la operación pagable tenga monto definitivo.
- Inmobiliaria: `CATALOG + LEAD + BOOKING/REQUEST`.
- Clínica/veterinaria: `CATALOG + BOOKING + REQUEST`, y `PAYMENT` solo cuando su política comercial lo permita.

Estos nombres sirven como configuración inicial. No introducen lógica de dominio por industria.

## Principio de arquitectura

El backend define hechos y transacciones. La IA interpreta lenguaje. Los adapters de voz, mensajería y proveedores externos deben permanecer delgados y compartir el mismo dominio universal.
