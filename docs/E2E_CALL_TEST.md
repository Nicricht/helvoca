# Helvoca - Prueba real end-to-end de llamada

Esta prueba valida el circuito completo:

```text
Teléfono real → Twilio → Helvoca HTTPS/WSS → OpenAI Realtime → Helvoca → Twilio → Teléfono real
```

## Requisitos externos

- aplicación Helvoca desplegada en una URL pública HTTPS
- WebSocket público WSS en `/ws/twilio`
- número Twilio Voice
- `TWILIO_AUTH_TOKEN`
- `OPENAI_API_KEY`
- número Twilio registrado dentro del tenant Helvoca
- `AI_AGENT` activo

Nunca guardar las claves reales en Git.

## Variables

```text
TWILIO_AUTH_TOKEN=...
TWILIO_PUBLIC_BASE_URL=https://TU-DOMINIO
TWILIO_MEDIA_STREAM_URL=wss://TU-DOMINIO/ws/twilio
OPENAI_API_KEY=...
```

Antes de llamar:

```bash
source .env
bash scripts/check-call-readiness.sh
```

## Twilio

Configurar el número para enviar Voice webhook mediante POST a:

```text
https://TU-DOMINIO/webhooks/v1/twilio/voice
```

Status callback:

```text
https://TU-DOMINIO/webhooks/v1/twilio/status
```

Helvoca devolverá TwiML con `<Connect><Stream>` hacia el WSS configurado.

## Caso mínimo de aceptación

1. llamar al número Twilio desde un teléfono real
2. escuchar el saludo configurado en `AI_AGENT`
3. preguntar por un servicio
4. comprobar que la respuesta usa el catálogo del tenant
5. pedir disponibilidad en una fecha/hora dentro del horario
6. identificarse o registrarse
7. confirmar servicio, fecha y hora
8. pedir crear la reserva
9. comprobar que el agente solo anuncia confirmación si el backend devuelve `success=true`
10. finalizar la llamada
11. consultar `GET /api/v1/calls/{id}`
12. verificar transcripción y resumen
13. verificar la reserva en `GET /api/v1/bookings`

## Casos negativos obligatorios

### Fuera de horario

Solicitar una reserva fuera del horario configurado. Debe responderse como no disponible y no debe existir una fila nueva en `booking`.

### Capability deshabilitada

Deshabilitar `CREATE_BOOKING` en `AI_AGENT`. La tool no debe exponerse a OpenAI y el backend también debe rechazar un intento inesperado con `TOOL_DISABLED`.

### Horario ocupado

Intentar reservar un slot ya ocupado. Debe resultar en `BOOKING_SLOT_UNAVAILABLE`. La IA no puede anunciar éxito.

### Interrupción

Hablar mientras el agente está respondiendo. Twilio debe recibir `clear` y el agente debe detener la respuesta pendiente.

### Firma inválida

Una petición falsa a los webhooks Twilio sin una firma válida debe ser rechazada.

## Evidencia de aprobación

Una prueba real se considera aprobada solo si existe evidencia de:

- llamada completada en Twilio
- `call_session` persistida
- `stream_sid` persistido
- transcripción USER/ASSISTANT
- resumen final
- operación de reserva coherente con el resultado backend
- ausencia de secretos en repositorio/logs

El CI valida código y reglas automatizables, pero no sustituye esta llamada real porque Twilio, el dominio público y las claves son recursos externos.
