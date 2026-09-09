# Twilio Trial Voice mode

Helvoca mantiene dos caminos telefónicos:

1. **Producción / Realtime**: Twilio `<Connect><Stream>` ↔ WebSocket ↔ OpenAI Realtime.
2. **Trial**: Twilio `<Gather input="speech">` → webhook HTTP de Helvoca → OpenAI Responses + tools → `<Say>`.

El modo Trial existe para validar el producto sin pagar Twilio antes de la primera prueba. No reemplaza la arquitectura Realtime.

## Variables

```text
TWILIO_TRIAL_MODE_ENABLED=true
TWILIO_TRIAL_PHONE_NUMBER=+17372508034
TWILIO_TRIAL_BUSINESS_NAME=Helvoca Restaurante Demo
TWILIO_TRIAL_GREETING=Hola, soy Helvoca. Puedes preguntarme por los servicios o pedirme una reserva.
TWILIO_TRIAL_LANGUAGE=es-CL
TWILIO_TRIAL_MAX_TURNS=8
OPENAI_TRIAL_MODEL=gpt-5.6-luna
```

`TWILIO_AUTH_TOKEN` y `OPENAI_API_KEY` siguen siendo secretos y nunca se versionan.

Al iniciar con Trial habilitado, Helvoca asegura un tenant demo asociado al número configurado, un servicio `Reserva de mesa` y una entrada mínima de Knowledge Base.

## Webhooks

Inicio de llamada Trial:

```text
POST /webhooks/v1/twilio/trial/voice
```

Turnos de reconocimiento de voz:

```text
POST /webhooks/v1/twilio/trial/gather
```

Ambos endpoints siguen protegidos por validación `X-Twilio-Signature`.

## Configuración en Try out Voice

En Twilio:

```text
Voice → Try out Voice → Inbound → Custom
```

Usar TwiML personalizado que redirija al endpoint público de Helvoca:

```xml
<Response>
  <Redirect method="POST">https://helvoca-api-production.up.railway.app/webhooks/v1/twilio/trial/voice</Redirect>
</Response>
```

Después guardar la prueba y llamar desde el número verificado al número Trial de Voice.

## Flujo

```text
caller
  ↓
Twilio Trial number
  ↓
/trial/voice
  ↓
<Gather input="speech">
  ↓
SpeechResult
  ↓
/ trial / gather
  ↓
OpenAI Responses
  ↓
RealtimeToolService
  ↓
PostgreSQL
  ↓
<Say>
  ↓
caller
```

La llamada conserva las mismas reglas de negocio del agente Realtime. Las tools reciben un `RealtimeCallContext` construido desde una llamada Twilio validada y no aceptan un `businessId` elegido por el modelo.

## Limitaciones deliberadas

- conversación por turnos, no audio full-duplex;
- pequeñas pausas entre cada intervención;
- máximo configurable de turnos para respetar el límite de saltos del Trial;
- timeout corto hacia OpenAI para responder antes del timeout de TwiML;
- si la segunda respuesta de IA después de una tool tarda demasiado, Helvoca verbaliza directamente el resultado seguro devuelto por el backend;
- `<Stream>` sigue reservado para la ruta Realtime una vez que la cuenta Twilio sea de producción.
