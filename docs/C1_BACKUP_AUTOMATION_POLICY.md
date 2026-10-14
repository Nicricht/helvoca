# Helvoca C1: política operativa de respaldos cifrados y recuperación

**Destino autorizado:** Google Drive institucional por elección expresa del titular el 2026-10-14. **Estado:** preparación en rama de PR #828. **No implementado como programación activa; no autoriza despliegue.** Este documento complementa exclusivamente C1 del [tablero maestro #823](https://github.com/Nicricht/helvoca/issues/823), no abre nuevas fases.

## Evidencia ya adquirida y que NO se repite

- Un respaldo nuevo de PostgreSQL 18 de producción fue exportado mediante acceso de solo lectura y cifrado con `age`. La consola del operador informó la restauración aislada inicial completada.
- Google Drive institucional entregó un objeto remoto cifrado de **673001 bytes** con SHA-256 **`fdf281efa0ff75dac61e00353229d2e8f61f55c8c434a876c75d33d7449a7ce8`**. Se descargó independientemente vía conector y el operador confirmó que coincide con el cifrado local.
- El operador ejecutó la restauración PostgreSQL 18 aislada desde descarga autenticada de Drive, con salida `C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED` y evidencia privada fechada 2026-10-14.
- **Nueva carpeta `HelvocaBackups` accesible (2026-10-14):** el conector autenticado comprobó la carpeta y el archivo cifrado de **673001 bytes**, lo descargó de forma independiente y verificó SHA-256 **`fdf281efa0ff75dac61e00353229d2e8f61f55c8c434a876c75d33d7449a7ce8`**, idéntico al ciphertext que ya fue recuperado en PostgreSQL 18. **Decisión operativa explícita del titular (2026-10-14):** continuar con almacenamiento de la cuenta institucional, que Google identifica como propietaria. La independencia de una cuenta personal deja de ser requisito elegido para C1; se acepta el riesgo residual de pérdida de acceso por caducidad de matrícula/políticas/cupo. **COPY_AND_READBACK=PASS; OWNER=INSTITUTIONAL_CONFIRMED; ACCOUNT_CONTINUITY=UNVERIFIED.** No registrar URL o ID de carpeta en repositorio.

Los comandos de pruebas de recuperación de los scripts de `scripts/ops/c1_*` no programan tareas ni tienen autorización para transferir datos productivos a otros servicios.

## Política propuesta (pendiente de aprobación y medición)

| Variable | Propuesta inicial | Puerta de aceptación |
| --- | --- | --- |
| RPO | Máximo **24 horas** de datos perdidos | Hora de cada backup tomada en origen y copia remota verificadas; no deducir RPO solo del cron |
| RTO | Máximo **4 horas** hasta servicio recuperado | Simulacro cronometrado desde descarga limpia, provisión, restore y reanudación funcional autorizada |
| Frecuencia | Una vez al día, objetivo local 03:30 `America/Santiago` | Dos ejecuciones automáticas consecutivas con upload y readback correctos |
| Retención | 30 copias diarias; 12 copias mensuales opcionales | Ajuste según privacidad, tamaño, costes y requisitos legales |
| Ensayo | Integridad remota diaria; restauración PostgreSQL 18 aislada semanal | Evidencia privada de última restauración con hora y resultados |
| Alertas | Backup vencido >26 h, upload incompleto, hash diferente, restore fallido, almacenamiento insuficiente | Destinatario de alertas real, prueba de alerta y acuse |
| Ubicación | Google Drive institucional elegido por el titular | Propiedad institucional conocida; verificar acceso continuado, cuota, MFA, permisos y recuperación al perder afiliación |

Los valores son **objetivos provisionales**, no compromisos existentes ni métricas observadas. Un backup diario por sí solo NO prueba RPO/RTO contractuales. Se recomienda revisar la necesidad de PITR antes de prometer RPO inferior a 24 horas.

## Arquitectura de automatización segura

