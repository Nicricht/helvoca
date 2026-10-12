# Helvoca · Parte 8/8 · Cierre técnico OFFLINE y preparación de pruebas telefónicas reales

**Fecha:** 2026-10-12. **Base certificada:** Parte 7, PR #797, `73f94adcdf9a416c6cfd0ed016a4c91fe77d5cdd`.
**Rama candidata:** `test/voice-final-certification-phase8-20261012`.
**Decisión hasta nueva autorización:** **NO-GO comercial / NO-GO llamadas reales**.

Esta parte completa las pruebas técnicas offline y deja un procedimiento reproducible para una posterior certificación telefónica real. **Una suite verde no demuestra que un cliente haya oído la voz de Gemini Live por Twilio** y no habilita por sí sola cambios productivos.

## Brecha de evidencia eliminada

Antes de este cambio, el estado de certificación podía sumar como hitos independientes `BOOKING_CREATED` y `BOOKING_CANCELLED`, sin correlacionar sus IDs. Una llamada con reserva A creada y reserva B cancelada podía cumplir falsamente ambos checks. `CallCertificationService.verifyAndCleanup` también podía declarar éxito en esa situación si la reserva A ya aparecía cancelada por otra vía.

Ahora:
- `CallCertificationBookingEvidence.hasCreatedBooking` exige `success`, `actionType=BOOKING_CREATED`, `entityType=BOOKING` y un `entityId` UUID.
- `hasMatchingCancellation` exige un `BOOKING_CANCELLED` exitoso con `entityType=BOOKING` y el **mismo entityId para cada reserva válida creada durante la llamada**.
- `CallCertificationStatusService.current()` mantiene los 12 checks técnicos, pero sus dos hitos de reservas usan evidencia correlacionada. El estado comercial se mantiene `commercialCertified=false` incluso con 12/12.
- `CallCertificationService.verifyAndCleanup()` rechaza la cancelación de otra reserva sin suplantar la evidencia de la reserva creada. Mantiene su limpieza de reservas de certificación dentro del tenant.

## Matriz de evidencia y condición de prueba

| Comprobación | Evidencia offline automatizable | Evidencia real pendiente |
|---|---|---|
| Firma Twilio y binding de tenant | Validación de firmas sintéticas, AccountSid/CallSid, WebSocket firmado, guard y PostgreSQL RLS | Twilio: estado actual del PhoneNumberSid, propiedad de subcuenta, webhook HTTPS POST y firma en llamada real |
| Media Stream y proveedor de voz | Sesiones simuladas, inicio/finalización, interrupciones, reintentos, fallos y limpieza | Confirmar conexión Media Stream de una llamada real y sesiones Gemini Live |
| Audio bidireccional audible | Tests de manejo de frames/contratos de audio, sin percepción humana | Persona autorizada confirma que escucha y conversa, sin demoras/interrupciones inaceptables |
| Herramientas de agenda y cancelación | Reserva/cancelación **del mismo ID**, consulta de servicios, disponibilidad, transcripción y resumen | Reserva de prueba acotada que se verifica y retira sin tocar reservas reales |
| Aislamiento de dos negocios | Testcontainers PostgreSQL 16, políticas RLS forzadas, intentos cross-tenant bloqueados | Dos números, cuentas y negocios autorizados con llamadas separadas sin cruce de datos |
| Calidad, costos y límites | CI offline y estimaciones internas, sin cobros | Consumo real de Twilio y Gemini conciliado con tarifa vigente y presupuesto máximo |
| Cierre y auditoría | Estado `COMPLETED`, stream detenido, limpieza y logs saneados, sin firma externa real | Carrier finalizó, audio recibido, CallSid/StreamSid vinculados, costos y eliminación de datos de prueba |

## Precondiciones obligatorias para una prueba real futura

- **Consentimiento y alcance por escrito:** autorización específica para usar el número Twilio y los números de destino, una ventana horaria acordada, participante que conteste y prueba limitada a datos sintéticos. Ninguna llamada real se inicia desde CI.
- **Presupuesto y límites:** tarifa revisada en cuentas respectivas, monto máximo autorizado por proveedor y sesión, duración máxima, límite de concurrencia = 1 durante el piloto, plan para cortar y revertir cambios; sin compras ni aprovisionamiento de números.
- **Identidad y posesión comprobadas:** número E.164, PhoneNumberSid, AccountSid/subcuenta, negocio/tenant y referencias actuales de PostgreSQL de Railway deben coincidir mediante accesos autenticados read-only. No solicitar tokens ni secretos por chat.
- **Arranque seguro:** verificar flags explícitos de voz, one-shot, migraciones y control de efectos externos; probar webhooks de manera autorizada y solo hacer cambios específicos después de otra confirmación.
- **Aislamiento y privacidad:** tenant A y B separados, no divulgar datos personales en respuestas, no registrar audio/transcripción íntegra o tokens; evidencia redactada para informes, registros de costes y audit trail.
- **Prueba mínima observable:** llamar al número verificado, oír saludo, responder a la IA, pedir lista de servicios/disponibilidad, crear/cancelar una cita descartable, terminar llamada, comprobar estado de carrier/stream/IA y auditar RLS en ambos negocios.
- **Criterio final:** emitir reporte `GO` solo después de evidencia real de todos los puntos y autorización para el servicio. Cualquier falta de coincidencia, fallo de audio o gasto fuera del límite significa `NO-GO`.

## CI requerido para declarar terminada esta Parte 8 en alcance offline

1. Ejecutar `.github/workflows/voice-final-certification-phase8.yml` en el HEAD exacto. Exigir `completed/success`, con pruebas de correlación de reservas, 12 checks, limpieza y PostgreSQL 16/RLS sin externos.
2. Ejecutar `.github/workflows/twilio-railway-safe-internal-gates.yml` en el mismo HEAD; **7/7 jobs SUCCESS**, backend Maven, Playwright, Home/Inventory y sistema E2E, sin webhook público.
3. Aplicar JaCoCo **100 % diferencial** en líneas, ramas y métodos Java modificados con `BASE_SHA=73f94adcdf9a416c6cfd0ed016a4c91fe77d5cdd`, sin reducir mínimos.
4. Documentar commit SHA, runs, conteos verificados, porcentajes efectivos, diferencias frente a la base y limitaciones en PR de Parte 8.
5. Mantener PR `DRAFT`, sin merge, sin deploy, sin operaciones Twilio/Railway/Gemini reales. No se confundirán los resultados de PR anteriores con los de este commit.

**Estados finales independientes:**
- `TECHNICAL_OFFLINE_CERTIFIED`: solo si los dos workflows terminan `success` en el HEAD exacto.
- `LIVE_CALL_CERTIFIED`: **PENDIENTE**, requiere pruebas y consentimiento reales.
- `COMMERCIAL_GO`: **NO-GO** hasta que exista evidencia real actual y presupuesto autorizado.
