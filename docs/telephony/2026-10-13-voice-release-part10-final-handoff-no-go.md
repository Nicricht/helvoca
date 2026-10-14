# Helvoca / RecepVoz · Parte 10/10 · Entrega final del candidato OFFLINE

**Fecha de captura de evidencia:** 2026-10-13.  
**Repositorio:** `Nicricht/helvoca`.  
**Base apilada certificada:** PR #819, SHA `663ef0c54e02e0e86b828cd913c2aab4bc5b42da`.  
**Rama final propuesta:** `test/voice-release-part10-final-handoff-no-go-20261013`.  
**Límite innegociable:** este documento es un expediente técnico offline, NO un plan preautorizado de ejecución sobre producción.

## 1. Qué queda entregado

- Los PR #811 a #819 permanecían DRAFT, OPEN, UNMERGED al momento de la auditoría. Cada uno estaba apilado sobre el HEAD de la rama anterior. El PR #810 es la raíz previa de esa cadena, no la fase 1.
- La Parte 9 se comprobó mediante dos runs independientes `38378146552` (push) y `38378187941` (PR) sobre exactamente el SHA de su HEAD: **8/8 jobs success cada uno**.
- En la Parte 9: **2.244 pruebas Java**, **615 tests Playwright generales**, **147 Home**, **150 Inventario**, **79 regresiones multiempresa**, **59 Golden Journey** y **5 pruebas de sistema**. Sin fallos/errores/skips reportados por los totales Java citados.
- JaCoCo con **100% de cobertura diferencial aplicable** de cambios desde la base Parte 3 `25f45a898ec531d90d06a7e547e0a16f1f4fae3a`, no cobertura total del código fuente completo. Home e Inventario tuvieron 100% por **tres y seis archivos vigilados respectivamente**; no se afirma 100% de todo React.
- El estado de Railway observado por una consulta de **solo lectura**: API y PostgreSQL online, cada uno 1/1 réplicas, cero advertencias/críticos, sin trabajo pendiente. No se han instalado las migraciones propuestas ni desplegado esta cadena.
- El estado de `main` observado: `ca58693e167752bd6eab8ff4e9be770391fe4293`.
- El archivo `docs/telephony/2026-10-13-voice-release-part10-manifest.json` congela **esta observación histórica**, nueve commits, CI de Parte 9 y los bloqueos que siguen abiertos. La instantánea no es un certificado de integridad firmado por GitHub/Twilio/Google ni un estado en tiempo real.

El nuevo CI de Parte 10 contempla cuatro validaciones aisladas: manifiesto/Git, restore sintético PG18, seguridad multiempresa/telefonía PG18 y React→Spring Boot→PG18. No reemplaza los ocho gates exhaustivos de la Parte 9 que siguen asociados al SHA anterior; sólo verifica que el cierre documental y el último commit no modifiquen código de producto ni migraciones.

## 2. Controles NO-GO pendientes y evidencia aceptable

| Requisito | Evidencia operativa futura mínima; no existe en el expediente |
| --- | --- |
| 1. Flyway actual | Lectura directa, fechada y autenticada de `flyway_schema_history` en producción, confirmación de última versión y checksums; ninguna SQL de escritura |
| 2. Backup real | Dump reciente de Postgres 18, hash e integridad, y restauración aislada con verificación de PK/FK/RLS; sin copiar PII al CI |
| 3. Redundancia | Copia **real** cifrada y autenticada en medio independiente fuera del ambiente Railway; prueba de restauración desde esa misma copia |
| 4. RPO/RTO | Medición y aceptación explícita de recuperación y pérdida máxima; no deducir de un restore sintético |
| 5. Propiedad Twilio | Consulta autenticada y en principio de solo lectura a cuenta/subcuenta; confirmar Phone Number SID, Account SID, número, provenance y tenant real en PG sin alterar nada |
| 6. Webhooks | Comprobar método, URL, endpoint y firma esperada del número real; no escribir configuración de carrier |
| 7. Identidad Google | Confirmar, sin mostrar API key, su proyecto, permisos y modelo seleccionado realmente en runtime. La allowlist local de `gemini-3.8-live` no demuestra acceso |
| 8. Precio y límite | Tarifas oficiales y facturación realmente aplicables; confirmar límites técnicos, si existen; un presupuesto *estimado* o buffer 2x no garantiza un hard cap |
| 9. Consentimiento | Participantes del piloto informados, grabación/transcripción/retención revisadas según las políticas pertinentes |
| 10. Autoridad | Dos aprobadores humanos distintos, autenticados y no autocertificados por un JSON; registro de responsabilidades |
| 11. Operación | Ventana de mantenimiento y rollback probado, con responsable designado, criterios de STOP y procedimientos de recuperación independientes |

**Ningún campo marcado `false` en el manifiesto puede volverse `true` porque alguien haya escrito ese valor en un JSON.** Evidencias auténticas deben archivarse por canales privados, con una política de expiración y doble revisión. No incorporar claves, dumps, teléfonos personales, SIDs privados o datos de empresas a PR, logs o artefactos públicos.

## 3. Orden operativo para un futuro release, con autorizaciones separadas

