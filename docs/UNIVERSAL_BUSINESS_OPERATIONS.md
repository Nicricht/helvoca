# Universal Business Operations

## Objetivo

Helvoca separa el cerebro conversacional de las operaciones que cada tenant puede ejecutar. Un restaurante, taller, clínica, tienda o inmobiliaria usa el mismo dominio; los verticales son presets de configuración, no ramas `if restaurant`, `if clinic`, etc.

El LLM interpreta lenguaje y reúne datos. El backend conserva autoridad sobre permisos, catálogo, precios, cobertura, estados, confirmaciones y persistencia.

## Autorización por tenant

`BusinessOperationCapability` es una capa de configuración/preset. La autorización efectiva de herramientas tiene una única fuente de verdad runtime: `AiAgent` + `AiCapability`, persistida en `ai_agent_capability`.

Presets comerciales actuales:

- `CATALOG`: catálogo universal.
- `ORDER`: cotizar, actualizar, confirmar, consultar y cancelar pedidos.
- `DELIVERY`: validar cobertura y ejecutar despachos autónomos versionados.
- `QUOTE`: cotizaciones estructuradas.
- `LEAD`: captura estructurada de potenciales clientes.

Dependencias normalizadas:

- `ORDER` implica `CATALOG`.
- `QUOTE` implica `CATALOG`.
- `DELIVERY` es autónomo y no obliga a habilitar `ORDER` ni `CATALOG`.

Las capacidades comerciales son opt-in. V26 agrega herramientas transaccionales de DELIVERY únicamente a tenants que ya tenían habilitadas las dos capacidades de delivery previas (`LIST_DELIVERY_ZONES` y `VALIDATE_DELIVERY_ADDRESS`). Los tenants sin delivery permanecen intactos.

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

La universalización es evolutiva, no un reemplazo destructivo. Las tablas operativas especializadas siguen existiendo como proyecciones compatibles:

- `business_order`
- `business_quote`
- `business_lead`
- `business_request`
- `booking`
- `business_delivery`

V23 convirtió ORDER en una proyección 1:1 de `business_operation`.

V24 hizo lo mismo con QUOTE, LEAD y REQUEST y migró los registros existentes.

V25 incorpora BOOKING. Cada `booking` tiene un `operation_id` único y no nulo. Los registros históricos se migran reutilizando el UUID de la reserva como UUID de operación. Las nuevas reservas obtienen su operación universal automáticamente en la misma transacción.

V26 agrega `business_delivery` como proyección 1:1 de una operación `DELIVERY` autónoma. Los ORDER existentes con `fulfillment_type=DELIVERY` siguen siendo operaciones ORDER y no se duplican como entregas autónomas.

## Conversation State Engine

V24 agrega `conversation_operation_state` para que el estado operativo no dependa únicamente del historial textual del modelo.

La clave lógica es:

`business_id + channel + source_reference_id`

El estado incluye:

- operación activa;
- JSON estructurado de conversación;
- revisión incremental;
- timestamps.

Semántica principal: la corrección más reciente reemplaza el valor anterior incompatible. Por ejemplo, si el cliente cambia dirección, cantidad u horario, el nuevo valor sustituye al anterior y aumenta la revisión correspondiente.

El estado está aislado por tenant y canal. Un patch que no trae una nueva operación activa conserva la operación actual en vez de borrarla accidentalmente.

Para REQUEST, `business_request.call_id` sigue reservado a llamadas reales. WhatsApp y otros canales conservan su referencia en `business_operation.source_reference_id`.

Para BOOKING, voz y WhatsApp enlazan la operación con el `callId` o `conversationId` real y actualizan el estado estructurado después de una creación, reprogramación o cancelación exitosa.

Para DELIVERY autónomo, `quote_delivery`, `update_delivery`, `create_delivery` y `cancel_delivery` actualizan el estado estructurado con operación, revisión, dirección, zona, costo y confirmación vigente.

## Policy Engine

`OperationPolicyService` centraliza la política base de confirmación y revisión humana.

Política actual:

- `ORDER`, `DELIVERY` y `BOOKING`: confirmación explícita.
- `QUOTE`, `LEAD` y `REQUEST`: no requieren confirmación transaccional adicional.
- todas pueden derivar a revisión humana ante fallo.

