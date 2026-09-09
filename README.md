# Helvoca - Sprint 3

Helvoca es un SaaS multi-tenant de atención telefónica con IA para empresas. El backend ya cuenta con autenticación, aislamiento por tenant, clientes, servicios, reservas, conocimiento empresarial y una capa telefónica Twilio autenticada por firma.

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
- estados de llamada
- asociación automática de cliente por teléfono
- webhook de voz Twilio
- webhook de estado Twilio
- validación obligatoria `X-Twilio-Signature`
- TwiML `<Connect><Stream>`
- WebSocket `/ws/twilio`
- validación de firma del handshake Media Stream
- persistencia de `CallSid`, `StreamSid`, tiempos y duración
- tabla y API de lectura de transcripción preparadas para Sprint 4
- test de integración del ciclo de llamada contra PostgreSQL 16

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks Twilio son una excepción al JWT porque provienen del proveedor telefónico. Se autentican obligatoriamente con `X-Twilio-Signature` y el Auth Token de Twilio.

## Base de datos

Flyway aplica:

- `V1__foundation.sql`
- `V2__seed_roles.sql`
- `V3__sprint2_core.sql`
- `V4__sprint3_telephony.sql`

V4 incorpora `phone_number`, `call_session` y `call_transcript`.

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

## Configurar Twilio

No guardes el Auth Token real en Git.

```text
TWILIO_AUTH_TOKEN=tu_token
TWILIO_PUBLIC_BASE_URL=https://tu-dominio-publico
TWILIO_MEDIA_STREAM_URL=wss://tu-dominio-publico/ws/twilio
```

Configura el número Twilio para llamar por POST a:

```text
https://tu-dominio-publico/webhooks/v1/twilio/voice
```

Y configura el callback de estados hacia:

```text
https://tu-dominio-publico/webhooks/v1/twilio/status
```

Después registra ese número en Helvoca mediante `POST /api/v1/phone-numbers`.

## Documentación

- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/API.md`

## Próximo sprint

Sprint 4 conecta el Media Stream con OpenAI Realtime:

1. puente de audio μ-law 8 kHz
2. sesión Realtime por llamada
3. VAD e interrupciones
4. transcripción real
5. tool calling
6. reservas por voz
7. respuestas de audio hacia Twilio
8. resumen final de llamada