1. **Ejecución 24/7 independiente del PC.** Un job dedicado y limitado (cron gestionado/cloud worker) obtiene un snapshot PostgreSQL usando credenciales con privilegios mínimos de lectura/backup. **No crear job productivo ni reutilizar la cuenta personal del operador de Railway para un cron hasta verificar el mecanismo de autenticación no interactivo.**
2. **Cifrado antes del envío.** La versión manual usa `age --passphrase`, interactivo. Para cron, migrar a `age` con **recipient público**; guardar clave privada de recuperación **fuera del job**, en bóveda segura con recuperación de acceso ensayada. Nunca enviar un `.dump` sin cifrar a Drive, GH, logs o servicios de terceros.
3. **Almacenamiento externo.** Se utiliza Google Drive institucional por elección expresa del titular. Para un job, usar identidad autorizada con alcance mínimo, token renovable protegido y acceso solo a la carpeta restringida. Comprobar cuota/MFA y caducidad de la afiliación; la disponibilidad futura queda como **riesgo aceptado y monitorizado**, no se declara garantizada. **No exponer la carpeta al público**.
4. **Verificación remota.** Registrar `run_id`, timestamp UTC, hash/size del ciphertext, tamaño/hora de origen, ID remoto y readback de bytes desde el proveedor; descargar y comparar SHA-256 real, no tratar `upload 200` ni existencia como suficiente.
5. **Fail-closed.** Cuando falla cualquier paso, alerta y se conserva backup anterior. Nunca borrar una copia sin que exista una sucesora **remota, cifrada y validada**. Operaciones de borrado/retención inicialmente solo `dry-run`; políticas reales de eliminación requieren destino estable y permisos verificados.
6. **Restauración aislada.** Programar ensayos separados de la exportación: PostgreSQL 18 desechable sin acceso a Internet, datos en volumen efímero privado, chequeos de tablas/migraciones y eliminación segura de plaintext temporal.
7. **Protección de secretos y privacidad.** Secrets en gestor del entorno del job, no CLI argument/log/CI artifacts; alcance al backup solo el mínimo indispensable. No incluir PII, base de datos, `private.json`, key material ni tokens en GitHub. Preferir que los dumps de recuperación queden localizados y con TTL breve.
8. **Pruebas de fallo.** Simular Drive desconectado, token revocado, cuota llena, CLI/Railway inaccesible, backup inválido, cifra corrupta, eliminación equivocada, job atrasado y alerta perdida, sin tocar producción.

## Implementación preparada para ejecución automática, todavía desactivada

- Herramienta: `scripts/ops/c1_unattended_institutional_backup.py`, destinada al **Google Drive institucional elegido por el titular** mediante un remoto rclone configurado en el runner.
- Exporta PostgreSQL vía Railway SSH en modo lectura, valida PGDMP y SHA, cifra mediante **clave pública recipient de age**, sube solamente `.dump.age`, descarga ciphertext por `rclone cat` y valida tamaño y SHA-256.
- **Doble autorización obligatoria**: bandera `--execute` y variable `HELVOCA_C1_ALLOW_EXECUTION=read_only_encrypted_backup_approved`. Sin ambas, el código no se conecta a proveedores.
- No existe cron activo ni hay credenciales de Railway/rclone en repositorio; no se desplegó servicio ni se creó recurso de cobro.
- Requiere runner 24/7 autorizado, Railway CLI no interactivo con permiso de exportación, rclone OAuth institucional renovable, `age` y recipient público de una **nueva clave de recuperación**. Guardar la clave privada fuera del runner, GitHub y Drive, en bóveda protegida.
- La antigua passphrase del backup manual **no** descifra los nuevos backups age-recipient. Custodiar ambos secretos mientras se migra y ensayar recuperación con nueva clave antes de activar cron.
- El directorio `--private-root` del runner debe estar fuera de Git y carpetas sincronizadas. Plaintext existe solo de forma temporal en disco privado y se elimina tras cada intento. No se borran copias remotas.
- La herramienta no implementa retención destructiva. Añadir solo vista previa de eliminaciones tras dos respaldos remotos válidos, con autorización y alarma de fallo comprobadas. CI ejecuta solo pruebas sintéticas y de guardas.

## Decisión de despliegue del programador en Railway (2026-10-14)

Railway soporta **Cron Jobs nativos**: arrancan el servicio a una hora UTC, ejecutan un proceso y terminan; no requieren un worker 24/7 consumiendo RAM entre ejecuciones. El servicio puede conllevar cargo de **CPU, memoria, transferencia y almacenamiento durante la ejecución**, según el plan. Railway omite una ejecución si la anterior sigue activa, por lo que se requiere comprobación externa de frescura del respaldo y alertas.

- **Implementación actual:** `scripts/ops/c1_unattended_institutional_backup.py` está implementado y probado offline. Utiliza Railway CLI SSH autenticado, `age` y `rclone`. **NO está empaquetado ni certificado para Railway Cron**: un cron independiente no hereda el `railway login` interactivo del PC del operador.
- **Siguiente trabajo de ingeniería:** crear variante para cron desplegado con `pg_dump` PostgreSQL 18 directo por la **red privada de Railway** usando variables de referencia del servicio Postgres, sin Railway CLI ni endpoint público. Utilizar `age` con clave pública, `rclone` con token OAuth institucional protegido y un directorio temporal efímero. No dar acceso de escritura a tablas de producción al usuario del backup.
- La hora candidata será `30 6 * * *` **UTC** (03:30 en Chile cuando UTC-3, 02:30 cuando UTC-4). Para hora local constante se necesita un scheduler consciente de `America/Santiago`; de lo contrario documentar los cambios estacionales.
- **No crear servicio cron real, no desplegar en producción y no establecer secrets de Google** mientras no exista consentimiento informado sobre coste y permisos, ni recipient público age con su clave privada fuera del servicio. No registrar tokens, passwords, claves privadas, archivos `.dump` ni configuraciones OAuth en GitHub o en este chat.
- Se verificará con al menos una ejecución real habilitada por el operador y lectura remota de bytes/hashes, luego dos ejecuciones programadas consecutivas. El workflow de GitHub solo cubre casos sintéticos, nunca prueba un cron real.

