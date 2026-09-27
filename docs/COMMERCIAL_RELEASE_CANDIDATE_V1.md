# Commercial Release Candidate V1

Fecha de coordinación: 2026-09-27

## Objetivo

Esta rama no desarrolla funcionalidades nuevas. Su propósito es concentrar la evidencia necesaria para decidir cuándo RecepVoz puede pasar de varias PR comerciales independientes a un único candidato de release verificable.

**Estado actual: NO-GO para release comercial.**

Esto no significa que el producto completo esté fallando. Significa que todavía faltan gates obligatorios antes de vender/activar un primer cliente real.

## Base actual

- repositorio: `Nicricht/helvoca`
- rama base: `main`
- SHA base al crear esta coordinación: `2bc4265a0c15498f6f6aa0363238d70b632cb4ae`
- ese SHA incluye el merge de PR #548, aislamiento de certificación de latencia read-only
- `main` continúa reportándose sin branch protection obligatoria

No se debe etiquetar ni desplegar un release candidate desde un SHA anterior.

## Matriz de trabajo comercial

| PR | Área | Estado de coordinación |
| --- | --- | --- |
| #549 | Release hardening | CI verde en su head; Draft |
| #550 | Voz y lifecycle | CI verde; certificación automática completa; Draft |
| #551 | Golden Journey comercial | CI verde; Draft |
| #552 | Billing SaaS | CI verde y mergeable; 1.260 backend tests, 100% diff line, 97,1% diff branch, 68 E2E; Draft |
| #553 | Operación primer cliente | CI verde; Draft |
| #554 | Dashboard del dueño | CI verde después de corregir la validación responsive; Draft |
| #555 | Demo tenant / onboarding reproducible | CI verde después de completar cobertura diferencial; Draft |
| #556 | Observabilidad comercial | conflicto con #548 reconciliado; CI verde y mergeable; 1.250 backend tests, 87,8% diff line, 100% diff branch, 68 E2E; Draft |

Todas estas PR deben permanecer sin merge a `main` durante esta coordinación.

## Resultado automatizado de esta ronda

- #552 head certificado: `a279f2bf78fc2e0121bf4e26958a1b9627a6a4c1`
  - workflow run #2230: PASS
  - backend: 1.260 tests, 0 failures, 0 errors
  - differential line coverage: 40/40 = 100%
  - differential branch coverage: 33/34 = 97,1%
  - browser E2E: 68 passed
- #556 head certificado: `8b3ad71a1dc973af91ca5bdc2f714b7e8d923005`
  - workflow run #2228: PASS
  - backend: 1.250 tests, 0 failures, 0 errors
  - differential line coverage: 36/41 = 87,8%
  - differential branch coverage: 10/10 = 100%
  - browser E2E: 68 passed
- #560, esta coordinación, pasó su propio CI sobre el `main` actual.

Con esto, el gate de **source PRs individuales** está verde. El estado global sigue siendo NO-GO porque aún no existe una rama integrada certificada con todas las PR juntas y siguen pendientes los gates humanos/externos.

## Gates obligatorios

### G1. Source PRs verdes

Antes de ensamblar un candidato combinado:

- [x] #549 Fast/Full Gate verde
- [x] #550 Fast/Full Gate verde
- [x] #551 Fast/Full Gate verde
- [x] #552 Fast/Full Gate verde en el head definitivo
- [x] #553 Fast/Full Gate verde
- [x] #554 Fast/Full Gate verde
- [x] #555 Fast/Full Gate verde
- [x] #556 Fast/Full Gate verde y mergeable contra el `main` actual

Un PR verde sobre una base antigua no sustituye la validación contra el `main` actual.

### G2. Compatibilidad con main actual

El candidato debe partir del último `main`, no de `67ca6bb...`.

Requisitos:

- [x] todas las PR seleccionadas son mergeables contra el SHA actual de `main`
- [x] conflictos con #548 resueltos preservando aislamiento read-only de latencia
- [x] no se pierde instrumentación `ai_model` / `duration_ms`
- [ ] no se pierde el fix de lifecycle de voz
- [ ] no se debilitan los gates de CI

