# Gemini Live: integración mínima y segura de RLS (8 nov 2026)

**Objetivo:** aislar la corrección ya certificada en PR #853 que persiste los hitos de sesión y las transcripciones de Gemini Live bajo el `businessId` del negocio correspondiente, sin arrastrar el enrolamiento Twilio, migraciones, cambios de duración ni otras funciones históricas.

## Base y límites

- Base de este PR: `main@739667cdc6d08fea21dbe79634388f97982f6ba8`.
- Fuente auditada: `fix/aurora-existing-twilio-stage-20261030@d852bf4e4133ed9ace9703a57a35aa239101bd34` (#853 DRAFT), cuyo CI [#42510896547](https://github.com/Nicricht/helvoca/actions/runs/42510896547) terminó en SUCCESS, 2.628 pruebas y cobertura diferencial 100%.
- **Alcance permitido:** `GeminiLiveVoiceProvider`, `GeminiLiveVoiceSession`, pruebas unitarias de constructor y regresión PostgreSQL de aislamiento por tenant.
- **Fuera de alcance:** compras o migraciones de números, activación de voz o WhatsApp, cambios de `max-media-session-seconds`, nuevos límites o su eliminación, flags de producción, altas de clientes, datos reales, credenciales, pago, envíos y llamadas.
- No tocar `main` ni producción. Este PR debe permanecer **DRAFT** y su integración posterior requiere certificación exacta y autorización.

## Comportamiento esperado

- Inyectar el `TenantDatabaseContext` real por Spring en el proveedor Gemini Live.
- Rechazar una sesión sin `businessId` antes de procesar eventos.
- Aplicar `runAsTenant(businessId)` a callbacks WebSocket, a la limpieza por error y al cierre de sesión, restaurando el contexto DENIED del hilo.
- Persistir hitos y transcripciones del negocio A sin visibilidad o escritura cruzada en B.
- Mantener los constructores de compatibilidad usados por pruebas existentes.

## QA

Validar sobre el HEAD exacto de este PR el Fast Gate, Golden Journey, pruebas PostgreSQL18/RLS, compatibilidad PG16/18 + V99/V103, System Integration E2E y JaCoCo diferencial (líneas, ramas y métodos al 100%). Los CI del PR #853 son evidencia histórica de la fuente, **no** certifican el nuevo PR hasta que terminen los propios.

## Estado inicial

Documento de preparación únicamente. **No significa implementación, despliegue, fusión ni aceptación comercial.** Registrar la evidencia de pre-cambio en este PR antes del commit de código.