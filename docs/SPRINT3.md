# Sprint 3 - Telefonía Twilio

Sprint 3 incorpora el transporte telefónico real sin conectar todavía el audio a OpenAI.

## Implementado

- números telefónicos por tenant
- `call_session` para historial y estado de cada llamada
- `call_transcript` preparado para Sprint 4
- webhook entrante `POST /webhooks/v1/twilio/voice`
- webhook de estado `POST /webhooks/v1/twilio/status`
- TwiML bidireccional mediante `<Connect><Stream>`
- WebSocket `/ws/twilio`
- validación `X-Twilio-Signature` para HTTP y handshake WSS
- asociación automática del cliente por teléfono cuando existe
- persistencia de `CallSid`, `StreamSid`, timestamps, duración y estado
- API multi-tenant de historial de llamadas
- API multi-tenant de números telefónicos
- prueba de integración PostgreSQL 16 + Flyway V4

## Seguridad

Los endpoints de Twilio no usan JWT porque Twilio no conoce usuarios de Helvoca. En su lugar, toda solicitud HTTP y el handshake del Media Stream deben superar la validación criptográfica de `X-Twilio-Signature` utilizando `TWILIO_AUTH_TOKEN`.

Si el token no está configurado, las solicitudes de Twilio son rechazadas.

La URL usada para validar la firma debe coincidir con la URL pública que Twilio llamó. Configure:

```text
TWILIO_PUBLIC_BASE_URL=https://TU-DOMINIO-PUBLICO
TWILIO_MEDIA_STREAM_URL=wss://TU-DOMINIO-PUBLICO/ws/twilio
```

## Configuración del número en Twilio

Voice webhook:

```text
POST https://TU-DOMINIO-PUBLICO/webhooks/v1/twilio/voice
```

Status callback de llamada:

```text
POST https://TU-DOMINIO-PUBLICO/webhooks/v1/twilio/status
```

El número debe registrarse además en Helvoca mediante `POST /api/v1/phone-numbers` usando formato E.164.

## Media Stream

Twilio abre:

```text
wss://TU-DOMINIO-PUBLICO/ws/twilio
```

El mensaje `start` debe contener el parámetro personalizado `callId`. Helvoca valida que ese `callId` corresponda al `CallSid` firmado por Twilio antes de asociar el `StreamSid`.

Los eventos `media` se reciben pero en Sprint 3 no se procesan. Sprint 4 conectará `media.payload` con OpenAI Realtime.
