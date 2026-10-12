# Helvoca: Fase 4 de 10, procedencia Twilio VERIFIED_EXISTING

## Alcance y restricciones

La migración Flyway V101 prepara la representación de números Twilio existentes comprobados sin declararlos falsamente compras. Solo se aplica cuando se despliega una versión con este archivo: en el PR en borrador NO se modifica Railway ni PostgreSQL de producción.

Archivo SQL: src/main/resources/db/migration/V101__twilio_verified_existing_binding_provenance.sql.

La migración no contiene instrucciones para modificar filas de phone_number. No compra ni configura números ni activa llamadas. No incorpora nuevos endpoints ni habilita el flujo de reconciliación. El estado comercial permanece NO-GO.

## Contratos de base de datos

- NULL continúa representando filas antiguas sin propiedad certificada.
- MANAGED_PURCHASE mantiene los requisitos originales de V100: SID AC y PN válidos; el timestamp de voz puede ser NULL hasta su posterior certificación.
- VERIFIED_EXISTING exige provider TWILIO, número E.164 canónico, SID de cuenta AC válido, SID telefónico PN válido y voice_verified_at no nulo en un único estado persistente. El timestamp por sí solo NO constituye prueba de titularidad ni de un webhook funcional.
- Se mantienen los índices históricos de unicidad de SID y de MANAGED_PURCHASE. V101 añade un índice parcial único de SID entre ambas procedencias certificadas.
- Se mantienen FORCE ROW LEVEL SECURITY, las políticas RLS y las columnas existentes: no hay ampliación de permisos de lectura ni backfill.
- El trigger BEFORE INSERT OR UPDATE impide que un rol normal de tenant o el propietario del esquema establezca o cambie registros VERIFIED_EXISTING. Solo el rol interno helvoca_system puede escribirlos. También impide retirar la procedencia desde un rol normal. No afecta filas NULL o MANAGED_PURCHASE.

El trigger limita además las modificaciones corrientes de un número VERIFIED_EXISTING, incluida su desactivación por rol tenant, hasta diseñar el flujo controlado posterior. Es una restricción intencional fail-closed, no un control administrativo final.

## Evidencia de pruebas

Pruebas contra PostgreSQL 16 real con Testcontainers y Flyway: versión V101 registrada; índices antiguos y nuevos; RLS forzado; persistencia de filas legacy sin procedencia y con SID histórico; compatibilidad MANAGED_PURCHASE; inserción y actualización por rol de sistema; rechazo de SID malformados, E.164 erróneo, proveedor ajeno, campos incompletos, fuente desconocida, duplicados y verificaciones de voz ausentes; rechazo de autopromoción por tenant o escrituras privilegiadas sin rol de sistema; separación entre empresas y lectura de llamadas protegida. Solo se utiliza la base de datos efímera del contenedor.

## Reglas para las próximas fases

Fase 5: revalidación real y reciente de Twilio, relectura exacta de fila, comparación transaccional, idempotencia, bloqueo y concurrencia RLS. El esquema no permite inferir que un SID o timestamp ya es una certificación comercial.

Fase 6: autenticación verdadera de dos operadores de plataforma, autorización, auditoría durable, expiración y permisos. La vista previa de Fase 3 no equivale a tal autorización.

Fases 7-10: rutas de llamadas y Media Stream, Gemini Live, pruebas integradas, y autorización expresa antes de cualquier despliegue, llamada real o gasto.

Antes de desplegar V101 en el futuro hay que revisar read-only el estado productivo de Flyway, preparar snapshot/simulación y plan de reversión; Flyway ejecutaría esta DDL sobre producción al arrancar una versión desplegada. No hacerlo ahora.

Criterio de certificación de esta fase: 7/7 jobs del SHA final SUCCESS y 100% de cobertura diferencial Java aplicable.
