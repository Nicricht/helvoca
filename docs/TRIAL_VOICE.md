# Twilio Trial Voice mode (retirado)

El flujo Trial antiguo basado en Twilio `<Gather>` + speech-to-text + OpenAI Responses + Twilio `<Say>` quedó retirado para las pruebas de voz de RecepVoz.

No debe usarse para evaluar la experiencia final porque no representa GPT-Live full-duplex y puede hacer que la voz suene como una operadora telefónica tradicional.

## Rutas válidas para voz

Prueba outbound:

```text
POST /webhooks/v1/twilio/outbound-test
```

Producción inbound:

```text
POST /webhooks/v1/twilio/voice
```

Ambas rutas son GPT-Live SIP-only. Si GPT-Live no está configurado o no está listo, la aplicación falla cerrada con un `<Hangup/>` silencioso. No existe fallback automático a Media Streams, `<Gather>`, Polly ni `<Say>`.

## Arquitectura activa

```text
Persona
  ↓
Twilio
  ↓
SIP seguro + SRTP
  ↓
OpenAI GPT-Live
  ↓
RecepVoz
  ↓
backend/tools
  ↓
PostgreSQL
```

El SIP generado por RecepVoz mantiene `secure=true` para negociar media segura/SRTP.

## Endpoints Trial antiguos

Estas rutas se mantienen únicamente como tombstones de compatibilidad para que una configuración antigua falle de forma evidente y no vuelva a activar la arquitectura anterior:

```text
POST /webhooks/v1/twilio/trial/voice
POST /webhooks/v1/twilio/trial/gather
POST /webhooks/v1/twilio/trial/transfer-result
POST /webhooks/v1/twilio/trial/stream-status
```

`/trial/voice`, `/trial/gather` y `/trial/transfer-result` responden con un `<Hangup/>` silencioso. `/trial/stream-status` se ignora. Ninguna de estas rutas puede iniciar una conversación ni emitir TTS.

## Configuración de Twilio para pruebas

Las pruebas nuevas deben redirigir exclusivamente a:

```xml
<Response>
  <Redirect method="POST">https://helvoca-api-production.up.railway.app/webhooks/v1/twilio/outbound-test</Redirect>
</Response>
```

No configurar `/trial/voice` como webhook de prueba.

## Código legacy

Pueden seguir existiendo clases y propiedades históricas de Trial durante la migración, pero están aisladas del flujo telefónico normal. Las pruebas automáticas deben fallar si `/voice` o `/outbound-test` vuelven a emitir `<Say>`, `<Gather>` o una voz Polly, o si vuelven a invocar el flujo Trial.
