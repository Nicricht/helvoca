# Etapa B: acta de prueba local V100-V103, PostgreSQL 18 (2026-10-13)

## Origen de evidencia

El operador ejecutó en su Windows la herramienta de Etapa B
`scripts/ops/twilio_pg18_migration_drill.py`, fijada al commit
`098b1e67f580651a738d9971efc481f24c085b36`.
El script exige que el `pg_dump` privado tenga SHA-256
`46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18`.

El operador compartió **solo la salida final de PowerShell**:

```text
LOCAL_PG18_MIGRATION_REHEARSAL_VERIFIED
Applied V100-V103 on restored private copy; RLS/constraints verified.
This is NOT a Flyway execution on the restored copy.
Private evidence: [redacted local directory]/helvoca-stage-b-v100-v103.private.json
```

**Alcance del éxito:** esta salida indica que el script completó su ruta
de éxito: verificó el SHA-256 del respaldo de Etapa A; restauró en
un contenedor PostgreSQL 18 efímero, sin red ni puertos publicados;
comprobó baseline Flyway V99 y ausencia previa de V100-V103; aplicó las
cuatro migraciones SQL mediante `psql`; verificó las barreras incluidas
en el script (RLS forzado en tres tablas internas, denegación al rol
tenant, triggers/índices, digest de campos previos de `phone_number`
y recuentos de `business`/`phone_number`); y ordenó eliminar el contenedor.

**Origen y límite de confianza:** resultado informado por el operador,
no inspección independiente del dump ni de su informe JSON privado.
La comparación no prueba identidad completa de todas las filas/tablas,
ni certificar el cifrado u otra copia fuera de Windows. No se ha
ejecutado Flyway sobre la copia restaurada: la comprobación auténtica
de Flyway V1-V103 se realizó por separado en CI, sobre PostgreSQL 18
con datos sintéticos.

## CI independiente

La ejecución de GitHub Actions del PR #807 sobre el commit
`ff69024568bffcf58e18fc554170a72b0bbbfc38`
([run #38346860096](https://github.com/Nicricht/helvoca/actions/runs/38346860096))
finalizó `completed/success`: 76 pruebas Java de integración
PostgreSQL 18 / Spring Boot / Flyway, sin fallos, errores ni saltos,
y 8 pruebas Python offline exitosas.

## Estado y restricciones

- `STAGE_A_LOCAL_RESTORE_VERIFIED=TRUE` (salida de operador).
- `STAGE_B_CI_PG18_FLYWAY=PASS` (GitHub Actions, commit exacto).
- `STAGE_B_RESTORED_COPY_MIGRATION=PASS_OPERATOR_REPORTED`.
- `STAGE_B_COMPLETE=TRUE` para los dos ensayos **acotados** acordados.
- `STAGE_C=NOT_STARTED`.
- `PROD_DEPLOY=NO-GO`.
- `LIVE_CALL_CERTIFIED=false`.
- `COMMERCIAL_GO=NO-GO`.

El respaldo original y el reporte `.private.json` permanecen locales;
**ninguno fue subido a GitHub**, ni se copiaron datos/secretos a esta acta.
No ejecutar restauración ni migraciones en Railway a partir de esta
certificación. La rama PR #807 continúa en draft, sin merge.
