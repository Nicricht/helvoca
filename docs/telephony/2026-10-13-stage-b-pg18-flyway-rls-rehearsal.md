# Etapa B (2026-10-13): V100–V103, Flyway y seguridad en PostgreSQL 18

**Estado:** la Etapa A tiene respaldo original restaurado en Docker, SHA-256
`46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18`.
El archivo real se encuentra únicamente en el ordenador del operador.

**Resultado actualizado (2026-10-13): Etapa B completada según evidencia de operador más CI.**
El operador proporcionó `LOCAL_PG18_MIGRATION_REHEARSAL_VERIFIED` tras ejecutar
V100–V103 con `psql` sobre la restauración local, mientras que el CI #38346860096
certificó 76 pruebas Java PostgreSQL 18 / Flyway y 8 Python. El reporte
privado no se subió a GitHub. Acta: `docs/telephony/evidence/2026-10-13-stage-b-local-pg18-v100-v103-attestation.md`.
La ejecución local no fue Flyway ni una auditoría de equivalencia global de todos
los datos. Producción sigue NO-GO; Etapa C todavía no se inicia.

**Dos ensayos separados son obligatorios:**

1. **CI GitHub, sin datos productivos:** Java 21 + Spring Boot 4.1.1 +
   Flyway aplica el historial completo V1–V103 a bases PostgreSQL 18
   Testcontainers, ejecutando las pruebas `TwilioVerifiedExistingMigrationPostgresIntegrationTest`,
   `TwilioExistingEnrollmentAdminPostgresIntegrationTest`,
   `TwilioExistingEnrollmentReconcilerPostgresIntegrationTest`,
   `TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest` y
   `PostgresRowLevelSecurityIntegrationTest`. Comprueban privacidad entre
   empresas, procedencia, administración, idempotencia, restricciones y roles.
2. **Ensayo con copia privada de producción (Windows):** el archivo
   `scripts/ops/twilio_pg18_migration_drill.py` comprueba el SHA-256
   original, restaura **solo en Docker local** (`--network none`, sin
   puertos publicados), obtiene los cuatro scripts SQL desde el commit
   inmutable de la Etapa A, y aplica V100, V101, V102 y V103 **con psql**,
   una transacción por migración, sobre la copia. Verifica que filas de
   `phone_number` no cambien, que exista V99 y falten las V100–V103
   previas, comprueba triggers, índices, políticas RLS forzadas, denegación
   del rol tenant en las tablas platform-only y aislamiento del tenant
   para `business`. Destruye el contenedor con la copia al terminar.

**Límite explícito:** el ensayo privado usa psql, no Flyway; por ello no
se atribuyen entradas Flyway V100–V103 al reporte privado. La aplicación
Flyway real queda acreditada por las ejecuciones Spring Boot en CI, sobre
base sintética limpia, no sobre el dump privado. Combinar los dos resultados
permite evidencia complementaria sin transferir datos.

## Acción local mínima tras certificar CI

Con Docker Desktop iniciado y Python 3 disponible, ejecutar la versión
fijada a un commit de la rama Stage B:

~~~powershell
$script = Join-Path $env:TEMP 'helvoca-stage-b-pg18.py'
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/Nicricht/helvoca/098b1e67f580651a738d9971efc481f24c085b36/scripts/ops/twilio_pg18_migration_drill.py' -OutFile $script
if ((Get-FileHash $script -Algorithm SHA256).Hash.ToLowerInvariant() -ne '8b6b68456869054c1248981ed93a42f4762f3ba45cd26122084976fd404a442c') { throw 'Stage B: integridad del script falló' }
py -3 $script
~~~

**Importante:** el enlace fija una versión inmutable del script que debe pasar
el CI exacto antes de ejecutar. El bloque comprueba su SHA-256 y no acepta
contenido distinto. El script solo lee
el archivo privado ya certificado en la Etapa A, no vuelve a conectarse a
Railway. Necesita Internet únicamente para descargar SQL desde una versión
GitHub fijada a SHA. No acepta URLs, credenciales, archivos alternativos ni
servicios productivos por argumentos. Nunca subir `.dump` o
`.private.json` al PR ni a los logs.

Éxito del ensayo local:
`LOCAL_PG18_MIGRATION_REHEARSAL_VERIFIED`.

## Gates

- `STAGE_A_LOCAL_RESTORE_VERIFIED`: evidencia aportada por el operador.
- `STAGE_B_CI_PG18_FLYWAY`: PASS; CI #38346860096 del commit `ff69024568bffcf58e18fc554170a72b0bbbfc38`.
- `STAGE_B_RESTORED_COPY_MIGRATION`: PASS_OPERATOR_REPORTED, `psql` sobre copia local.
- `PROD_DEPLOY=NO-GO`.
- `LIVE_CALL_CERTIFIED=false`.
- `COMMERCIAL_GO=NO-GO`.

No usar este ensayo para desplegar Flyway sobre producción. La rama Stage B permanece en PR #807 sin fusionar a main y no se ejecutaron
llamadas facturables en esta etapa.
