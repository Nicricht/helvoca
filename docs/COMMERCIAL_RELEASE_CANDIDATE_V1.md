# Commercial Release Candidate V1

Fecha de coordinación: 2026-09-27

## Objetivo

Esta rama ensambla y certifica un único candidato comercial de RecepVoz sin tocar `main`, sin deploy y sin activar proveedores reales.

**Estado actual: RC integrado, certificación final automatizada en curso. NO mergear a `main` todavía.**

## Base y límites

- repositorio: `Nicricht/helvoca`
- rama: `chore/commercial-release-candidate-v1`
- base de ensamblaje: `main@2bc4265a0c15498f6f6aa0363238d70b632cb4ae`
- #548 ya forma parte de esa base y conserva la certificación de latencia read-only
- `main` continúa sin branch protection/ruleset obligatorio
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
- [x] `main` no fue modificado

### G3. Certificación automatizada del HEAD integrado

El HEAD definitivo del RC debe pasar como una sola unidad:

- [ ] Fast Gate
- [ ] Full backend suite + JaCoCo
- [ ] differential Java coverage
- [ ] browser E2E
- [ ] Meta webhook public smoke
- [ ] Golden Journey incluido en el gate
- [ ] voice commercial certification incluida en el gate
- [ ] billing commercial certification incluida en el gate
- [ ] demo/onboarding tests incluidos en el gate

No se considera certificado por el mero hecho de que las ramas fuente estuvieran verdes.

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
- [ ] invoice approved activa entitlement correcto
- [ ] invoice rejected/canceled aplica el estado esperado sin duplicar efectos
- [ ] webhook repetido es idempotente
- [ ] referencias e IDs inconsistentes fallan cerrados
- [ ] cancelación respeta la política de acceso
- [ ] pricing público coincide con catálogo backend
- [ ] cualquier prueba live requiere autorización explícita y queda fuera de esta certificación

No confundir este gate con merchant payments de los clientes de nuestros clientes.

### G6. Seguridad del repositorio

Antes de cualquier merge/tag comercial:

- [ ] proteger `main`
- [ ] exigir PR para merge
- [ ] exigir `fast-gate`
- [ ] exigir Full Gate / `test`
- [ ] bloquear direct push normal
- [ ] bloquear force push

La conexión GitHub disponible puede auditar el repositorio, pero no expone escritura administrativa de branch protection/rulesets. Este bloqueo no debe marcarse como resuelto hasta aplicarlo desde una credencial/canal autorizado.

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

El RC queda técnicamente listo para una decisión de merge solo cuando G3 esté completamente verde.

El lanzamiento comercial real sigue además condicionado a:

1. billing sandbox autorizado, cuando el flujo de cobro SaaS vaya a activarse;
2. protección obligatoria de `main`;
3. onboarding y canales del primer cliente;
4. autorización explícita antes de tag, merge a `main`, deploy o cobro real.

## Prohibiciones durante esta coordinación

- no deploy
- no merge a `main`
- no force push
- no llamadas reales adicionales
- no WhatsApp real
- no cobro real
- no credenciales live
- no activar proveedores para hacer pasar una demo
- no esconder fallos bajando umbrales de CI
