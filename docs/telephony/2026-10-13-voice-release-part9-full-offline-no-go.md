# Helvoca · Parte 9/10 · regresión integral multiempresa y release offline

Fecha: 2026-10-13. Base: Parte 8 PR #818, HEAD
`304aba39a7aed6932ce69af1a8a4c4a905e2ce44`.
Rama apilada: `test/voice-release-part9-full-regression-offline-20261013`.
Destino de cambios: GitHub, PR DRAFT; **nunca producción**.

## Objetivo

Certificar el candidato **actual** con los siete gates integrados de
Parte 5 y una octava batería de prueba adversarial que atraviesa
los límites entre tenants, enrutamiento telefónico y seguridad de voz.
Las certificaciones antiguas sobre otro SHA no sustituyen esta ejecución.

## Cobertura, sin inventar porcentajes

La Parte 9 reutiliza los siete gates del workflow
`.github/workflows/voice-release-part5-integrated-offline.yml` con
triggers seguros actualizados y **agrega un octavo gate**.
El workflow completo nuevo vive en
`.github/workflows/voice-release-part9-full-regression-offline.yml`.

| Gate | Evidencia exigida |
| --- | --- |
| `meta-webhook-offline-contract` | Firma, carrier GET simulado, auditor Python de contratos de la Parte 5 y Parte 9, denegación económica Parte 8, sin HTTP público |
| `golden-journey-release-contract` | Camino de negocio Golden Journey sin facturación ni llamadas reales |
| `fast-gate` | Cambios desde la base original de Parte 3, sin cambiar arbitrariamente el SHA base |
| `test` | Java 21 completo con JaCoCo, **100% diferencial aplicable** de líneas, ramas y métodos respecto de Parte 3, TypeScript, Playwright completo |
| `inventory-frontend-coverage` | Browser real e instrumentación de archivos originales de Inventario; inspeccionar cifras concretas de log antes de afirmar 100% |
| `home-frontend-coverage` | 100% en código objetivo de Home para líneas, ramas, funciones y declaraciones |
| `system-integration-e2e` | React → Spring Boot → Flyway → PostgreSQL 18 sintético en localhost y navegador |
| `part9-cross-tenant-voice-adversarial` | RLS real de PostgreSQL 18, límites de teléfono y tenant, reconciliador Twilio, flujos de aprobación, acciones de IA restringidas, rechazo de arranque defectuoso de Media Stream y readiness local Gemini |

**Aclaración sobre la cobertura:** el 100% diferencial de código Java
desde SHA Parte 3 no es 100% de todos los archivos Java del repositorio.
Cobertura absoluta de Home/Inventario es por archivos originales vigilados,
no todos los componentes de la interfaz. Registrar las líneas,
ramas y métodos efectivamente medidos en CI.

## Invariantes de ejecución

- Sólo ramas `test/voice-release-part9-full-regression-offline-*` y
  PR apilado sobre `test/voice-release-part8-gemini-cost-consent-offline-*`.
  Sin `workflow_dispatch`, sin trigger `main`.
- Token GitHub `contents:read`, ninguna referencia a secretos externos.
- Twilio compra/provisioning, llamadas de certificación, webhooks mutables,
  Gemini Live/probe, OpenAI Live, outbound y jobs: todas banderas OFF.
- El webhook público de producción NO se solicita: las pruebas de firma
  y ownership usan mocks locales.
- PostgreSQL 18 de GitHub Actions/Testcontainers usa datos sintéticos.
  No se abre túnel a Railway y no se ejecuta SQL contra el servicio real.
- Sin `railway deploy`, `railway ssh`, cambios de producción ni
  PR merge. El status del CI no habilita llamadas ni acredita
  propiedad real del número.
- Descartar cualquier evidencia local sin log de job con éxito y SHA
  coincidente; no inferir que un job está verde por tests históricos.

## Auditoría de la definición

`scripts/ci/voice_part9_integrated_gate_contract.py` y
`scripts/ci/test_voice_part9_integrated_gate_contract.py` verifican
estáticamente: nombres de ocho jobs, SHA diferencial, cobertura estricta
aplicable, Home/Inventario, siete suites previas, ramas seguras, permisos
mínimos, banderas externas, PostgreSQL local y tests de tenants obligatorios.
Los tests adversariales rechazan borrar un gate, bajar cobertura, habilitar
outbound, cambiar la base, invocar una URL pública o señalar falsamente GO.

Esta auditoría estática no demuestra por sí misma que se ejecutaron
Maven, Playwright ni una consulta a la base. Solo los logs GitHub Actions
del **HEAD final exacto** pueden completar la certificación.

## Riesgos reales aún NO CERTIFICADOS

- No existe una nueva restauración verificada desde un backup productivo
  actualizado y una segunda copia cifrada independiente con acceso probado.
- No se ha certificado mediante una lectura autenticada Twilio el
  número real, PN SID, Account SID/subcuenta y webhook actuales
  contra la fila de PostgreSQL sin alterar esos datos.
- La clave de Gemini Live de Railway no tiene comprobación autenticada
  documentada de proyecto, entitlement, modelo realmente elegido y cuota.
  No se habilitan probes ni se intenta WebSocket.
- Aprobadores humanos reales, ventana, consentimiento, presupuesto
  verificable, procedimientos de rollback productivo y autorización
  individual de cualquier llamada siguen pendientes.
- Una reversión automática de V100–V103 o restauración in-place de la base
  real **no** forma parte de estas pruebas.

## Cierre y transición

La Parte 9 termina sólo si GitHub confirma **8/8 jobs de push y 8/8
jobs de PR con `completed/success` sobre el mismo HEAD**, sin jobs
skipped/cancelled, y se registra la evidencia en la descripción del PR
sin crear más commits. Si falla un job se corrige y se repite en otro SHA.

Tras completar esta parte, detenerse. La Parte 10 es una certificación
de **candidato y plan de producción** separada, y no está autorizada
a desplegar, fusionar o realizar llamadas sin condiciones humanas y
evidencia operativa adicional.

`PART9_FULL_OFFLINE_CI=PENDING`
`PROD_DEPLOY=NO-GO`
`LIVE_CALL_CERTIFIED=false`
`COMMERCIAL_GO=NO-GO`
