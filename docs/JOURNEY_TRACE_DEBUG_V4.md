# Journey Trace / Debug Engine V4

## Objetivo

V4 agrega una vista de diagnóstico read-only para reconstruir un journey comercial sin crear otro sistema de tracing paralelo.

La fuente de verdad sigue siendo la información que Helvoca ya persiste:

- `call_session` y `call_action`;
- conversaciones y mensajes de WhatsApp;
- `business_operation` y `business_operation_event`;
- `business_operation_retry_attempt`;
- `persistent_job`;
- `business_payment`;
- `payment_webhook_event`;
- `outbound_message`.

No se agrega una migración ni una tabla nueva para duplicar estos eventos.

## Endpoint

```http
GET /api/v1/debug/journeys/{identifier}
```

Acceso:

- solo `BUSINESS_ADMIN`;
- siempre usa el `business_id` del JWT;
- no existe fallback global si un identificador no pertenece al tenant.

## Identificadores aceptados

El resolver puede partir desde un identificador operativo ya existente, incluyendo:

- call UUID;
- provider call id;
- stream SID;
- WhatsApp conversation UUID;
- message UUID;
- external/provider message id;
- operation UUID;
- source reference UUID;
- persistent job UUID;
- job idempotency key;
- correlation id persistido por un durable job;
- outbound message UUID;
- outbound idempotency key;
- outbound provider message id;
- payment webhook UUID;
- webhook event id;
- payment provider external id.

Desde esa semilla se reconstruyen las operaciones relacionadas, incluyendo enlaces payment -> target operation y order <-> delivery.

## Timeline

La respuesta ordena eventos sanitizados de:

`CALL -> AI -> TOOL -> OPERATION -> WHATSAPP -> RETRY -> JOB -> PAYMENT -> WEBHOOK -> OUTBOUND`

Cada entrada puede incluir solamente metadatos de diagnóstico como:

- timestamp;
- etapa y evento;
- estado;
- call id;
- operation id;
- resource id;
- source reference id;
- provider;
- actor;
- correlation id;
- intento/máximo de intentos;
- error code;
- duración o delay;
- transición de estado;
- señal de recuperación automática.

El resumen entrega los call IDs y operation IDs involucrados, providers, correlation IDs, retries observados, última etapa con fallo, tiempo total observado y si hubo recuperación automática.

## Datos que V4 no expone

El trace no proyecta:

- caller/destination phone;
- nombres o emails;
- dirección de entrega;
- texto de conversación;
- reply text;
- recipient address;
- contenido de outbound;
- checkout URL;
- webhook body;
- payload hash;
- payload JSON completo;
- secretos o tokens.

El JSON de un persistent job se consulta solamente para extraer el campo estructurado `correlationId` cuando existe.

## Duplicados

V4 no inventa evidencia.

Los stores actuales previenen o deduplican muchos efectos antes de crear una segunda fila. Por eso un duplicado rechazado que nunca se persistió no puede reconstruirse retroactivamente como un evento independiente.

La respuesta declara esta limitación con:

`REJECTED_DUPLICATE_ATTEMPTS_ARE_NOT_PERSISTED_BY_CURRENT_SOURCES`

Los retries, attempt counts, idempotency envelopes persistidos y webhooks existentes sí aparecen en la traza.

## Inventory

El `main` usado para V4 no contiene todavía el módulo runtime de inventario que vive en trabajo comercial independiente. V4 no importa ni mezcla esa rama. Cuando inventory llegue a `main`, el agregador puede sumar sus movimientos usando el mismo patrón tenant-scoped y read-only.

## Certificación

V4 debe pasar:

1. tests unitarios de tenant scope, resumen, identificadores y exclusión de datos sensibles;
2. prueba PostgreSQL/Testcontainers del SQL real;
3. Fast Gate;
4. sincronización única con `main` justo antes de la certificación final si `main` avanzó;
5. Full CI con backend, JaCoCo differential coverage y Playwright.

La PR permanece DRAFT hasta completar la certificación y no se hace merge automático.


## Commercial observability V1 extension

The commercial support pass keeps Journey Trace V4 as the correlation surface and adds only persisted evidence that was previously missing:

- AI setup events expose the persisted voice model as `transition=model=<id>`.
- TOOL events expose `durationMs` from `call_action.duration_ms`.
- USAGE events reuse the append-only V41 `usage_meter_event` ledger and expose quantity/unit plus available estimated or actual cost.
- No transcript text, phone number, provider response body, authorization header, token or credential is added to the trace.

For the complete support workflow and privacy boundary, see `docs/COMMERCIAL_OBSERVABILITY_V1.md`.
