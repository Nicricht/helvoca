# HELVOCA · Parte 3/10 · Certificación multiempresa y protección Twilio

**Fecha:** 2026-10-13. **Base inmutable:** Parte 2 PR #812, `6520ede7e072483f83cd0a70e2eb21cb186c87e6`.
**Estado:** pruebas offline y PostgreSQL 18 desechable. **PRODUCCIÓN Y LLAMADAS: NO-GO.**

## 1. Amenazas y superficies auditadas

El objetivo de esta etapa es demostrar que una empresa (tenant) no puede
usar el repositorio de números telefónicos o el proceso de aprobación de
Twilio para observar o alterar la identidad de otra empresa. La comprobación
de propiedad de Twilio reportada en Etapa D fue `GET` autenticado y la lectura
de Railway fue `READ ONLY`; eso no reemplaza una prueba de aislamiento.

Contratos inspeccionados en el código fuente:
- `TenantAwareDataSource`: cada conexión desde el pool aplica
  `SET ROLE helvoca_runtime` / `helvoca_system` y un
  `app.tenant_id` fijado según la sesión. Restablece ambos antes de
  devolver la conexión, incluso tras ejecución excepcional.
- `TenantDatabaseContext`: scopes `TENANT`, `SYSTEM` y `DENIED`;
  al salir de scopes anidados restaura el ámbito anterior.
- Flyway V40: políticas `RLS ENABLE` + `FORCE` para
  `phone_number`, con restricciones `USING` / `WITH CHECK` para
  `business_id`; `helvoca_runtime` sin BYPASSRLS.
- Flyway V101: `helvoca_verified_existing_write_guard` impide que
  tenants inventen la procedencia `VERIFIED_EXISTING`, incluso con acceso
  de escritura a su propia fila.
- Flyway V102–V103: reclamaciones, solicitudes y auditoría de enrollment
  revocadas al rol tenant, **FORCE RLS**, privilegios SYSTEM limitados;
  V103 conserva la aprobación humana separada y transiciones de estado.
- `TwilioVerifiedExistingVoiceRouteGate`: además de procedencia y datos
  firmados requiere ámbito SYSTEM, piloto explícitamente RUNNING,
  flag global y evidencia de ejecución de dos personas. No basta el
  `NOT_PILOT_MANAGED` ni la coincidencia de SID.
- `VoiceCarrierOwnershipPreflightService`: búsqueda de número limitada
  al tenant autenticado, sin proveedor GET para recursos ajenos y respuesta
  sin secretos. Controller exige BUSINESS_ADMIN.

## 2. Nuevas pruebas adversariales PostgreSQL 18

`TwilioPhoneTenantBoundaryPostgresIntegrationTest` usa Java 21,
Spring Boot + Flyway V1–V103 y PostgreSQL 18 Testcontainers; solo introduce
datos sintéticos y ficticios.

| Prueba adversarial | Resultado exigido |
|---|---|
| Tenant A consulta teléfonos A y B | A observa únicamente A, B únicamente B; SYSTEM ve ambos |
| Tenant A intenta SELECT/UPDATE/DELETE del teléfono B | SELECT cuenta 0, UPDATE y DELETE afectan 0 filas |
| Tenant A intenta INSERT con `business_id` B | PostgreSQL niega mediante RLS `WITH CHECK` |
| Tenant A intenta cambiar su fila a `business_id` B | PostgreSQL deniega la operación |
| Contexto ausente sin JWT/scope | Rol runtime DENIED; ningún teléfono de A o B visible |
| Tenant A y B acceden a claim/request/audit Twilio | SELECT y DELETE denegados por grants/RLS; SYSTEM puede inspeccionar |
| Scopes anidados SYSTEM → TENANT | Siempre vuelven a TENANT anterior, luego DENIED |
| Dos tenants concurrentes reusan conexiones Hikari | No se filtran visibilidad ni permisos entre scopes |

Los tests previos de `PostgresRowLevelSecurityIntegrationTest`,
`TwilioExistingEnrollmentAdminPostgresIntegrationTest` y
`TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest` refuerzan
aislamiento, autorizaciones y flujo de dos personas;
`VoiceCarrierOwnershipPreflightServiceTest` y
`VoiceCarrierOwnershipPreflightControllerTest` verifican aislamiento
y rol sobre el preflight sin llamar al proveedor.

**No se inserta ningún usuario real, tenant real, SID o número productivo
en GitHub Actions**. No se ejecutan llamadas telefónicas ni se consulta Twilio.

## 3. Ejecución y criterios de certificación

El workflow `.github/workflows/voice-release-part3-tenant-isolation.yml`
contiene:
1. Job Java PostgreSQL 18, migraciones reales Flyway dentro de contenedor
   efímero, pruebas de acceso cruzado y autorización de telefonía.
2. Job offline Python de integridad inmutable V100–V103, incluidos hashes,
   ausencia de backfill y NO-GO.

La **Parte 3** solo se declara terminada si los jobs del HEAD exacto
final están en estado `completed/success`, sin fallos ignorados.
Estas pruebas **no son** siete jobs completos de release ni demuestran
un despliegue real, datos reales, firma de webhook real, Media Streams
o consentimiento de llamada.

## 4. Riesgos residuales y entrega

- En producción Flyway sigue V99 hasta autorización explícita; V100–V103
  existen solo en ramas y PostgreSQL aislados. El piloto actual carece de
  los tres campos nuevos de procedencia verificada.
- La seguridad de capas Java/JWT no sustituye FORCE RLS y roles PostgreSQL.
  El rol owner/migration puede observar todas las filas por diseño,
  y su uso debe permanecer restringido a migraciones, no a endpoints tenant.
- Un `SYSTEM` mal autorizado sería una ruta de alto impacto.
  Mantener la doble aprobación, lock y auditoría inmutable;
  el token del tenant no debe suministrar SID de carrier verificable.
- Cambios en V100–V103 están prohibidos salvo revisión y un plan nuevo
  que conserve checksums Flyway. `flyway repair` no es rollback.
- Pendiente la validación del código completo consolidado con CI 7/7
  **sobre el SHA exacto final**, verificación de producción V99 read-only
  renovada justo antes de release, respaldo privado reciente cifrado y
  validación real de llamadas autorizadas.
- **Parte 4 siguiente, no iniciada:** preparación de reconciliación
  Twilio sobre el piloto existente, autorización humana y comparación
  de transiciones administrativas sin reparar producción.

`PROD_DEPLOY=NO-GO`, `LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO`.
No merges a main, migraciones productivas, llamadas, compras ni webhooks modificados.
