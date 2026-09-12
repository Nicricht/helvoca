# RecepVoz - Product V1

RecepVoz es un SaaS multi-tenant de atención telefónica con IA para empresas. El backend cuenta con autenticación, aislamiento por tenant, clientes, servicios, reservas, conocimiento empresarial, telefonía, agente de voz, herramientas controladas por backend y trazabilidad de llamadas.

## Enfoque de producto

La V1 se concentra primero en negocios que trabajan con horas o reservas. La promesa comercial es simple: RecepVoz contesta, resuelve preguntas repetitivas, agenda clientes y escala a una persona cuando corresponde.

El core no depende de un proveedor específico. Twilio transporta las llamadas y RecepVoz selecciona un proveedor de voz en tiempo real saludable antes de construir la ruta de audio.

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

- voice edge multi-provider
- Gemini Live con audio nativo mediante Twilio Bidirectional Media Streams
- OpenAI GPT-Live mediante SIP seguro/SRTP como proveedor alternativo
- conversación full-duplex con interrupciones
- herramientas de negocio compartidas por todos los proveedores
- circuit breaker para créditos, autenticación, rate limits, sesiones y errores upstream
- fail-closed cuando no existe un proveedor saludable
- sin fallback a Twilio `<Gather>`, `<Say>` ni voces Polly
- sin pipeline clásico STT → LLM → TTS para Gemini Live o GPT-Live

## Arquitectura independiente de proveedores

```text
Caller
  ↓
Twilio
  ↓
RecepVoz Voice Edge
  ↓
VoiceCallRouter
  ├── Gemini Live      → Bidirectional Media Stream → audio nativo
  └── OpenAI GPT-Live → SIP seguro / SRTP
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
- `VoiceCallRouter`
- `VoiceProviderHealthRegistry`
- `CallLifecycleService`

El orden se controla por configuración:

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_PROVIDER_ORDER=gemini,openai-live
```

El router selecciona el primer proveedor configurado y saludable. Si un proveedor devuelve un fallo operativo, su circuito se abre temporalmente y las llamadas posteriores pueden usar el siguiente proveedor. Si ninguno está listo, RecepVoz falla cerrado en vez de enviar llamadas hacia una IA que sabemos que no funciona.

## Gemini Live

Gemini usa Twilio Media Streams únicamente como transporte de audio. No se convierte la conversación en un pipeline tradicional de STT, modelo de texto y TTS.

```text
Teléfono
  ↓ PCMU 8 kHz
Twilio Media Stream
  ↓
RecepVoz Voice Edge
  ↓ PCM16 16 kHz
Gemini Live
  ↓ PCM16 24 kHz
RecepVoz Voice Edge
  ↓ PCMU 8 kHz
Twilio
  ↓
Teléfono
```

Configuración:

```text
GEMINI_LIVE_ENABLED=true
GEMINI_API_KEY=tu_api_key
GEMINI_LIVE_MODEL=gemini-3.1-flash-live-preview
GEMINI_LIVE_VOICE=Kore
```

No habilites Gemini hasta haber configurado una credencial válida.

## OpenAI GPT-Live

GPT-Live conserva la ruta SIP directa y puede seguir formando parte del orden de failover:

```text
OPENAI_LIVE_ENABLED=true
OPENAI_PROJECT_ID=tu_project_id
OPENAI_WEBHOOK_SECRET=tu_webhook_secret
OPENAI_LIVE_MODEL=gpt-live-1
OPENAI_LIVE_VOICE=marin
```

Errores terminales como falta de créditos o autenticación abren el circuit breaker. RecepVoz también reconoce esos fallos como decisiones terminales para evitar redeliveries inútiles del mismo webhook.

## Readiness operativo

`/actuator/health` indica si la aplicación está viva. La capacidad real de atender llamadas se consulta mediante:

```text
GET /api/v1/operations/voice-readiness
GET /api/v1/operations/readiness
```

Ejemplo conceptual:

```text
Gemini       READY
OpenAI Live  OPEN / NO_CREDITS
Selected     gemini
Voice        READY
```

O, si ningún proveedor puede atender:

```text
Gemini       UNCONFIGURED
OpenAI Live  OPEN / NO_CREDITS
Selected     none
Voice        NOT READY
```

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks HTTP de Twilio se autentican mediante `X-Twilio-Signature`. El inicio de cada Media Stream además lleva metadata de ruta firmada y de corta duración que liga negocio, caller, `CallSid` y proveedor. Las tools de voz tampoco aceptan un tenant elegido por el modelo: utilizan un `RealtimeCallContext` construido desde una llamada previamente resuelta por RecepVoz.

## Regla crítica de IA

El modelo solicita acciones, pero el backend decide su resultado.

```text
IA → function call → RecepVoz → PostgreSQL → tool result → IA
```

Una reserva solo puede ser anunciada como confirmada si RecepVoz devuelve éxito. Un error como `BOOKING_SLOT_UNAVAILABLE` debe comunicarse como error, nunca como una confirmación inventada.

## Base de datos

Flyway administra el esquema y PostgreSQL sigue siendo la fuente de verdad del negocio.

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

No guardes tokens ni API keys reales en Git.

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_PROVIDER_ORDER=gemini,openai-live
TWILIO_AUTH_TOKEN=tu_token
TWILIO_PUBLIC_BASE_URL=https://tu-dominio-publico
TWILIO_MEDIA_STREAM_PATH=/ws/v1/twilio/media
```

Configura el número Twilio para llamar por POST a:

```text
https://tu-dominio-publico/webhooks/v1/twilio/voice
```

Callback de estados:

```text
https://tu-dominio-publico/webhooks/v1/twilio/status
```

Para una prueba outbound, la ruta canónica es:

```text
https://tu-dominio-publico/webhooks/v1/twilio/outbound-test
```

`/webhooks/v1/twilio/trial/voice` existe únicamente como compatibilidad temporal para configuraciones antiguas y debe eliminarse una vez que Twilio deje de apuntar allí.

## Documentación

- `docs/PRODUCT_ARCHITECTURE.md`
- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/API.md`
- `docs/design/`

## Próximos hitos comerciales

1. configurar al menos un proveedor Live con credenciales y capacidad activa
2. certificar una llamada real completa con audio, interrupciones y tool calling
3. agregar un tercer proveedor full-duplex como contingencia comercial
4. configuración del agente por tenant
5. horarios y excepciones
6. transferencia humana
7. dashboard mínimo
8. onboarding self-service
9. medición de uso y costo por llamada
10. planes, límites y billing