1. **Preflight sin cambios.** Releer `main`, los HEADs y bases de #811–#820, los runs exactos, branch protections, despliegue Railway efectivo, historial Flyway y salud. Una rama movida, un PR retargeteado o una divergencia del SHA obliga a detener y recertificar.
2. **Respaldos antes de cualquier migración.** Crear backup real desde entorno privado autorizado, hash, copiar con cifrado autenticado a almacenamiento independiente y restaurar fuera de producción. Probar control de acceso, tiempos, integridad y retención. Si no hay evidencia de restauración, **STOP**.
3. **Diseño de migración.** Reconciliar qué V100–V103 están ausentes en producción, comprobar checksums y compatibilidad con el backend actualmente desplegado. Flyway forward migrations no implican rollback reversible. No aplicar `repair`, `drop` ni restauración sobre BD activa como atajo. Si no se puede recuperar sin pérdida inaceptable, **STOP**.
4. **Revisión de aislamiento.** Validar por pruebas/consultas limitadas a solo lectura que los tenants ajenos permanecen aislados. No usar datos de clientes reales en CI. Definir observabilidad y alertas de violación.
5. **Portabilidad de números.** Verificar con Twilio los SIDs, cuenta, tenant y callbacks reales contra la BD. Cualquier desajuste permanece como bloqueo, no se corrige masivamente y no se compra número de reemplazo.
6. **Verificar IA sin confundir configuración con uso.** Comprobar modelo, proyecto Google, cuota y facturación; no habilitar startup probe, no enviar audio ni probar WebSocket sin autorización separada y presupuesto medido.
7. **Dos firmas humanas verificables.** Revisión de backups, costes, privacidad, objetivos RPO/RTO, incidentes, operación y consentimiento. Registrar ventana, propietario de rollback, canales de comunicación y límites aceptados. Un CI verde no sustituye una aprobación.
8. **Autorizar cada clase de cambio por separado.** Una aprobación futura para merge no autoriza automáticamente migración; una migración no autoriza Twilio webhooks; un webhook no autoriza una llamada facturable o lanzamiento comercial. Mantener `may_call=false` hasta autorización expresa de una prueba telefónica concreta.
9. **Despliegue controlado, solo después de certificación externa.** Revisar PRs apilados #811–#820 en orden; resolver conflictos, revisar efectos de cambios acumulados frente a `main` y repetir CI con la base definitiva. Ejecutar una ventana supervisada con rollback previamente preparado. **Esta Parte 10 NO ejecuta ese despliegue**.
10. **Canary, verificación y cierre comercial independientes.** Observar métricas por tenant, presupuestos, errores y trazabilidad. Una sola llamada controlada exige consentimiento, límite y aceptación del riesgo de coste. GO comercial requiere evidencia posterior distinta a GO técnico.

## 4. Stop inmediato y recuperación

Cualquier fallo de integridad de backup, pérdida de aislamiento/RLS, SID o webhook divergente, versión Flyway inesperada, factura/coste no controlado, proveedor de IA no autorizado, logs con secretos, falta de doble aprobación o imposibilidad de revertir sin pérdida aceptada implica **STOP**.

Durante un incidente: pausar exclusivamente las funciones nuevas afectadas mediante el mecanismo autorizado; conservar trazas protegidas y números de correlación; comprobar alcance sobre tenants; no eliminar tablas, aplicar SQL correctiva especulativa ni restaurar el backup sobre el servicio activo sin plan de pérdida de datos y aprobación. El rollback de backend puede ser incompatible con nuevas migraciones: validar su compatibilidad antes de cualquier cambio.

## 5. Certificación GitHub offline de Parte 10

En `.github/workflows/voice-release-part10-final-offline.yml`:
- Todos los providers externos, Twilio write, outbound, Live probes, jobs y llamadas quedan `false`.
- GitHub token `contents:read`; el workflow corre solo en rama apilada Parte 10 y PR sobre Parte 9.
- Checkout usa el **SHA HEAD exacto del PR**, no el merge-commit artificial de GitHub; verifica ancestro de Parte 9 y que el diff está compuesto **exactamente de cinco archivos nuevos** relacionados con auditoría, tests, workflow y documentación. No toca `main`.
- Las pruebas adversariales rechazan falsos GO, otro repo, SIDs/SHAs manipulados, PR merged, declaraciones de backup no demostradas, consentimiento falso, cambios de variables o archivos de producto y reducciones de seguridad.
- PostgreSQL 18 para restore e integración es efímero y sintético. Las pruebas de carrier usan mocks y tests internos sin consultas a Twilio. No hay llamadas ni sesiones pagadas.
- El expediente final se completa únicamente al observar **4/4 jobs `completed/success` en push y 4/4 en PR, mismo SHA exacto**, anotando sus URLs y el estado read-only contemporáneo de Railway y main.

Esta certificación significa **“paquete documental seguro y código de release sin cambios de producto”**, no prueba de backup real, carrier, Google, billing o llamadas.

## 6. Resultado de entrega

`PART10_FINAL_OFFLINE_PACKAGE=REQUIRES_CI_VERIFICATION`  
`PRODUCTION_FLYWAY=UNVERIFIED_CURRENT`  
`PRODUCTION_BACKUP_OFFSITE=UNVERIFIED`  
`TWILIO_REAL_OWNERSHIP=UNVERIFIED`  
`GEMINI_REAL_ENTITLEMENT=UNVERIFIED`  
`PROD_DEPLOY=NO-GO`  
`LIVE_CALL_CERTIFIED=false`  
`COMMERCIAL_GO=NO-GO`

La Parte 10 no es una autorización implícita para ejecutar el punto 3.
No se requiere intervención humana para **cerrar el alcance offline**;
sí es imprescindible aportar/revalidar evidencia privada y dar
autorizaciones específicas antes de cualquier cambio real de producción.
