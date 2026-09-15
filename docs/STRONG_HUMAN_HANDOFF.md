# V32 Strong Human Handoff

V32 convierte `HUMAN_HANDOFF` en una operación durable y verificable. La IA no puede afirmar que una persona fue involucrada solo porque una política lo sugirió: el handoff debe existir en PostgreSQL.

## Regla principal

El handoff automático solo puede ocurrir cuando se cumplen simultáneamente estas condiciones:

1. V31 clasifica el fallo como `UNRESOLVABLE`.
2. La política efectiva del tenant permite `ONLY_IF_UNRESOLVABLE`.
3. La creación durable del handoff termina correctamente.

Los fallos `TRANSIENT` agotados siguen usando `RETRY_LATER`. Los fallos `RESOLVABLE_WITH_FALLBACK` siguen utilizando su fallback automático. Ninguno de ellos crea un handoff humano.

Si el almacenamiento del handoff falla, Helvoca devuelve `STOP_SAFELY` y `automation.humanEscalation=false`. Nunca se comunica al cliente que existe intervención humana cuando no existe evidencia durable de ella.

## Cola durable

`human_handoff` mantiene una cola por tenant con:

- referencia de conversación/canal mediante `source_reference_id`;
- operación universal y `operation_id` cuando está disponible;
- herramienta que originó el fallo;
- código de motivo;
- clase de fallo;
- cantidad de retries previos;
- prioridad;
- resumen seguro sin argumentos crudos ni secretos;
- asignación;
- timestamps de lifecycle.

Los estados son:

`OPEN -> ACKNOWLEDGED -> ASSIGNED -> RESOLVED`

También se puede pasar desde un estado activo a `CANCELLED`.

La asignación puede hacerse directamente desde `OPEN` o `ACKNOWLEDGED`.

## Deduplicación

Existe como máximo un handoff activo para la misma combinación lógica de tenant, conversación, operación, tipo y motivo. Repetir el mismo fallo recupera el handoff existente en lugar de abrir tickets duplicados.

Los estados terminales `RESOLVED` y `CANCELLED` dejan de bloquear un handoff futuro si el problema vuelve a ocurrir.

## Auditoría

`human_handoff_event` es append-only y registra:

- `CREATED`
- `ACKNOWLEDGED`
- `ASSIGNED`
- `RESOLVED`
- `CANCELLED`

UPDATE y DELETE de eventos son rechazados por PostgreSQL. Los handoffs tampoco se pueden borrar; su estado cambia mediante lifecycle.

## API administrativa

Solo `BUSINESS_ADMIN` y `OPERATOR`:

- `GET /api/v1/handoffs`
- `GET /api/v1/handoffs?status=OPEN`
- `GET /api/v1/handoffs/{id}/events`
- `POST /api/v1/handoffs/{id}/acknowledge`
- `POST /api/v1/handoffs/{id}/assign` con `{ "assignee": "..." }`
- `POST /api/v1/handoffs/{id}/resolve`
- `POST /api/v1/handoffs/{id}/cancel`

Todas las consultas y mutaciones administrativas obtienen `business_id` desde el JWT con `TenantProvider`. Un tenant no puede consultar ni mutar handoffs de otro tenant.

## Metadata para los canales

Cuando V31 detecta un fallo irresoluble, `automation` incluye además:

- `handoffRequested`: la política permitía intentar escalamiento;
- `handoffCreated`: se creó un nuevo handoff en esta ejecución;
- `handoffId`: id durable, también cuando se reutiliza un handoff activo;
- `handoffStatus`: estado del handoff.

`automation.humanEscalation=true` implica que `handoffId` existe.

## Seguridad

V32 no persiste payloads de herramientas, direcciones, teléfonos, credenciales de proveedores, tokens ni argumentos crudos. El resumen automático contiene únicamente tipo de operación y código seguro de motivo.
