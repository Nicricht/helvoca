# Etapa A: respaldo PostgreSQL 18 y restauración aislada

Estado al 2026-10-13 (UTC): **RESTORE_PROOF_MISSING**. Este documento NO
certifica que exista ya una copia restaurable ni autoriza despliegues o llamadas.

## Alcance y datos comprobados

- Proyecto Railway: Helvoca, entorno production.
- Postgres: servicio 107bb612-f25d-4c57-98a4-f494ea6b9cb4.
- Imagen: ghcr.io/railwayapp-templates/postgres-ssl:18.
- Volumen original: b28c1046-168e-4f2e-bf02-18a2a2f1e611, 500 MB.
- Inspección Railway (conector): Postgres y API online, sin cambios staged;
  Postgres no tiene TCP proxy público.
- GitHub main en ca58693e167752bd6eab8ff4e9be770391fe4293.
- **No se ha realizado dump de datos de producción desde esta sesión.**

## Por qué NO usar el script antiguo como respaldo de producción

El script scripts/ops/postgres-restore-drill.sh usa el host definido por
PGHOST tanto para leer como para createdb/dropdb. Nunca debe apuntarse a
Postgres productivo como mecanismo de restauración aislada. En su lugar,
scripts/ops/railway_pg18_snapshot_drill.py restaura exclusivamente dentro de
un contenedor PostgreSQL 18 local con --network none y sin puertos publicados.

## Única ejecución manual en Windows

Requisitos locales: Python 3, Railway CLI autenticado (railway login) con
una clave SSH autorizada, y Docker Desktop ejecutándose para certificar la
restauración. No se necesita Railway Pro ni instalar herramientas pg_dump
locales: se ejecuta pg_dump del PostgreSQL 18 dentro de Railway por SSH.
Si falta Docker, se guarda el respaldo pero no se certifica la restauración.

Con la rama de esta etapa actualizada en la copia local del repositorio:

~~~powershell
py -3 .\scripts\ops\railway_pg18_snapshot_drill.py
~~~

No pegar credenciales ni dumps en chats. El comando usa variables presentes
dentro del contenedor de PostgreSQL sin exponerlas en argumentos, hace solo
operaciones de lectura en producción y guarda el resultado por defecto en
%LOCALAPPDATA%\Helvoca\private-backups. La base local efímera no tiene
conectividad de red y se elimina al terminar la prueba.

**Protección de datos:** el dump contiene datos reales y debe permanecer
fuera de GitHub, OneDrive, logs y directorios compartidos. Idealmente, el
disco local debe tener BitLocker o Device Encryption habilitado. El script
rechaza rutas dentro de un árbol Git y enlaces simbólicos. Conserva el
archivo privado para recuperación real; haz una copia cifrada sin compartir
las claves por chat.

## Estados del comando

- Exit 0: dump válido, SHA-256 calculado, lista y restauración pg_restore
  correctas, cinco tablas núcleo y flyway_schema_history encontrados.
- Exit 3: dump local generado, pero restauración **no certificada**.
- Exit 2: respaldo no obtenido o inválido; no se conserva archivo corrupto.

El reporte privado .json describe únicamente tamaño, SHA-256, número
de tablas, número de migraciones Flyway y conteos de filas núcleo. No
contiene credenciales ni datos de clientes. Los conteos son de la copia;
esta etapa **no demuestra igualdad fila a fila con una producción cambiante**,
ni demuestra todavía RLS, comportamiento del software o compatibilidad
de V100-V103. Estas verificaciones pertenecen a la Etapa B.

La herramienta railway_postgres_recovery_audit.py complementa el respaldo:
consulta solo estado de PITR y lista de respaldos del volumen; no crea,
habilita, restaura ni elimina nada. Requiere Railway CLI local.

## Evidencia exigida antes de Etapa B o despliegue

Registrar fecha, SHA-256, tamaño del dump, código de salida, éxito de
pg_restore y ubicación privada del reporte. No enviar dump, URL de
conexión, clave, password ni el reporte sin redactar. Si la prueba falla,
mantener RESTORE_PROOF_MISSING.

COMMERCIAL_GO=NO-GO, LIVE_CALL_CERTIFIED=false y ninguna migración
productiva se habilita por haber ejecutado este script.

Referencias oficiales:
- https://docs.railway.com/cli/ssh
- https://docs.railway.com/cli/postgres
- https://docs.railway.com/cli/connect
