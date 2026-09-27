# Commercial Release Candidate V1

Fecha de coordinación: 2026-09-27

## Objetivo

Este documento registra el baseline comercial integrado y la evidencia canónica de cierre técnico de RecepVoz, sin activar proveedores reales ni ejecutar un deploy.

**Estado actual: CIERRE TÉCNICO AUTOMATIZADO PASS. `main` incluye el fix de concurrencia de reservas, la certificación de agotamiento de inventario y el runbook de rollback/gates externos. El lanzamiento comercial real sigue bloqueado únicamente por gates administrativos/externos explícitos.**

## Base y límites

- repositorio: `Nicricht/helvoca`
- baseline canónico: `main@4244f5350f8049be00138c26a73a60db8026cfb4`
- #541 sigue incluido (controlled real business launch safety v1)
- #548 sigue preservado y conserva la certificación de latencia read-only
- #602 está integrado: elimina la carrera concurrente de confirmación de bookings y agrega V79
- #604 está integrado: certifica `stock 2 -> 1 -> 0 -> INSUFFICIENT_STOCK` contra PostgreSQL Testcontainers
- #601 está integrado: rollback del primer cliente y gates externos explícitos
- `main` está protegido por el ruleset activo `Protect main` (ID `24088016`)
- no se ha creado tag comercial
- no se ha desplegado este RC

## Fuentes integradas

| Fuente | Área | Integración al RC |
| --- | --- | --- |
| #549 | Release hardening / CI | #567 merged al RC |
| #550 | Voice lifecycle / hangup fence | #568 merged al RC |
| #551 | Golden Journey comercial | #561 merged al RC |
| #552 | Billing SaaS / V77 | #562 merged al RC |
| #556 | Observabilidad comercial / V78 | #563 merged al RC |
| #553 | Operación primer cliente | #569 merged al RC |
| #555 | Demo tenant / onboarding reproducible | reconciliado con #553 en #571 |
| #554 | Dashboard del dueño | #565 merged al RC |
| #559 | Separación owner / operations | #566 merged después de #554 |

La integración temporal #564 fue cerrada como superseded por #571. La reconciliación conserva simultáneamente los requisitos comerciales de #553 y los requisitos de perfil/demo de #555.

## Integraciones de cierre posteriores al RC

- **#602**: fix productivo de concurrencia en confirmación de reservas; PR CI PASS antes de merge.
- **#604**: certificación comercial de agotamiento de inventario; PR CI PASS antes de merge.
- **#601**: documentación de rollback y gates externos; PR CI PASS antes de merge.
- **#584**: cerrada como superseded; su rama histórica quedó detrás del baseline actual.
- **#543**: cerrada como superseded; el preflight actual de `main` reemplaza esa rama apilada histórica.

No quedan PRs funcionales pendientes después de esta convergencia; #603 es únicamente el cierre documental de la evidencia final.

## Evidencia de source PRs

Todas las fuentes seleccionadas llegaron al ensamblaje con su CI individual verde.

Evidencia especialmente sensible:

- #552: billing SaaS certificado sin cobros reales; migración `V77__saas_billing_webhook_idempotency.sql`.
- #556: observabilidad certificada; migración `V78__commercial_observability_runtime_metadata.sql`; preserva el aislamiento read-only introducido por #548.
- #550: agrega fence de salida para impedir audio/clear tardío después de un hangup aceptado; no cambia Sulafat, prompt ni Hybrid VAD.
- #549: elimina la dependencia de `[verify]` para que Fast Gate + Full Gate también se ejecuten en futuros pushes a `main`.

## Voz y latencia congeladas

Se completó una llamada humana controlada sobre `main@2bc4265a0c15498f6f6aa0363238d70b632cb4ae` con Gemini Live, Sulafat y Hybrid VAD.

Resultado observado:

- mediana de respuesta: 1.765 s
- máximo: 3.629 s
- 0 respuestas >5 s
- barge-in observado
- herramientas observadas: `list_services` y `end_call`
- sin operaciones mutantes observadas
- cierre limpio mediante `end_call`
- bootstrap de certificación limpiado después del run

Voz + latencia quedan congeladas salvo regresión reproducible.

La integración posterior de #550 no cambia voz, prompt ni VAD: únicamente endurece el transporte después del hangup. Su regresión está cubierta por tests automatizados. Bajo las reglas actuales no se repite otra llamada real solo para este ensamblaje.

## Gates

### G1. Source PRs verdes

- [x] #549
- [x] #550
- [x] #551
- [x] #552
- [x] #553
- [x] #554
- [x] #555
- [x] #556
- [x] #559

### G2. Compatibilidad e integración

