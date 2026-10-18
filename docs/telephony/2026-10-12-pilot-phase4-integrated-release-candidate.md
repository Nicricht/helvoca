# Helvoca · Piloto real Parte 4/6 · Candidato integrado, certificación y reversión

Fecha: 2026-10-12. Repositorio: `Nicricht/helvoca`.

## 1. Punto de partida verificado

- `main` de referencia: **`ca58693e167752bd6eab8ff4e9be770391fe4293`**, también observado como commit desplegado exitosamente en Railway. La Parte 4 no toca producción.
- PR #796 (**`c88eea19f07b361c0a00f7d77d258d68db9db29a`**): auditoría multi-tenant/RLS, rama base del #797.
- PR #797 (**`73f94adcdf9a416c6cfd0ed016a4c91fe77d5cdd`**): recuperación y redacción, base del #798.
- PR #798 (**`d95c16951c3fde1f18082b86404d85c214c60320`**): certificación offline precedente, base del #799.
- PR #799 (**`82335652263599eeca4ba5ee55d9d1186cfb690c`**): preflight local. CI de 7 jobs #38301946282 `completed/success`, 7/7 al SHA exacto.
- PR #800 (**`522b4cd9eaae8edc1e2ebf3fba78dda50c0ed70e`**): titularidad de cuenta y webhook. CI #38302437940 `completed/success`, 7/7 al SHA exacto.
- PR #801 (**`8b13ea90ca6dff50f47a094825e4a7c096c8aa9e`**): resiliencia audio/Gemini y cleanup. CI #38303777064 `completed/success`, 7/7 al SHA exacto.
- Todos los PR citados permanecían **OPEN/DRAFT/UNMERGED** al preparar Parte 4. No confundir estado de rama con código presente en Railway.

La comparación remota GitHub `main...Parte3` verificó `status=ahead`, `205` commits delante, `0` detrás, `131` archivos cambiados, de ellos `86` Java y `4` SQL. Por ese tamaño, **NO** fusionar ni desplegar automáticamente el stack ni usar una promoción forzada a `main`.

## 2. Contrato nuevo de integración

`VoicePilotIntegratedReadinessService` orquesta:

1. `VoiceRealCallPreflightService.inspect(phone)` (requiere tenant JWT; número activo, SID, procedencia, cuenta, permisos de piloto, runtime de Twilio y Gemini).
2. **Solo si pasa el local**, `VoiceCarrierOwnershipPreflightService.inspect(phone)` (Twilio GET-only, identidad de cuenta, phone SID, capacidad voz y webhooks). El usuario jamás suministra el AccountSid o PN; vienen de PostgreSQL con alcance tenant.
3. Solo si ambas respuestas son positivas, reportar `LOCAL_AND_CARRIER_PREFLIGHT_PASSED`. Incluso así, `liveCallCertified=false` y `commercialGo=false` incondicionales. Nunca se habilita ruta de voz mediante este endpoint.

El servicio falla cerrado como `BLOCKED_LOCAL` sin solicitar Twilio cuando el piloto no está habilitado; `BLOCKED_CARRIER` cuando Twilio no confirma o la verificación devuelve errores. Un fallo inesperado de Twilio se representa con código genérico sin copiar mensajes del proveedor. No hay llamadas, escrituras ni operaciones de aprovisionamiento.

`GET /api/v1/operations/voice-pilot-integrated-preflight?phone=%2B14355652512` es un diagnóstico **solo BUSINESS_ADMIN**, no un endpoint público de compra, inscripción ni llamada. Está disponible **únicamente en código de PR** hasta su posterior despliegue seguro.

### Pruebas de integración negativas

- Piloto detenido, cuenta sin vincular, proveedor Gemini no listo o teléfono no E.164: nunca consultar carrier.
- Propiedad Twilio válida pero admission local inválida: NO-GO.
- Webhook incorrecto y errores de proveedor: NO-GO.
- Ambas comprobaciones correctas: `LOCAL_AND_CARRIER_PREFLIGHT_PASSED`, **no** `LIVE_CALL_CERTIFIED`.
- No publicar datos del carrier en la respuesta al operador.
- Otras suites existentes cubren Spring Security, PostgreSQL real RLS, callbacks Twilio firmados, PCMU/PCM, interrupción Gemini, recuperación de sesiones, reserva sintética y cierre.

## 3. CI requerido para cerrar Parte 4

- **Especializado** `.github/workflows/voice-live-pilot-phase4-integrated-release.yml`: pruebas unitarias y PostgreSQL16 real para preflight, Twilio, Gemini y RLS, sin llamadas ni proveedores externos.
- **Integral** `.github/workflows/twilio-railway-safe-internal-gates.yml`: exactamente siete jobs: `fast-gate`, `meta-webhook-offline-contract`, `golden-journey-release-contract`, `test`, `home-frontend-coverage`, `inventory-frontend-coverage` y `system-integration-e2e`.
- Dentro de `test`, revisar las **tres** métricas de `scripts/ci/check_diff_coverage.py` sin excepciones nuevas: líneas 100 %, ramas 100 %, métodos 100 %, con `BASE_SHA=8b13ea90ca6dff50f47a094825e4a7c096c8aa9e`.
- Verificar número de casos Java del HEAD exacto y Playwright, no extrapolar de PRs anteriores.
- **No declarar 7/7 aprobado mientras haya jobs `in_progress`, `queued`, `failure` o `cancelled`.** No confundir workflow especializado verde con certificación integral.

## 4. Estrategia de liberación y reversión para Parte 5 (plan, NO ejecutado)

1. Obtener estado `READY` por tenant específico y número correcto, evidencia de verificación real en Twilio API vía endpoint protegido y corroboración explícita de `twilio_binding_source`, `twilio_account_sid`, `voice_verified_at` y aprobación dual cuando corresponde. **No inventar datos faltantes**.
2. Confirmar identidad del propietario cuenta principal vs subcuenta y que el inbound `POST` firmado apunta al dominio público esperado. Revisar suscripción y límite del piloto, cuotas de Gemini y presupuesto telefónico antes de permitir tráfico.
3. Preparar respaldo de esquema y verificación de migraciones PostgreSQL contra versión productiva. Evaluar todos los commits SQL y posibles incompatibilidades; no aplicar scripts indiscriminadamente.
4. Definir SHA exacto que se despliega, verificación Railway `SUCCESS`, health y logs; proteger el resto de tenants en modo no disponible. No habilitar global kill switch por simple satisfacción del preflight.
5. Si degradación de servicio, detener efectos externos del piloto y restaurar servicio a SHA estable previo con el método de Railway documentado. **No** revertir automáticamente migraciones ni restablecer campos de PostgreSQL sin análisis de cambios persistidos.
6. Prueba real de Parte 6 solo tras verificación carrier, política de consentimiento, destino, duración máxima, límite de gasto y capacidad de finalizar inmediatamente.

Este es un plan de reversión; no implica que exista un snapshot reciente ni garantiza que una reversión de código pueda deshacer escrituras de esquema.

## 5. Estado de salida de Parte 4

`LIVE_CALL_CERTIFIED=PENDING`; `COMMERCIAL_GO=NO-GO`. Ningún cambio de producción autorizado por un resultado verde de CI. La **Parte 5** queda expresamente fuera de esta sesión.
