# Helvoca · Plan de integración de voz · Parte 2 de 10
## V100–V103: integridad, actualización incremental Flyway, PostgreSQL 16/18 y recuperación

**Fecha:** 2026-10-13. **Base:** PR #811, commit `38aad94e8b2e0abe51885eab71203010ffe4b9c3`.
**Estado de liberación:** `NO-GO`. **Esta parte es exclusivamente aislada / offline.**

### 1. Situación comprobada

- `main` y Railway API: `ca58693e167752bd6eab8ff4e9be770391fe4293`; PostgreSQL productivo online.
- Operador verificó Flyway **V99** con consulta productiva `READ ONLY`. V100–V103 no están instaladas.
- Stage B ya había probado `psql` sobre una copia privada y aislada del respaldo real y **Flyway V1–V103 sobre PostgreSQL 18 sintético** en CI.
- Auditor real Twilio GET-only + PostgreSQL SELECT devolvió solo:
  `DB_ACCOUNT_SID_MISSING_OR_MISMATCH`,
  `DB_PROVENANCE_UNVERIFIED`,
  `DB_VOICE_TIMESTAMP_MISSING`.
  En V99 las tres columnas todavía no existen. **No reparar por SQL manual** ni deducir aprobación de Twilio de un SID.
- Parte 1 certificó la cadena de 18 PR apilados y detectó #805 como bifurcación paralela.
- En este trabajo no se ejecutó ni se autoriza ningún SQL sobre Railway.

### 2. Contrato exacto e inmutable de las cuatro migraciones

| Versión | Acción | Comportamiento seguro |
| --- | --- | --- |
| V100 | Añade tres columnas `twilio_binding_source`, `twilio_account_sid`, `voice_verified_at` y sus CHECK/índice | Todos los números legados conservan esos campos NULL; no certifica automáticamente |
| V101 | Añade procedencia `VERIFIED_EXISTING`, trigger `helvoca_verified_existing_write_guard` y unicidad | Bloquea a rol no SYSTEM cuando intenta registrar la propiedad verificada; no actualiza teléfonos |
| V102 | Crea la tabla de reclamos `twilio_existing_enrollment_claim` | RLS ENABLE+FORCE y solo privilegios internos SYSTEM, tabla vacía |
| V103 | Crea solicitud y auditoría de aprobación con dos personas | RLS ENABLE+FORCE, transiciones protegidas, no solicitudes/aprobaciones automáticas |

Se inspeccionaron los cuatro SQL originales sin editarlos. Los blob SHA Git fijados para detectar cualquier alteración:
```text
V100 a3e300ff2eb7891e8dc6069244318608d8aa76ed
V101 54eeb2d87bac7df26fe29cc56885fd2494312613
V102 2c06b6170899c5dedcb851d7a4b6fc7cec58ad5b
V103 8289dc3d97a4240585fcbb46dee2396f6a8b6431
```

`scripts/ci/voice_part2_migration_contract.py` es un verificador completamente offline que comprueba nombres, integridad del contenido, fences contra DML en `phone_number`, restricciones, RLS y salida de estado `NO-GO`. Las regresiones correspondientes no leen secretos ni envían tráfico externo.

### 3. Lo que faltaba cubrir con Flyway real, sin producción

La prueba nueva `TwilioV99ToV103FlywayUpgradePostgresIntegrationTest` abre un
**PostgreSQL 18 Testcontainers desechable**, no una conexión a Railway:

1. Aplica Flyway `target=99`, verifica que V99 se haya registrado y que aún no existan las tres columnas nuevas.
2. Crea un tenant sintético y un número Twilio ficticio con un PN artificial; mide conteos, huella estable de filas antiguas y checksum Flyway V99.
3. Aplica **Flyway real** hasta `LATEST`, exige exactamente **cuatro** migraciones nuevas exitosas (V100–V103), validez del historial y ningún fallo.
4. Verifica conservación de los bytes lógicos de cada registro de `phone_number` preexistente excluyendo solo las tres columnas añadidas, sin cambios de cantidad de tenants/teléfonos, sin cambio de checksum V99.
5. Exige tres columnas nuevas aún **NULL** para el teléfono legado, tablas internas vacías, RLS forzado y triggers presentes; una actualización directa intentando certificación debe fallar.

