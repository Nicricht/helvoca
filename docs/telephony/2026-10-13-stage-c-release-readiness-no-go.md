# Helvoca · Etapa C de 5 · Certificación de candidato e integración segura (2026-10-13)

**Alcance adoptado:** el paso posterior a A (respaldo restaurable) y B
(migraciones V100-V103 en PostgreSQL 18) es auditar la secuencia de cambios
antes de que cualquier PR llegue a `main`. Esta etapa **no** fusiona,
despliega, compra números, toca PostgreSQL productivo ni inicia llamadas.

## Resultado de la inspección read-only (instantánea, no estado permanente)

- `main` en GitHub: `ca58693e167752bd6eab8ff4e9be770391fe4293`.
- Railway `helvoca-api`: `online`, fuente GitHub
  `Nicricht/helvoca`, rama `main`, healthcheck `/actuator/health`,
  último despliegue con estado `SUCCESS`.
- Railway PostgreSQL: `online`, imagen
  `ghcr.io/railwayapp-templates/postgres-ssl:18`, volumen original
  `b28c1046-168e-4f2e-bf02-18a2a2f1e611` intacto y sin cambios staged.
- GitHub: la cadena **PR #793–#804, #806 y #807** es coherente por
  bases/heads; todos esos 14 PR permanecen abiertos, DRAFT y sin merge.
  El PR #805 es ajeno a esta cadena y **no debe insertarse artificialmente**.
- Último commit Stage B: `9c1810457468fc2be8ab8cfe105af8c7448ac324`.
  El CI especializado Stage B sobre ese commit fue SUCCESS con
  76 pruebas Java (Postgres 18 + Flyway) y 8 Python. No equivale a un
  CI integral de 7 jobs del candidato final.
- Etapas A y B: `STAGE_A_LOCAL_RESTORE_VERIFIED` y
  `STAGE_B_RESTORED_COPY_MIGRATION=PASS_OPERATOR_REPORTED`. Evidencia
  local aportada por el operador, sin acceso de este agente al dump o
  reportes JSON privados.
- Una segunda consulta read-only a los metadatos de despliegues de Railway
  **sí confirmó** que el deployment `4636d0d9-7be0-4a66-abce-58c68ccaede5`
  corresponde al commit Git exacto de `main`
  `ca58693e167752bd6eab8ff4e9be770391fe4293`.
  La conexión disponible sigue sin proporcionar SQL read-only de
  `flyway_schema_history` ni confirmación autenticada de AccountSid/PhoneNumberSid Twilio.

Estos hechos se fijan sin secretos en:
`docs/telephony/evidence/2026-10-13-stage-c-observed-no-go.json`.
Es una **foto de la inspección**, no una fuente dinámica de producción.

## Qué está implementado

`scripts/ci/stage_c_release_readiness.py` comprueba offline:

1. Integridad y base exacta de cada PR en la cadena, SHA de candidato
   y ausencia de un commit inesperado en `main`.
2. Fuente Railway `main`, salud, entorno/servicio y cero cambios
   pendientes, más prueba positiva del SHA desplegado.
3. CI integral 7/7 sobre SHA exacto; el CI especializado de Etapa B
   o un CI de otro HEAD **no** lo sustituye.
4. SHA del respaldo restaurado, identidad del volumen, nueva copia
   vigente para la fecha del despliegue, historial real de Flyway
   consultado en modo lectura, revisión de compatibilidad hacia atrás
   y respaldo cifrado redundante fuera del equipo.
5. Identidad Twilio verificada por el proveedor, tenant y SIDs correctos,
   kill switches y otros tenants protegidos, ventana autorizada, responsable
   y plan de detención/rollback.

Los datos de ejemplo completos en pruebas nunca equivalen a pruebas reales.
Incluso una evaluación `READY_FOR_INDEPENDENT_RELEASE_REVIEW` retorna
`may_merge=false`, `may_deploy=false`, `may_run_live_call=false`,
`commercial_go=false`: **el programa no autoriza ninguna acción**.

El workflow `.github/workflows/stage-c-release-readiness.yml` prueba
la clasificación fail-closed y exige que la observación REAL actual
continúe `BLOCKED`. Además se habilitó en la rama de Etapa C el
workflow preexistente
`.github/workflows/twilio-railway-safe-internal-gates.yml`,
para ejecutar los siete gates completos internos, sin tráfico HTTP a
producción.

## Riesgo principal al fusionar

El servicio productivo obtiene código de la rama **`main`**. Fusionar
14 PR encadenados sin un plan puede llevar al backend a nuevos cambios y
migraciones Flyway antes de revisar el estado real de la base.
Por eso **no fusionar PR #793–#807 de forma automática o masiva**.

Una reversión de código **no** revierte migraciones SQL ni implica que el
esquema siga compatible. La reversión debe considerar el estado Flyway y
la compatibilidad de la aplicación con V100–V103. No ejecutar
`flyway repair`, `clean` ni restauraciones in-place como remedios
genéricos. Verificar y aprobar ventana de mantenimiento y responsable
antes de cualquier cambio.

## Resultado y siguiente puerta

Para esta Etapa C se certifican **el auditor offline y la seguridad de las
comprobaciones del candidato**, no un despliegue. Resultado esperado:
`STAGE_C_RELEASE_AUDIT=IMPLEMENTED_AND_CI_PASS`,
`STAGE_C_PRODUCTION_RELEASE=BLOCKED` hasta corroborar evidencias externas.

Lo pendiente para otra etapa (sin comenzar aquí) incluye: historial Flyway
real consultado en producción read-only; verificar de nuevo SHA exacto desplegado en la fecha del release;
CI 7/7 del candidato consolidado; comprobación Twilio autenticada;
copia nueva antes del despliegue; plan de reversión/ventana y
autorización para afectar producción.

**Gates inalterables:** `PROD_DEPLOY=NO-GO`,
`LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO`.
