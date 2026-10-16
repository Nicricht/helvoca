# PostgreSQL production recovery and HA gate


## C1 2026-10-16: recuperación REAL PostgreSQL 18 + API, roles/ACL y desmontaje (prevalece sobre los checkpoints históricos)

**Resultado real sin cambiar producción:** el operador restauró en Railway DR aislado el respaldo cifrado nuevo del 2026-10-15 procedente de Google Drive. El PowerShell del custodio mostró `C1_CLOUD_DOWNLOAD_INTEGRITY_VERIFIED` y `C1_RAILWAY_DR_PG18_RESTORE_VERIFIED`. La transferencia/`pg_restore` tomó **5,5 s**, que NO representa el RTO completo. El primer arranque real de Java falló en Flyway V101, SQLSTATE `42704`: faltaba el rol global `helvoca_runtime`. Un `pg_dump --no-owner --no-acl` NO incluye roles globales de PostgreSQL ni las concesiones ACL; una restauración de tablas no basta.

El operador ejecutó una reparación acotada a DR que creó `helvoca_runtime` y `helvoca_system` como `NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS`, recuperando **290 sentencias** `GRANT/REVOKE/ALTER DEFAULT PRIVILEGES` a partir de un `pg_dump --schema-only` del origen **solo lectura**. Después, la API DR reiniciada desde el SHA aprobado del PR #846 se conectó a PostgreSQL 18.6 por red privada: Railway registró **102 migraciones validadas, V101/V102/V103 aplicadas, catálogo V103** y estado **API ONLINE 1/1, deployment SUCCESS** con healthcheck `/actuator/health`. Los proveedores externos, jobs, seeds, llamadas y cobros LIVE permanecieron apagados; no había dominio público.

