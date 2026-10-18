# HELVOCA · Parte 5/10 · Certificación integrada backend, frontend y CI

**Fecha:** 2026-10-13. **Base:** Parte 4 PR #814, SHA
`80094d6316ac92006c376cb7ab4514abe837bb6f`.
**Ámbito:** siete gates **internos aislados**. No producción, no llamadas
reales, no cambios a Twilio ni PostgreSQL de Railway. No merge a `main`.

## 1. Por qué es necesario un CI integrado nuevo

La Etapa C había certificado siete gates de seguridad sobre el SHA de
PR #808 y la Parte 4 ejecutó regresiones PostgreSQL 18 focalizadas.
Ninguna de esas ejecuciones certifica por sí sola el **candidato
actual**, que incorpora las Partes 1–4. El workflow estándar
`.github/workflows/ci.yml` no es apto para este preflight offline:
al abrir un PR contra `main` realiza un smoke HTTP en dominios
públicos de producción.

Por eso se reproduce la suite segura existente
`.github/workflows/twilio-railway-safe-internal-gates.yml` en
`.github/workflows/voice-release-part5-integrated-offline.yml`,
desencadenada solo por rama Parte 5 y PR apilado sobre #814.
No usa `workflow_call`, claves Railway, cuentas Twilio ni endpoints
productivos. La prueba del webhook se limita a mock unitario local.

## 2. Gates exigidos sobre el mismo HEAD exacto

| Gate | Certificación prevista |
| --- | --- |
| `meta-webhook-offline-contract` | Tests Spring de firma y ownership por GET simulado; además guard Python fail-closed del propio workflow |
| `golden-journey-release-contract` | Recorrido comercial sandbox sin pagos/llamadas reales |
| `fast-gate` | Cambios desde Parte 3 SHA `25f45a898ec531d90d06a7e547e0a16f1f4fae3a`; Java/QA afectado |
| `test` | Java `mvn -Pcoverage test` completo, TS `frontend:typecheck`, build, Chromium Playwright completo y JaCoCo diferencial 100 % |
| `inventory-frontend-coverage` | Instrumentación original y pruebas Inventario; utiliza **excepción documentada PR #768 al 95 %**, no fingir 100 % |
| `home-frontend-coverage` | Instrumentación y 100 % de líneas/branches/statements/functions de Home |
| `system-integration-e2e` | React → Spring Boot → Flyway → **PostgreSQL 18 aislado** en `127.0.0.1`; backend arrancado temporalmente y Playwright sistema |

La base diferencial explícita de Parte 3 incluye el cambio productivo
`TwilioLegacyBindingInspector` de Parte 4. No sustituye la comparación
completa acumulada de 300 commits desde `main`; esa auditoría más
amplia será necesaria en el release final.

## 3. Barreras de seguridad adicionales

El workflow define antes de los jobs los switches de Twilio, Gemini,
OpenAI, outbound, llamadas de certificación, compras y efectos externos
en `false`. La integración sistema solo usa PostgreSQL local de GitHub
Actions con datos y cuentas de demo sintéticos. Los scripts de fixture
no reciben secretos del usuario.

`scripts/ci/voice_part5_integrated_gate_contract.py` comprueba
**estáticamente** los siete nombres de jobs, permisos `contents:read`,
triggers solo de rama apilada, switches desactivados, PostgreSQL 18,
`127.0.0.1`, Java 100 % diferencial, React, inventario, Home y el
webhook con tests simulados. Rechaza URLs públicas, `railway up`,
`flyway migrate`, merges o `GO` indebidos. Tests de mutación adversarial
en `test_voice_part5_integrated_gate_contract.py`. Ambos son
**auditores de definición**, no pruebas de ejecución: solo el resultado
real de GitHub Actions sobre SHA exacto constituye evidencia de la suite.

El workflow deriva del CI interno seguro de Etapa C. Reutilizar sus
scripts implica los riesgos habituales de los tests de integración:
todos se ejecutan con cuentas de ejemplo en runners efímeros, sin
acceso a credenciales productivas. Si un gate falla, se mantiene NO-GO;
no usar `continue-on-error` ni marcarlo manualmente como verde.

## 4. Evidencia final y bloques pendientes

La Parte 5 solo se certifica cuando **los siete jobs** del **último
commit exacto de la rama** terminen `completed/success` sin
`skipped` ni `cancelled`, con reportes numéricos comprobados
(Java, Playwright y suites de cobertura). Un workflow anterior al
último commit no certifica el HEAD nuevo. Si cambia `main` o la
rama base, repetir línea de ancestros, CI y riesgos.

Aunque pase 7/7, siguen bloqueadas la migración V100–V103 productiva,
reconciliación real con doble aprobación, backup cifrado redundante
actualizado, ventana de cambios, rollback, consentimiento para llamadas
y prueba real de audio Gemini Live. El workflow está deliberadamente
separado de la cadena productiva y nunca otorga autorización comercial.

**Siguiente Parte 6, NO iniciada:** preparar y verificar el runbook
de despliegue/rollback/backup sin ejecutar cambios productivos.

`PART5_INTEGRATED_CI=PENDING_EXACT_SHA`;
`PROD_DEPLOY=NO-GO`;
`LIVE_CALL_CERTIFIED=false`;
`COMMERCIAL_GO=NO-GO`.
