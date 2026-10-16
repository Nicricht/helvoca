# PLAN MAESTRO ÚNICO DE CIERRE COMERCIAL · HELVOCA / RECEPVOZ V1

**Creado:** 2026-10-13 · **Tablero vivo y único:** [Issue #823](https://github.com/Nicricht/helvoca/issues/823) · **Estado inicial:** EN EJECUCIÓN, NO-GO para voz comercial nueva.

## Checkpoint vigente · 2026-10-16: foco en publicación, no en apagar funcionalidades

**Este checkpoint sustituye las frases antiguas que describen como pendiente algo ya demostrado.** Los bloques históricos inferiores siguen siendo actas fechadas, no nuevas tareas. La fuente de seguimiento es [Issue #823](https://github.com/Nicricht/helvoca/issues/823); los cambios de software se hacen solo en [PR #846](https://github.com/Nicricht/helvoca/pull/846), sin intervenir `main` ni Railway producción salvo autorización específica.

| Puerta | Último estado demostrado | Única brecha real que sigue abierta |
| --- | --- | --- |
| **C0** | Alcance SaaS V1 y tablero maestro C0–C7 definidos; nada de fases nuevas. | Ninguna; no reabrir alcance por preferencias opcionales. |
| **C1** | 2026-10-15/16: descarga cifrada independiente Drive con hash, restore auténtico a PostgreSQL 18 Railway DR, reparación acotada de roles globales y 290 ACL, Flyway hasta V103 y API DR saludable; destino DR eliminado. Duración observada **36 min 54 s desde provisión DR DB hasta salud API**, excluye descarga previa. Captura Railway production Postgres > Backups 2026-10-16: snapshot nativo **126 MB, ~6 h** en ese momento, segundo diario ~1 día. | El snapshot **nativo concreto** no fue restaurado ni declarado recuperable; RPO sostenido <=24 h y RTO extremo a extremo <=4 h no certificados; custodia/operador, frescura **en ventana de release** y recuperación acordada. **No repetir el DR cifrado ya exitoso.** |
| **C2-A** | PR #846 DRAFT `c96d166d56cea0ec9765fc6c021263b1a0291948`; 14/14 GitHub workflows **SUCCESS sobre ese HEAD**. Comparación con `main@ca58693e167752bd6eab8ff4e9be770391fe4293`: ahead 515, behind 0, 291 archivos; cuatro migraciones V100–V103 nuevas. | Mantener CI exact-HEAD tras cualquier nueva modificación y preparar decisión de liberación, sin fusionar automáticamente. |
| **C2-B** | Railway API production y Postgres18 online 1/1, deployment API `971b6949-1894-421d-9494-eb3f96724a94`, cero staged/pending. Siete variables preventivas se pusieron en false **con respaldo DPAPI local de originales 5 existentes/2 ausentes**; primer probe 19/19 OFF. Auditor ampliado probó después **45/46 OFF, `TWILIO_WHATSAPP_ENABLED` NOT_OFF**. Se mantuvo **sin cambio** este octavo interruptor tras petición del dueño de no apagar funcionalidades innecesariamente. | **No tratar 46/46 OFF como definición de producto vendible.** Faltan verificaciones de STOP por tenant, `NOT_PILOT_MANAGED`, jobs ya `RUNNING`, dependencias de proveedores, rollback schema-aware tras V100–V103, custodio y ventana, y autorización de merge independiente. Los siete interruptores no son una configuración comercial definitiva; la activación real se evaluará por servicio y tenant. |
| **C3/C4** | Integración Twilio, Gemini y llamadas previas con QA offline y llamada humana controlada histórica. | Vinculación auténtica existente con doble aprobación y certificación de llamada **para el tenant piloto final**, con consentimiento y presupuesto. No repetir llamada histórica por inercia. |
| **C5** | Checkout Mercado Pago TEST auténtico histórico con preapproval e invoice aprobada; Java/PG aislado ejercitado con simulador, navegación y estados idempotentes cubiertos por QA. | **Webhook firmado auténtico** de Mercado Pago al backend Java desplegado en entorno de ensayo y PostgreSQL aislado; luego autorización separada de cobro LIVE y comprobación de entitlements. No confundir TEST histórico con este round-trip. |
| **C6/C7** | Privacidad, soporte, checklists y Golden Journey offline documentados y probados. | Aprobaciones/contactos/criterios legales y operativos; primer negocio piloto real, aceptación del circuito completo y primer pago verificado. Sin cliente real aún, ningún GO comercial. |

**Prioridad de ejecución sin acciones del propietario ahora:** (1) reutilizar QA/recovery auténticos, no repetir pruebas; (2) preparar y revisar evidencias de STOP por tenant/cola y release/rollback con herramientas read-only y tests existentes; (3) cuando las puertas previas sean demostrables, pedir **una autorización puntual de ventana y merge**, informando que `main` auto-despliega Flyway V100–V103; (4) habilitar **por fases** voz IA, Twilio y cobros para pruebas reales consentidas, no mantenerlos apagados para siempre. Las opciones no incluidas en V1, como Meta WhatsApp comercial LIVE, no bloquean lanzamiento.

**Interpretación vinculante:** `14/14 CI` demuestra software offline en ese SHA, **no** autenticidad de proveedores ni aprobación comercial. `TWILIO_WHATSAPP_ENABLED: NOT_OFF` no prueba mensaje enviado ni obliga a apagar el canal; no modificarlo sin aprobación separada. `C1_NATIVE_RESTORE=UNVERIFIED`; `C2_B_RELEASE=NO-GO`; `HELVOCA_V1_COMERCIAL=NO-GO`. Nada de activar proveedores, gastar, fusionar PR, lanzar pagos, restaurar volumen productivo o copiar secretos por este documento.


## 0. Contrato de finalización: qué significa «Helvoca terminado»

Este documento **sustituye como tablero principal de cierre** cualquier lista dispersa de «qué tenemos y qué falta». Los documentos previos siguen siendo especificaciones, runbooks o evidencias, **no programas de desarrollo paralelos**. El Issue #823 es el único tablero de avances. Cada trabajo nuevo debe estar asociado a una puerta C0–C7 de ese issue. No añadir «fases» sin una brecha medible contra esta definición.

**Producto final incluido en V1:** SaaS multiempresa para un negocio de servicios/citas en Chile que permite registro e inicio de sesión, elección de plan y **pago de suscripción a Helvoca**, configuración empresarial e información/horarios/FAQ, vinculación controlada de un número Twilio ya propio, recepción **real** de llamadas con voz IA Gemini Live en español de Chile, consulta de disponibilidad y creación/corrección de reservas sin duplicarlas, derivación a humano, panel con resultados, límites comerciales, seguridad multiempresa, privacidad, respaldo y soporte. El administrador del negocio debe realizar el recorrido usual desde la web; la conciliación de un número preexistente **puede requerir aprobación asistida de dos administradores de plataforma** por razones de seguridad, sin presentarla como autovinculación instantánea.

**No incluido y no bloqueante en V1:** merchant payments LIVE realizados por los clientes del negocio, Meta WhatsApp LIVE, campañas salientes, un tercer proveedor de voz, calendario externo, otros países/idiomas, marca blanca, disponibilidad enterprise contractual o características nuevas no imprescindibles para el primer negocio. No vender esos extras como disponibles antes de su certificación particular.

Dos hitos distintos:
- **PILOTO_ASISTIDO_VENDIBLE:** ya existe núcleo FAQ/reservas y una oferta de piloto web asistido sin afirmar que voz, pago automático ni proveedores externos estén certificados. Puede ofrecerse **solo dentro del alcance documentado** en `docs/COMMERCIAL_LAUNCH_STATUS.md` y `docs/FIRST_CUSTOMER_OPERATION.md`.
- **HELVOCA_V1_COMERCIAL=TERMINADA:** C0–C7 cerradas con evidencia; flujo real registro→pago SaaS→configuración→recepción voz Twilio→acción de agenda→panel→soporte; primera activación de un negocio bajo condiciones aceptadas; sin P0, costes y protección de datos controlados. No declarar esto solo por CI verde, una demo o una compra simulada.
- **PRIMER_CLIENTE_PAGADOR=VERIFICADO:** hito de negocio separado y adicional: se ha recibido un pago auténtico y el negocio realmente opera el alcance contratado. Para declarar concluido el **lanzamiento comercial inicial**, también debe estar cerrado; nunca falsificar interés, consentimiento o pago.

## Estado corregido y preparación C2-B (2026-10-14, evidencia auditada)

> **Este bloque tiene precedencia sobre las afirmaciones históricas inferiores que figuren como PENDING o 'Railway Pro no contratado'.** No constituye permiso de merge, deploy, migración, llamadas o cobros. [Issue maestro #823](https://github.com/Nicricht/helvoca/issues/823) conserva los checkpoints y las decisiones del titular.

| Puerta | Certificado/realizado | Pendiente no duplicado |
| --- | --- | --- |
| C0 | Alcance V1 y tablero maestro | Ninguna nueva fase de planificación |
| C1 | **Ensayo real 2026-10-15/16:** nuevo respaldo age en Drive con descarga independiente y hash verificados por operador; pg_restore auténtico a PostgreSQL18 DR, **5,5 s de transferencia y restore**; rol global + ACL faltantes causaron un primer fallo Flyway V101, se reconstruyeron ambos roles y **290 sentencias ACL** solo en DR. Reinicio API contra ese PG18: **Flyway V103, /actuator/health, Railway API Online 1/1 y DB Online 1/1**; **36 min 54 s desde provisión DB a API saludable** según timestamps Railway. Entorno DR limpiado a **0 servicios, 0 volúmenes y 0 staged**; producción intacta. [Acta #823](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6204969940) y [runbook C1](docs/POSTGRES_PRODUCTION_RECOVERY.md). | **C1 sigue IN_PROGRESS / NO-GO comercial:** esa ventana no incluyó descarga previa de Drive ni prueba adversarial completa de dos tenants. **RPO <=24h sostenido y RTO <=4h contractual no certificados**, falta custodia independiente de llave, respuesta a alertas/cuota institucional y frescura real de snapshots completados en la ventana C2-B. No repetir el ensayo ya logrado, no contratar servicios ni activar PITR por defecto. No fusionar PR #846 sin gate C2-B y aprobación separada. |
| C2-A | **PR integrado [#846](https://github.com/Nicricht/helvoca/pull/846)** DRAFT, 481 commits por delante de `main` al crear, CI [#38667741615](https://github.com/Nicricht/helvoca/actions/runs/38667741615) PASS sobre SHA `3ead3f0698f18780a7629aab0300b6381e863966`: 2.262 Java, 620 Playwright, diferencial Java líneas/ramas/métodos 100%; 11 suites especializadas PASS. **Flyway producción V99 predespliegue `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` comunicado por operador (2026-10-15T02:29Z)** | C2-B: revalidar CI en SHA nuevo si cambia documentación o código, acreditar respaldo nativo fresco, RPO/RTO medidos según alcance aprobado, responsable/ventana, rollback compatible con DDL, protección de tenants, autorización de merge **separada y explícita**. Railway auto-despliega `main` y puede ejecutar V100–V103; **NO-GO** sin esas puertas. Tras el despliegue autorizado, verificar SHA, versión Flyway V103, RLS y salud de API. |
| C3-A/C4-A | Conciliación Twilio y Gemini Live, límites de duración, aislamiento, pruebas offline certificadas [#6174117430](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6174117430), [#6174418553](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6174418553); llamada histórica 27-09-2026 [contrato externo](COMMERCIAL_EXTERNAL_GATES_V1.md) | C3-B autenticación real Twilio + dos personas; C4-B llamada autorizada del **tenant final** con costes y control STOP; no repetir llamadas por CI |
| C5-A/C6-A | Mercado Pago sandbox: preapproval TEST autorizado y factura aprobada [CI real](https://github.com/Nicricht/helvoca/actions/runs/36360749617); PostgreSQL real contra gateway externo simulado [PR #840](https://github.com/Nicricht/helvoca/pull/840); navegación/selección plan [#843](https://github.com/Nicricht/helvoca/pull/843); privacidad/soporte documental [#844](https://github.com/Nicricht/helvoca/pull/844) | Webhook auténtico del proveedor al Java **desplegado** y persistencia; pago LIVE autorizado; consentimiento, operación legal, costes y responsables aceptados |
| C7 | Golden Journey automático offline preparado y QA PASS | Flujo real piloto: negocio registra→paga→configura→recibe llamada→reserva→soporte, con aceptación y pago real |

### Próxima operación permitida: C2-B *preflight*, solo lectura

1. Conservar `main` y Railway production intactos. Comparación autenticada: PR #842 estaba 462 commits ahead/0 behind de `main` (2026-10-14), y la rama de #845 474 ahead/0 behind; ambos son **candidatos**, no deployments.
2. **Flyway ya comprobado en sesión Railway CLI autenticada del titular** mediante `scripts/ops/railway_pg18_flyway_readonly_preflight.py` y [runbook Stage D](telephony/2026-10-13-stage-d-operational-readonly-preflight.md): `STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED` a las 2026-10-15T02:29Z, **PASS comunicado por operador**; el informe permanece privado, no inspeccionado por esta sesión. No repetir. El conector Railway del asistente no ejecuta SQL; si cambia producción antes de release, la evidencia podría quedar obsoleta y debe revalidarse proporcionalmente.
3. Preparar despliegue y estrategia STOP/rollback **sin ejecutarlo**: migraciones V100–V103 añaden restricciones y estructuras nuevas; revertir el binario no revierte el esquema. El dump histórico y el snapshot anterior no prueban frescura *en la ventana futura*. Antes del merge debe existir recuperación utilizable con objetivos aceptados.
4. No habilitar PITR, restaurar in-place sobre la base activa, modificar variables, contratar servicios, desplegar ni fusionar hasta aprobar específicamente la intervención. Las operaciones ordinarias de GitHub y QA offline no están bloqueadas por C1.

**Estado actualizado 2026-10-15:** `C2-A=DONE_OFFLINE_FULL_QA_AT_PREVIOUS_SHA`, `C1_RPO_RTO_TARGETS=OWNER_APPROVED`, `C1_FRESH_SNAPSHOT=UNVERIFIED`, `C1_FULL_SERVICE_RTO=UNMEASURED`, `C2_FLYWAY_PREDEPLOY=PASS_OPERATOR_REPORTED`, `C2-B_DEPLOY=NO-GO`, `HELVOCA_V1_COMERCIAL=NO-GO`. Este bloque no transforma la evidencia reportada del operador en una lectura directa de su informe privado.

### Checkpoint C2-B verificado el 2026-10-15

- **CI del HEAD anterior a esta actualización documental:** `b6e1c381e7d7bc8cfde645946b85223ba1690e4c`: [Full CI #38701084323](https://github.com/Nicricht/helvoca/actions/runs/38701084323) `completed/success`, **620 Playwright passed**; **11 workflows especializados SUCCESS**. El código no cambió en esta actualización; el **nuevo HEAD documental necesitará CI exacto**. No confundir cobertura diferencial con global.
- Railway API + PG18 online; cron backup opcional sin despliegue y sin staging/DR aislado. La última captura del titular de Postgres > Backups mostró snapshot nativo 126 MB ~2h antes: frescura visual **al tomar la captura**, no restaurabilidad/RTO. RPO<=24h y RTO<=4h solo objetivos aprobados.
- El control `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED` aparece por **nombre** en Railway, valor no verificado. `ControlledPilotExternalEffectGuard` permite `NOT_PILOT_MANAGED` si no existe `pilot_launch_control`: no es un kill switch global universal. Exigir piloto `PAUSED`, rutas externas bloqueadas, responsable/ventana y rollback **schema-aware** V100-V103 antes de solicitar permiso para `main`. [Runbook operativo](POSTGRES_PRODUCTION_RECOVERY.md).
- `C1_RTO=UNMEASURED`, `C2-B_DEPLOY=NO-GO`, `COMMERCIAL_GO=NO-GO`. No merge, restore, SQL productivo, activación de pagos ni llamadas autorizadas por este checkpoint.

## Actualización operativa 2026-10-14: desarrollo primero, C1 antes de producción

**Decisión explícita del titular:** posponer la automatización personalizada de Google Drive/rclone y priorizar cerrar software V1. La vía propuesta para recuperar C1 antes de despliegue será estudiar y, si se autoriza el precio, habilitar **backups nativos de Railway**, probar una restauración aislada y conservar el respaldo externo cifrado ya recuperado. No se ha comprado plan ni activado backup aquí. Nunca asumir que el backup nativo reemplaza automáticamente una copia independiente fuera de Railway.

Esta decisión modifica **el orden de ejecución, no los criterios de GO ni el checklist C0–C7**:

- **AHORA, sin cambios productivos y sin necesidad de nuevos secretos:** C2-A, preparar la convergencia de ramas y CI de integración en una rama no desplegada; C3-A, preparar/verificar estáticamente contratos de Twilio y dos administradores; C4-A, revisar límites y pruebas deterministas Gemini/Twilio sin llamada; C5-A, revisar/pruebas de Mercado Pago TEST y onboarding offline con mocks y sin cobros; C6-A, elaborar privacidad, soporte, costes y checklist de aceptación. Reutilizar evidencia ya aprobada; corregir únicamente brechas reproducibles.
- **ANTES DE CUALQUIER MERGE A `main`, migración Flyway V100–V103 o despliegue que toque datos:** retomar C1 con backup fresco verificable, restore aislado desde backup, copia/estrategia independiente, RPO/RTO aceptados, plan de rollback y autorización de ventana. Como Railway auto-despliega `main`, no realizar merges solo para terminar código.
- **DESPUÉS Y CON AUTORIZACIONES REALES:** C2-B desplegar con seguridad; C3-B reconciliación Twilio auténtica de dos operadores; C4-B llamada real consentida con presupuesto; C5-B proveedor TEST auténtico y pago LIVE acotado; C6-B aprobaciones operativas; C7 recorrido completo.
- **CONGELADO HASTA C1:** trabajo nuevo sobre `rclone` / OAuth Google Drive, archivos de automatización de backup, token Drive, creación de Cron, rutas productivas. Mantener `helvoca-backup-cron` sin source, schedule o despliegue. No borrar respaldo manual ni contraseñas age.
- **No inferir** que el tiempo comercial está resuelto solo por pagar Railway. El primer negocio pagador se mantiene como evidencia separada.

**Siguiente frente de ejecución:** C2-A, convergencia sin merge y pruebas CI del ensamble ya preparado en PR #826; en paralelo se puede avanzar C5-A/C6-A sin desplegar. Al cerrar cada brecha, documentar commit/CI en Issue #823 y avanzar al siguiente frente sin repetir la tarea.

## 1. Estado base: reutilizar logros, NO repetir

| Elemento | Última evidencia conocida a fecha de creación | Interpretación correcta |
| --- | --- | --- |
| Software V1 en `main` | Backend Java/Spring Boot, React, PostgreSQL, tenant isolation, agenda, billing y launch cage documentados en README/AGENTS | Software funcional ya existente; no reconstruir sin defecto reproducible |
| 10 fases de voz | PR #810–#820 apilados, #820 SHA `b11b73571fde1e248fe3111e60a3ad7602affd0a` | Completadas offline, **no integradas ni desplegadas** |
| Corrección Stage D | [PR #822](https://github.com/Nicricht/helvoca/pull/822), SHA `61ced933d04361b2d7e0f7ab9e886216cd7e1c51`, CI push #38391306689 **9/9** y PR #38391339854 **9/9** | Auditor ya sabe distinguir Flyway V99 sin columnas de SID realmente incoherente. No repetir esa corrección |
| Railway producción | Último `main` comprobado: `ca58693e167752bd6eab8ff4e9be770391fe4293`; API y PostgreSQL 18 online, sin staged work | No asumir que código de PR ya está en Railway |
| Flyway producción | Log histórico del arranque 11-10-2026: V99 | V100–V103 **no confirmadas en producción**; se requiere consulta fresca solo lectura antes de migrar |
| Backup | [Restauración PG18 local](https://github.com/Nicricht/helvoca/blob/b11b73571fde1e248fe3111e60a3ad7602affd0a/docs/telephony/evidence/2026-10-13-stage-a-local-pg18-restore-attestation.md) reportada por operador, hash SHA-256 registrado; `docs/POSTGRES_PRODUCTION_RECOVERY.md` describe backup diario Railway a 01-10-2026 | No repetir simulación por repetir; falta prueba actual de copia **externa independiente cifrada** y restauración desde ella |
| Twilio existente | La auditoría previa GET autenticado + PostgreSQL READ ONLY no informó discrepancias de PN SID, teléfono o tenant; tres bloques eran columnas de provenance ausentes en V99 | **No comprar ni reparar el número otra vez**; tras migrar, reconciliar campos con dos administradores reales |
| Modelo Gemini | Candidato configura por defecto `gemini-3.8-live`, modelo en allowlist. Ya existe evidencia de llamada humana controlada de 27-09-2026 en `docs/COMMERCIAL_EXTERNAL_GATES_V1.md` | La llamada anterior no prueba que **el proyecto/tenant desplegado ahora** tenga derecho, coste ni conexión real exitosa |
| Suscripciones SaaS | Código y contratos de Mercado Pago, precios, entitlements y pruebas sintéticas existentes | Round-trip **auténtico TEST** y cobro LIVE controlado aún no certificados. No confundir con merchant payments |
| Segundo administrador | El titular informó tener una persona de confianza disponible | Aún no demuestra cuenta, privilegio, autenticación ni aprobación; solicitar solo lo estrictamente necesario |

**Regla de evidencia:** cambiar estado solo con artefacto fechado que pruebe el criterio exacto: commit, SHA y CI; GET proveedor autenticado sin secretos; lectura SQL READ ONLY; restauración privada; comprobante de pago real; consentimiento/veredicto humano. Una etiqueta `true` o un JSON creado por el agente no equivale a una prueba externa.

## 2. Los ocho frentes cerrados C0–C7

### C0 · Congelar alcance y fuente única [PRIORIDAD 0]

**Objetivo:** no volver a trabajar en círculos. El Issue #823 contiene la lista única. Un cambio o bug futuro se registra con ID Cn, reproducción, estado inicial, prueba de aceptación, responsable, enlace PR y decisión, no con otra «parte 1/10».

**Hacer:** vincular este documento desde README y AGENTS, mantener un único checkpoint en #823, enumerar brechas contra la V1 anterior, etiquetar pruebas históricas que siguen siendo válidas y evitar duplicar #821 (solo subexpediente operativo). Planear dependencias antes de gastar o desplegar.

**DONE C0:** documento + Issue accesibles, alcance/exclusiones explícitos, registro de evidencias inicial y regla anti-repetición activa.

**Responsable:** asistente. **No requiere** nuevos secretos, capturas o autorizaciones productivas.

### C1 · Respaldo real recuperable, redundancia y RPO/RTO [P0 ANTES DE MIGRAR, NO BLOQUEA TRABAJO OFFLINE]

**Ya tenemos:** dump/restauración PG18 local reportada y hash; documentación de rutina Railway. No repasar el simulacro anterior salvo que cambie el archivo o la compatibilidad.

**Falta estrictamente:** desde canal privado, comprobar frescura de backup y procedencia, crear/verificar **segunda copia con cifrado autenticado** almacenada fuera de la máquina/volumen de origen, **restaurar desde esa copia** a PostgreSQL 18 aislado, comprobar datos/PK/FK/RLS relevantes sin filtrar PII, medir duración (RTO) y ventana de pérdida (RPO), definir objetivos aceptados y persona responsable de recuperación. Comprobar estado de backup Railway/PITR actual, sin inferirlo de documentación histórica.

**DONE C1:** al menos una copia actual restaurable + segunda copia independiente cifrada también restaurada, SHA/fecha/ubicación privada atestiguados, RPO/RTO medidos y aprobados, destino de recuperación identificado; ni dump ni claves en GitHub/chat/CI público.

**Responsables:** asistente prepara y revisa; titular ejecuta/autoriza en su máquina o conecta almacenamiento privado autorizado. **STOP:** sin backup recuperable no hay C2 productivo. **NO bloquea** C2-A ni otros trabajos en ramas con CI y sin efectos en producción.

### C2 · Convergencia GitHub, Flyway V100–V103 y despliegue acotado [P0]

**Ya tenemos:** funcionalidades y migraciones implementadas en PR apilados; CI offline grande aprobado. #822 diagnostica correctamente V99. No reimplementar migraciones ni duplicar PR.

**Hacer:** comprobar HEAD actual de `main`/PRs; resolver historia apilada y compatibilidad de `main` con candidato sin perder commits; asegurar que V100–V103 tienen checksums e invariantes; ejecutar CI apropiado **en el SHA final**, incluyendo tests de Flyway PG18, RLS y multiempresa. Verificar versión actual Flyway por SQL autenticado READ ONLY. Con C1 aprobado, autorización de ventana, on-call, freeze, plan de rollback y control de coste, integrar los cambios necesarios según protección de rama y aplicar migraciones por el mecanismo normal; después comprobar Flyway actual, réplica, métricas, endpoints no destructivos, logs sin secretos, compatibilidad y comportamiento de otros tenants.

**DONE C2:** la rama/composición aprobada forma parte de `main` y Railway ejecuta exactamente ese commit, Flyway esperado y checksums verificables, sin errores ni regresión cross-tenant, rollback documentado/probado para el binario y operaciones de recuperación compatibles con migraciones forward-only.

**Responsables:** asistente en GitHub/CI; operador autoriza release productivo y asume ventana/recuperación. **STOP:** no fusionar una cadena que despliega migraciones por accidente ni forzar `repair`/`drop`.

### C3 · Número existente Twilio conciliado y sin falsos bloqueos [P0]

**Ya tenemos:** número piloto y tenant identificados, PN SID corregido anteriormente; auditor Stage D sin desajuste de PN SID; flujo de dos aprobadores / peticiones/claim/RLS implementado y probado en PostgreSQL temporal.

**Hacer:** con C2 aplicado, cotejar mediante GET **autenticado** y SELECT read-only la propiedad actual: E.164, PN SID, cuenta/subcuenta, tenant, capacidad voice y URLs/métodos de webhooks, con vencimiento temporal de evidencia. Confirmar dos administradores de plataforma **diferentes**, con cuentas propias y roles reales. Uno solicita y otro aprueba/ejecuta la conciliación segura diseñada; comprobar `twilio_account_sid`, `twilio_binding_source=VERIFIED_EXISTING`, `voice_verified_at`, auditoría, estado de activación y aislamiento, sin escribir SQL manual ni permitir llamadas incidentales.

**DONE C3:** evidencia real coincidente de carrier + DB + doble aprobación + provenance persistida y confirmada; número existente preservado, no duplicado ni comprado. La vinculación NO equivale a permiso de llamar.

**Responsables:** asistente software/protocolo; operador y segundo admin autorizan auténticamente, según permisos disponibles.

### C4 · Gemini, costes, privacidad y llamada real limitada [P0]

**Ya tenemos:** motor Gemini + voz, tests offline y anterior llamada humana documentada. Existen controles de circuit breaker y piloto, pero la bandera global NO protege por sí sola a tenants `NOT_PILOT_MANAGED`; la ruta de números `VERIFIED_EXISTING` exige límites y evidencia propia.

**Hacer sin repetir pruebas gratuitas:** verificar metadatos no secretos de **modelo efectivamente cargado** en Railway, Google project/entitlement/cuota, sesión y coste aplicable en la cuenta. Analizar la admisión real de llamadas: tenant+teléfono+cuenta fijados, `pilot_launch_control` PAUSED, scopes, concurrencia, duración máxima, presupuesto, protecciones por consumo, métricas y alarmas, apagado probado y tolerancia a cobros asíncronos. Implementar SOLO una brecha demostrada; tarifas estimadas no son un hard cap contractual del proveedor. Cumplir consentimiento/aviso de IA y bases para transcripción/grabación y retención **antes** de cualquier llamada.

**Prueba posterior y limitada:** solo con C1–C3 cumplidos y aprobación exacta de persona/número/horario/presupuesto/duración, hacer una llamada controlada desde un participante que consienta; certificar Twilio webhook firmada, Media Stream, Gemini setup real, dos vías de audio en español chileno, barge-in, reserva/consulta sin invención o duplicación, persistencia aislada, despedida y corte limpios, consumo y alertas. No hacer llamadas por verificar CI.

**DONE C4:** autorización rastreable + ejecución real con éxito/errores registrados de forma privada, límites de gasto razonables y STOP demostrable; no venta telefónica antes de esto.

**Responsables:** asistente código/CI/revisión; operador proyecto Google/Twilio, presupuesto, consentimiento y verificación humana de audio.

### C5 · Suscripciones SaaS y autoservicio de alta [P0 PARA SAAS V1]

**Ya tenemos:** planes Emprende/Negocio/Pro, precios en `/api/v1/public/pricing`, estado de cobro SaaS, gateway Mercado Pago, webhooks y pruebas sintéticas; Enterprise cotización y no checkout público. No duplicar el motor de billing.

**Hacer:** ejecutar exclusivamente en TEST el round-trip proveedor para suscripción: creación, comprador de prueba, webhook firmado de preapproval + pago realmente aprobado, idempotencia de duplicado, renovación rechazada → PAST_DUE/gracia → reactivación con pago. Validar sandbox sin pasar secretos al chat. Evaluar config LIVE con aprobación del titular, un pago real acotado y verificación de plan/entitlements/recibo/estado/suspensión/reembolso o ruta de soporte. Comprobar recorrido de usuario: crea cuenta del negocio, elige un plan con precios correctos, paga, accede al plan, agrega datos/hours/FAQ/servicios/agenda, solicita vinculación segura de su teléfono, ve readiness y panel. El negocio no debe necesitar PowerShell ni soporte técnico para tareas usuales; la **aprobación especial** de número existente puede seguir siendo asistida por plataforma.

**DONE C5:** pago autorizado y registrado por canal real al menos una vez, suscripción/entitlements correctos y estados de fallo/duplicado seguros, alta/configuración completada en navegador por un usuario de negocio sin intervención de desarrollador en flujo habitual. Un cobro manual permite PILOTO_ASISTIDO, pero **NO cierra el SaaS V1 de autosuscripción**.

**Responsables:** asistente integraciones y pruebas; titular posee/autentica cuenta comercial Mercado Pago, financia y autoriza pago controlado; tester humano cuando aplique.

### C6 · Privacidad, comercialización y operaciones de cliente [P0]

**Ya tenemos:** `docs/DATA_RETENTION_POLICY.md`, `docs/FIRST_CUSTOMER_OPERATION.md`, `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md`, launch cage, políticas iniciales y catálogo de planes.

**Hacer:** revisar condiciones comerciales, privacidad y obligaciones chilenas aplicables, aviso de IA/registro/consentimiento y relación responsable/encargado con negocios, retención y solicitudes de datos; aprobación de responsable/legal antes de uso real, especialmente cambios normativos próximos. Verificar servicios de soporte e incidentes, contactos y responsabilidades reales, derecho a cancelar, facturación, precios CLP, excedentes/margen neto usando costes vigentes de proveedor, impuestos/comisiones aplicables y sostenibilidad; observar costo y anomalías con umbrales. Dejar demo, onboarding y mensaje comercial alineados con alcance probado, nunca prometer WhatsApp LIVE, merchant LIVE, disponibilidad perfecta ni margen garantizado.

**DONE C6:** documentos y avisos accesibles/aprobados, procedimiento de soporte y rollback probado, contactos y escalación asignados, acuerdos y riesgos aceptados, coste unitario y plan validados, 0 P0 de privacidad/seguridad para el alcance vendido. La revisión jurídica final pertenece al titular/profesional competente.

**Responsables:** asistente redacción/auditoría/revisión del flujo; titular acepta políticas, precios, facturación y servicio; asesor legal si corresponde. Los subrequisitos de consentimiento aplican ANTES de C4.

### C7 · Aceptación Golden Journey, primer negocio y cierre [ÚLTIMO]

**Dependencias:** C1–C6 completos, external gates probados específicamente para el tenant, pago y consentimiento autorizados; CI exacto y producción sin incidentes.

**Hacer:** con negocio piloto identificado (no inventarlo), probar como cliente real en dispositivo/navegador y canal telefónico real: alta→selección de plan→checkout LIVE aprobado→entitlements→configuración autónoma→vinculación segura→llamada entrante→FAQ/consulta disponibilidad→reserva confirmada por backend→panel/uso→derivación si procede→pausa y soporte. Ejercitar reintento/doble webhook sin doble cargo ni doble reserva, tenant A≠B y caída/STOP. Hacer seguimiento real del uso, coste, salud, límites, soporte y cierre del piloto; registrar pago realmente recibido del primer negocio para hito de lanzamiento.

**DONE C7:** acta firmada de aceptación con enlaces a pruebas auténticas y privado sin PII, sin P0, operador y responsable del negocio designados, facturación válida y plataforma operando su alcance contratado. El titular confirma GO comercial con rollback disponible. Se cierra Issue #823 y se publica resumen final **HELVOCA_V1_COMERCIAL=TERMINADA**.

**Responsables:** asistente pruebas/código/registro; titular y segundo admin operan sus identidades; primer negocio/participantes reales aprueban experiencia, pago y consentimiento.

## 3. Camino crítico y trabajo paralelo

```text
C0 Plan único
  |
  +---> C1 Backup restaurable independiente ---> C2 PR + Flyway + Railway ---> C3 Twilio dos administradores
  |                                                                               |
  +---> C6 Privacidad y consentimiento -----------------------------------------+----> C4 Gemini + voz real controlada
  |
  +---> C5 Billing TEST y UX (paralelo sin cobrar) ---> cobro LIVE autorizado ------+
                                                                                  |
                                                      C7 Golden Journey real <---+
                                                      + primer negocio pagador
```


**Lectura vigente tras cambio de prioridades:** en el diagrama siguiente, la flecha C1→C2 se refiere **solo a C2-B (merge y producción)**, no a C2-A (integración y pruebas en GitHub). C3-A/C4-A/C5-A/C6-A se pueden ejecutar en paralelo, sin llamadas reales ni cobros. Ningún nuevo backup automatizado se exige para abrir una rama o correr CI.

C6 soporte, consentimiento y acuerdos aplicables deben estar **aprobados antes de C4 real**, aunque se trabaje en paralelo. C5 LIVE requiere su propia autorización y los controles del entorno real. **No llevar a producción una tarea por el solo hecho de estar delante en el diagrama.**

## 4. Regla anti-repetición y control de avance

1. Consultar primero **este documento → Issue #823 → HEAD/CI de los PR afectados → evidencia existente**. Si hay prueba vigente, marcar **HECHO** y enlazarla, no ejecutar de nuevo.
2. Crear solo una corrección asociada a una casilla Cn y a una condición de aceptación sin evidencia. Identificar causa raíz antes de cambiar código.
3. Clasificar riesgo según `AGENTS.md` y `docs/engineering/QA_POLICY.md`; hacer tests seleccionados, baratos primero; Full Gate una vez sobre SHA final, sin afirmar cobertura global 100%.
4. En GitHub usar rama, PR y revisión; registrar checkpoint: HEAD exacto, CI exacto, bloqueos, acciones de usuario imprescindibles. No hacer nuevas diez fases ni dejar PRs apilados indefinidamente sin plan de integración.
5. El único cierre válido requiere evidencia positiva de **hechos**. `READY` de configuración no equivale a Twilio comprado/poseído, media real, modelo disponible, backup restaurado o pago recibido.
6. Solo revalidar una evidencia previamente cerrada si el sistema/proveedor cambió, el artefacto se reemplazó, el plazo de validez expiró, hubo incidente o la nueva entrega depende de un SHA distinto.
7. **STOP** por pérdida de aislamiento, secreto/PII expuesta, backup ausente, discrepancia de carrier, gasto sin control, autorización humana faltante, privacidad no aprobada, imposibilidad de rollback o P0. No manipular estados para fabricar GO.

### Registro/checkpoint obligatorio en Issue #823

```text
FECHA/HORA UTC:
TRABAJO: Cn / descripción precisa
ESTADO: PENDIENTE | EN CURSO | BLOQUEADO | CERTIFICADO
REPO / PR / HEAD:
EVIDENCIA: run exacto o reporte privado autenticado (sin secretos)
LO QUE YA NO DEBE REPETIRSE:
BLOQUEO EXTERNO ESPECÍFICO:
QUÉ PUEDE HACER CHATGPT AHORA:
ÚNICA ACCIÓN QUE REQUIERE TITULAR/SEGUNDO ADMIN:
SIGUIENTE PASO:
PROD_DEPLOY / LIVE_CALL / COMMERCIAL_GO:
```

## 5. Qué necesita el asistente y qué no puede hacer solo

**El asistente realiza**: inspección y cambios en GitHub, pruebas, PR, integraciones y documentación, revisión no invasiva en Railway, planes de rollout/rollback, informes, preparación de herramientas seguras, validación de CI y resultados disponibles.

**El titular debe proporcionar o autorizar, cuando llegue el momento:** acceso local privado al respaldo/copia cifrada o ejecución con credenciales bajo su control; objetivos RPO/RTO; dos cuentas reales de administradores; aprobación de un release y responsable operativo; proyecto Google/Twilio y tarifas reales comprobables desde un canal conectado o pantalla privada; presupuesto exacto y consentimiento para llamada; cuenta Mercado Pago LIVE y transacción autorizada; políticas, identidad de negocio y consentimiento de clientes. **Nunca pegar secretos, dumps ni tokens en chats, issues, PRs o logs.**

**Segundo administrador:** revisión, aprobación y ejecución auténticas, desde cuenta distinta y rol suficiente. Su mera disponibilidad no certifica esa parte.

**Decisiones del primer negocio:** aceptación del alcance y datos, horarios, permisos, medios de cobro al titular, prueba real y aprobación.

## 6. Fuentes de verdad subordinadas (no repetirlas aquí)

- **[Issue #823](https://github.com/Nicricht/helvoca/issues/823)**: único tablero de tareas y checkpoints.
- [Issue #821](https://github.com/Nicricht/helvoca/issues/821): subexpediente sobre producción, Flyway, carrier, backups, costes.
- [PR #820](https://github.com/Nicricht/helvoca/pull/820) y [PR #822](https://github.com/Nicricht/helvoca/pull/822): software y certificación offline, no deployments.
- `docs/POSTGRES_PRODUCTION_RECOVERY.md`, `docs/telephony/2026-10-13-stage-a-postgres18-recovery.md`: respaldo/recuperación.
- Gates voz y GitHub, todavía en cadena PR sin fusionar: `docs/telephony/2026-10-13-voice-release-part10-final-handoff-no-go.md` ([PR #820](https://github.com/Nicricht/helvoca/pull/820)).
- `docs/COMMERCIAL_EXTERNAL_GATES_V1.md`: llamada humana previa, Mercado Pago TEST y tenant.
- `docs/SAAS_BILLING_COMMERCIAL_READINESS.md`, `docs/SAAS_BILLING_SANDBOX_PROVIDER_GATE.md`: suscripciones Helvoca versus merchant payments.
- `docs/GOLDEN_JOURNEY_RELEASE_CONTRACT.md`, `docs/CONTROLLED_REAL_BUSINESS_PILOT_V1.md`: recorrido y launch cage.
- `docs/FIRST_CUSTOMER_OPERATION.md`, `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`, `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md`: venta, preparación, soporte.
- `docs/DATA_RETENTION_POLICY.md`: privacidad/retención.
- `AGENTS.md`, `docs/engineering/QA_POLICY.md`: contratos obligatorios de ingeniería.

## 7. Acta final única

No se firma antes de cerrar ocho frentes con sus pruebas. Copiar en Issue #823 únicamente cuando sea cierto:

```text
HELVOCA_V1_COMERCIAL=TERMINADA
C0-C7=8/8_CERTIFICADOS_CON_EVIDENCIA
MAIN_SHA=[real]
RAILWAY_DEPLOYED_SHA=[real, mismo aprobado]
POSTGRES_FLYWAY=[versión real verificada]
BACKUP_OFFSITE_RESTORE=[fecha, evidencia privada]
TWILIO_TWO_HUMANS=[identidades verificadas sin revelar PII]
GEMINI_REAL_CALL=[sesión real consentida, resultado, gasto acotado]
MERCADOPAGO_SAAS_PAYMENT=[pago proveedor auténtico, estado correcto]
ONBOARDING_BROWSER=[recorrido real]
TENANT_ISOLATION=PASS
PRIVACY_AND_SUPPORT=APPROVED
P0_OPEN=0
HUMAN_COMMERCIAL_GO=[responsable, fecha]
FIRST_PAYING_BUSINESS=[verificado o pendiente, declarado separado]
```

Cualquier campo sin evidencia real sigue **PENDIENTE**. No se marca «terminado» por una conversación o por cerrar PRs. El objetivo es **cerrar definitivamente el V1 vendible**, no continuar expandiéndolo indefinidamente.
