# Helvoca · Etapa D de 5 · Comprobación operacional read-only (2026-10-13)

**Alcance:** validar hechos del entorno productivo antes de autorizar
cualquier integración a `main`, V100–V103 o llamada real. No activar
interrupciones, comprar números, publicar webhooks, ejecutar cambios
Flyway, liberar PR ni iniciar sesiones Gemini.

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

El ejecutable está fijado al commit `027b54e2ca642d336cd6d2f3c4ec0f8fb1ccbe78`
y a SHA-256 `9757f1fd4a383ba1498e71ac62bbae8a44a9dc845b636d0347ef85e52a193186`.
Ejecutar **solo una vez confirmado SUCCESS del CI exacto**:

~~~powershell
$p = Join-Path $env:TEMP 'helvoca-stage-d-flyway.py'
$u = 'https://raw.githubusercontent.com/Nicricht/helvoca/027b54e2ca642d336cd6d2f3c4ec0f8fb1ccbe78/scripts/ops/railway_pg18_flyway_readonly_preflight.py'
Invoke-WebRequest -Uri $u -OutFile $p
if ((Get-FileHash $p -Algorithm SHA256).Hash.ToLowerInvariant() -ne '9757f1fd4a383ba1498e71ac62bbae8a44a9dc845b636d0347ef85e52a193186') {
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
`027b54e2ca642d336cd6d2f3c4ec0f8fb1ccbe78`: codifica el SQL fijo en Base64 antes de pasarlo
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
