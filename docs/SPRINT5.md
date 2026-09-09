# Helvoca - Sprint 5 - Agente configurable y horarios

## Objetivo

Permitir que cada negocio configure su propio agente de voz sin modificar código y hacer que esa configuración gobierne realmente las llamadas de OpenAI Realtime.

## Implementado

### Agente por tenant

Cada negocio tiene un `ai_agent` con:

- nombre del agente
- idioma
- voz
- saludo inicial
- instrucciones adicionales
- estado activo/inactivo
- capacidades autorizadas

La configuración se administra con:

```text
GET /api/v1/ai-agent
PUT /api/v1/ai-agent
```

Solo `BUSINESS_ADMIN` puede modificarla. `OPERATOR` puede consultarla.

### Capacidades

Las capacidades se almacenan en `ai_agent_capability` y controlan qué tools se exponen a OpenAI Realtime y cuáles acepta el backend:

- `GET_BUSINESS_INFORMATION`
- `LIST_SERVICES`
- `SEARCH_KNOWLEDGE`
- `FIND_CALLER`
- `REGISTER_CALLER`
- `CHECK_BOOKING_AVAILABILITY`
- `CREATE_BOOKING`

La seguridad se aplica en dos capas:

1. una tool deshabilitada no se incluye en `session.update`
2. aunque el modelo intentara invocarla, `RealtimeToolService` devuelve `TOOL_DISABLED`

## Instrucciones protegidas

Las instrucciones personalizadas del negocio son subordinadas a las reglas de seguridad de Helvoca. Un administrador puede definir estilo y comportamiento del agente, pero no puede convertir una operación fallida en éxito ni permitir acceso a otro tenant.

## Horarios

El horario semanal admite varios bloques por día, por ejemplo:

```text
Lunes 09:00-13:00
Lunes 15:00-19:00
```

API:

```text
GET /api/v1/business/hours
PUT /api/v1/business/hours
```

Los días sin bloques quedan cerrados una vez que el negocio ha configurado al menos un horario. Para tenants existentes sin horarios configurados se mantiene compatibilidad y no se bloquean reservas hasta que el administrador configure el calendario.

## Feriados y excepciones

API:

```text
GET    /api/v1/business/schedule-exceptions
PUT    /api/v1/business/schedule-exceptions
DELETE /api/v1/business/schedule-exceptions/{id}
```

Una excepción puede:

- cerrar completamente una fecha
- reemplazar el horario de una fecha por un bloque especial
- guardar un motivo

La excepción tiene prioridad sobre el horario semanal.

## Reservas

`SchedulePolicyService` se aplica tanto a reservas administrativas como a reservas creadas por voz.

Una reserva fuera de horario devuelve/confirma conflicto con `BUSINESS_CLOSED`. La IA no puede evitar esa validación.

## Runtime de llamada

Antes de abrir OpenAI Realtime, Helvoca carga la configuración del agente del tenant.

```text
Twilio validado
  ↓
RealtimeCallContext
  ↓
AI_AGENT del tenant
  ↓
voz + saludo + idioma + instrucciones + capabilities
  ↓
OpenAI Realtime session.update
```

Si el agente está desactivado, Helvoca no inicia la sesión de IA. Sprint 6 agregará el fallback hacia operador humano.

## Base de datos

Flyway `V6__sprint5_agent_schedule.sql` agrega:

- `ai_agent`
- `ai_agent_capability`
- `business_hours`
- `business_schedule_exception`

La migración crea un agente por defecto para tenants ya existentes y habilita las capacidades de Sprint 4 para no romper el comportamiento previo.

## Prueba telefónica real

El código puede validarse por CI sin credenciales, pero una llamada end-to-end real requiere infraestructura externa:

```text
TWILIO_AUTH_TOKEN
TWILIO_PUBLIC_BASE_URL=https://dominio-publico
TWILIO_MEDIA_STREAM_URL=wss://dominio-publico/ws/twilio
OPENAI_API_KEY
```

No se consideran secretos reales como parte del repositorio.

## Definition of Done

- agente persistido por tenant
- configuración administrativa protegida por rol
- voz y saludo aplicados en llamada
- instrucciones personalizadas protegidas por reglas inmutables
- tools filtradas por capability
- validación backend adicional de capability
- horario semanal multi-bloque
- excepciones/feriados
- reservas fuera de horario rechazadas
- migración Flyway versionada
- pruebas automatizadas
- CI verde antes del merge
