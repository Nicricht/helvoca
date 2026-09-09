# Helvoca - Sprint 5

Helvoca es un SaaS multi-tenant de atención telefónica con IA para empresas. El backend cuenta con autenticación, aislamiento por tenant, clientes, servicios, reservas, conocimiento empresarial, telefonía Twilio autenticada, un agente de voz OpenAI Realtime y configuración independiente del agente por negocio.

## Estado actual

### Plataforma

- Java 21
- Spring Boot 4.1.1
- Spring Security + JWT HS256
- PostgreSQL 16+
- Flyway
- Docker Compose
- Swagger/OpenAPI
- GitHub Actions CI
- Twilio Voice + Media Streams
- OpenAI Realtime

### Sprint 1

- autenticación
- roles
- negocio multi-tenant
- usuarios
- auditoría

### Sprint 2

- clientes
- catálogo de servicios
- reservas y disponibilidad
- Knowledge Base
- tests PostgreSQL con Testcontainers

### Sprint 3

- números telefónicos por negocio
- historial `call_session`
- webhooks Twilio firmados
- TwiML `<Connect><Stream>`
- WebSocket `/ws/twilio`
- persistencia de CallSid/StreamSid y ciclo de llamada

### Sprint 4

- puente de audio PCMU Twilio ↔ OpenAI Realtime
- sesión Realtime independiente por llamada
- Server VAD y barge-in
- transcripción real de usuario y asistente
- function calling ejecutado por Helvoca
- consulta de información, catálogo y Knowledge Base
- identificación y registro del caller
- comprobación de disponibilidad
- creación de reservas por voz
- resumen automático post-llamada
- regla fail-closed: la IA nunca decide si una operación fue exitosa

### Sprint 5

- `AI_AGENT` configurable por tenant
- nombre, voz, idioma y saludo independientes por empresa
- instrucciones adicionales protegidas por reglas inmutables de Helvoca
- activación/desactivación del agente
- capabilities por agente
- filtrado de tools antes de enviarlas a OpenAI
- validación backend adicional de tools deshabilitadas
- horarios semanales con múltiples bloques por día
- feriados y excepciones de horario
- validación de horario en reservas administrativas y por voz
- migración Flyway `V6__sprint5_agent_schedule.sql`
- APIs administrativas de agente y horarios

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks Twilio se autentican mediante `X-Twilio-Signature`. Las tools de Realtime utilizan un `RealtimeCallContext` construido desde la llamada Twilio validada.

## Regla crítica de IA

El modelo solicita acciones, pero el backend decide el resultado.

```text
IA → function call → Helvoca → PostgreSQL → function_call_output → IA
```

Una reserva solo puede anunciarse como confirmada si Helvoca devuelve `success=true`. Las capabilities del agente también se validan nuevamente en el backend, aunque una tool no haya sido expuesta al modelo.

## Horarios

Una vez configurado un horario semanal, los días sin bloques se consideran cerrados. Las excepciones por fecha tienen prioridad sobre el calendario semanal.

```text
Horario semanal → excepción de fecha → validación de reserva → PostgreSQL
```

Los tenants existentes sin horario configurado conservan temporalmente comportamiento abierto para no romper reservas previas. Al guardar su primer calendario pasan a usar la política de horario.

## Base de datos

Flyway aplica:

- `V1__foundation.sql`
- `V2__seed_roles.sql`
- `V3__sprint2_core.sql`
- `V4__sprint3_telephony.sql`
- `V5__sprint4_ai_voice.sql`
- `V6__sprint5_agent_schedule.sql`

V6 agrega `ai_agent`, `ai_agent_capability`, `business_hours` y `business_schedule_exception`.

## Ejecutar con Docker

```bash
cp .env.example .env
docker compose up --build
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Health:

```text
http://localhost:8080/actuator/health
```

## Configurar Twilio + OpenAI

No guardes tokens ni API keys reales en Git.

```text
TWILIO_AUTH_TOKEN=tu_token
TWILIO_PUBLIC_BASE_URL=https://tu-dominio-publico
TWILIO_MEDIA_STREAM_URL=wss://tu-dominio-publico/ws/twilio
OPENAI_API_KEY=tu_api_key
```

Voice webhook:

```text
POST https://tu-dominio-publico/webhooks/v1/twilio/voice
```

Status callback:

```text
POST https://tu-dominio-publico/webhooks/v1/twilio/status
```

Media Stream:

```text
wss://tu-dominio-publico/ws/twilio
```

## Documentación

- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/SPRINT5.md`
- `docs/API.md`
- `docs/design/`

## Próximo sprint

Sprint 6 implementará transferencia humana y fallback operacional:

1. entidad `HUMAN_TRANSFER`
2. solicitud de operador desde IA
3. operadores disponibles por tenant
4. contexto automático de la conversación
5. aceptación y cierre de transferencia
6. fallback/callback cuando no existe operador disponible
7. tool `request_human_transfer`
8. auditoría y tests multi-tenant

## Prueba telefónica real

El código puede compilarse y probarse por CI sin secretos. Para declarar una llamada real end-to-end como aprobada todavía se necesitan un dominio público HTTPS/WSS, un número Twilio configurado y credenciales reales de Twilio/OpenAI fuera de Git.