La política no la decide el LLM. Esta primera versión es una política backend centralizada; todavía no es una matriz configurable por tenant en base de datos.

BOOKING conserva por ahora sus guardas conversacionales y de certificación existentes. Declarar `BOOKING` como `EXPLICIT` no equivale a afirmar que ya usa el token/versionado de ORDER o DELIVERY.

## Herramientas comerciales

Cuando el `AiAgent` del tenant las autoriza, voz y WhatsApp pueden publicar:

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

El servicio comercial vuelve a validar la capability exacta en runtime y falla cerrado aunque un adapter futuro publique accidentalmente una herramienta no autorizada.

## ORDER

ORDER utiliza un flujo estructurado:

1. `quote_order` crea un `business_operation` en `AWAITING_CONFIRMATION`.
2. El backend calcula precios, cantidades, moneda, cobertura, mínimo y despacho.
3. Devuelve `operationId`, `revision` y `confirmationToken`.
4. Si el cliente corrige el pedido, `update_order` reemplaza el estado completo del borrador, incrementa revisión y genera un token nuevo.
5. El token anterior queda inválido.
6. Solo después de una confirmación explícita en la conversación se llama `create_order` con `operationId + confirmationToken`.
7. El backend vuelve a recalcular usando el estado actual.
8. Si precio o total cambió, responde `ORDER_TOTAL_CHANGED`, genera nueva revisión/token y no materializa el pedido.
9. Si todo coincide, crea una única proyección `business_order` y snapshots de líneas.
10. Un retry secuencial devuelve el mismo pedido como replay idempotente.

El token/versionado garantiza que solo la versión más reciente del borrador pueda materializarse. No pretende demostrar criptográficamente que una persona pronunció una palabra concreta; la capa conversacional debe solicitar y observar confirmación explícita antes de invocar `create_order`.

La conversación estructurada también se actualiza cuando `ORDER_TOTAL_CHANGED` devuelve una nueva cotización, aunque la respuesta de la herramienta tenga `success=false`.

## BOOKING

V25 mantiene la lógica de booking existente para disponibilidad, solapamientos, propiedad del cliente, horarios y guardas de certificación, pero convierte `booking` en una proyección obligatoria de `business_operation`.

La invariancia se protege en PostgreSQL:

1. antes de insertar una reserva, un trigger garantiza un `operation_id` y crea la operación `BOOKING` dentro de la misma transacción;
2. una reprogramación sincroniza `startAt`, `endAt`, metadata y aumenta la revisión universal;
3. una cancelación sincroniza el estado universal a `CANCELLED`;
4. voz y WhatsApp agregan su referencia real de conversación y proyectan la mutación al Conversation State Engine;
5. si esa sincronización de canal falla, el wrapper transaccional marca la mutación para rollback.

Las respuestas exitosas de mutaciones de BOOKING en los wrappers universales pueden incluir `operationId` y `operationRevision` además del `bookingId` legacy.

Esto no reemplaza aún el flujo de BOOKING por un draft tokenizado estilo ORDER. La disponibilidad y confirmación conversacional existente siguen siendo la autoridad de ejecución.

## DELIVERY

`delivery_zone` almacena nombre, términos de cobertura, costo de despacho, mínimo opcional y estado. `DeliveryCoverageService` es el resolvedor backend común de cobertura para las herramientas autónomas.

DELIVERY autónomo utiliza un flujo estructurado:

1. `validate_delivery_address` comprueba cobertura sin crear una operación.
2. `quote_delivery` crea un `business_operation` tipo `DELIVERY` en `AWAITING_CONFIRMATION` y devuelve `operationId`, `revision`, `confirmationToken`, zona y costo.
3. La dirección siempre se vuelve a resolver en backend. Un `deliveryZoneId` enviado por el modelo nunca sustituye esa resolución.
4. Si el usuario corrige dirección, pedido vinculado o instrucciones, `update_delivery` reemplaza el estado vigente, incrementa revisión y genera un token nuevo.
5. El token anterior queda inválido.
6. `create_delivery` solo debe invocarse después de confirmación explícita de las condiciones más recientes.
7. Antes de materializar, el backend vuelve a resolver cobertura y costo. Si zona, costo o moneda cambian devuelve `DELIVERY_TERMS_CHANGED`, renueva revisión/token y no crea `business_delivery`.
8. Si las condiciones siguen vigentes, crea una única proyección `business_delivery`.
9. Un retry secuencial de la confirmación devuelve el mismo despacho como replay idempotente.
10. `get_delivery_status` consulta el despacho del cliente actual y `cancel_delivery` solo permite cancelar mientras siga `CONFIRMED`.

