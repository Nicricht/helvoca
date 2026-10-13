# Etapa B (2026-10-13): V100–V103, Flyway y seguridad en PostgreSQL 18

**Estado:** la Etapa A tiene respaldo original restaurado en Docker, SHA-256
`46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18`.
El archivo real se encuentra únicamente en el ordenador del operador.

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
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/Nicricht/helvoca/746b952cde8325c2cec73b4f5837842cae5a324d/scripts/ops/twilio_pg18_migration_drill.py' -OutFile $script
py -3 $script
~~~

**Importante:** actualizar este enlace a un commit final exacto cuando cierre
el CI. No iniciar el ensayo sobre una rama no verificada. El script solo lee
el archivo privado ya certificado en la Etapa A, no vuelve a conectarse a
Railway. Necesita Internet únicamente para descargar SQL desde una versión
GitHub fijada a SHA. No acepta URLs, credenciales, archivos alternativos ni
servicios productivos por argumentos. Nunca subir `.dump` o
`.private.json` al PR ni a los logs.

Éxito del ensayo local:
`LOCAL_PG18_MIGRATION_REHEARSAL_VERIFIED`.

## Gates

- `STAGE_A_LOCAL_RESTORE_VERIFIED`: evidencia aportada por el operador.
- `STAGE_B_CI_PG18_FLYWAY`: esperar SUCCESS del commit exacto.
- `STAGE_B_RESTORED_COPY_MIGRATION`: esperar ejecución local real.
- `PROD_DEPLOY=NO-GO`.
- `LIVE_CALL_CERTIFIED=false`.
- `COMMERCIAL_GO=NO-GO`.

No usar este ensayo para desplegar Flyway sobre producción. No se fusionará
la rama Stage B a main ni se ejecutarán llamadas facturables en esta etapa.