### G3. Candidato integrado

CI verde por PR individual no demuestra que todas las PR funcionen juntas.

Antes de release:

- [ ] construir una rama de integración desde el `main` vigente
- [ ] incorporar únicamente los cambios comerciales aprobados
- [ ] resolver conflictos sin force push
- [ ] ejecutar Fast Gate
- [ ] ejecutar Full Gate
- [ ] ejecutar Golden Journey
- [ ] ejecutar voice commercial certification
- [ ] ejecutar billing commercial certification
- [ ] ejecutar demo/onboarding certification
- [ ] verificar browser E2E
- [ ] verificar cobertura diferencial

No se debe mergear esa rama a `main` hasta completar los gates humanos.

### G4. Voz humana real

La automatización no puede certificar percepción acústica.

Antes de vender telefonía real se requiere una llamada humana controlada que confirme:

- [ ] Sulafat permanece femenina y consistente desde saludo a despedida
- [ ] español natural para clientes de Chile
- [ ] ritmo rápido/natural y respuestas breves
- [ ] interrupción real corta el audio inmediatamente
- [ ] dos interrupciones rápidas no reanudan audio viejo
- [ ] tool lenta no produce comportamiento extraño
- [ ] datos conocidos no se preguntan otra vez
- [ ] una corrección del usuario reemplaza el dato anterior
- [ ] despedida completa
- [ ] carrier corta después de la última palabra audible
- [ ] no existe audio residual después del hangup

Esta prueba no debe ejecutarse mientras la certificación vigente prohíba llamadas reales.

### G5. Billing externo

La lógica SaaS debe seguir separada de merchant payments.

Antes de cobrar a un cliente:

- [ ] checkout sandbox con cuenta/proveedor autorizado
- [ ] webhook firmado sandbox
- [ ] invoice approved activa entitlement correcto
- [ ] invoice rejected/canceled entra a PAST_DUE sin duplicar efectos
- [ ] webhook repetido es idempotente
- [ ] referencias e IDs inconsistentes fallan cerrados
- [ ] cancelación de suscripción retira acceso según política
- [ ] pricing público coincide con catálogo backend
- [ ] prueba live solo cuando exista autorización explícita y fuera de esta certificación

### G6. Seguridad del repositorio

Antes del tag comercial:

- [ ] proteger `main`
- [ ] exigir PR para merge
- [ ] exigir `fast-gate`
- [ ] exigir Full Gate / `test`
- [ ] bloquear direct push normal
- [ ] bloquear force push

La integración conectada actualmente permite auditar estas reglas, pero no aplicar branch protection. No se debe fingir que están configuradas.

### G7. Primer cliente

Antes de declarar CUSTOMER:

- [ ] onboarding aprobado con datos reales del negocio
- [ ] precios/servicios/horarios/FAQ aprobados
- [ ] canal telefónico real certificado
- [ ] billing/facturación de RecepVoz autorizado
- [ ] dashboard muestra actividad del tenant correcto
- [ ] Journey Trace permite diagnosticar una llamada del piloto
- [ ] no existe P0/P1 conocido en el flujo contratado
- [ ] rollback y contacto de soporte definidos

## Criterio de GO

RecepVoz pasa a **GO comercial** únicamente cuando:

1. los source PRs están verdes y compatibles con el `main` vigente;
2. un candidato integrado independiente pasa todos los tests;
3. la llamada humana real pasa el checklist acústico/carrier;
4. billing sandbox queda certificado con proveedor autorizado;
5. `main` queda protegido antes de cualquier tag/merge comercial;
6. el primer cliente tiene onboarding, soporte y rollback definidos.

Hasta entonces, la decisión permanece **NO-GO**, aunque varias áreas por separado ya estén certificadas.

## Prohibiciones durante esta coordinación

- no deploy
- no merge a `main`
- no force push
- no llamada real bajo las reglas actuales
- no WhatsApp real
- no cobro real
- no credenciales live
- no activar proveedores para “hacer pasar” una demo
- no esconder fallos bajando umbrales de CI