Diferencia esencial: la prueba incremental recién creada usa **datos sintéticos**. No debe presentarse como Flyway real sobre el respaldo privado del operador. Se preserva la evidencia anterior complementaria (`psql` sobre copia restaurada).

### 4. Decisión explícita sobre PR #805

La comparación GitHub de #805 con el candidato #810 confirmó **cuatro commits exclusivos**, sin relación de ancestro. Sus cambios consistían en dos pruebas que permiten seleccionar la imagen PostgreSQL por propiedad de JVM, una configuración CI ligada a la rama #805 y un workflow PostgreSQL 18 independiente.

**Resolución de Parte 2:**
- Conservar la mejora útil de seleccionar imagen en las dos pruebas JUnit pertinentes:
  `System.getProperty("helvoca.test.postgres.image", "postgres:18-alpine")`.
- Mantener **PostgreSQL 18 como default obligatorio**, a diferencia del fallback PG16 del PR #805.
- Añadir CI aislado, matriz PostgreSQL **16 y 18**: ambas versiones corren las pruebas de procedencia Twilio y RLS; solo PG18 corre adicionalmente la prueba incremental Flyway V99→V103. Esto cubre el propósito del workflow #805 sin copiar automáticamente sus commits.
- **Mantener #805 DRAFT y abierto**, no fusionarlo, no cerrarlo ni sobrescribir los workflows existentes. Resolver su destino administrativo en una revisión posterior; su funcionalidad de tests ya tiene equivalente reforzado en esta rama.

### 5. Plan de reversión de cualquier implementación FUTURA, no autorizado

**Antes de la ventana de producción:** backup PG18 **nuevo**, restauración de prueba, copia cifrada redundante verificada, inventario exacto de SHA/imagen/backend, validación V99 read-only actual, límites de gastos, piloto en PAUSED, responsables y doble autorización. Todas las acciones productivas aún pendientes.

**Durante una ventana expresamente aprobada:** separar migración del esquema y habilitación del tráfico; no habilitar automáticamente `VERIFIED_EXISTING`, webhooks, flags ni llamadas reales por el mero hecho de que Flyway llegue a V103. Medir tiempos/locks y estado de Flyway. Suspender si hay drift de versión, checksums, RLS, roles, ownership, tenant, ID o estado de telefonía.

**Si falla:** detener efectos externos, mantener piloto PAUSED, preservar evidencia, revertir el código si es seguro y analizar restauración controlada de backup. **Volver el código atrás NO revierte Flyway ni elimina las columnas.** No ejecutar `DROP` de V100–V103 ni `flyway repair` para ocultar errores. El plan de restauración puede implicar downtime y pérdida de nuevas escrituras, por lo que requiere una decisión humana independiente.

**Nunca restaurar el respaldo privado del usuario en CI ni subir un dump a GitHub.**

### 6. Estado de evidencias y límites

- Los tests fuente/CI se certificarán únicamente si el workflow del HEAD exacto termina `completed/success`, incluidos los jobs PostgreSQL 16 y 18. No considerar un CI pendiente como aprobación.
- Los tests Testcontainers no demuestran permisos Twilio actuales ni audio real, aprobación comercial, credenciales Gemini ni producción.
- La decisión de revisar V100–V103 **no autoriza a ejecutar Flyway sobre producción** ni a fusionar a `main`. Una versión de producción V99 todavía es compatible con la app desplegada en `main`.
- **Entrega a la Parte 3:** reforzar aislamiento multiempresa, roles, permisos y pruebas RLS aplicables sobre el candidato consolidado, sin saltarse las revisiones actuales.

`PART2_SOURCE_MIGRATIONS=IMMUTABLE`.
`PART2_FLYWAY_REAL_RESTORED_DUMP=NOT_EXECUTED`.
`PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO`.
