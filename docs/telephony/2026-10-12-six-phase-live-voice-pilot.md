# HELVOCA · PILOTO DE LLAMADAS REALES · PLAN DE 6 PARTES

Fecha: 2026-10-12. Fuente de verdad: PR #798, commit base `d95c16951c3fde1f18082b86404d85c214c60320`. Proyecto Railway: `Helvoca`, servicio `helvoca-api`. Número autorizado para conciliación: `+14355652512`. Tenant: `044a5d73-2df7-4e77-849f-8639d05ac30b`. Phone Number SID: `PN62601c6420599e74bc940f42de2dd1e4` (valor en DB verificado mediante captura SQL tras corrección manual).

## Objetivo y división cerrada

No mezclar certificación offline con una llamada real. Cada parte termina con evidencia y un checkpoint en GitHub. No adelantar las siguientes.

| Parte | Alcance acotado | Criterio de cierre |
| --- | --- | --- |
| **1/6: seguridad y preflight local** | Inventario read-only; verificación de configuración, tenant, SID local, proveedor seleccionado, guard; endpoint protegido sin efectos externos; pruebas de negativas y CI | Código probado en PR draft; sin merge, despliegue ni gasto |
| **2/6: Twilio y tenencia** | Contratos del AccountSid padre/subcuenta, titularidad de PhoneNumberSid, webhook firmado, guard y suscriptores; reconciliación sin compras o transferencias | Pruebas de cruce y caso real read-only cuando exista acceso Twilio; cualquier ausencia marcada PENDING, no inferida |
| **3/6: Gemini y audio** | Sesiones reales del código, rutas WebSocket, interrupciones, codecs, retrocesos, límites de duración; inyección de fallos simulada | Pruebas especializadas, fallback/cleanup fail-closed, sin gastos externos |
| **4/6: integración certificada** | Revisar dependencias de PR #790-#798, rama candidata íntegra, 7 gates seguros, Java/JaCoCo, Playwright, PostgreSQL RLS y scripts de rollback | CI verde del SHA exacto; aprobaciones / estado de merge expresos |
| **5/6: preparación productiva controlada** | Identidad de recursos, estado de piloto, plan de adopción sin afectar otros tenants, rollback; entrega o despliegue acotado si capacidades, impacto y pruebas lo permiten | Solo reportar deploy cuando Railway confirme SUCCESS y el código/flags concuerden |
| **6/6: validación telefónica real** | Prueba de una llamada autorizada con presupuestos verificables, duración limitada, consentimientos, audio bidireccional, reserva sintética y cancelación mismo UUID, facturación y reporte | Solo LIVE_CALL_CERTIFIED con evidencia real: nunca inferirla del CI. Si no hay API Twilio conectada o interlocutor disponible, emitir bloqueo explícito |

## Estado inicial verificado

- Railway producción `helvoca-api` y Postgres: último despliegue `SUCCESS`, backend en `main` commit `ca58693e167752bd6eab8ff4e9be770391fe4293` y sin staged changes. PRs #796, #797 y #798 siguen draft / sin fusionar.
- Logs del último arranque observado: `CHANNEL_RUNTIME_READINESS twilioCredentials=true securePublicBaseUrl=true mediaStreamUrl=true voiceReady=true selectedVoiceProvider=gemini`. **No demuestra llamada real ni que el backend haya recibido audio**.
- Twilio Console mostrado por usuario: número en cuenta principal, webhook entrante `POST` a `/webhooks/v1/twilio/voice` y estado a `/webhooks/v1/twilio/status`. El usuario confirmó `GEMINI_LIVE_ENABLED=true`, `HELVOCA_TWILIO_SUBACCOUNT_VOICE_CONFIGURE_ON_STARTUP=false`.
- Railway OAuth entrega solo **nombres de variables**, no valores; no hay conector Twilio conectado disponible para comprobar cuenta, tarifas o realizar llamadas. No recrear secretos sellados ni pedir tokens en chat.
- Medición de tráfico 7 días de ruta exacta `/webhooks/v1/twilio/voice`: 0 solicitudes. Status callback: 4 HTTP 2xx; stream-status: 2 HTTP 2xx. No implica audio o firma Twilio auténtica.
- El estado real del tenant en `pilot_launch_control`, el opt-in global, su suscripción y la pertenencia actual del PN al AccountSid de Twilio **NO han sido verificados directamente**. No activar flags ni transferir el número por suposición.

## Parte 1: implementación

Se añade `GET /api/v1/operations/voice-real-call-preflight?phone=...`, exclusivo de BUSINESS_ADMIN con JWT y resolución del tenant desde `TenantProvider`, transacción `readOnly`. Solo consulta la tabla local, `ChannelRuntimeReadinessService.snapshot()` y `ControlledPilotExternalEffectGuard.evaluate(VOICE)`.

Se bloquea cuando el teléfono no es E.164, no está activo para ese tenant, carece de PN SID válido, falta el runtime de Twilio, Gemini no es el proveedor disponible seleccionado o el tenant no está inscrito/habilitado en el piloto. **No acepta un tenant no inscrito** aunque el flujo antiguo permitiera operar sin `pilot_launch_control`.

La respuesta nunca revela credenciales ni tokens. `liveCallCertified=false` y `commercialGo=false` **incluso cuando `localPreflightPassed=true`**. Éxito local significa solo `LOCAL_PREFLIGHT_PASSED`, no `GO comercial` ni `se hizo una llamada`.

## Límites operativos

El consentimiento amplio del propietario permite investigación y desarrollo, pero la protección contra cargos imprevistos sigue vigente: sin método activo de llamada, presupuesto cuantificado, duración máxima, verificación en tiempo real del carrier y mecanismo de corte, **no ejecutar operaciones facturables**. No generar números, transferir titularidad, revelar secretos ni desactivar límites por defecto.

**Estado inicial LIVE_CALL_CERTIFIED=PENDING. COMMERCIAL_GO=NO-GO.**