- [x] candidato parte del `main` vigente de la coordinación
- [x] #548 read-only preservado
- [x] V77 billing entra antes de V78 observabilidad
- [x] fix de lifecycle de voz preservado
- [x] hardening de CI preservado
- [x] #553 + #555 reconciliados sin descartar requisitos
- [x] #554 integrado antes de la rama apilada #559
- [x] sin force push
- [x] cambios de cierre integrados mediante PRs explícitamente autorizadas (#602, #604 y #601)
- [x] PRs históricas #584 y #543 cerradas como superseded

### G3. Certificación automatizada del `main` integrado

Evidencia canónica posterior a la convergencia:

- baseline certificado: `main@4244f5350f8049be00138c26a73a60db8026cfb4`
- workflow: **RecepVoz CI #2296 / run 36357933434 — SUCCESS**
- Fast Gate: **PASS**
- Golden Journey release contract: **PASS**
- production-gate: **PASS**
- backend + JaCoCo: **1.324 tests, 0 failures, 0 errors, 0 skipped**
- Maven: **BUILD SUCCESS**
- browser E2E / Playwright: **74/74 passed**
- status canónico: **`helvoca/full-verification = success`**

Además:

- #602 pasó differential Java coverage antes del merge;
- #604 pasó differential Java coverage y su suite PostgreSQL específica ejecutó 6/6 tests sin fallos antes del merge;
- #601 no modificó Java productivo;
- el push a `main` omite differential coverage por diseño del workflow, porque ese control se aplica en PR.

Por tanto:

- [x] Fast Gate
- [x] Full backend suite + JaCoCo
- [x] browser E2E
- [x] Golden Journey release contract
- [x] production-gate
- [x] full-verification de `main`
- [x] booking concurrency fix integrado y recertificado
- [x] inventory depletion certification integrada y recertificada

La evidencia canónica ya no es una rama RC: es el `main` integrado indicado arriba.

### G4. Voz humana

- [x] Sulafat consistente durante la prueba humana
- [x] respuesta percibida dentro del objetivo de latencia definido
- [x] interrupción/barge-in observada
- [x] lookup read-only real observado
- [x] despedida + `end_call` completados
- [x] sin acciones mutantes durante la certificación
- [x] sin nueva prueba real requerida mientras no exista una regresión reproducible

### G5. Billing externo

Antes de cobrar a un cliente mediante el flujo SaaS real:

- [ ] checkout sandbox con cuenta/proveedor autorizado
- [ ] webhook firmado sandbox
- [x] invoice approved activa entitlement correcto en la certificación interna
- [x] invoice rejected/canceled aplica el estado esperado sin duplicar efectos en la certificación interna
- [x] webhook repetido es idempotente en la certificación interna
- [x] referencias e IDs inconsistentes fallan cerrados en la certificación interna
- [ ] cancelación respeta la política de acceso
- [ ] pricing público coincide con catálogo backend
- [ ] cualquier prueba live requiere autorización explícita y queda fuera de esta certificación

No confundir este gate con merchant payments de los clientes de nuestros clientes.

### G6. Seguridad del repositorio

Ruleset activo verificado: **`Protect main` / ID `24088016`**.

- [x] proteger `main`
- [x] exigir pull request antes de merge
- [x] exigir `fast-gate`
- [x] exigir Full Gate / `test`
- [x] exigir que la rama esté actualizada antes de merge
- [x] bloquear non-fast-forward / force push
- [x] bloquear eliminación de `main`
- [x] sin bypass para el usuario actual

El ruleset está en enforcement `active`, aplica a la rama por defecto y usa los checks de GitHub Actions `fast-gate` y `test`. No exige aprobación humana adicional (`required_approving_review_count=0`). La propia PR #603 fue bloqueada al intentar mergear con checks de un HEAD desactualizado, confirmando que la política estricta está siendo aplicada.

### G7. Primer cliente

Antes de declarar un cliente real activo:

- [ ] onboarding con datos aprobados del negocio
- [ ] servicios/productos/precios/horarios/FAQ aprobados
- [ ] canal incluido certificado para ese tenant
- [ ] medio autorizado para pagar/facturar RecepVoz
- [ ] dashboard muestra el tenant correcto
- [ ] Journey Trace permite diagnosticar su operación
- [ ] no existe P0/P1 conocido dentro del alcance vendido
- [ ] rollback y contacto de soporte definidos

## Condición de salida

**El cierre técnico automatizado está completo y verde.** No quedan bugs P0/P1 conocidos identificados por esta convergencia ni PRs funcionales pendientes de integración.

El lanzamiento comercial real permanece deliberadamente bloqueado por gates que no pueden sustituirse con mocks internos:

1. checkout + webhook sandbox con una cuenta/proveedor SaaS autorizado, antes de cobrar RecepVoz a un cliente;
2. onboarding aprobado del primer cliente real, con sus datos, canales, responsables y soporte;
3. autorización explícita para activar proveedor/deploy/cobro real.

Hasta completar esos puntos, el estado correcto es **TECHNICALLY READY / EXTERNAL GATES PENDING**, no “producción activada”.

## Prohibiciones después del cierre técnico

- no direct push normal a `main`
- no force push
- no deploy comercial hasta cerrar gates externos
- no llamadas reales adicionales sin una ventana de piloto autorizada
- no WhatsApp real sin autorización del tenant/canal
- no cobro real ni credenciales live durante certificaciones
- no activar proveedores para hacer pasar una demo
- no esconder fallos bajando umbrales de CI
