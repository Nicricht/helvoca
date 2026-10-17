# Helvoca · Parte 6/8 · Certificación de seguridad integral, PostgreSQL, RLS y voz

**12 de octubre de 2026. Validación offline. Production / Commercial: NO-GO.**

## Alcance y árbol exacto

Esta fase se apila sobre el PR #795 certificado offline, `c9a233a5336cc0df50ba35302941929c3f17b235` y, por su historia, incluye #794 (enrolamiento seguro), #793 (auditoría), #792 (integración), #791 y #790. Ninguno se fusiona a `main`.

PostgreSQL 16 de Testcontainers, Flyway, roles reales `helvoca_runtime` y `helvoca_system`, y políticas `FORCE ROW LEVEL SECURITY` son los mecanismos bajo prueba. **No se utiliza Railway ni un Postgres productivo.** No se accede a cuentas Twilio o Gemini, no hay audio de clientes, ni llamadas, ni servicios pagados.

## Auditoría de cobertura existente

- `PostgresRowLevelSecurityIntegrationTest`: aislamiento cross-tenant en entidades comerciales, operaciones, auditorías, tenant context, pool scrubbing, invariantes de claves foráneas, idempotencia y permisos.
- `TwilioTenantBindingIntegrationTest`: dos tenants, cuatro rutas entrantes/callback/audio, CallSid/StreamSid/SID de cuenta, RLS y `MANAGED_PURCHASE` versus `VERIFIED_EXISTING` inhabilitado.
- `TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest`: prueba fase 4 de activación **solo en configuración sintética** después de un claim y dos aprobadores, bloqueo de un piloto no registrado o en PAUSED.
- `RealtimeToolTenantScopeIntegrationTest`: herramientas de la recepcionista IA restringidas a su negocio sin privilegio SYSTEM ambiental.
- `GeminiLiveVoiceSessionTest`, `GeminiLiveAudioInterruptionContractTest`, `GeminiLiveToolReplayContractTest`: puente de audio, interrupciones y herramientas sin Google real.
- `e2e/` y frontend: Playwright real en navegador y system E2E de stack local.

## Hueco específico reforzado

El test de PostgreSQL para `VERIFIED_EXISTING` necesita evidencias **negativas reales**, más allá de mocks unitarios:

1. Conexión como tenant dueño y como tenant extranjero: `SELECT` y `DELETE` en `twilio_existing_enrollment_request`, `claim` y `audit` deben fallar por privilegios/RLS, incluso si el negocio es dueño del número. SYSTEM puede observar, no tenant.
2. Un `EXECUTED/APPLIED` emitido por el solicitante no satisface la aprobación del segundo operador; `EXECUTION_BLOCKED` del aprobador tampoco. Solo la entrada correcta por el aprobador permite la evaluación sintética.
3. Después de evidencia correcta, cualquier discrepancia entre número de destino E.164, PhoneNumberSid o owning AccountSid almacenados y el claim inmutable vuelve a cerrar la ruta, sin perder el registro ni moverlo de tenant.
4. Otro negocio con su propia fila `VERIFIED_EXISTING`, cuenta y número diferentes no puede usar el pinning ni la evidencia del tenant piloto. El contexto ThreadLocal vuelve a DENIED después de cada operación.
5. La preparación de fixtures de otro negocio mantiene la protección V101: la procedencia certificada se asigna solo con rol SYSTEM, nunca con el usuario de migración o un tenant.

## Gates exactos

- Ejecutar un workflow especializado offline con **Postgres 16 Testcontainers**, `TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest`, `TwilioTenantBindingIntegrationTest`, `PostgresRowLevelSecurityIntegrationTest` y `RealtimeToolTenantScopeIntegrationTest`.
- Ejecutar el flujo de integración segura alternativo: Fast Gate, Golden Journey, backend completo con JaCoCo diferencial **100 % en líneas/ramas/métodos aplicables**, Home, Inventario, System E2E, Playwright, y webhook **simulado**. **No** se sustituye conceptualmente por 7 checks oficiales.
- Cada resultado corresponde al **HEAD SHA exacto de esta rama**. Cualquier falla o CI pendiente significa certificación no completada.
- La garantía offline no cubre titularidad real del número, subcuenta, SID reales, callback público, RLS actual de Railway, credenciales/capacidad Google, consentimientos ni facturas.

## Decisión operativa

- `HELVOCA_TWILIO_EXISTING_VOICE_ROUTE_ENABLED` sigue OFF por defecto.
- No desplegar, fusionar, activar piloto, adquirir números, usar Gemini pagado, registrar claves, consultar producción ni ejecutar llamadas.
- La seguridad local comprobada no constituye permiso para abrir llamadas. **PRODUCTION / COMMERCIAL: NO-GO**.
- No comenzar Parte 7 automáticamente; esperar cierre de esta fase y autorización contextual.