Fuentes: [Railway Cron Jobs](https://docs.railway.com/cron-jobs) y [Railway pricing](https://docs.railway.com/pricing/plans).

## Servicio Railway creado y verificado (2026-10-14)

- Proyecto: `Helvoca`; environment: `production`; servicio **`helvoca-backup-cron`** (ID `42bfe173-2f69-4605-ae63-a144a4a6e1dd`).
- **Estado verificado en Railway:** creado VACÍO, sin source, sin despliegue, sin cron, sin variables, sin dominio y sin montaje de volumen. API `helvoca-api` y `Postgres` conservan sus desplegados en `SUCCESS`. Ninguna escritura en base de datos ni gasto generado por ejecución de este servicio.
- Código del futuro runner Railway-native: `scripts/ops/c1_railway_cron_backup.py`; contenedor preparado en `infra/backup-cron/Dockerfile`. Este contenedor tiene un **CMD inocuo**. No conectar automáticamente la rama o la imagen a Railway: hacerlo podría desplegar una nueva versión; requiere revisión de presupuesto y OAuth primero.
- El runner usa `pg_dump` PostgreSQL 18 directamente sobre la **red privada de Railway**: no necesita Railway CLI. Exportación sin owner/ACL, solo lectura. Credenciales por entorno, NO por argumentos, logs o GitHub.
- Variables que deberán configurarse en el servicio (valores omitidos): `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` (idealmente mediante referencias privadas al servicio Postgres y rol de backup read-only); `RCLONE_CONFIG_INSTITUTIONAL_TYPE=drive`, `RCLONE_CONFIG_INSTITUTIONAL_TOKEN` y `RCLONE_CONFIG_INSTITUTIONAL_ROOT_FOLDER_ID`; `HELVOCA_C1_AGE_RECIPIENT` (solo clave pública). Para desplegar la primera ejecución se requieren además **dos habilitaciones**: `HELVOCA_C1_ALLOW_EXECUTION=read_only_encrypted_backup_approved` y comando `python3 scripts/ops/c1_railway_cron_backup.py --execute`.
- **Clave pública age configurada (2026-10-14):** el propietario proporcionó únicamente el recipient público age X25519, se validó su estructura y checksum Bech32 y se configuró `HELVOCA_C1_AGE_RECIPIENT` en el servicio `helvoca-backup-cron` con `skipDeploys=true`. Readback confirmó la variable; source=null, cron=null, deployment=null; API y PostgreSQL continúan con deployments SUCCESS previos. **No registrar aquí la clave privada ni presumir que sus permisos/custodia están certificados.** La advertencia del operador en Windows sobre archivo de clave privada world-readable aún debe verificarse y corregirse en su PC.
- **Variables públicas ya preparadas en Railway (2026-10-14, sin despliegue):** el servicio vacío `helvoca-backup-cron` recibió `PGHOST=${{Postgres.PGHOST}}`, `PGPORT=${{Postgres.PGPORT}}`, `PGDATABASE=${{Postgres.PGDATABASE}}`, `RCLONE_CONFIG_INSTITUTIONAL_TYPE=drive`, `RCLONE_CONFIG_INSTITUTIONAL_ROOT_FOLDER_ID` apuntando a la carpeta institucional autenticada (ID almacenado solo en Railway, no en repositorio). `skipDeploys=true`. Readback confirmó las cinco variables, el servicio sin `source`, sin `cron` y sin deployment; API/Postgres sin cambios.
- **Aún no configurar secrets, source ni cron:** faltan rol y password de respaldo read-only, token OAuth Drive autónomo y recipient público age. La credencial OAuth para Google Drive no se extrae del conector ChatGPT; habrá que autorizarla separadamente para el job. La clave privada age se queda fuera de Railway y de Google Drive.
- Un cron nativo en Railway trabaja en UTC. Antes de activar, revisar el horario, coste/consumo de ejecución y prueba de alertas; Railway puede omitir intervalos si un job anterior continúa ejecutándose.
- Validación de código: GitHub Actions `#38515642709`, pruebas **56/56** sintéticas offline (17+9+10+9+11), sintaxis y guardas de seguridad. CI no ejecuta ni construye el contenedor contra Railway, no lee datos productivos y no certifica backups automáticos reales.

## Autorización OAuth institucional asistida, sin instalar rclone en Windows

- Script de un solo uso: `scripts/ops/c1_authorize_institutional_drive.ps1`. Este script **NO** autoriza Google por sí solo: necesita que el operador abra el navegador y conceda el acceso con su cuenta institucional. No recibe OAuth por el conector de ChatGPT.
- Si el PC no tiene rclone, descarga una versión **portátil** desde los releases oficiales, contrasta su SHA-256 con SHA256SUMS, la ejecuta desde directorio temporal y no la instala en PATH. Las credenciales se guardan en `%USERPROFILE%\HelvocaPrivate\oauth\rclone.conf` con ACL de Windows restrictivas.
- Solicita scope de Google **`drive.file`**, no acceso completo a todo Drive. Por ese motivo crea la nueva carpeta `HelvocaBackups-Automaticos` en **la misma cuenta institucional**; el respaldo manual en `HelvocaBackups` permanece intacto y ajeno al token.
- Comprueba la escritura y lectura remota de un archivo pequeño de prueba sin datos de producción y localiza el ID de la carpeta propia de la app. Transmite token OAuth con `railway variable set ... --stdin --skip-deploys` y actualiza únicamente las variables `RCLONE_CONFIG_INSTITUTIONAL_TOKEN`, `...ROOT_FOLDER_ID` y `...SCOPE` del servicio cron vacío. **Nunca pega token por chat ni lanza backup/deploy**.
- Importante: el cliente OAuth compartido de rclone está siendo retirado durante 2026. Si Google lo bloquea o el refresh token deja de ser válido, la configuración debe rehacerse con un **OAuth client propio de Google Cloud** autorizado por el titular institucional. No declarar operación 24/7 durable con el OAuth compartido sin comprobar que renovación de token funciona.
- El asistente es una ayuda para el operador; el proyecto no puede ejecutar un consentimiento OAuth en su nombre ni exportar credenciales desde el plugin de Google Drive. Usar solo desde una copia local de `Nicricht/helvoca` actualizada. Guardar el archivo `rclone.conf` con el mismo cuidado que cualquier contraseña y **nunca** compartirlo.
- **Comando local (Windows, no requiere instalar rclone):** abrir PowerShell en la copia existente del repo, actualizar rama del PR #828 y ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ops\c1_authorize_institutional_drive.ps1`. Solo compartir el código de éxito o bloqueo `C1_*`, sin capturas con OAuth.

## Puertas de habilitación (todas obligatorias)

- [x] Backup nuevo, cifrado, copia externa institucional y restauración independiente reportada por operador.
- [x] Copia adicional cifrada en `HelvocaBackups` descargada y comparada mediante SHA-256 independiente.
- [x] Titular elige y mantiene la cuenta **institucional** como destino; registrar explícitamente el riesgo de pérdida de acceso. No exigir una segunda cuenta personal.
- [ ] Verificar continuidad de acceso, cuota y mecanismo de recuperación de esa cuenta, más alertas antes de expiración; sin afirmar custodia garantizada.
- [ ] Guardar y recuperar la passphrase manual existente en bóveda separada; verificar el procedimiento sin revelar la clave.
- [ ] Elegir destino de ejecución automática independiente del PC y comprobar autenticación PostgreSQL backup read-only, Drive y mecanismo de cifrado no interactivo.
- [x] Implementación opt-in de generación cifrada no interactiva y readback SHA-256 para Drive institucional, sin automatización activa.
- [x] Crear servicio Railway `helvoca-backup-cron` vacío sin source/variables/cron/despliegue; verificar que API y Postgres no cambian.
- [x] Preparar runner compatible con red privada Postgres PG18, imagen inerte y pruebas sintéticas 56/56.
- [ ] Configurar credenciales institucionales OAuth, rol DB de solo lectura y recipient age custodiado fuera de Railway, con autorización de coste para una ejecución piloto.
- [ ] Programar cron independiente del PC, métricas, alertas, retención solo dry-run inicialmente; verificar dos ejecuciones reales y recuperación.
- [ ] Medir RPO y RTO desde origen y simulacro de recuperación autorizado.
- [ ] Incorporar permisos y presupuestos operativos aprobados antes de activar un cron de producción.

**STOP absoluto:** sin destino remoto institucional accesible por el job, sin credenciales de recovery custodiadas, sin método seguro de `age` no interactivo, sin prueba real de alertas o sin capacidad de recuperación, mantener `C1=NO-GO`, `PROD_DEPLOY=NO-GO`, `COMMERCIAL_GO=NO-GO`. No volver a exportar solo para repetir pruebas.
