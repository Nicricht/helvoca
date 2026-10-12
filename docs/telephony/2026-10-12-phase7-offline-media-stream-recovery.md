# Helvoca · Parte 7/8 · Resiliencia de Media Streams y prevención de filtraciones (OFFLINE)

**Fecha:** 12-10-2026. **Decisión para producción:** NO-GO.
**Base inmutable:** PR #796, `c88eea19f07b361c0a00f7d77d258d68db9db29a`.
**Rama:** `test/voice-offline-lifecycle-phase7-20261012`, PR apilado sobre la Parte 6.
**No corresponde al antiguo PR #789 de otra secuencia de ocho partes.**

## Propósito

Antes de autorizar una llamada telefónica real, fortalecer las transiciones entre un Media Stream autenticado de Twilio, el registro de ciclo de vida de la llamada en PostgreSQL y la creación de sesión del proveedor de IA. Ninguna de estas pruebas llama a Twilio, usa Gemini pagado, Railway ni teléfonos de clientes.

## Hallazgos y correcciones

1. **Creación de sesión IA fallida:** tras haber admitido y registrado una llamada, una excepción antes de `ai.start()` podía dejar el registro de llamada y stream abiertos sin limpieza explícita. Ahora, tras la admisión del carrier, el stream, el identificador de llamada y el transporte quedan disponibles para cerrar la conexión y marcar `failed`/stream detenido con operaciones acotadas a SYSTEM.
2. **Fallo de arranque de sesión IA:** la limpieza común cierra también la sesión IA que llegó a crearse y evita dejar transporte de audio disponible; la señal de fallo llega al circuito de salud. El contexto de base de datos vuelve a `DENIED`.
3. **Fallo de persistencia intermedia:** si `markStreamStarted` falla después de `startInboundCall`, la ruta de fallo libera el carrier y marca la llamada como fallida, sin iniciar Gemini.
4. **Diagnósticos del callback de Twilio:** los campos `StreamError` y `StreamEvent` recibidos del carrier no son adecuados para volcarse literalmente en logs. El registro usa mensajes estáticos; conserva solamente los identificadores vinculados por `TwilioCallBindingGuard`.

## Evidencia y alcance de tests

- `TwilioMediaStreamStartupRecoveryPhase7Test`: inyección de fallo al crear sesión, al iniciar `ai.start()` y al persistir stream; verifica cierre `SERVER_ERROR`, hangup simulado, `updateStatus(failed)`, `markStreamStopped`, cierre de sesión, circuito de salud, contexto DENIED y rechazo de audio a una sesión cerrada.
- `TwilioVoiceCallbackRedactionPhase7Test`: captura de mensajes Logback para errores/eventos sintéticos, comprueba que el secreto inyectado no aparece en los logs.
- Suite existente: ruta firmada, guard de binding, validación stream SID, callbacks, sesiones de transporte, Gemimi Live (sin llamadas reales), aislamiento multiempresa y PostgreSQL 16/FORCE RLS.
- `.github/workflows/voice-offline-lifecycle-phase7.yml`: ejecución dedicada, flags de aprovisionamiento, IA y efectos comerciales desactivados.
- `.github/workflows/twilio-railway-safe-internal-gates.yml`: suite de siete jobs **alternativos**; `meta-webhook-offline-contract` reemplaza `meta-webhook-public-smoke` para no llamar a producción. Cobertura diferencial JaCoCo exige **100 %** de líneas, ramas y métodos Java cambiados aplicables, comparado con base exacta `c88eea19f07b361c0a00f7d77d258d68db9db29a`.

## Criterios de cierre de esta Parte 7

Solo certificar el **HEAD exacto del PR** si los jobs dedicados y los 7 internos alternativos concluyen `completed/success`, con conteos reales de Java/Playwright, cobertura diferencial por categoría y ausencia de fallos. Si fallan, corregir sin relajar los gates. Mantener PR en DRAFT, sin merge o deploy. Publicar evidencia en PR #797 (o número asignado por GitHub) y comentario de continuidad en #796. No iniciar Parte 8.

## Condiciones aún NO demostradas

No se comprueba en esta fase la coincidencia actual de número E.164, PhoneNumberSid, AccountSid o subcuenta real contra Railway, ni que el webhook de Twilio alcance producción, ni que Gemini Live devuelva audio audible al cliente, ni consumo facturable, acciones comerciales reales o aislamiento en llamadas entre dos empresas. Las pruebas con mocks y PostgreSQL efímero no sustituyen una llamada autorizada.

**PRODUCTION / COMMERCIAL NO-GO.** Sin llamadas telefónicas, compras, cambios de webhooks, lectura de secretos, contacto con PostgreSQL productivo, uso de Gemini pagado, flags comerciales, despliegues ni fusiones.
