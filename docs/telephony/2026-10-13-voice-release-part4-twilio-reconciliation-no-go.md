# Helvoca · Parte 4 de 10 · Reconciliación controlada de un Twilio existente

**Fecha:** 2026-10-13. **Base:** PR #813 SHA `25f45a898ec531d90d06a7e547e0a16f1f4fae3a`.
**Estado:** preparación y pruebas aisladas, NO-GO productivo.

## 1. Punto de partida real

El operador ejecutó el auditor Twilio REST `GET` + PostgreSQL de Railway en
transacción `READ ONLY`. El código recibió propiedad/estado/webhooks sin
discrepancias y encontró exactamente estos tres bloqueos:

```text
DB_ACCOUNT_SID_MISSING_OR_MISMATCH
DB_PROVENANCE_UNVERIFIED
DB_VOICE_TIMESTAMP_MISSING
```

Con Flyway de producción en **V99**, esas columnas aún no existen. La ausencia
de `twilio_account_sid` en V99 **no prueba que su valor sea incorrecto**.
Las columnas aparecen en V100 y la procedencia `VERIFIED_EXISTING` se
habilita en V101. Las migraciones no deben asignarles valores por defecto,
copiar SIDs anteriores ni activar llamadas.

## 2. Flujo de admisión, sin llamadas

El código existente se inspeccionó y se mantuvo el siguiente protocolo:

1. Un actor con JWT válido, rol `PLATFORM_ADMIN` vigente en PostgreSQL y
   ámbito `SYSTEM` presenta el **tenant + ID interno del teléfono +
   cuenta Twilio esperada**. El teléfono debe pertenecer a ese tenant,
   ser Twilio y no tener procedencia verificada previa.
2. PostgreSQL toma `FOR UPDATE` de la fila elegible y rechaza otra solicitud
   `PENDING/APPROVED` válida para ese teléfono. Se crea petición `PENDING`
   con caducidad máxima de 15 minutos y auditoría `REQUESTED`.
3. Otro administrador distinto e igualmente autenticado aprueba antes de
   la caducidad. El primer actor no puede aprobarse a sí mismo, ni un
   tercero reemplazar la aprobación. El estado pasa a `APPROVED`.
4. Únicamente el administrador aprobador ejecuta; se vuelve a comprobar que
   el solicitante conserve sus privilegios y que la solicitud esté vigente.
   `TwilioExistingEnrollmentPlanner` hace **GET autenticado** al carrier
   y verifica E.164, cuenta principal/subcuenta autorizada, SID PN, voz,
   webhooks POST, propiedad, disponibilidad y rutas incompatibles.
5. `TwilioExistingEnrollmentReconciler` vuelve a comprobar fila y
   evidencia, hace un `UPDATE` compare-and-set y añade la reclamación
   de idempotencia en una transacción; si falla cualquiera, ninguna
   promoción incompleta debe persistir. Solo entonces se registra
   `VERIFIED_EXISTING`, `twilio_account_sid` y `voice_verified_at`.
6. Se registra auditoría `EXECUTED`. La mera existencia de esos campos
   **nunca habilita llamadas**: `TwilioVerifiedExistingVoiceRouteGate`
   exige piloto `RUNNING`, interruptor global, tenant/teléfono/cuenta
   fijados explícitamente y la evidencia de dos personas. Todas las
   banderas de pruebas y producción permanecen desactivadas.

La prueba integrada nueva
`TwilioExistingReconciliationReadinessPostgresIntegrationTest`
ejercita 5 escenarios en PostgreSQL 18 Testcontainers y con Twilio
**simulado (Mockito)**, no utiliza credenciales ni peticiones al carrier:

- Petición `PENDING` -> aprobación independiente -> GET simulado con
  identidad coherente -> `APPLIED`, con reclamación y auditoría, campos
  guardados una vez, teléfono aún inactivo.
- Peticiones duplicadas, antes y después de la aprobación, bloqueadas
  sin reemplazar personas ni abrir otra solicitud vigente.
- SID PN devuelto distinto al histórico: `BLOCKED`, ninguna fila
  promovida ni reclamación insertada, evidencia `EXECUTION_BLOCKED`;
  un GET posterior correcto permite reintento aprobado.
- Solicitante intentando autoaprobar o un tercero ejecutando no pueden
  provocar ningún GET ni insertar evidencia.
- Un tenant distinto no puede hacerse pasar por propietario; una fila
  `VERIFIED_EXISTING` ya registrada no puede inscribirse nuevamente.

Además se corrigió `TwilioLegacyBindingInspector`: una fila con todos los
datos `VERIFIED_EXISTING` válidos se considera **localmente completa**,
del mismo modo que `MANAGED_PURCHASE`. Esto NO constituye prueba del
carrier ni autoriza llamada. Valores desconocidos o campos ausentes
continúan informando bloqueos sin modificar la fila.

## 3. Duplicados, persistencia y seguridad

- No modificar V100–V103 ni sus checksums. Son los contratos de Flyway
  ya validados con PostgreSQL 18.
- `V102` aplica PK única al `change_id`, UNIQUE por `phone_number_id`
  y UNIQUE por SID de carrier en la tabla de reclamación. `V101` impone
  también un índice de unicidad sobre SIDs certificados. No pueden
  certificarse de forma independiente dos empresas con el mismo PN.
- Todas las tablas de solicitud, aprobación y reclamación permanecen
  SYSTEM-only con RLS forzado de V102–V103. No se ofrece endpoint tenant.
- Los DTO públicos solo exponen `changeId` y estados genéricos; no
  tokens Twilio ni SIDs en logs del flujo.
- La caducidad es obligatoria; la petición antigua no es autorización
  para una nueva. El retry no puede omitir la verificación de carrier
  antes de un primer `APPLIED`; un replay solo es idempotente para el
  mismo conjunto de tenant, teléfono, humanos y cuenta.

## 4. Para una operación futura real (NO autorizada aquí)

En etapas posteriores se necesitarán una copia reciente cifrada de
PostgreSQL 18 y su restauración comprobada, validación de checksums Flyway
sobre el estado V99 vigente, aprobación para V100–V103 en una ventana
controlada, dos administradores humanos, propietario Twilio actual probado
por GET y supervisión de la transacción. Si faltan los privilegios o
los datos reales de dos operadores, no automatizar su identidad.

**No ejecutar este procedimiento en producción todavía.** Una prueba
sintética con carrier simulado no prueba una respuesta real posterior
a un despliegue, ni autoriza aplicar SQL manualmente ni habilitar Gemini.
Devolver `BLOCKED` es el comportamiento correcto ante cualquier duda.

## 5. Certificación y entrega

La etapa se considera terminada **solo** con checks sobre el HEAD final:
suite nueva PostgreSQL 18, suites previas de reconcilación, propiedad,
administración y route gate, tests de clasificación de procedencia, y
control de integridad inmutable V100–V103. Hasta finalizar CI, no
atribuir resultado exitoso a ningún commit nuevo.

**Parte 5 siguiente, aún no iniciada:** certificación integrada de
backend/frontend y gates de CI del candidato, sin peticiones HTTP
productivas ni llamadas reales.

`PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`,
`COMMERCIAL_GO=NO-GO`. Sin merge, cambios de Twilio/Railway, SQL productivo,
compras, despliegues ni llamadas.