Un DELIVERY puede enlazarse opcionalmente a un `business_order` del mismo tenant y cliente. Si existe `minimum_order`, el backend puede verificarla usando el subtotal persistido del pedido enlazado. Sin `orderId`, el mínimo se reporta como información pero no se confía en un subtotal enviado por el LLM.

La proyección `business_delivery` tiene estados operativos `CONFIRMED`, `IN_TRANSIT`, `DELIVERED` y `CANCELLED`. La API administrativa controla transiciones válidas y sincroniza `projectionStatus` y revisión en `business_operation`.

El costo de DELIVERY es un hecho operativo de cobertura; V26 no ejecuta pagos ni cobra dos veces un despacho ya incluido en un ORDER. PAYMENT sigue siendo un dominio futuro.

Al igual que ORDER, el token/versionado protege la versión del borrador que se materializa, pero no constituye prueba semántica o criptográfica de que el humano dijo “sí”. La capa conversacional sigue siendo responsable de solicitar confirmación explícita antes de llamar `create_delivery`.

## QUOTE

`create_quote` pasa por `UniversalOperationWorkflowService`.

Si se entregan ítems:

- el backend consulta solo el catálogo del tenant;
- rechaza ítems inactivos o ajenos;
- calcula cantidad, precio y moneda en backend;
- persiste snapshots en `business_operation_item`;
- crea la proyección `business_quote` enlazada al `operationId`.

Si no hay precio determinista, la proyección puede quedar `REQUESTED` sin inventar un monto.

## LEAD

`create_lead` crea primero una operación universal y luego su proyección `business_lead`. Nombre e interés son obligatorios; correo, presupuesto y notas son opcionales. El presupuesto no puede ser negativo.

## REQUEST

`create_request`, tanto manual como desde IA, crea primero una operación universal y después la proyección `business_request`.

Voz utiliza el `call_id` legacy además de `source_reference_id`. WhatsApp utiliza el ID de conversación únicamente como `source_reference_id`, evitando mezclar IDs de conversación con la FK de llamadas.

Los cambios administrativos de estado de REQUEST también sincronizan el estado y revisión de su operación universal.

## Consola/API operativa

- `GET /api/v1/commercial/orders`
- `PATCH /api/v1/commercial/orders/{id}/status`
- `GET /api/v1/commercial/deliveries`
- `PATCH /api/v1/commercial/deliveries/{id}/status`
- `GET /api/v1/commercial/quotes`
- `GET /api/v1/commercial/leads`

Las consultas operativas requieren `BUSINESS_ADMIN` u `OPERATOR`. Las mutaciones de estado requieren `BUSINESS_ADMIN`.

## Ejemplos de presets

- Restaurante: `CATALOG + ORDER + DELIVERY`.
- Tienda: `CATALOG + ORDER + DELIVERY` cuando ofrece despacho.
- Courier o logística: `DELIVERY` sin necesidad de `ORDER`.
- Taller: `CATALOG + QUOTE + BOOKING`.
- Inmobiliaria: `CATALOG + LEAD + BOOKING/REQUEST`.
- Clínica o veterinaria: `CATALOG + BOOKING + REQUEST` según configuración.

Estos nombres sirven para configuración inicial. No introducen lógica de dominio por industria.

## Principio de arquitectura

La dirección del producto es:

`Conversation -> structured state -> policy -> universal operation -> typed projection -> audit/integration`

El backend define hechos y transacciones. La IA interpreta lenguaje. Los adapters de voz y mensajería deben permanecer delgados y compartir el mismo dominio.
