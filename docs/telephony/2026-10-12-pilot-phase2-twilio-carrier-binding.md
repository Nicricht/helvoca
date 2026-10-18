# HELVOCA · PILOTO REAL 2/6 · Propiedad Twilio, cuenta, firma y preflight fail-closed

**Fecha:** 2026-10-12. **Base:** PR #799, commit `1fe7ecf6d9070c84b71ecb395444088ed3fe77a1`. **Rama:** `feat/voice-real-pilot-2of6-carrier-binding-20261012`.

## Brecha encontrada

Parte 1 verificaba que `external_id` tuviera sintaxis `PN[hex]{32}`, pero el **`TwilioCallBindingGuard` real** también exige:
- `voice_verified_at != null`;
- `twilio_account_sid` con formato `AC[hex]{32}`, coincidente con el `AccountSid` del webhook firmado;
- `twilio_binding_source` = `MANAGED_PURCHASE` o `VERIFIED_EXISTING`;
- para `VERIFIED_EXISTING`, el `TwilioVerifiedExistingVoiceRouteGate` activo, tenant/phone/account fijados y auditoría de aprobación/ejecución de dos personas, junto con piloto `RUNNING` y switch global habilitado.

La actualización anterior de `external_id` **NO** acredita por sí sola el resto. Si faltan esos campos, la llamada puede ser rechazada sin llegar a Gemini, aunque el SID esté correcto y `voiceReady=true` en arranque.

## Implementación en Parte 2

1. `TwilioLiveCallAdmissionReadiness`: espejo de lectura del guard de llamada. Verifica metadatos completos, formato de cuentas y pertenencia a cuenta principal o subcuenta explícitamente autorizada. La ruta `MANAGED_PURCHASE` solo admite la cuenta principal; `VERIFIED_EXISTING` consulta, con alcance SYSTEM temporal y restauración DENIED, el guard de evidencia de dos personas. Nunca actualiza ni activa nada.
2. `VoiceRealCallPreflightService` de Parte 1: nuevo bloqueo **`TWILIO_VOICE_BINDING_NOT_CERTIFIED`** cuando no pase el guard anterior. Un PN correcto no genera un falso GO.
3. `VoiceCarrierOwnershipPreflightService`: verificación remota **solo GET**, bajo `@Transactional(readOnly=true)` y JWT de tenant; primero limita el recurso a la lista de números del tenant y exige AccountSid/PhoneNumberSid plausibles. Después ejecuta el verificador Twilio existente `TwilioExistingNumberOwnershipVerifier.verify` con cuenta y SID tomados de PostgreSQL, nunca del usuario.
4. `GET /api/v1/operations/voice-carrier-ownership-preflight?phone=%2B14355652512`: acceso **solo BUSINESS_ADMIN**. Devuelve estados `INVALID_DESTINATION`, `LOCAL_BINDING_INCOMPLETE`, `NOT_READY` o `CARRIER_AND_LOCAL_CHECKS_PASSED` y el código categórico del carrier. No devuelve credenciales, respuesta cruda de Twilio, PhoneNumberSid ni AccountSid.
5. Aunque verifique el carrier, `liveCallCertified=false` y `commercialGo=false` hasta completar la **Parte 6 real**. El endpoint no se ejecuta al arrancar ni en CI, y **NO ESTÁ DISPONIBLE EN PRODUCCIÓN** hasta un despliegue verificado.
6. Pruebas de cuentas padre/hija/ajena, binding incompleto, flag de piloto y aprobación, firma Twilio y WebSocket, tampering de SID, webhook POST, aislamiento PostgreSQL/RLS y regresión del preflight de Parte 1. Workflow específico y siete gates seguros alternativos, JaCoCo diferencial 100 % sobre base de Parte 1.

## Estado real / evidencia disponible

- Twilio Console del usuario: número `+14355652512` listado en cuenta principal, webhook `POST` dirigido a `https://helvoca-api-production.up.railway.app/webhooks/v1/twilio/voice`, callback `/status`.
- PostgreSQL producción consultado por el usuario: `external_id=PN62601c6420599e74bc940f42de2dd1e4`, `provider=TWILIO`, `active=true`, tenant `044a5d73-2df7-4e77-849f-8639d05ac30b`.
- NO se ha comprobado directamente el contenido actual de `twilio_account_sid`, `voice_verified_at`, `twilio_binding_source`, aprobación dual o el estado efectivo `pilot_launch_control` de ese tenant.
- Railway producción sigue en `main` commit `ca58693e167752bd6eab8ff4e9be770391fe4293`; PRs #796–#799 no fusionados. Los logs internos `voiceReady=true` son configuraciones, no pruebas de voz.
- Herramientas disponibles no contienen conector Twilio autenticado que permita **ejecutar hoy** el endpoint a distancia o comparar el estado del carrier sin desplegar. No afirmar que hay verificación en vivo.

## GO / NO-GO

**Preflight offline:** pendiente del CI de este HEAD. **CARRIER_VERIFIED_REAL:** pendiente de ejecución autenticada y respuesta Twilio efectiva. **LIVE_CALL_CERTIFIED:** pendiente. **COMMERCIAL_GO:** NO-GO.

**Sin compras, transferencias, cambios de webhooks, variables de Railway, merges, despliegues, llamadas reales ni gastos** durante la Parte 2. Los cambios de producción se estudian separadamente en la Parte 5. **No iniciar Parte 3 antes de cerrar y documentar la Parte 2.**
