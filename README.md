# Helvoca - Sprint 4

Helvoca es un SaaS multi-tenant de atención telefónica con IA para empresas. El backend cuenta con autenticación, aislamiento por tenant, clientes, servicios, reservas, conocimiento empresarial, telefonía Twilio autenticada y un agente de voz conectado a OpenAI Realtime.

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
- Server VAD
- interrupciones / barge-in
- transcripción real de usuario y asistente
- function calling ejecutado por Helvoca
- consulta de información del negocio
- catálogo y Knowledge Base por voz
- identificación y registro del caller
- comprobación de disponibilidad
- creación de reservas por voz
- regla fail-closed: la IA nunca decide si una operación fue exitosa
- resumen automático post-llamada mediante Responses API
- resumen incluido en detalle de llamada

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks Twilio se autentican mediante `X-Twilio-Signature`. Las tools de Realtime tampoco aceptan un tenant elegido por el modelo: utilizan un `RealtimeCallContext` construido desde la llamada Twilio ya validada.

## Regla crítica de IA

El modelo solicita acciones, pero el backend decide su resultado.

```text
IA → function call → Helvoca → PostgreSQL → function_call_output → IA
```

Una reserva solo puede ser anunciada como confirmada si Helvoca devuelve `success=true`. Un error como `BOOKING_SLOT_UNAVAILABLE` debe comunicarse como error, no como éxito.

## Base de datos

Flyway aplica:

- `V1__foundation.sql`
- `V2__seed_roles.sql`
- `V3__sprint2_core.sql`
- `V4__sprint3_telephony.sql`
- `V5__sprint4_ai_voice.sql`

V5 incorpora `call_summary`.

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

Configura el número Twilio para llamar por POST a:

```text
https://tu-dominio-publico/webhooks/v1/twilio/voice
```

Callback de estados:

```text
https://tu-dominio-publico/webhooks/v1/twilio/status
```

Después registra ese número en Helvoca mediante `POST /api/v1/phone-numbers`.

## Documentación

- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/API.md`
- `docs/design/`

## Próximo sprint

Sprint 5 debe convertir la configuración del agente en datos editables por cada negocio:

1. entidad `AI_AGENT`
2. nombre, voz, idioma y saludo por tenant
3. instrucciones configurables protegidas
4. horarios y excepciones/feriados
5. capabilities permitidas por agente
6. activación/desactivación
7. API administrativa
8. tests de aislamiento y permisos
