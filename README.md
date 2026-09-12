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

- Twilio Voice con SIP seguro/SRTP
- OpenAI GPT-Live como ruta telefónica activa
- conversación full-duplex con interrupciones
- herramientas del backend mediante sideband/delegación
- sin fallback a Twilio `<Gather>`, `<Say>` ni voces Polly

## Arquitectura independiente de proveedores

RecepVoz define puertos propios para voz y mantiene los contratos de negocio separados de los proveedores. El flujo telefónico activo es:

```text
Caller
  ↓
Twilio
  ↓
SIP seguro / SRTP
  ↓
OpenAI GPT-Live
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

Selección actual:

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_AI_PROVIDER=openai
```

## Regla crítica multi-tenant

Las APIs administrativas no confían en un `businessId` enviado por el frontend. El backend obtiene `business_id` desde el JWT mediante `TenantProvider` y filtra las consultas por tenant.

Los webhooks de Twilio se autentican mediante `X-Twilio-Signature`. Las tools de voz tampoco aceptan un tenant elegido por el modelo: utilizan un `RealtimeCallContext` construido desde una llamada previamente resuelta por RecepVoz.

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

## Configurar Twilio + OpenAI

No guardes tokens ni API keys reales en Git.

```text
HELVOCA_TELEPHONY_PROVIDER=twilio
HELVOCA_VOICE_AI_PROVIDER=openai
TWILIO_AUTH_TOKEN=tu_token
TWILIO_PUBLIC_BASE_URL=https://tu-dominio-publico
OPENAI_API_KEY=tu_api_key
OPENAI_LIVE_ENABLED=true
OPENAI_PROJECT_ID=tu_project_id
OPENAI_WEBHOOK_SECRET=tu_webhook_secret
OPENAI_LIVE_MODEL=gpt-live-1
OPENAI_LIVE_VOICE=marin
```

Configura el número Twilio para llamar por POST a:

```text
https://tu-dominio-publico/webhooks/v1/twilio/voice
```

Callback de estados:

```text
https://tu-dominio-publico/webhooks/v1/twilio/status
```

Para una prueba outbound, la única ruta válida es:

```text
https://tu-dominio-publico/webhooks/v1/twilio/outbound-test
```

## Documentación

- `docs/PRODUCT_ARCHITECTURE.md`
- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/SPRINT3.md`
- `docs/SPRINT4.md`
- `docs/API.md`
- `docs/design/`

## Próximos hitos comerciales

1. llamada telefónica real estable
2. conversación GPT-Live estable
3. configuración del agente por tenant
4. horarios y excepciones
5. transferencia humana
6. dashboard mínimo
7. onboarding self-service
8. medición de uso y costo por llamada
9. planes, límites y billing
10. primer cliente pagado
