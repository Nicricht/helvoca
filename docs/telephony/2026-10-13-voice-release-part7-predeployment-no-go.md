# Helvoca · Parte 7/10 · Certificación de STOP/NO-GO previo al despliegue (offline)

**Fecha:** 2026-10-13. **Base:** PR #816 (Parte 6), SHA
\`8939fd4844eee83bcf74c71c7e0685de24f05f19\`.
**Rama apilada:** \`test/voice-release-part7-predeployment-no-go-20261013\`.

> Esta etapa desarrolla un **ensayo de decisión operacional** y regresiones
> aisladas. NO ejecuta despliegues, migraciones en Railway, RESTORE productivo,
> cambios Twilio, llamadas reales ni habilita Gemini Live externo.
> Ninguna salida de los tests autoriza una operación de producción.

## 1. Objetivo y frontera de confianza

Las Partes 1–6 reforzaron la línea de PRs, Flyway V100–V103,
aislamiento multiempresa, reconciliación simulada Twilio, CI integrado
y restauración/cifrado exclusivamente sintéticos. Eso NO significa
que el número real esté reconciliado, que la última copia de PostgreSQL
esté cifrada en destino independiente o que exista GO comercial.

Esta Parte 7 añade \`scripts/ci/voice_part7_predeployment_gate.py\`.
Acepta un objeto JSON **no confiable** y comprueba que el expediente
operativo simulado contenga coherentemente identidad de ramas, CI,
estado de BD, recuperación, telefonía, límites de efectos externos y
revisión humana. Ni siquiera un expediente sintético perfecto devuelve GO.
La función jamás conecta con servicios: no tiene clientes HTTP, DB,
GitHub, Railway, Twilio, Gemini ni comandos de ejecución externa.

### Reglas obligatorias del gate

| Dimensión | Condición de STOP |
|---|---|
| Cadena Git | PR #816 no es el padre, se mueve SHA o base, \`main\` o deploy divergentes, PR no está draft/unmerged |
| CI | Partes 5 o 6 no certificadas en sus SHA, o el SHA del candidato no coincide con CI |
| Vigencia | Hora UTC ausente/inválida, evidencia de más de 2 horas o demasiado futura (sólo coherencia simulada) |
| Flyway | V99 no comprobado recientemente por lectura real, V100–V103 alteradas o aplicadas indebidamente, falla RLS |
| Recuperación | Respaldo nuevo, restauración aislada, copia cifrada independiente, restauración desde offsite o medición RPO/RTO no acreditadas |
| Teléfono | Propiedad carrier, SID/cuenta/tenant, callbacks POST y modelo de voz sin evidencia real |
| Efectos externos | Llamadas, outbound, Twilio writes o Gemini externo activados; otro tenant expuesto o piloto no pausado |
| Personas | No existen dos revisores distintos identificados, ventana escrita, responsable rollback y criterios STOP |

Los campos de entrada son **declaraciones no autenticadas**, nunca
certificados firmados. Si una muestra declara que todo está verificado,
el gate sólo informa \`OFFLINE_REHEARSAL_ONLY\` y deja siempre
\`prod_deploy=NO-GO\`, \`may_merge=false\`,
\`may_migrate=false\`, \`may_modify_webhooks=false\`,
\`may_make_real_calls=false\`, \`commercial_go=false\`.
Tampoco imprime el contenido del snapshot: únicamente códigos de bloqueo.

El CLI incluye \`--demo-blocked\` (bloqueos reproducibles) y
\`--demo-optimistic\` (sólo comprueba la lógica positiva sin GO).
Un archivo aportado mediante \`--evidence\` se evalúa sin subirlo,
sin acceder a la red y sin autorizar cambios. Está prohibido introducir
tokens, dump, nombre/teléfono de cliente o secrets en el expediente CI.

## 2. Casos de caos/abort y rollback simulado

La batería adversarial rechaza:

- SHA incorrecto o una rama retargeteada a \`main\`.
- Historia de migraciones V99 omitida o V103 ya presente en producción.
- Respaldo desactualizado o sin copia offsite restaurable.
- Divergencia de propiedad carrier, SID, tenant, callback o selección de IA.
- \`pilot_paused=false\`, llamadas/outbound/voz habilitados.
- Aprobador repetido, autorización incompleta o falta de operador de rollback.
- Pruebas CI incompletas, campos booleanos falsificados como \`1\`,
  y timestamps vencidos/futuros.
- Intentos de marcar GO basándose sólo en datos sintéticos.

**Rollback NO es un botón automatizado:** el camino seguro ensayado
en el gate es \`STOP\` sin mutaciones. En un despliegue real ya iniciado,
hay que evaluar compatibilidad del backend con V100–V103, preservar
logs, no ejecutar \`flyway repair\` para ocultar errores ni \`DROP\`
de migraciones; una restauración real requiere evaluar pérdida de
escrituras posteriores, outage, consentimiento y autorización separada.

## 3. Cuatro gates de CI

Workflow: \`.github/workflows/voice-release-part7-predeployment-no-go.yml\`

1. \`predeployment-stop-decision\`: modelos adversariales y control de
   definición del workflow, contrato de recuperación Parte 6 y migraciones
   V100–V103 inmutables.
2. \`restore-regression\`: DOS contenedores PostgreSQL 18 sin red,
   \`pg_dump\`, copias cifradas sintéticas temporales y \`pg_restore\`.
   No es respaldo offsite ni RTO de producción.
3. \`flyway-tenant-rollback-regression\`: Java 21, Testcontainers PG18,
   migración incremental V99→V103, RLS y doble aprobación de enrolamiento.
4. \`react-spring-pg18-system-e2e\`: React→Spring Boot→PG18 efímero
   en localhost, identidades sintéticas y Playwright; sin externos.

El workflow corre exclusivamente por push a rama Parte 7 y PR apilado
sobre Parte 6. Permisos GitHub \`contents:read\`; no secrets,
endpoints productivos, workflows de despliegue ni merges. Banderas
de llamadas, outbound, IA externa, compra y jobs siempre OFF.
Si falta un gate, hay pruebas fallidas o el SHA difiere, no certificar.

## 4. Estados productivos que NO deben inferirse

La última observación read-only previa mostró API y PostgreSQL
Railway online y \`main\` en
\`ca58693e167752bd6eab8ff4e9be770391fe4293\`.
La última versión de Flyway productiva **reportada anteriormente** fue
V99; sin una nueva consulta directa no debe tratarse como valor actual.

No se ha verificado con nueva evidencia:
- Dump privado actualizado y restauración reciente.
- Copia cifrada autenticada redundante en medio independiente.
- PITR y RTO/RPO medidos.
- Dos administradores reales y ventana de autorización.
- Comparación autenticada Twilio Account SID / PN SID / webhook para el
  número piloto con los datos actuales de producción.
- Prueba facturable consentida del audio Gemini Live.

Por tanto, un 4/4 SUCCESS de CI no cambia
\`PROD_DEPLOY=NO-GO\`, \`LIVE_CALL_CERTIFIED=false\`,
\`COMMERCIAL_GO=NO-GO\`.

## 5. Evidencia final y siguiente frontera

Al cerrar la Parte 7: guardar links CI de push y PR, mismo SHA HEAD,
jobs, totales de tests reales, resultado de Railway y \`main\`
consultados read-only, y el listado de bloqueos pendientes.
Actualizar sólo la descripción del PR, sin mover SHA certificado.

**No iniciar Parte 8** hasta la instrucción explícita \`CONTINÚA\`.
La Parte 8 deberá definir el siguiente paso de certificación real bajo
autorizaciones independientes y límites de coste, sin convertir
automáticamente un PASS offline en GO comercial.
