# Commercial Observability V1

## Objetivo

Esta certificación permite investigar una llamada de RecepVoz sin crear un sistema paralelo de observabilidad.

La ruta de soporte reutiliza tres fuentes existentes:

1. **Journey Trace** para correlación sanitizada entre subsistemas.
2. **Call Detail** para transcript, resumen y acciones dentro del tenant autenticado.
3. **Conversation Replay** para reproducir el comportamiento con anonimización de datos sensibles.

La regla operativa es: **trace primero, contenido después y solo cuando sea necesario**.

## Mapa de observabilidad actual

| Necesidad | Fuente de verdad | Identificador principal |
| --- | --- | --- |
| Tenant | JWT / TenantProvider | business_id |
| Llamada | call_session | call UUID, provider call id, stream SID |
| Turnos | call_transcript | call UUID + sequence |
| Resumen | call_summary | call UUID |
| Proveedor AI | call_session.ai_provider | call UUID |
| Modelo AI | call_session.ai_model | call UUID |
| Tool calls | call_action | call UUID |
| Resultado tool | call_action.success/error_code | action UUID |
| Entidad creada/modificada | call_action.entity_type/entity_id | action UUID |
| Latencia tool | call_action.duration_ms | action UUID |
| Operación de dominio | business_operation + events | operation UUID |
| Retry | business_operation_retry_attempt | operation/source UUID |
| Job durable | persistent_job | operation/idempotency/correlation id |
| Mensajería | messaging + outbound | conversation/message/operation ids |
| Pago merchant | payment + webhook | operation/provider external id |
| Uso/coste | usage_meter_event | source type + source id |
| Cierre | call_session.status/resolution/ended_at | call UUID |
| Replay | Conversation Replay V2 | call UUID |

## Recorrido de diagnóstico

### 1. Empezar por Journey Trace

Como BUSINESS_ADMIN:

~~~http
GET /api/v1/debug/journeys/{identifier}
~~~

El identificador puede ser, entre otros, call UUID, provider call id, stream SID, operation UUID, job/correlation id, outbound id o identificador de webhook/pago ya soportado por V4.

La timeline correlaciona evidencia persistida de:

~~~text
CALL -> AI -> TOOL -> OPERATION -> WHATSAPP -> RETRY -> JOB
     -> PAYMENT -> WEBHOOK -> OUTBOUND -> USAGE -> CALL_ENDED
~~~

Para voz, el evento AI_SETUP_COMPLETED expone el modelo como metadata diagnóstica en transition con formato model=<id>. No se expone ninguna credencial.

Los eventos TOOL incluyen durationMs cuando la ejecución pasó por la instrumentación runtime.

Los eventos USAGE reutilizan V41 usage_meter_event. transition contiene cantidad/unidad y, cuando existe, coste estimado o real. No se crea una segunda fuente de metering.

### 2. Abrir Call Detail solo si hace falta contenido

~~~http
GET /api/v1/calls/{callId}
~~~

Permite responder:

- qué preguntó el cliente;
- qué respondió el asistente;
- qué resumen quedó;
- qué tool se ejecutó;
- si tuvo éxito;
- qué entityType/entityId creó o modificó;
- cuánto tardó la tool;
- proveedor y modelo usados;
- estado, resolución, duración y costes estimados de la llamada.

Call Detail contiene contenido de conversación y datos operativos. Debe usarse únicamente dentro del tenant autorizado para un caso de soporte concreto.

### 3. Preferir Conversation Replay para reproducir

~~~http
GET /api/v1/quality/calls/{callId}/replay
~~~

Conversation Replay V2 anonimiza valores conocidos del cliente y patrones como teléfonos, emails, RUT y URLs, y reemplaza ids de entidades por referencias locales del fixture.

Es la opción preferida para reproducir un comportamiento sin copiar PII a tickets, chats internos o fixtures.

## Preguntas de soporte

