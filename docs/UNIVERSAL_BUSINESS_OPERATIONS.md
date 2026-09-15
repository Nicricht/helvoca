# Universal Business Operations

## Objetivo

Helvoca separa el cerebro conversacional de las operaciones que cada tenant puede ejecutar. Un restaurante, taller, clínica, tienda o inmobiliaria usa el mismo dominio; los verticales son presets de configuración, no ramas `if restaurant`, `if clinic`, etc.

El LLM interpreta lenguaje y reúne datos. El backend conserva autoridad sobre permisos, catálogo, precios, cobertura, estados, confirmaciones y persistencia.

## Autorización por tenant

`BusinessOperationCapability` es una capa de configuración/preset. La autorización efectiva de herramientas tiene una única fuente de verdad runtime: `AiAgent` + `AiCapability`, persistida en `ai_agent_capability`.

Presets comerciales actuales:

- `CATALOG`: catálogo universal.
- `ORDER`: cotizar, actualizar, confirmar, consultar y cancelar pedidos.
- `DELIVERY`: zonas, cobertura y costos de despacho.
- `QUOTE`: cotizaciones estructuradas.
- `LEAD`: captura estructurada de potenciales clientes.

Dependencias normalizadas:

- `ORDER` implica `CATALOG`.
- `QUOTE` implica `CATALOG`.
- `DELIVERY` implica `ORDER + CATALOG`.

Las capacidades comerciales son opt-in. Los tenants legacy no reciben automáticamente nuevas herramientas transaccionales.

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

V23 convirtió ORDER en una proyección 1:1 de `business_operation`.

V24 hace lo mismo con QUOTE, LEAD y REQUEST y migra los registros existentes. Cada nueva proyección guarda un `operation_id` único y no nulo.

BOOKING conserva por ahora su dominio especializado y todavía no es una proyección de `business_operation`.

## Conversation State Engine

V24 agrega `conversation_operation_state` para que el estado operativo no dependa únicamente del historial textual del modelo.

La clave lógica es:

`business_id + channel + source_reference_id`

El estado incluye:

- operación activa;
- JSON estructurado de conversación;
- revisión incremental;
- timestamps.

Semántica principal: la corrección más reciente reemplaza el valor anterior incompatible. Por ejemplo, si el cliente cambia dirección o cantidad, el nuevo valor sustituye al anterior y aumenta la revisión.

El estado está aislado por tenant y canal. Un patch que no trae una nueva operación activa conserva la operación actual en vez de borrarla accidentalmente.

Para REQUEST, `business_request.call_id` sigue reservado a llamadas reales. WhatsApp y otros canales conservan su referencia en `business_operation.source_reference_id`.

## Policy Engine

`OperationPolicyService` centraliza la política base de confirmación y revisión humana.

Política actual:

- `ORDER` y `DELIVERY`: confirmación explícita.
- `QUOTE`, `LEAD` y `REQUEST`: no requieren confirmación transaccional adicional.
- todas pueden derivar a revisión humana ante fallo.

La política no la decide el LLM. Esta primera versión es una política backend centralizada; todavía no es una matriz configurable por tenant en base de datos.

## Herramientas comerciales

Cuando el `AiAgent` del tenant las autoriza, voz y WhatsApp pueden publicar:

- `list_catalog`
- `list_delivery_zones`
- `validate_delivery_address`
- `quote_order`
- `update_order`
- `create_order`
- `get_order_status`
- `cancel_order`
- `create_quote`
- `create_lead`

El servicio comercial vuelve a validar la capability en runtime y falla cerrado aunque un adapter futuro publique accidentalmente una herramienta no autorizada.

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

## Delivery

`delivery_zone` almacena nombre, términos de cobertura, costo de despacho, mínimo opcional y estado.

El backend normaliza la dirección y exige:

- capability `DELIVERY` activa;
- dirección no vacía;
- coincidencia inequívoca con una zona activa;
- compra mínima satisfecha;
- costo de despacho obtenido del backend.

`deliveryZoneId` nunca reemplaza la validación de la dirección. El backend vuelve a resolver cobertura antes de confirmar.

DELIVERY está integrado actualmente en ORDER. El tipo universal `DELIVERY` existe, pero un workflow de entrega autónoma todavía es una evolución posterior.

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
- `GET /api/v1/commercial/quotes`
- `GET /api/v1/commercial/leads`

Las consultas operativas requieren `BUSINESS_ADMIN` u `OPERATOR`. Las mutaciones de configuración requieren los permisos administrativos correspondientes.

## Ejemplos de presets

- Restaurante: `CATALOG + ORDER + DELIVERY`.
- Tienda: `CATALOG + ORDER`.
- Taller: `CATALOG + QUOTE + BOOKING`.
- Inmobiliaria: `CATALOG + LEAD + BOOKING/REQUEST`.
- Clínica o veterinaria: `CATALOG + BOOKING + REQUEST` según configuración.

Estos nombres sirven para configuración inicial. No introducen lógica de dominio por industria.

## Principio de arquitectura

La dirección del producto es:

`Conversation -> structured state -> policy -> universal operation -> typed projection -> audit/integration`

El backend define hechos y transacciones. La IA interpreta lenguaje. Los adapters de voz y mensajería deben permanecer delgados y compartir el mismo dominio.
