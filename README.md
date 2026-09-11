# RecepVoz - Product V1

RecepVoz es un SaaS multi-tenant de atención telefónica con IA para empresas. El backend cuenta con autenticación, aislamiento por tenant, clientes, servicios, reservas, conocimiento empresarial, telefonía, agente de voz, herramientas controladas por backend y trazabilidad de llamadas.

## Enfoque de producto

La V1 se concentra primero en negocios que trabajan con horas o reservas. La promesa comercial es simple: RecepVoz contesta, resuelve preguntas repetitivas, agenda clientes y escala a una persona cuando corresponde.

El core ya no debe depender de un proveedor específico. Twilio y OpenAI son los primeros adaptadores, no la arquitectura completa del producto.

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
- arquitectura multi-tenant

### Capacidades de negocio

- autenticación, roles, usuarios y auditoría
- clientes
- catálogo de servicios
- reservas y disponibilidad
- Knowledge Base
- números telefónicos por negocio
- historial de llamadas
- transcripción y resumen
- tool calling con backend como fuente de verdad

### Voz

- Twilio Voice + Media Streams como primer adaptador de telefonía
- OpenAI Realtime como primer adaptador de IA de voz
- Server VAD
- interrupciones / barge-in
- audio PCMU bidireccional
- modo Twilio Trial mediante `<Gather input="speech">` + `<Say>`

## Arquitectura independiente de proveedores

RecepVoz define puertos propios para voz:

```text
Caller
  ↓
Telephony adapter
  ↓
CallLifecycleService
  ↓
VoiceAiProvider
  ↓
RecepVoz tools / business rules
  ↓
PostgreSQL
```

Contratos principales:

- `VoiceAiProvider`
- `VoiceAiSession`
- `VoiceTransportSession`
- `VoiceAiProviderRegistry`
- `CallLifecycleService`

`OpenAiRealtimeBridgeFactory` implementa `VoiceAiProvider` y `TwilioVoiceTransportSession` adapta el WebSocket de Twilio a `VoiceTransportSession`. El AI provider ya no necesita conocer el protocolo de Twilio.

Selección actual:

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_AI_PROVIDER=openai
```

La base queda preparada para añadir adapters Telnyx, SIP u otros motores de IA sin reescribir reservas, clientes, conocimiento ni reglas multi-tenant.

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks de producción de Twilio se autentican mediante `X-Twilio-Signature`. Las tools de voz tampoco aceptan un tenant elegido por el modelo: utilizan un `RealtimeCallContext` construido desde una llamada previamente resuelta por RecepVoz.

## Regla crítica de IA

El modelo solicita acciones, pero el backend decide su resultado.

```text
IA → function call → RecepVoz → PostgreSQL → tool result → IA
```

Una reserva solo puede ser anunciada como confirmada si RecepVoz devuelve éxito. Un error como `BOOKING_SLOT_UNAVAILABLE` debe comunicarse como error, nunca como una confirmación inventada.

## Trazabilidad de proveedores

Cada `call_session` registra:

```text
telephony_provider
ai_provider
```

Eso permite construir después costos por llamada, comparación de proveedores, fallback y margen por negocio sin adivinar qué infraestructura atendió cada conversación.

## Base de datos

Flyway aplica:

- `V1__foundation.sql`
- `V2__seed_roles.sql`
- `V3__sprint2_core.sql`
- `V4__sprint3_telephony.sql`
- `V5__sprint4_ai_voice.sql`
- `V6__provider_independent_voice.sql`

V5 incorpora `call_summary`. V6 incorpora trazabilidad de proveedor telefónico y proveedor de IA en `call_session`.

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
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_AI_PROVIDER=openai
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

Después registra ese número en RecepVoz mediante `POST /api/v1/phone-numbers`.

## Documentación

- `docs/PRODUCT_ARCHITECTURE.md`
- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/TRIAL_VOICE.md`
- `docs/API.md`
- `docs/design/`

## Próximos hitos comerciales

El orden recomendado desde aquí es:

1. llamada telefónica real estable
2. conversación Realtime estable
3. configuración del agente por tenant
4. horarios y excepciones
5. transferencia humana
6. dashboard mínimo
7. onboarding self-service
8. medición de uso y costo por llamada
9. planes, límites y billing
10. primer cliente pagado

Redis, Telnyx, SIP y proveedores adicionales se incorporan cuando resuelvan una necesidad medida de escala, costo, disponibilidad o geografía. RecepVoz se mantiene como monolito modular mientras esa sea la opción más simple y confiable.