| Pregunta | Dónde responderla |
| --- | --- |
| ¿Qué conversación falló? | Journey Trace: call id + failureStage |
| ¿Qué preguntó el cliente? | Call Detail transcript o Replay |
| ¿Qué respondió el modelo? | Call Detail transcript o Replay |
| ¿Qué proveedor/modelo se usó? | AI event + Call Detail |
| ¿Qué tool ejecutó? | TOOL events / Call Detail actions |
| ¿La tool funcionó? | status, success, errorCode |
| ¿Creó o modificó algo? | entityType/entityId + operation events |
| ¿Cuánto tardó? | tool durationMs, stage durations y journey elapsedMs |
| ¿Por qué terminó? | CALL_ENDED: status/error + resolution |
| ¿Cuánto consumió? | USAGE events del ledger V41 |
| ¿Cuánto costó? | USAGE cost fields y call estimated costs |
| ¿Hubo retry/recuperación? | RETRY/JOB + recoveredAutomatically |

## Privacidad y secretos

Journey Trace sigue siendo una vista sanitizada y tenant-scoped. No proyecta:

- caller/destination phone;
- texto del transcript o reply;
- nombre/email/dirección;
- recipient address;
- checkout URL;
- webhook body;
- payload JSON/hash;
- Authorization headers;
- API keys, access tokens o credenciales.

V1 además elimina el body HTTP de errores/logs del runtime GPT-Live en los dos puntos encontrados durante la auditoría (OpenAiLiveSipService y OpenAiLiveSidebandManager). Los errores estructurados conservan status, error code, request id, session id y latencia, que son suficientes para diagnóstico sin copiar respuestas arbitrarias del proveedor.

## Gaps encontrados

### Cerrados en V1

- **Modelo exacto no persistido:** se guarda call_session.ai_model.
- **Latencia de tool no persistida:** se guarda call_action.duration_ms.
- **Uso/coste separado del journey:** Journey Trace incorpora eventos sanitizados desde usage_meter_event.
- **Provider response body en logs GPT-Live:** eliminado de los paths detectados.

### Limitaciones conocidas, no inventadas

- **Turno exacto -> tool call:** call_transcript y call_action tienen orden/tiempo, pero hoy no existe una FK o provider tool-call id persistido que demuestre qué turno exacto originó cada tool. No se infiere esa relación.
- **Tokens AI:** Journey Trace puede mostrar eventos de metering cuando existen, pero el runtime no garantiza contadores de input/output tokens para todos los proveedores de voz.
- **Duplicados rechazados antes de persistir:** mantienen la limitación V4 REJECTED_DUPLICATE_ATTEMPTS_ARE_NOT_PERSISTED_BY_CURRENT_SOURCES.
- **Correlation IDs universales:** existen en durable jobs y varios subsistemas, pero no todos los eventos externos tienen un correlation id común persistido.

Estos gaps requieren instrumentación adicional de proveedor o cambios de contrato y no se rellenan con datos inventados.

## Checklist de soporte

1. Buscar el identifier en Journey Trace.
2. Confirmar business_id implícito por el JWT y los call/operation ids devueltos.
3. Leer failureStage, retries, tool errors, model y USAGE.
4. Si se necesita contenido, abrir Call Detail del call id.
5. Si hay que reproducir o compartir el caso, usar Conversation Replay anonimizado.
6. Usar logs de proveedor solo como última capa, siempre por ids estructurados y nunca copiando bodies/headers.
7. No incluir transcripts, teléfonos, tokens ni payloads crudos en tickets de soporte.

## Certificación esperada

La PR debe demostrar:

- migración de metadata diagnóstica;
- unit tests de latencia/modelo;
- integración PostgreSQL de Journey Trace con AI model, tool latency y V41 usage;
- contrato de sanitización del SQL;
- CI del repositorio en verde.

No se realizan llamadas reales, cobros, deploy, merge ni activación de credenciales live.
