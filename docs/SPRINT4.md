# Helvoca - Sprint 4 - Agente de voz OpenAI Realtime

## Objetivo

Convertir la capa telefónica de Sprint 3 en un agente de voz funcional. El audio de una llamada Twilio entra a Helvoca por Media Streams, se envía a OpenAI Realtime y el audio generado vuelve a la misma llamada.

## Flujo

```text
Cliente
  ↓ voz
Twilio
  ↓ Media Stream PCMU
/ws/twilio
  ↓
OpenAiRealtimeBridge
  ↓ WebSocket servidor a servidor
OpenAI Realtime
  ↓ function calls
RealtimeToolService
  ↓
PostgreSQL / lógica Helvoca
  ↑
function_call_output
  ↑
OpenAI Realtime
  ↓ audio PCMU
Twilio
  ↓
Cliente
```

## Implementado

- una sesión OpenAI Realtime por llamada
- `gpt-realtime-2.1` configurable
- audio entrante y saliente `audio/pcmu`
- audio Twilio → `input_audio_buffer.append`
- audio OpenAI → evento `media` de Twilio
- Server VAD
- interrupción de respuesta por voz del cliente
- `clear` del buffer de Twilio para barge-in
- transcripción de usuario con `gpt-live-transcribe` configurable
- transcripción de respuesta del asistente
- persistencia ordenada en `call_transcript`
- tools de negocio controladas por backend
- consulta de negocio
- catálogo de servicios
- búsqueda de Knowledge Base
- identificación del caller
- registro/actualización del caller
- consulta real de disponibilidad
- creación real de reservas con `BookingSource.AI_CALL`
- resumen automático post-llamada
- resumen visible en el detalle de llamada

## Regla crítica de tool calling

El modelo no es autoridad sobre el resultado de una operación.

```text
Modelo
  ↓ pide create_booking
Helvoca backend
  ↓ valida tenant, cliente, servicio, fecha y disponibilidad
PostgreSQL
  ↓
resultado estructurado
  ↓
function_call_output
  ↓
Modelo comunica el resultado
```

Una reserva solo puede comunicarse como confirmada cuando `create_booking` devuelve:

```json
{
  "success": true,
  "data": {
    "bookingId": "...",
    "status": "CONFIRMED"
  },
  "error": null
}
```

Si el horario dejó de estar disponible, el backend devuelve `BOOKING_SLOT_UNAVAILABLE` y la IA debe informar el conflicto, nunca inventar éxito.

## Seguridad multi-tenant

Las tools no aceptan `businessId` como fuente de autoridad. `businessId`, `callId`, teléfono del caller y `streamSid` provienen del `RealtimeCallContext` creado después de validar la llamada Twilio.

Aunque el modelo intentara enviar otro `businessId` dentro de sus argumentos, `RealtimeToolService` lo ignora.

## Configuración

Variables mínimas:

```text
TWILIO_AUTH_TOKEN=
TWILIO_PUBLIC_BASE_URL=https://tu-dominio-publico
TWILIO_MEDIA_STREAM_URL=wss://tu-dominio-publico/ws/twilio
OPENAI_API_KEY=
```

Opcionales:

```text
OPENAI_REALTIME_MODEL=gpt-realtime-2.1
OPENAI_REALTIME_VOICE=marin
OPENAI_TRANSCRIPTION_MODEL=gpt-live-transcribe
OPENAI_SUMMARY_MODEL=gpt-5.6-luna
OPENAI_REALTIME_URL=wss://api.openai.com/v1/realtime
OPENAI_RESPONSES_URL=https://api.openai.com/v1/responses
```

Nunca guardes tokens reales en Git.

## Base de datos

Flyway `V5__sprint4_ai_voice.sql` agrega `call_summary`.

La transcripción continúa utilizando `call_transcript` de Sprint 3.

## Resumen automático

Al terminar el Media Stream, el resumen se genera de forma asíncrona mediante la Responses API. El prompt exige que el resumen sea factual y que no invente reservas, pagos, pedidos ni otras acciones.

Si OpenAI no está configurado, Helvoca no abre una sesión de IA para una llamada. Si el resumen falla después de una conversación ya completada, la llamada y su transcripción se conservan y se registra un resumen de fallback sin inventar contenido.

## Definition of Done

- el Media Stream ya no descarta eventos `media`
- audio bidireccional implementado
- VAD e interrupciones implementados
- transcript persistido
- tools ejecutadas exclusivamente en backend
- tools aisladas por tenant
- reserva por voz protegida por validaciones de backend
- resumen post-llamada persistido
- configuración sin secretos reales
- pruebas automatizadas de reglas críticas
- CI verde antes del merge
