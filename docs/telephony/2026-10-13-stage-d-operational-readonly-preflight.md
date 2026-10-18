# Helvoca · Etapa D de 5 · Comprobación operacional read-only (2026-10-13)

> **Actualización prevalente 2026-10-15:** el operador ya obtuvo `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` a las 02:29 UTC mediante el auditor existente. Evidencia `PASS_OPERATOR_REPORTED`, JSON local privado no inspeccionado. Los párrafos históricos inferiores que afirman que sigue pendiente ejecutarlo quedan superados mientras producción no cambie. **No repetir** el auditor de rutina. PR #846 no fusionado, Flyway V100-V103 no aplicada. Ventana/rollback: [runbook C2-B](../POSTGRES_PRODUCTION_RECOVERY.md); [Issue #823](https://github.com/Nicricht/helvoca/issues/823).

**Alcance:** validar hechos del entorno productivo antes de autorizar
cualquier integración a `main`, V100–V103 o llamada real. No activar
interrupciones, comprar números, publicar webhooks, ejecutar cambios
Flyway, liberar PR ni iniciar sesiones Gemini.

## Seguimiento de producción 2026-10-14 (lecturas autenticadas)

La auditoría del Issue maestro [#823](https://github.com/Nicricht/helvoca/issues/823) se retomó **sin ejecutar SQL en producción**, porque el conector Railway activo no ofrece una consola SQL read-only ni ejecución de Railway SSH. El script existente `scripts/ops/railway_pg18_flyway_readonly_preflight.py` está disponible y conserva su QA offline, pero **todavía no se ejecutó esta comprobación autenticada de catálogo**. No crear un segundo auditor ni pedir al operador que repita pruebas sin causa.

- **GitHub:** `main@ca58693e167752bd6eab8ff4e9be770391fe4293`; candidato C2-A PR #842 HEAD `354f4920638ff1a4eacb974ff80f406b27abc6f3`, `ahead=462`, `behind=0`, DRAFT, CI [#38598336554](https://github.com/Nicricht/helvoca/actions/runs/38598336554) PASS. PR posteriores #843–#845 añaden mejoras y QA pero no despliegue.
- **Railway real:** `helvoca-api` deploy ID `4636d0d9-7be0-4a66-abce-58c68ccaede5`, Git SHA `ca58693e167752bd6eab8ff4e9be770391fe4293`, estado SUCCESS desde 2026-10-11; Postgres 18 online, sin cambios staged. El dominio personalizado `recepvoz.cl` figura verificado, registro DNS propagado y certificado válido. En muestra de las últimas 24 h consultada en este checkpoint: **390 solicitudes, 0 respuestas HTTP 5xx**, pero **364 son 4xx**; no presentarlas como 390 solicitudes exitosas ni deducir disponibilidad garantizada.
- **Historial Flyway visible en logs del despliegue del 11-10-2026:** `Current version of schema "public": 99` y `Schema "public" is up to date. No migration necessary.` Se verificó directamente su marca temporal `2026-10-11T09:11:36Z`. **Esto solo demuestra V99 en aquel arranque**, no Flyway actual ni permisos/estructura vigente de producción. Resultado `FLYWAY_CURRENT=UNVERIFIED` hasta consultar `flyway_schema_history` y catálogo dentro de una transacción autenticada READ ONLY.
- **Aún no autorizadas:** fusión en `main`, migraciones V100–V103, nueva compra, cambios de backup/PITR, llamadas, pagos y escrituras productivas. La confirmación de Railway Pro + backup nativo de 126 MB y el restore externo previamente reportado están preservados en [#823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6181716186); no repetir esas operaciones.

**Decisión:** `C2-A=DONE_OFFLINE_ONLY`, `PRODUCTION_FLYWAY_READ_ONLY=PENDING`, `C1_CUSTODY_RPO_RTO=PENDING`, `PROD_DEPLOY=NO-GO`. La próxima operación habilitadora es obtener solo el resultado seguro del auditor **existente** en un canal autorizado cuando corresponda, luego confirmar responsable, ventana, rollback y autorización expresa. No usar este addendum como aprobación de despliegue.

## Observaciones verificadas mediante conexión de solo lectura

- GitHub `main` sigue en `ca58693e167752bd6eab8ff4e9be770391fe4293`.
- Railway backend `helvoca-api` y `Postgres` siguen `online`,
  sin cambios `staged`.
- El deployment de API en Railway corresponde exactamente al mismo
  SHA de `main`: `ca58693e167752bd6eab8ff4e9be770391fe4293`.
- PR #808 de Etapa C permanece draft y sin merge. En el SHA
  `5e75cc87f824312b363e89c24cf269677677e6de` sus siete
  gates **internos offline** fueron SUCCESS y las 15 pruebas del
  auditor, PASS. El webhook público no se probó.
- El código del candidato posee 98 migraciones numeradas hasta V99
  antes de V100–V103. Stage B ensayó V100–V103 solo en copia
  privada restaurada y pruebas Flyway en Testcontainers.
- No existe, en el conector Railway, una acción autorizada que ejecute
  consultas SQL read-only dentro del contenedor PostgreSQL. Tampoco
  se dispone de una conexión Twilio autenticada para afirmar
  AccountSid, PhoneNumberSid, subcuenta y URL de webhook.

## Preflight que se implementó

`scripts/ops/railway_pg18_flyway_readonly_preflight.py` usa el
Railway CLI **ya autenticado en Windows** para entrar por SSH
únicamente en el servicio PostgreSQL productivo existente y
ejecutar una consulta fija con:

- `BEGIN TRANSACTION READ ONLY` y `COMMIT`, con `psql -X -qAt`;
- metadatos del catálogo (versión PostgreSQL, historial de
  `flyway_schema_history`, presencia de columnas/tablas de V100–V103);
- una comprobación de que la transacción efectivamente es READ ONLY;
- **ninguna lectura de datos de clientes, reservas, nombres, teléfonos o
  identificadores Twilio**; ningún SQL recibido por argumentos externos;
- salida limitada a un único JSON de conteos/booleanos, sin secretos;
- almacenamiento exclusivo del informe en
  `%LOCALAPPDATA%\Helvoca\private-backups\*.private.json`.

La consulta está diseñada para bloquear si Flyway V99 no aparece como
última migración exitosa, alguna V100–V103 ya está aplicada, existe
historial fallido, aparecen estructuras futuras o no es PostgreSQL 18.
Ante respuesta inesperada falla cerrada y no intenta reparar/alterar
Flyway.

**El informe local real todavía NO se ha ejecutado ni obtenido.**
`PRODUCTION_FLYWAY_READ_ONLY=PENDING`. No simular resultados a
partir de los tests del workflow.

### Ejecución en Windows, tras certificar CI

El operador deberá usar un script fijado por SHA de commit y
su hash SHA-256 confirmado. Esta ejecución solo requiere
`railway` CLI y Python 3; no requiere Docker Desktop ni nuevo
`pg_dump`. Puede hacerse desde cualquier carpeta.

El ejecutable está fijado al commit `38ff2aa90deca3598e05d960b451ff13e44c1272`
y a SHA-256 `aeb86ac44d8fe3613d902c7f970a2e8534c121083ab527ecf38da25f490f5987`.
Ejecutar **solo una vez confirmado SUCCESS del CI exacto**:

~~~powershell
$p = Join-Path $env:TEMP 'helvoca-stage-d-flyway.py'
$u = 'https://raw.githubusercontent.com/Nicricht/helvoca/38ff2aa90deca3598e05d960b451ff13e44c1272/scripts/ops/railway_pg18_flyway_readonly_preflight.py'
Invoke-WebRequest -Uri $u -OutFile $p
if ((Get-FileHash $p -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'aeb86ac44d8fe3613d902c7f970a2e8534c121083ab527ecf38da25f490f5987') {
    throw 'Stage D: el script no coincide con la versión verificada'
}
py -3 $p
~~~

El operador debe compartir únicamente la salida
`STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` o `STAGE_D_BLOCKED`,
sin revelar el JSON ni credenciales. **Esta ejecución aún no ha ocurrido.**

## Otros controles de la Etapa D

`TWILIO_CARRIER_IDENTITY=PENDING`. Requiere comprobación autenticada
de solo lectura ante Twilio, acotada al número piloto,
`PhoneNumberSid`, `AccountSid`/subcuenta, métodos POST y
URLs HTTPS de voz/callback, contrastados contra los **valores
actuales** de PostgreSQL para el tenant piloto. Sin un acceso
Twilio autenticado seguro, **no declarar coincidencia**, ni
iniciar llamadas o modificar los webhooks.

`ROLLOUT_FENCES=PENDING`: confirmar en la siguiente revisión
las banderas de telefonía y el ámbito del tenant desde una
fuente que permita corroborar efectivamente los valores, sin
activar switches. No imprimir claves ni tokens.

`ENCRYPTED_REDUNDANT_BACKUP=PENDING`: respaldar en otro medio cifrado
el dump cuya restauración se certificó durante la Etapa A y
probar acceso. El script nunca lo copia ni lo sube.

`FRESH_PREDEPLOY_BACKUP=PENDING`: justo antes de un futuro
despliegue autorizado requerirá un respaldo nuevo
restaurado y verificado, diferente de esta comprobación.

## Estado de seguridad

- `STAGE_D_CODE_AND_OFFLINE_TESTS`: depende de CI del SHA exacto.
- `PRODUCTION_FLYWAY_READ_ONLY=PENDING`.
- `TWILIO_CARRIER_IDENTITY=PENDING`.
- `STAGE_D_REAL_OPERATIONS=NO-GO`.
- `PROD_DEPLOY=NO-GO`.
- `LIVE_CALL_CERTIFIED=false`.
- `COMMERCIAL_GO=NO-GO`.

Este documento no se debe interpretar como autorización de merge,
despliegue, migración, llamada pagada o paso a la Etapa E.

## Correción del diagnóstico tras primer intento del operador

El primer intento local devolvió:
`STAGE_D_BLOCKED: Railway SSH or read-only psql query failed`.
Ese mensaje no distinguía fallo de transporte de un error SQL.
La herramienta se reparó en commit
`38ff2aa90deca3598e05d960b451ff13e44c1272`: codifica el SQL fijo en Base64 antes de pasarlo
al contenedor por Railway SSH, evitando interacciones entre comillas del
CLI/PowerShell/SH. El SQL **sigue siendo el mismo y siempre READ ONLY**.
Ahora captura `stderr` solo en memoria para clasificar
`SSH_KEY_OR_AUTH`, `POSTGRES_AUTH`,
`FLYWAY_TABLE_ACCESS_OR_ABSENT`, `REMOTE_SQL_SYNTAX`,
`PSQL_COMMAND_UNAVAILABLE` o `REMOTE_ERROR_UNCLASSIFIED`
sin imprimir ni guardar el stderr sin filtrar. El hash del script fue
recalculado y las pruebas offline verifican transporte fiel y
no divulgación. **La ejecución real todavía debe repetirse en Windows**.

Si el estado sigue `STAGE_D_BLOCKED` después de la corrección,
no reinterpretarlo como `PASS`, no realizar cambios productivos y
utilizar solamente la categoría segura del error para el siguiente paso.

## Segunda ejecución del operador: error de escritura local en Windows

El operador recibió:
`STAGE_D_BLOCKED: [Errno 13] Permission denied: ...private-backups/helvoca-stage-d-read-only-flyway-*.private.json`.
Este error ocurrió **después** de obtener y validar la salida de
`railway ssh` y de `psql`, pero **antes** de guardar el informe y
mostrar el estado de compatibilidad. No afirmar aprobación Flyway
hasta completar un nuevo intento y guardar la evidencia.

El script v3 fijado a
`38ff2aa90deca3598e05d960b451ff13e44c1272` intenta primero el
directorio local habitual `%LOCALAPPDATA%/Helvoca/private-backups` y,
si Windows deniega la creación del archivo, el directorio privado
alternativo `%LOCALAPPDATA%/Helvoca/private-evidence`.
**No envía resultados al CI, no sube el JSON, no modifica Railway**
y rechaza ubicaciones externas, Git y enlaces simbólicos. Si ambos
destinos son inaccesibles, termina `STAGE_D_BLOCKED`. La prueba
`test_railway_pg18_flyway_readonly_preflight.py` verifica ambos casos.

El script sigue consultando solo catálogo e historial Flyway en una
transacción `READ ONLY`; los bloqueos de Twilio y despliegue se mantienen.
