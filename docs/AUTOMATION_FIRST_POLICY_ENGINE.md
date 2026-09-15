# Automation-first Policy Engine

## Objetivo

Helvoca debe resolver y ejecutar operaciones por sí sola siempre que las reglas del negocio y las invariantes del backend lo permitan. La intervención humana no forma parte del flujo normal: es un último recurso para casos realmente irresolubles.

La política separa explícitamente tres conceptos que no deben confundirse:

1. **Confirmación del cliente**: el cliente confirma una decisión transaccional propia.
2. **Ejecución automática**: Helvoca ejecuta la operación después de cumplir las invariantes aplicables.
3. **Escalamiento humano**: fallback excepcional cuando Helvoca no puede resolver el caso de forma segura.

Por ejemplo, `ORDER` puede exigir un “sí” explícito del cliente y aun así ser 100% automático para el negocio. Ningún empleado tiene que aprobarlo.

## V30

V30 incorpora `business_automation_policy`, con una fila opcional por tenant y tipo de operación. Las filas son overrides sparse: si no existe una fila, se aplican defaults de plataforma.

Tipos cubiertos:

- `ORDER`
- `QUOTE`
- `LEAD`
- `DELIVERY`
- `REQUEST`
- `BOOKING`
- `PAYMENT`

Campos:

- `auto_execute`
- `customer_confirmation`
- `payment_requirement`
- `retry_policy`
- `max_auto_retries`
- `escalation_policy`

## Defaults automation-first

Los defaults de plataforma son:

- `autoExecute = true`
- `retryPolicy = SAFE_AUTOMATIC`
- `maxAutoRetries = 2`
- `escalationPolicy = ONLY_IF_UNRESOLVABLE`
- `paymentRequirement = NONE`

Confirmación del cliente:

- `ORDER`, `DELIVERY`, `BOOKING`, `PAYMENT` -> `EXPLICIT`
- `QUOTE`, `LEAD`, `REQUEST` -> `NONE`

La confirmación explícita de las operaciones transaccionales es un piso de seguridad. Un tenant no puede debilitarla mediante una fila directa en base de datos ni por API.

`PAYMENT` nunca puede exigir otro `PAYMENT` de forma recursiva.

## Aplicación runtime

`OperationPolicyService` es la autoridad central de política.

`AiAgentService` aplica `autoExecute` a todas las herramientas que mutan operaciones universales, incluyendo BOOKING y REQUEST además de las operaciones comerciales. Cuando `autoExecute=false`:

- la herramienta mutante deja de publicarse al agente;
- una ejecución directa también queda rechazada por la misma autoridad de capabilities;
- las herramientas de consulta siguen disponibles.

Ejemplo para ORDER con automatización pausada:

- `quote_order` -> oculto/bloqueado
- `update_order` -> oculto/bloqueado
- `create_order` -> oculto/bloqueado
- `cancel_order` -> oculto/bloqueado
- `get_order_status` -> disponible
- `list_catalog` -> disponible

Eso permite detener mutaciones sin volver inútil al asistente.

## API administrativa

Lectura:

```http
GET /api/v1/automation-policies
GET /api/v1/automation-policies/{operationType}
```

Roles: `BUSINESS_ADMIN` y `OPERATOR`.

Cambio:

```http
PATCH /api/v1/automation-policies/{operationType}
```

Solo `BUSINESS_ADMIN`.

Ejemplo:

```json
{
  "autoExecute": true,
  "retryPolicy": "SAFE_AUTOMATIC",
  "maxAutoRetries": 3,
  "escalationPolicy": "ONLY_IF_UNRESOLVABLE"
}
```

Reset al default de plataforma:

```http
DELETE /api/v1/automation-policies/{operationType}
```

Solo `BUSINESS_ADMIN`.

La API nunca acepta `businessId`; lo obtiene desde el JWT mediante `TenantProvider`.

Cada cambio/reset genera un registro en el audit log administrativo.

## Clasificación de fallos

El motor distingue:

- `TRANSIENT`: error temporal que no justifica molestar a una persona.
- `RESOLVABLE_WITH_FALLBACK`: Helvoca puede intentar otra ruta segura.
- `UNRESOLVABLE`: no existe una salida segura/permitida con la información y herramientas disponibles.

Con `ONLY_IF_UNRESOLVABLE`, solamente la tercera categoría habilita escalamiento humano.

Esto evita convertir Helvoca en una bandeja de aprobaciones.

## Garantías de seguridad

- Las reglas se resuelven por `business_id`.
- Las operaciones transaccionales conservan confirmación explícita del cliente.
- Desactivar automatización no elimina capacidades de consulta.
- La política no autoriza una herramienta que el AiAgent no tenga habilitada.
- La política puede restringir capabilities, nunca expandirlas.
- No existe aprobación humana rutinaria en los defaults.

## Alcance de V30

V30 vuelve configurable y ejecutable el control de `autoExecute` y centraliza las decisiones de confirmación, retry y escalamiento. Los workflows existentes conservan sus invariantes transaccionales actuales.

La ejecución automática de reintentos multi-step, payment chaining y el Human Handoff persistente deben consumir estas decisiones en sus respectivos subsistemas; V30 no finge que un retry ocurrió ni crea handoffs automáticamente solo por existir una política.
