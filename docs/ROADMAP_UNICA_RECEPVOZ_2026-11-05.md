# Ruta única de ejecución · Helvoca / RecepVoz

**Corte de estado:** 2026-11-05 (Chile)  
**Tablero operativo único:** [Issue #823](https://github.com/Nicricht/helvoca/issues/823)  
**Incidencias y aceptación de Aurora:** [Issue #848](https://github.com/Nicricht/helvoca/issues/848)  
**Producción/base auditada:** `main@197b5407f8e6172a0895a5825f2fb4cc09624e92`

> Este documento sustituye como guía de ejecución a los checkpoints antiguos fechados en octubre. El historial se conserva; para decidir qué hacer se usa esta ruta y la evidencia más reciente de cada PR/Issue. No se vuelve a ejecutar una tarea marcada como completada sin una nueva razón y evidencia.

## 1. Objetivo y definición de terminado

Construir y poner en marcha RecepVoz como SaaS de recepción para negocios, con:

1. Atención telefónica fiable y reservas correctas.
2. WhatsApp de Aurora mediante coexistencia, conservando su WhatsApp Business App.
3. Operación segura por tenant, con costes y efectos externos controlados.
4. Un primer piloto comercial verificable y, después, facturación SaaS orgánica.

Hay dos hitos distintos:

- **PILOTO_ASISTIDO_VENDIBLE:** un negocio puede probar y usar el alcance web/agenda acordado con acompañamiento humano. No requiere fingir que WhatsApp o la facturación SaaS automática ya están listos.
- **HELVOCA_V1_COMERCIAL:** cierre completo de las puertas comerciales aplicables C0–C7, incluida aceptación de cliente, seguridad operativa y webhook de facturación orgánico.

No convertir mejoras opcionales de CRM en requisito para el primer piloto.

## 2. Reglas que no cambian

- `main` y producción no se modifican sin autorización expresa. Los PR se mantienen **DRAFT** hasta que su QA exacto esté aprobado.
- No fusionar, desplegar, activar flags, cambiar credenciales ni ejecutar tráfico real por inferencia.
- No realizar llamadas, mensajes WhatsApp, pagos, migraciones de número o acciones de Meta con clientes sin autorización específica y controles verificados.
- No usar el número Twilio terminado en **2512**. No comprar ni registrar un número temporal para Aurora.
- Aurora debe conservar el número WhatsApp Business App terminado en **0447**. Si Meta ofrece migración estándar, registro que elimine la app, borrado o pérdida de acceso, cancelar el flujo.
- Nunca solicitar ni copiar contraseñas, tokens, OTP, PIN o frases semilla por chat o GitHub. Los secretos se cargan solo por el canal seguro del proveedor.
- No repetir reservas, checkouts, llamadas, migraciones, restauraciones ni pruebas ya acreditadas. Una prueba nueva debe cubrir una brecha identificada.
- Cada avance se registra en este documento y en el Issue #823 con SHA, enlaces de CI, resultado, riesgo y siguiente acción.

## 3. Línea base: ya realizado, no repetir

| Área | Evidencia y estado confirmado al corte 2026-11-05 | Tratamiento |
| --- | --- | --- |
| Repositorio | `main@197b5407f8e6172a0895a5825f2fb4cc09624e92`. | No reemplazar esta base por SHA antiguos ni modificar main por inferencia. |
| Producción | PostgreSQL 18 deployment `2436bd68-11d7-48c1-82c2-c47d0ec22e4d` SUCCESS; API deployment `9935c1f0-b751-4d95-a64f-8497975ff584` SUCCESS; Railway muestra ambos servicios LIVE y sin cambios staged. | No repetir restore, recrear DB ni redeploy sin una causa nueva. |
| Dominios públicos | `recepvoz.cl` y `www.recepvoz.cl` VERIFIED en Railway, TLS VALID. CNAME www propagado y TXT de Railway coincidente. Logs HTTP del 5-nov: GET / con 200 en ambos dominios y recursos principales con 200. | Fase 4 completada. No modificar apex, DNS ni correo. |
| Aurora DEMO | Entorno `demo-isolated`; API/PostgreSQL existentes. Deployment `de7cb62c-0efa-4338-a850-f22d557f5236` SUCCESS, source `cd282aab65cb39aad4e3c36511742c68e75b2db3`. | No repetir bootstrap, migraciones o deploy histórico. El runtime no incluye el HEAD actual de PR #857 ni PR #855. |
| Voz | Existe una llamada real histórica de 478 segundos. El guard de calendario y la prueba de cancelación del mismo booking ya están implementados y certificados offline en la Fase 1. | No repetir la llamada histórica ni realizar otra llamada de diagnóstico. |
| WhatsApp | Coexistencia en desarrollo. Aurora sigue `whatsappEnabled=false`, `whatsappReady=false`; no se vinculó el número terminado en 0447 ni se envió un mensaje real. | No activar tráfico, no registrar el número por la vía estándar y no usar Twilio 2512. |
| Meta | Dominio verificado y clasificación Tech Provider aceptada. Verificación empresarial de RECEPVOZ SpA está **En revisión**; Access Verification continúa **Not verified**. | Esperar la revisión existente y completar Access Verification después; no crear solicitudes duplicadas. |
| Cobros | Checkout/invoice TEST aprobados y recuperación de prueba local ejecutada. No existe evidencia de webhook orgánico firmado. | No repetir checkout/invoice ni presentar pruebas manuales como evento orgánico.

## 4. Ruta de trabajo, en orden

### Fase 1 — Seguridad de calendario y operaciones de agenda de voz

**Estado:** PASS técnico; certificación de seguimiento exact-HEAD en curso · [PR #857](https://github.com/Nicricht/helvoca/pull/857) OPEN · DRAFT  
**HEAD actual:** `8a75466cb666f830c63ae1e6f9aeb68671f85377`

**Brecha corregida:** el backend contrasta el día solicitado con la fecha local del negocio antes de listar disponibilidad, comprobar disponibilidad, crear una reserva en Fase 1 o reprogramar. La Fase 2 conserva `operationId` + `confirmationToken`; cuando el cliente no menciona un día de semana, se mantiene la compatibilidad y no se inventa uno.

**Evidencia técnica:**
- Phase 8 sobre el HEAD actual: [run #42059233629](https://github.com/Nicricht/helvoca/actions/runs/42059233629) **SUCCESS · 119/119**.
- La certificación previa exact-HEAD en `bcccf175e7ce54b0d126dc06efcb96f9f418c262` aprobó 2.479 pruebas backend, cobertura diferencial Java de líneas/ramas/métodos al 100%, Browser E2E 629/629 y Phase 3 Full Gate.
- El Golden Journey comercial incluye confirmación en dos fases, reprogramación y cancelación explícita de la misma reserva; la prueba de integración PostgreSQL pasó 5/5.
- Una ejecución posterior de Phase 3 detectó un fallo aislado en el test E2E de exportación de auditoría CSV (628/629). Se corrigió el matcher de red y se añadió una aserción del endpoint `/api/v1/audit/export` en el commit actual.
- Phase 3 sobre el HEAD actual: [run #42059233646](https://github.com/Nicricht/helvoca/actions/runs/42059233646). Fast Gate, Golden Journey, Meta webhook offline contract y System Integration E2E ya están **SUCCESS**; Home Coverage, Inventory Coverage y el job backend/browser continúan en ejecución.
- PR CI exact-HEAD: [run #42059243213](https://github.com/Nicricht/helvoca/actions/runs/42059243213). Fast Gate, Golden Journey, Meta webhook public smoke, System Integration E2E, Home/Inventory Coverage y Legacy runtime v99/v103 están **SUCCESS**; el job backend/browser continúa en ejecución.
- PR #859 contiene una rama DRAFT aislada para la misma corrección E2E. No fusionar ni duplicar; cerrar como supersedida cuando #857 tenga certificación terminal.

**Criterio de salida:** esperar todos los jobs aplicables en SUCCESS sobre `8a75466`. Mantener #857 DRAFT y no repetir Phase 8 si su evidencia exact-HEAD sigue vigente.

### Fase 2 — Aceptación funcional de voz en Aurora DEMO

**Estado:** ACEPTACIÓN OFFLINE PASS · aceptación tenant/runtime pendiente.

**Evidencia:**
1. Phase 8 offline: 119/119 PASS en el HEAD de seguimiento.
2. `GoldenJourneyCommercialV1IntegrationTest`: 5/5 PASS, incluyendo propuesta, confirmación, persistencia, reprogramación, consulta y cancelación explícita de la misma reserva.
3. El backend impide confirmar una fecha/día incoherente antes de consultar o mutar la agenda.
4. La llamada histórica de 478 segundos ya acreditó audio bidireccional real, pero reveló el defecto de calendario y una cancelación no exitosa; no se repite como diagnóstico.

**Pendiente:** el runtime Aurora DEMO sigue en el deployment `cd282aab`, no en el HEAD actual de PR #857. La aceptación tenant/runtime solo se hará después de certificar el HEAD y obtener autorización separada para desplegar a `demo-isolated`. Una llamada nueva solo se solicita si queda una brecha no demostrable offline y con destino, duración, presupuesto y consentimiento expresos.

**Aceptación:** no anunciar reserva, reprogramación o cancelación como exitosa antes de recibir el resultado persistido de la herramienta.

### Fase 3 — Completar WhatsApp coexistence de Aurora

**Estado:** CÓDIGO EN QA · BLOQUEADO POR META · [PR #855](https://github.com/Nicricht/helvoca/pull/855) DRAFT, apilado sobre #853; [PR #856](https://github.com/Nicricht/helvoca/pull/856) es el paraguas de validación CI.

**Decisión invariable:** conectar el número existente de Aurora terminado en **0447**, conservando WhatsApp Business App. Sin número temporal, sin proveedor alternativo, sin migración estándar y sin Twilio 2512.

**Estado externo comprobado:**
- Meta Business Info muestra RECEPVOZ SpA con verificación empresarial **En revisión**; Meta estima aproximadamente dos días hábiles desde el envío.
- Meta for Developers Access Verification sigue en **Not verified**. Es una aprobación separada; no asumir que la verificación empresarial ya la concede.
- El dominio `recepvoz.cl` ya aparece verificado en Meta.
- La sociedad fue constituida el 2026-11-05. El estado del Inicio de Actividades ante SII no se revalidó en este checkpoint; no afirmar que está completado sin evidencia.
- En Railway Aurora DEMO, el readback de runtime indica `whatsappEnabled=false` y `whatsappReady=false`. Los nombres de las credenciales requeridas de Embedded Signup (App ID, Config ID, Business ID, System User ID, tokens, App Secret y Verify Token) no están configurados. Los valores de variables existentes están redactados por OAuth.
- No se inició Embedded Signup, no se vinculó el número …0447 y no se tocaron credenciales ni flags.

**QA de código:**
- HEAD actual de PR #855: `e7cda8b1053308e9caedc99cf3e9f7655a9c39d1`.
- CI #42055580434: Fast Gate, Golden Journey, Meta webhook public smoke, Home/Inventory Coverage, System Integration E2E y Legacy runtime v99/v103 **SUCCESS**.
- Backend JaCoCo y el gate diferencial pasaron bajo la excepción temporal aprobada de **80 %**; el job continúa en Browser E2E. No declarar CI completo hasta resultado terminal.
- La excepción temporal está documentada en `docs/CI_TEMPORARY_COVERAGE_EXCEPTION_PR855_2026-11-05.md`. No convertirla en política permanente: retirar el override al completar la etapa y volver a exigir 100 % diferencial.

**Secuencia obligatoria:**
1. Esperar resolución de la verificación empresarial existente de Meta.
2. Completar Access Verification y revisión/permisos de app.
3. Obtener una configuración Embedded Signup v4 válida y credenciales de sistema/webhook mediante el flujo oficial; cargarlas solo en `demo-isolated` por canal seguro.
4. Confirmar por Graph el WABA, Phone Number ID, `is_on_biz_app`, `platform_type`, número y tenant correctos.
5. Ejecutar el staging de coexistencia, que debe permanecer deshabilitado; nunca llamar a `/{phone_number_id}/register` para este número existente.
6. Solicitar aprobación separada para desplegar únicamente `demo-isolated`.
7. El titular completa el emparejamiento desde el teléfono. Cancelar ante cualquier flujo que ofrezca migración estándar, eliminación o pérdida de WhatsApp Business App.
8. Prueba controlada de mensaje entrante → tenant Aurora → IA → respuesta entregada; verificar eco, duplicados, historial y continuidad de Business App.

**Aceptación:** inbound y outbound reales con recibo de entrega, tenant/phone binding verificado, sin duplicados y Business App preservada. No habilitar flags ni tráfico antes de todos los gates y autorizaciones.

### Fase 4 — Dominio y presencia pública

**Estado:** <badge>DONE · 2026-11-05</badge>

- Cloudflare: CNAME `www → h8rnmpcx.up.railway.app` en DNS only; TXT `_railway-verify.www` coincide con el valor solicitado por Railway.
- Railway confirma ambos dominios `VERIFIED` y TLS `VALID`; el CNAME de www está `PROPAGATED`.
- Logs HTTP reales de Railway del 5-nov: `GET /` respondió **200** en `recepvoz.cl` y `www.recepvoz.cl`; los recursos principales CSS, JS y manifest también respondieron 200.
- Hubo 401/403 en rutas protegidas y 404 aislados, incluido `/favicon.ico`; no impiden servir la página pública ni la aplicación.
- No se modificó el apex, registros de correo, DNS ni despliegues.

**Criterio de reapertura:** solo ante nueva evidencia de fallo HTTP/TLS. No repetir configuración DNS ni la verificación ya aprobada.

### Fase 5 — Facturación SaaS y cierre comercial C0–C7

**Estado:** PARCIAL · las pruebas TEST ya hechas no se repiten.

**Única brecha técnica C5 identificada:** observar un webhook orgánico, firmado y atribuible del proveedor `subscription_authorized_payment` hasta Java y PostgreSQL. Un HTTP 200 genérico o una recuperación generada por operador no prueban entrega orgánica.

**Trabajo:**
1. Leer el evento real del proveedor y correlacionarlo con la suscripción/entitlement en PostgreSQL, sin crear otro checkout ni invoice de prueba.
2. Verificar idempotencia, firma, reconciliación y manejo de reintentos.
3. No activar pagos LIVE ni workers/flags por inferencia; requerir autorización específica.
4. Revisar las condiciones C1 (frescura y custodia de backups, RPO ≤24 h y RTO ≤4 h), C2 (release/rollback compatible con esquema), C6 (privacidad, consentimiento, soporte, precios y costos) y C7 (primer cliente, aceptación y pago) usando evidencia vigente, sin reconstruir documentos que ya existen.
5. Actualizar la fuente de prospectos y el estado del primer cliente desde datos actuales. No reutilizar cifras antiguas de prospectos ni enviar borradores comerciales sin autorización.

**Aceptación:**
- `PILOTO_ASISTIDO_VENDIBLE`: alcance y limitaciones comunicados, primer negocio piloto confirmado, operación web/agenda y soporte probados.
- `HELVOCA_V1_COMERCIAL`: C0–C7 aceptados con evidencia vigente, webhook orgánico acreditado y primer pago real autorizado y confirmado.

### Fase 6 — Mejoras posteriores al primer piloto

No bloquean el primer piloto ni se deben desarrollar antes de cerrar las fases anteriores salvo una necesidad validada del cliente:

- CRM ampliado y ficha unificada.
- Bandeja única de llamadas y WhatsApp.
- Historial compartido entre canales.
- Seguimientos automáticos, analítica y reportes avanzados.
- Automatización de onboarding y autoservicio de clientes.

## 5. Dependencias y quién debe actuar

| Dependencia | Quién puede resolverla | Cuándo |
| --- | --- | --- |
| Guard de calendario, pruebas y CI de #857 | Desarrollo técnico en GitHub | Ahora, Fase 1 |
| Lectura y decisión legal en Meta | Titular de RECEPVOZ SpA, con datos reales | En paralelo a Fase 1 |
| Access Verification, app review y Embedded Signup v4 | Titular + Meta; desarrollo prepara integración | Después de la verificación legal |
| Emparejamiento de coexistencia desde el teléfono | Titular de Aurora | Después de QA, configuración y autorización |
| Autorización de despliegue DEMO | Titular del proyecto | Solo tras exact-HEAD PASS |
| Evento orgánico de facturación | Proveedor + operador autorizado | Fase 5 |
| Aceptación del piloto y primer pago | Cliente + titular | Fase 5 |

## 6. Qué no hacer / qué no repetir

- No repetir la llamada real histórica de 478 s para volver a descubrir la discrepancia de calendario.
- No repetir checkout, invoice o recuperación TEST.
- No repetir el restore histórico de PostgreSQL ni crear otra base.
- No repetir bootstrap del administrador Nicolás.
- No generar otra invitación de administrador sin leer primero la fila real; la aceptación de un segundo administrador no se presume.
- No activar WhatsApp de Aurora, migrar el número, usar Twilio 2512, comprar otro número ni enviar mensajes de prueba antes de la autorización y aceptación indicadas.
- No declarar certificación, despliegue, coexistencia o venta completados por tener código, un PR abierto o una prueba local.

## 7. Formato obligatorio de cada checkpoint futuro

Toda actualización en #823 debe responder, en este orden:

1. **Fase actual y objetivo exacto.**
2. **Qué cambió:** archivos, PR, commit SHA y entorno.
3. **Qué evidencia se ejecutó:** enlaces de workflows, resultado exact-HEAD y métricas.
4. **Qué quedó aprobado y qué falló:** separar defectos reales de bloqueos externos.
5. **Qué no se tocó:** main, producción, DB, flags, números, proveedores, pagos y tráfico real.
6. **Siguiente acción única:** concreta, ejecutable y con responsable.
7. **Estado de salida:** `DONE`, `IN_PROGRESS`, `BLOCKED_EXTERNAL` o `NO_GO`.

**Regla de continuidad:** al retomar el trabajo, leer primero este documento, el último comentario de #823, el Issue #848 y el HEAD/CI actual de los PR activos. Continuar desde la primera fase no cerrada; no volver a empezar el proyecto.
