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
GEMINI_LIVE_VOICE=Sulafat
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

La ruta interna `/webhooks/v1/twilio/inbound-certification` está deshabilitada por defecto. Solo una certificación real explícitamente autorizada debe habilitarla temporalmente con `TWILIO_CERTIFICATION_INGRESS_ENABLED=true`, junto con la simulación y el caller de certificación correspondientes. La antigua ruta `/webhooks/v1/twilio/trial/voice` fue retirada.

## Documentación

- `docs/COMMERCIAL_DEMO_ONBOARDING.md`
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`
- `docs/PRODUCT_ARCHITECTURE.md`
- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/API.md`
- `docs/design/`

## Estado de cierre V1

El core V1 ya implementa en `main`:

- configuración del agente por tenant;
- horarios y excepciones;
- transferencia humana;
- dashboard/operación;
- onboarding e importación asistida;
- medición de uso;
- planes, límites, entitlements y billing SaaS;
- Agenda/reservas, pedidos e inventario;
- voz multi-provider y stack Meta WhatsApp;
- launch cage, readiness, kill switches y observabilidad de piloto.

Para un primer cliente, el trabajo pendiente ya no es construir estos subsistemas. Son compuertas de activación que dependen del alcance vendido:

1. certificar voz real para el tenant si el plan incluye voz;
2. certificar entrega real de Meta WhatsApp si se incluye ese canal;
3. completar un round-trip de Mercado Pago TEST si el cobro SaaS automático se exige desde el primer lanzamiento;
4. completar onboarding, responsables, alcance, criterio de éxito y obtener `Launch Cage GO`.

No bloquean V1: un tercer proveedor de voz, merchant payments LIVE para los clientes del negocio ni la automatización total de todos los proveedores externos.

La matriz de cierre y su cruce contra las ramas históricas están en:

- `docs/repository/HELVOCA_V1_FINISH_AUDIT_2026-10-07.md`;
- `docs/repository/HELVOCA_V1_FINISH_BRANCH_CROSS_2026-10-07.md`.