**Ventana observada:** desde creación del PostgreSQL DR `2026-10-15T23:30:02.627Z` hasta API saludable `2026-10-16T00:06:56.628Z`: **36 min 54 s**. Incluyó base, restauración, diagnóstico, reconstrucción de roles/ACL y arranque API; **NO incluyó el download anterior de Drive ni una prueba adversarial cruzada de dos tenants**. No garantiza contractual ni sostenidamente RTO <=4h / RPO <=24h. Continuidad de backups, custodia independiente, frescura de snapshots y aislamiento adversarial siguen abiertos. Estado comercial `NO-GO`. [Acta real en Issue #823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6204969940).

**Limpieza verificada:** el agente eliminó exclusivamente API DR, Postgres DR y su volumen en el proyecto aislado. Railway devolvió **0 servicios, 0 volúmenes, 0 buckets, 0 staging, 0 pendingWork**; producción seguía online. Importe facturado **no consultado**. NO reactivar recursos para duplicar una prueba ya documentada.

### Orden obligatorio en futuras recuperaciones expresamente autorizadas

1. Verificar respaldo, SHA/bytes, custodio y clave `age`, presupuesto y autorización específica; probar destino **distinto a producción** antes de cualquier escritura. Proyecto DR existente `helvoca-c1-dr-isolated-20261015` quedó **vacío**. No usar Railway Restore in-place sobre origen ni fusionar `main`.
2. Proveer PostgreSQL 18 DR con volumen privado y contraseña aleatoria; sin dominio ni proxy TCP. Restaurar la copia cifrada solo desde el PC del custodio con SSH binario, en directorio privado y sin canalizar bytes mediante PowerShell en modo texto. Validar tablas y catálogo antes de API.
3. **Antes de arrancar la API (Flyway)** reconstruir ambos roles globales y ACL de producción. Ejecutable versionado: [`scripts/ops/c1_restore_global_roles_acls.py`](../scripts/ops/c1_restore_global_roles_acls.py). Requiere flag `--execute` y `--dr-service` con el UUID **del nuevo servicio PostgreSQL DR**, no el servicio antiguo ya destruido:

   ~~~powershell
   py -3 .\scripts\ops\c1_restore_global_roles_acls.py --execute --dr-service <UUID_DEL_NUEVO_POSTGRES_DR>
   ~~~

   Fija proyecto/entorno/servicio de origen y ejecuta **solo `pg_dump --schema-only` en producción**. Comprueba por SSH proyecto, entorno, servicio, usuario y DB de ambos extremos, vuelve a validar DR antes de escribir; aplica transaccionalmente **solo** roles de V40 y ACL derivadas del esquema, nunca filas de clientes. Rechaza SQL encadenado, dump corrupto, roles incompletos, error SSH, permisos que expondrían el historial Flyway o RLS no forzada; comprueba `SET LOCAL ROLE helvoca_runtime` sin contexto tenant: 0 filas visibles. No difundir SQL privado, claves, stderr ni PII. `C1_BLOCKED` es STOP, **no reintentar a ciegas**.
4. Desplegar API DR desde commit pin con JDBC privado, JWT temporal independiente, healthcheck y límites de RAM/CPU; sin dominio público. Variables: `SEED_ENABLED=false`, `APP_JOBS_ENABLED=false`, **`HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED=false` (default TRUE)**, conciliación y certificación telefónica off, outbound off, Twilio, Mercado Pago, Meta, Gemini y OpenAI desactivados. El switch global de piloto **no es un STOP universal** para tenants sin fila de control; bloquear también workers y proveedores.
5. Verificar migraciones y RLS bajo roles no privilegiados, intentos tenant A -> tenant B, autenticación, `/actuator/health`, tiempo t0/t1 y edad del respaldo, sin publicar registros personales. Borrar API, base y volumen **solo de DR** y confirmar inventario vacío + producción sana. Separar restauración real de pruebas sintéticas.

**QA versionada:** `scripts/ops/test_c1_restore_global_roles_acls.py` y [workflow offline C1 roles y ACL](../.github/workflows/c1-dr-roles-acls-contract.yml) comprueban el contrato de acceso, identidades de destino, rechazo de SQL inyectado y ramas de error sin invocar Railway real. Sus pruebas no reemplazan la evidencia de restauración real anterior ni prueban una nueva instancia en producción. El script temporal del chat que contiene el ID de PostgreSQL DR eliminado **no se debe reusar**.

**QA de integración adicional, 2026-10-16:** el mismo workflow de roles/ACL incorpora el job `c1-roles-acls-pg18` con **PostgreSQL 18 desechable y datos completamente ficticios**. Ejecuta [`scripts/ops/test_c1_pg18_roles_acl_integration.py`](../scripts/ops/test_c1_pg18_roles_acl_integration.py), comprueba primero el fallo **RED** esperado por roles globales ausentes, luego utiliza las funciones versionadas `extract_acl` y `compose_acl_sql` para reconstruir roles y permisos, comprueba idempotencia, atributos `NOLOGIN/NOBYPASSRLS`, acceso de sistema a ambos tenants, ausencia de visibilidad sin contexto tenant, aislamiento de lectura A/B y rechazo real de una escritura de A hacia otro tenant. El job **nunca se conecta a Railway**: solo puede dirigirse a `127.0.0.1:5432/helvoca_c1_ci` con `CI=true` y `C1_PG18_SYNTHETIC_CI=1`. No es evidencia de restaurar de nuevo una copia real de clientes, ni certifica el aislamiento adversarial de esa copia restaurada ya eliminada, ni implica RPO/RTO contractual. El job verde se registra en el PR/Issue maestro con su SHA exacto.




### C2-B · Auditor de variables efectivas, solo lectura (2026-10-16)

La conexión Railway de GitHub/ChatGPT permite confirmar la **existencia de nombres** de variables, pero no los valores efectivos. Por seguridad, no declarar STOP, PAUSED o GO comercial a partir de nombres. Para la ventana autorizada de revisión de producción existe el auditor versionado `scripts/ops/c2b_external_effects_readonly_preflight.py`. Utiliza la CLI Railway autenticada y **solo lee** un conjunto fijo de flags del contenedor `helvoca-api` productivo, tras comprobar IDs de proyecto, entorno y servicio. No muestra secretos, no exporta variables generales, no cambia configuración, no ejecuta SQL, no llama a Twilio/Gemini/Mercado Pago.

Cada flag se clasifica como `OFF` (exactamente false/0), `MISSING` o `NOT_OFF`; una variable ausente es un **bloqueo** hasta revisar el valor por defecto en la versión candidata. Importante: `HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED` está habilitado por defecto, y algunos workers/proveedores pueden tener efectos aunque el interruptor global del piloto esté apagado. Este auditor **no es una garantía universal de STOP**.

**Revisión de alcance posterior al primer preflight (2026-10-16):** el primer auditor examinó **19 flags**. El operador verificó `C2B_STARTUP_FLAGS_OFF_AT_PROBE_ONLY` **únicamente respecto a esos 19**, después de apagar siete valores con respaldo DPAPI privado y comprobar la API desplegada. Durante el inventario de código de `main` se encontraron rutas adicionales que podían causar **efectos externos o cambios de datos** si sus respectivos opt-ins estuvieran activos, por ejemplo `OPENAI_LIVE_ENABLED`, `TWILIO_WHATSAPP_ENABLED`, `HELVOCA_WHATSAPP_CERTIFICATION_ON_STARTUP`, `HELVOCA_WHATSAPP_SENDER_ACTIVATE_ON_STARTUP`, `HELVOCA_MERCHANT_PAYMENT_BOOTSTRAP_ON_STARTUP` y `HELVOCA_BOOKING_PURGE_ON_STARTUP`. El auditor ya versionado se amplió a **46 nombres exactos** para incluir esos proveedores y los demás opt-ins de ejecución al arranque identificados; conserva el mismo protocolo de salida limitado a nombres y estados, IDs fijados y cero acciones sobre producción. Un resultado antiguo de 19 NO certifica el nuevo conjunto de 46. Las variables adicionales todavía requieren una lectura autenticada read-only **del ejecutor ampliado en su HEAD certificado**, sin asumir su valor por aparecer en Railway. Si alguna está `MISSING`/`NOT_OFF`, STOP y revisar impacto antes de pedir otro cambio; no extender automáticamente la autorización productiva anterior a nuevas variables. La lectura de 46 nombres tampoco demuestra cero trabajos `RUNNING`, ausencia de tráfico de proveedores previamente emitido, ni protección de tenants `NOT_PILOT_MANAGED`.

Solo si hay ventana de revisión aprobada, con Railway CLI autenticada y el archivo del commit certificado presente en el equipo privado del operador:

~~~powershell
py -3 .\scripts\ops\c2b_external_effects_readonly_preflight.py --probe-read-only
~~~

- `C2B_STARTUP_FLAGS_NOT_OFF` identifica **solo nombres conocidos**, nunca sus valores; requiere autorización específica para cualquier cambio posterior.
- `C2B_STARTUP_FLAGS_OFF_AT_PROBE_ONLY` demuestra únicamente que esas banderas estaban apagadas en ese instante; **no autoriza merge, cobro, llamada ni despliegue**.
- `C2B_BLOCKED` ante identidad/SSH/respuesta extraña equivale a NO-GO, no a permiso para reintentar indiscriminadamente.

Antes de considerar C2-B GO siguen siendo necesarias la verificación de `pilot_launch_control`/PAUSED para el tenant piloto, rutas `NOT_PILOT_MANAGED`, trabajos en vuelo, proveedores reales, snapshot recuperable y fresco, aprobación de responsable y ventana, rollback DDL-aware y consentimiento **separado** para fusionar el PR #846 a `main`. No introducir en GitHub secretos ni resultados de entorno distintos de los códigos seguros. QA sintética y adversarial: `scripts/ops/test_c2b_external_effects_readonly_preflight.py`; workflow específico `.github/workflows/c2b-external-effects-readonly-contract.yml`. El CI offline no significa que la lectura efectiva se haya ejecutado en Railway.


## Purpose

RecepVoz production data must not rely on a single unverified volume. A backup is useful only when the team knows how it will be restored and what evidence is required before a recovery path is trusted.

This runbook covers the production PostgreSQL service used by Helvoca/RecepVoz.

## Current production posture

As of 2026-10-01:

- PostgreSQL runs on Railway with a persistent volume mounted at `/var/lib/postgresql/data`.
- A **DAILY Railway volume backup schedule is enabled**.
- PostgreSQL is currently single-node.
- PITR is not yet enabled because the connected Railway automation surface does not expose the PITR enable mutation.
- Production application connections remain tenant-aware and PostgreSQL RLS is the authoritative cross-tenant boundary.

Railway documents daily volume backups as retained for six days. Volume-backup restore is an in-place operation on the source service, so it must not be used merely as a production recovery drill.

## 2026-10-14 confirmed evidence and remaining recovery gate

This section supersedes **only** older unverified present-tense claims below. Historical notes dated 2026-10-01 are not proof of today's backup schedule or successful recovery.

- **Railway snapshot:** The operator supplied a production Postgres > Backups screenshot displaying a volume backup at 2026-10-14 20:21, displayed size **126 MB**, with the UI announcing its next backup in four hours. The operator separately confirmed Daily / Weekly / Monthly scheduling in [master issue #823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6181515770). Neither the current Railway connector nor the screenshot proves all future scheduled jobs will succeed. Monitor actual completed snapshots and freshness. The operator reports Pro purchased; Railway connector access does not independently inspect subscription billing.
- **Already completed independent recovery:** The operator reported the exact terminal success status `C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED` after using `scripts/ops/c1_independent_remote_restore.py` on the browser-downloaded Google Drive `age` ciphertext, matching the remote SHA and original dump SHA, with a disposable **network-isolated PostgreSQL 18**. [Original operator attestation](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6160941717). The private report and passphrase have not been read by this agent. **Do not require re-downloading or restoring that same archive with a second tool just to duplicate this result.** This is not a restoration of a Railway native volume snapshot.
- **Current Railway environment:** API and PostgreSQL 18 services reported online, database volume attached, no staged changes; the `helvoca-backup-cron` application service has no deployed cron job. That dormant service is not the Railway **native volume-backup schedule**.
- **Backup escrow risk and accepted storage choice:** The ciphertext resides in the Google Drive **institutional account explicitly chosen by the operator**, who accepted the residual risk that affiliation/policies/quota may end access (see [C1 backup policy](C1_BACKUP_AUTOMATION_POLICY.md)). **Do not require a personal Google Drive account or a second account for C1.** Still verify ongoing access, available quota, a recovery contact and secure separate access to the existing `age` passphrase by an authorized custodian. Keep secrets, plaintext dumps and private attestation JSON out of GitHub.
- **Recovery targets OWNER APPROVED, ability NOT measured (2026-10-14 Chile):** The owner expressly accepted **maximum RPO target 24 hours** and **maximum RTO target 4 hours**. This signs off business objectives only; it does **not** certify achieved restoration time/data freshness or constitute a customer SLA. Still measure end-to-end recovery including a functioning API, nominate an accountable operator, and verify completed snapshot freshness at the authorized release window. A daily schedule does not guarantee a 24-hour data recovery point; isolated dump-restore duration is **not** full-service RTO. Never infer a numeric RTO from a 126 MB snapshot.
- **No in-place restore drill:** Railway volume-backup Restore on the live Postgres service may replace its attached production volume. An isolated drill must not overwrite production. PITR remained **disabled** in the operator's screenshot. PITR enablement or a new Railway environment may change resources/costs and needs separate authorization; the already successful independent offline logical restore remains useful even without PITR.

**Operational choice:** Native Railway Pro backups are already scheduled according to the operator. The separately developed `helvoca-backup-cron`/rclone OAuth automation is **not an additional mandatory C1 prerequisite** and remains disabled unless it is separately needed, cost-reviewed and authorized. Keep the independently recovered institutional Drive archive as an additional recovery layer.

**Release disposition:** `C1_RECOVERY_FROM_INDEPENDENT_COPY=PASS_OPERATOR_REPORTED`; `C1_BACKUP_SCHEDULE=OPERATOR_CONFIRMED`; `C1_CUSTODY_RPO_RTO=OPEN`; `C1_COMMERCIAL_ACCEPTANCE=NO-GO`. Before C2-B (`main` auto-deploy + Flyway V100–V103), check a sufficiently fresh recoverable backup for the actual release window, the current Flyway catalog in a SQL READ ONLY transaction, and owner-approved recovery/runbook/rollback. The restored archive from 2026-10-13 does not certify the age of a future pre-release backup.

### 2026-10-15 C1 verified archive metadata vs fresh release snapshot

- Via authenticated Google Drive metadata-by-existing-ID, the **historical** encrypted archive `helvoca-postgres18-20261013T213529Z-ced3d8.dump.age` remained readable in the selected institutional account, `HelvocaOffsite`, at **673001 bytes**, modified `2026-10-13T21:35:55.021Z`, `shared=false` and owner-only. No download, decrypt, repeat restore or hash verification was performed on 2026-10-15; preserve the **previous** owner-reported isolated PG18 recovery. A Drive search returning no children despite the direct-ID response does **not** prove deletion.
- This 2026-10-13 ciphertext by itself cannot demonstrate **24-hour RPO freshness** on 2026-10-15. The Railway native backup record (126 MB, Daily/Weekly/Monthly schedules) is separately operator-confirmed on 2026-10-14. The connected Railway service API does **not** expose backup listing/individual completion timestamps or a safe native snapshot restore to a separate instance. Neither an API healthcheck nor attached volume size proves a recoverable, fresh native snapshot.
- Railway Postgres' **actual latest successfully completed native backup** must be identified at the **approved release window**, including backup completion time and recoverability under the signed-off RPO goal. Do not presume a newer snapshot or use in-place Restore just to test the snapshot. Source of evidence: Railway operator's authenticated Postgres > Backups view, shared only as non-secret completion metadata if needed.
- RTO **four hours** is approved as a goal, still **unmeasured for the whole SaaS**. A safe isolated PostgreSQL recovery and cloud-download restore was already operator-reported PASS and must **not** be repeated merely to obtain a second passing report. Any new *timed full-service* recovery drill requiring provider costs, new Railway services or deployment is a separate explicitly authorized experiment, never an implicit obligation triggered by this documentation.
- The authentic read-only Flyway baseline probe run from the owner's authenticated Railway CLI returned `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` on `2026-10-15T02:29Z`, **operator-reported**, with a private local report not read by the agent. This validates the expected **pre-deploy** PG18 / V99 state, **not** V100–V103 migration or release permission.
- The integrated release candidate [PR #846](https://github.com/Nicricht/helvoca/pull/846) was certified in its previous exact HEAD `3ead3f0698f18780a7629aab0300b6381e863966`: 2262 Java, 620 Playwright and differential Java coverage 100% on [CI #38667741615](https://github.com/Nicricht/helvoca/actions/runs/38667741615). If this document update changes its HEAD, verify CI on the *new* HEAD before claiming current certification. The live Railway API/PG18 remained on older `main`, with no staged changes.

**Operational gate:** `RECOVERY_TARGETS=OWNER_APPROVED`, `C1_OFFSITE_RESTORE=PASS_OPERATOR_REPORTED`, `RAILWAY_NATIVE_SNAPSHOT_FRESHNESS=UNVERIFIED`, `FULL_SERVICE_RPO_RTO_MEASURED=NO`, `FLYWAY_PREDEPLOY=PASS_OPERATOR_REPORTED`, `C2_DEPLOY=NO-GO`. No automatic merge, migration, PITR, provider call, or restore follows from this note.

## 2026-10-15 C2-B: ventana, STOP y rollback schema-aware (NO-GO)

**Este procedimiento NO autoriza el merge ni operaciones productivas.** Control único [Issue #823](https://github.com/Nicricht/helvoca/issues/823) y [PR #846](https://github.com/Nicricht/helvoca/pull/846). Backup externo age/Drive ya restaurado en PG18 aislado según informe del operador: no repetir. Captura 2026-10-15 de Postgres > Backups: snapshot nativo Daily **126 MB, ~2h de antigüedad en ese momento**, pero sin prueba de recuperación del snapshot. Objetivos **RPO <=24h / RTO <=4h aprobados, NO medidos ni garantizados**.

### Condiciones obligatorias antes de autorizar la publicación

1. Responsable de release, responsable de recuperación/STOP y contacto de escalamiento nominados; ventana y congelación de cambios autorizadas. Sin responsables disponibles: NO-GO.
2. Full CI y workflows relevantes en **SHA final**; verificar `main`, PR #846, estado de la rama y PG18/Flyway V99. El auditor read-only `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` fue **reportado PASS por el operador 2026-10-15 02:29 UTC**; no repetir salvo nueva causa/caducidad de evidencia. Flyway V100-V103 **no aplicadas aún**.
3. Comprobar la última copia nativa Railway **completada y fresca en la ventana real** y su capacidad recuperable. No usar Restore in-place como ensayo, no habilitar PITR sin permiso. Custodio autorizado debe poder recuperar la passphrase age fuera de GitHub, Railway y de la dependencia exclusiva del Drive institucional; comprobar continuidad, cuota y alerta sin publicar claves.
4. Capacidad de **RTO extremo a extremo <=4h sigue UNMEASURED**. **Existe un proyecto Railway DR separado, pero VACÍO:** `helvoca-c1-dr-isolated-20261015` / environment `dr-isolated`, verificado en 2026-10-15 con **0 servicios, 0 volúmenes, 0 buckets y 0 cambios staged**. No contiene PostgreSQL ni API y no prueba restauración alguna. Ensayo futuro: solo con **autorización específica de coste, recursos y manejo de datos**, PostgreSQL18 y backend separados del volumen activo, red sin tráfico externo ni proveedores LIVE, inicio `t0` al simular incidente y final `t1` cuando PG restore + Flyway/RLS + API /actuator/health sean funcionales. Reportar `t1-t0` y edad del punto de recuperación para RPO. No volver a repetir el restore histórico para simular esta prueba distinta.
5. Confirmar valores efectivos de flags y rutas de pago/telefonía desde canal seguro, no solo nombres de variables. **El `ControlledPilotExternalEffectGuard` no es STOP universal:** sin fila `pilot_launch_control`, devuelve `allowed=true / NOT_PILOT_MANAGED` aunque `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false`. Para piloto concreto, comprobar fila exacta y `PAUSED`; verificar también las demás rutas/proveedores. No declarar a otros tenants detenidos por ese flag.
6. Solo tras cumplir puertas y autorización humana **explícita y separada para fusionar #846 a `main`** se programa el rollout. El merge a `main` auto-despliega, y `spring.flyway.enabled=true` puede ejecutar DDL.

### Secuencia posterior autorizada y criterios STOP

- Registrar SHA **real fusionado** a `main` (no confundir con PR HEAD/test merge), observar Railway deploy y Flyway V100 (provenance), V101 (restricciones), V102 (claims/FK/RLS), V103 (doble aprobación/auditoría/RLS) con checksums, sin versiones fallidas. Validar el catálogo en read-only tras arrancar.
- Comprobar misma versión desplegada, health, errores/latencia, aislamiento multiempresa, RLS, compatibilidad de otros tenants y ausencia de efectos externos. Pruebas productivas solo no destructivas y expresamente autorizadas; proveedor real y pagos LIVE requieren sus permisos separados.
- **STOP** inmediato por Flyway inconcluso, RLS/tenant incorrecto, acción externa indebida, pérdida de salud o responsable ausente. Pausar piloto y escalar antes de otra intervención.
- **Rollback anterior a DDL:** puede considerarse volver a binario conocido con autorización y evidencia de compatibilidad. **Rollback posterior a V100-V103:** revertir contenedor/commit **NO revierte SQL** y el binario antiguo puede fallar contra el esquema migrado. Conservar DB, registrar migraciones, contener efectos; **no** ejecutar `DROP`, `flyway repair/undo`, edición del historial ni restauración productiva improvisada.
- Si se necesita revertir datos/esquema: propietario de incidente aprueba plan específico, punto de restauración y pérdida estimada frente a RPO, preferentemente destino aislado; restaurar en producción es una actuación destructiva separada. Al cerrar, comprobar SHA, Flyway, API, RLS, salud y efectos; conservar RTO `UNMEASURED` si no hubo medición.

**NO-GO pendiente:** custodio/operador, recuperabilidad del snapshot, frescura en ventana, ensayo RTO integral autorizado y medido, STOP efectivo por tenant/servicio, ventana y consentimiento explícito del merge. Ninguno de estos pasos de producción se ha ejecutado aquí.

## C1 sin costo adicional: auditoría de frescura de snapshot nativo (2026-10-15)

El repositorio ya dispone de `scripts/ops/railway_postgres_recovery_audit.py`. Se amplió el **auditor existente**, sin crear un servicio ni otro programa de backup: ejecuta únicamente `railway postgres pitr status --json` y `railway postgres pitr backup list --json` sobre el PostgreSQL seleccionado (consultas READ ONLY). La documentación oficial Railway confirma que `pitr backup list` enumera **volume backups**, incluso aunque el mismo grupo de comandos también incluya PITR. No ejecuta `enable`, `create`, `restore`, `delete`, ni cambios al proyecto.

La consulta guarda la respuesta original **solo en un directorio privado local fuera de Git**, pero muestra únicamente una clasificación no sensible: `RECENT_COMPLETED_METADATA` si el proveedor informa explícitamente estado completado y sello de finalización UTC dentro de 24 horas, `STALE_COMPLETED_METADATA` si supera 24 horas, `NO_BACKUPS_LISTED`, `COMPLETION_UNVERIFIED` o `UNVERIFIED`. Un JSON con `createdAt` pero sin estado y hora explícita de finalización **NO** es prueba de backup completado. Si el proveedor cambia el formato, el auditor falla cerrado. Ninguna clasificación prueba la capacidad de restauración del volumen ni el RTO integral, y el resultado general permanece `RESTORE_PROOF_MISSING` / NO-GO.

Para el operador **solo cuando corresponda verificar un snapshot fresco** (no repetir exportación/restore históricos), en una terminal local con Railway CLI autenticado:

```powershell
py -3 .\scripts\ops\railway_postgres_recovery_audit.py --output-dir "$env:LOCALAPPDATA\Helvoca\private-evidence\railway-c1"
```

**No publicar** en GitHub ni en el chat el contenido bruto del informe, los identificadores de backups, datos personales o secretos. Si el auditor indica `UNVERIFIED`, comprobar únicamente metadatos no sensibles del último backup completado en Postgres > Backups del Dashboard. La evidencia se registra para la ventana de release, no como garantía permanente. La restauración nativa aislada y el RTO completo siguen pendientes y requieren autorización separada si consumen recursos.

## Inventario reutilizable para el siguiente ensayo C1 (2026-10-15; NO-GO)

Esta es una **preparación de solo lectura**, no una certificación de respaldo/restauración ni permiso para generar gastos:

- **Destino existente:** Railway project `helvoca-c1-dr-isolated-20261015`, environment `dr-isolated`. A la fecha observada: 0 servicios, 0 volúmenes, 0 buckets, 0 cambios staged. Reutilizarlo **solo después** de autorización presupuestaria acotada y aprobación del tratamiento de datos. Crear otro proyecto DR sería duplicar inventario, no mejorar recuperabilidad.
- **Origen intacto:** `Helvoca / production` conserva `helvoca-api` y `Postgres` online (1/1 réplicas), sin trabajo pendiente; el backend desplegado aún corresponde a `main@ca58693e167752bd6eab8ff4e9be770391fe4293`. El destino DR no puede compartir su volumen, secretos LIVE, base ni conexión de escritura con origen.
- **No utilizar `compose.yml` de desarrollo para datos recuperados:** la configuración existente usa `postgres:16`, publica `5432` y `8080` sin restricción a loopback y pone `SEED_ENABLED=true` con credenciales de demostración. No demuestra PG18, aislamiento de red ni condiciones de seguridad de DR. Para un ensayo privado aprobado usar PG18, red aislada, sin exposición pública, `SEED_ENABLED=false`, `APP_JOBS_ENABLED=false`, salida y credenciales de Twilio/Gemini/Mercado Pago/Meta deshabilitadas, sin tráfico real ni cobros, y permisos mínimos.
- **Fuente:** una copia **reciente, íntegra y autenticada** con fecha de punto recuperable; copia age histórica de 2026-10-13 ya fue restaurada offline y no debe reutilizarse para aparentar una medición RPO vigente. No ejecutar el botón `Restore` del volumen productivo ni activar PITR en nombre del ensayo.
- **Prueba de aceptación distinta de los tests ya cerrados:** cronómetro `t0` antes de recuperar desde origen independiente; restauración del mismo backup hacia **PostgreSQL18 DR**, comprobación de Flyway y constraints/RLS, denegación cruzada entre tenants, API real arrancada **contra esa base recuperada** y salud autenticada/no destructiva, cronómetro `t1`; registrar `t1-t0`, fecha de recuperación y edad del punto. No llamar RTO completo al tiempo de `pg_restore` solamente. Custodio retiene localmente evidencia privada sin PII ni secretos públicos. Contener gastos y destruir solo los recursos DR creados para ese ensayo, jamás origen.
- **Barreras actuales:** no hay respaldo actual verificado como recuperable en este ensayo, ni medición extremo a extremo, ni autorización nueva de costes, ni custodio/ventana nominados. Objetivos RPO ≤24 horas y RTO ≤4 horas son metas aceptadas por titular, **no SLA certificado**. `C1=OPEN`, `C2B_PROD=NO-GO`, `COMMERCIAL_GO=NO-GO`.

La evidencia de inventario y el alcance de incertidumbre están en el checkpoint [Issue #823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6198837265). Este procedimiento NO autoriza merge de [PR #846](https://github.com/Nicricht/helvoca/pull/846), despliegues, variaciones de servicios ni efectos externos.

## Recovery layers

### Layer 1: Railway volume backup

Purpose: recover the whole database to a scheduled snapshot.

Rules:

1. Keep the DAILY schedule enabled.
2. Never test restore by replacing the live production volume.
3. Before any real in-place restore, record the incident timestamp, desired recovery point, current deployment SHA, current database service and current volume.
4. Treat the restore as a production change and verify application health plus tenant isolation afterward.

### Layer 2: Point-in-time recovery

PITR is the preferred safe recovery drill because Railway restores into a new sibling PostgreSQL service and leaves the source service untouched.

PITR must be enabled before it is needed. The restore window starts only after the first post-enable base backup.

Enable from either:

```bash
railway postgres pitr enable --service Postgres
```

or Railway Dashboard -> Postgres -> Backups -> Enable PITR.

After PITR is enabled:

1. Confirm the source Postgres returns online.
2. Confirm PITR reports healthy archiving.
3. Wait until the first restorable timestamp exists.
4. Restore to a **new sibling service**.
5. Do not change the production application's connection string.
6. Verify the sibling contains:
   - `flyway_schema_history`;
   - `business`;
   - `app_user`;
   - `booking`;
   - `business_operation`;
   - `business_payment`.
7. Record restore duration and restore point age.
8. Delete only the temporary restored sibling after evidence is captured.

A PITR drill is successful only when the restored sibling starts, the required schema exists and the production source was never modified.

### Layer 3: provider-independent logical dump (isolated, no production writes)

**HIGH-RISK recovery safety correction, 2026-10-15:** Do **not** use the old
`scripts/ops/postgres-restore-drill.sh`. That script created/restored/dropped
a scratch database **on `PGHOST`**, which could be production. Although
it did not replace the source database, temporary schema and data writes,
storage consumption and locks in production would not be an isolated recovery
test. The legacy entry point is now **permanently disabled**, returns exit
code 2 / `C1_LEGACY_SAME_SERVER_RESTORE_BLOCKED`, reads no credentials and
invokes no database client. Offline regression tests simulate production-like
environment variables and fake DB clients, and check that no command runs.

**Use the existing safe offline restore instead.** The already implemented
`scripts/ops/c1_independent_remote_restore.py` validates the historically
approved ciphertext's known SHA-256 and size, prompts for the age passphrase
locally, decrypts only in a private temporary directory and restores to a
disposable PostgreSQL 18 Docker instance with **no network**. Synthetic
negative tests exist and the operator already reported
`C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED` for that
specific 2026-10-13 archive. **Do not repeat the same restore just to
duplicate existing evidence.** Its pinned hash and size make the script
unsuitable for arbitrary **new** backup files without new review.

**Future measured end-to-end C1 DR** requires distinct authorization for
handling real data, operator key custody and any new billable resources.
Restore a **fresh** independently verified encrypted backup only to a
separate private PostgreSQL 18 with its own volume, no live provider
credentials and no production traffic. Check Flyway, PK/FK/RLS, tenant
isolation, API connectivity and health; measure real `t0` to `t1` and
recovery point age, destroy the temporary resources, and store raw evidence
only under private operator custody. A clean API start on an empty PG18 is
not proof that real data can be recovered, and Railway native volume Restore
must never be rehearsed in place on production.

## RPO and RTO

With PITR currently off and a daily native backup schedule configured:

- the schedule alone **does not guarantee a 24-hour RPO**. Verify an actually completed and restorable recovery point at the release window. The 2026-10-15 Railway UI capture reported native snapshots younger than 24 hours, but did not prove their restorability;
- full-service RTO remains **unmeasured** until a real backup is restored to an isolated database and a functioning API, with elapsed time recorded.

After PITR is certified, record the observed restore duration and oldest/newest restorable timestamps here or in the incident/release evidence.

Do not invent an RTO from deployment times.

## HA gate

Do not convert PostgreSQL to HA merely because the option exists.

HA becomes the next infrastructure step when all of these are true:

- backup schedule is healthy;
- at least one isolated restore drill has passed;
- PITR is enabled and healthy;
- the application has reconnect/retry behavior compatible with a database failover;
- the additional recurring Railway cost is accepted;
- a maintenance/failover window has been selected.

When approved, prefer a Railway Postgres HA cluster with at least two streaming replicas behind Railway's HAProxy/Patroni topology. After conversion, run a controlled failover certification and verify:

- API reconnects without cross-tenant leakage;
- background jobs recover safely;
- payment and webhook idempotency remains intact;
- inventory reservations remain consistent;
- health checks return healthy after failover.

## Release evidence

A database-hardening change is not complete merely because code was committed.

Required evidence:

- fail-closed tenant-scope regression tests green;
- `PostgresRowLevelSecurityIntegrationTest` green;
- PR Full Gate green on the exact final commit;
- exact main merge SHA green;
- Railway deploy of that exact main SHA healthy;
- daily backup schedule still enabled;
- PITR/restore drill tracked separately until it can be certified without touching production.
