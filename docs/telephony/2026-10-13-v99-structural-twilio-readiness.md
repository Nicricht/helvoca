# Helvoca · V99 vs V100+ · corrección de diagnóstico real, sin modificación de producción

Fecha: 2026-10-13. Rama: `fix/voice-v99-schema-ambiguity-20261013`. Base: PR #820, commit `b11b73571fde1e248fe3111e60a3ad7602affd0a`.

## Qué se comprobó sin escribir en producción

- Railway despliega `main` SHA `ca58693e167752bd6eab8ff4e9be770391fe4293`, no el candidato de la cadena de PR.
- El log de arranque del servicio del 11/10/2026 identifica PostgreSQL **18.6**, `Current version of schema "public": 99`, y Flyway declara que no había nuevas migraciones para **ese binario**.
- La verificación anterior del operador con Twilio autenticado **GET** y PostgreSQL `READ ONLY` devolvió únicamente `DB_ACCOUNT_SID_MISSING_OR_MISMATCH`, `DB_PROVENANCE_UNVERIFIED` y `DB_VOICE_TIMESTAMP_MISSING`.
- Las columnas `twilio_account_sid`, `twilio_binding_source`, `voice_verified_at` aún no existen en Flyway V99. El auditor legacy obtiene `NULL` al leerlas mediante `to_jsonb(p)`, por lo que los tres mensajes **no prueban un SID realmente incorrecto**.
- V100–V103 ya tienen implementaciones, checksums y tests en la cadena aún **sin fusionar**; la reconciliación real exige carrier GET reciente, token de admin y un segundo humano independiente. No se inventará esa aprobación.
- Hay constancia previa de una restauración local desde dump real reportada por el operador. No hay evidencia confirmada de una segunda copia independiente cifrada/restaurada ni autorización de aplicar nuevas migraciones.

## Cambio concreto

`scripts/ops/twilio_carrier_db_readonly_audit.py` ahora recoge en **la misma transacción `READ ONLY`**:
- Última versión instalada en `flyway_schema_history`.
- Conteo exacto de las 3 columnas críticas en `information_schema.columns`.
- Los campos existentes `phone_number` y respuestas autenticadas GET de Twilio, manteniendo los controles de tenant, PN SID, provider, `active`, voice routing y webhook.

Los resultados se separan de forma fail-closed:

| Estado real de DB | Diagnóstico |
| --- | --- |
| Flyway `99` y ninguna de las 3 columnas existe | `DB_SCHEMA_V100_V103_NOT_DEPLOYED` |
| Versiones/columnas inconsistentes | `DB_PROVENANCE_SCHEMA_INCONSISTENT` |
| Flyway 100+ y las 3 columnas presentes | Mantener comprobaciones de **mismatch auténtico** por cuenta, procedencia y fecha |
| Tenant/PN SID/carrier-webhook no coinciden en cualquier versión | Conserva sus bloqueos correspondientes, aunque falten columnas |

Los metadatos nuevos son números/versiones **no secretos**. Nunca se imprimen ni registran claves, números privados, registros de clientes ni SIDs. La consulta sigue siendo `BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY` + `SELECT`. Toda excepción no corroborada bloquea.

## Pruebas reales y límites

- `scripts/ops/test_twilio_carrier_db_readonly_audit.py`: tests unitarios adversariales, sin HTTPS ni SQL productivo.
- `.github/workflows/voice-v99-schema-remediation-nine-offline.yml`: los ocho gates completos ya existentes de la Parte 9, **repetidos sobre el nuevo SHA**, más el noveno gate que comprueba diferencias de V99/V100 y ejecuta la actualización **Flyway V99→V103 solo en PostgreSQL 18 temporal de Testcontainers**.
- La corrida CI que se complete exitosamente prueba la corrección del diagnóstico del *software*, no aplicación de migraciones a Railway ni contacto autenticado con Twilio.
- No se compran números, no se cambian webhooks, no se hace backup/restauración real desde GitHub CI, ni se habilitan llamadas pagadas.

## Pasos aún requeridos antes de activar llamadas reales

1. **Verificar respaldo actual y copia cifrada separada**, recuperable sin afectar al PostgreSQL principal. Confirmar recuperación, RPO/RTO y responsable operativo.
2. **Consultar Flyway actual por canal privado, autenticado y READ ONLY**. Si no existe un cambio desde el último arranque, seguirá siendo V99. Nunca inferir aplicación a producción porque el CI aplicó V103 en Docker.
3. Resolver conflictos de la cadena apilada con `main`, conseguir revisión y autorización para un release con mantenimiento y rollback. No ejecutar V100–V103 sueltas ni reescribir checksums.
4. Después de instalar el código/migraciones mediante un despliegue aprobado, ejecutar **el protocolo existente de reconciliación por dos administradores** con GET auténtico a Twilio. Esto vincula los campos faltantes sin fabricar pruebas.
5. Corroborar modelo efectivo de Gemini en el entorno desplegado, cuota, límites de facturación y consentimiento. Una variable configurada no demuestra conexión real.
6. Autorizar por separado una sola llamada piloto con número, participante, presupuesto, duración, hora, observación y rollback definidos. No usar la bandera de pilot global como única barrera para tenants no administrados.

**Estado: diagnóstico software más preciso; producción no alterada; `PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO`.**