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

| Área | Evidencia y estado confirmado al corte | Tratamiento |
| --- | --- | --- |
| Repositorio | `main@197b5407f8e6172a0895a5825f2fb4cc09624e92`. | No reemplazar esta base por SHA antiguos. |
| Producción | PostgreSQL y API restaurados el 2026-11-04 reutilizando el volumen existente. PostgreSQL deployment `2436bd68-11d7-48c1-82c2-c47d0ec22e4d` SUCCESS; API deployment `9935c1f0-b751-4d95-a64f-8497975ff584` SUCCESS; Flyway schema 108, up to date. | No repetir restore, recrear DB ni redeploy sin una causa nueva. |
| Dominio raíz | `recepvoz.cl` adjunto a Railway, DNS propagado y certificado válido; Meta muestra el dominio verificado. | Falta readback HTTP fresco tras la recuperación; no confundir dominio verificado con verificación legal de Meta. |
| Aurora DEMO | Entorno `demo-isolated` existente; API/PostgreSQL ONLINE. Deployment actual reportado `de7cb62c-0efa-4338-a850-f22d557f5236`, source `cd282aab65cb39aad4e3c36511742c68e75b2db3`. | No repetir bootstrap, migraciones o deploy histórico. El runtime no incluye el HEAD de PR #855. |
| Voz | Ya existe una llamada real histórica de 478 segundos. La revisión detectó problemas de día/fecha y de ejecución explícita de cancelación en el flujo de certificación. | No repetir la llamada histórica; corregir primero la brecha y probarla de forma dirigida. |
| WhatsApp | Código de entrada/salida y coexistencia en desarrollo; Aurora sigue `whatsappEnabled=false`, `whatsappReady=false`. No se ha vinculado el número …0447 ni se ha enviado un mensaje real. | No activar tráfico ni usar Twilio 2512. |
| Meta | `recepvoz.cl` verificado como dominio. La clasificación Tech Provider fue aceptada por el titular. Verificación legal de negocio y Access Verification siguen pendientes. | Continuar la solicitud existente; no abrir una solicitud duplicada ni declarar aprobación. |
| Cobros | Checkout/invoice TEST aprobados y recuperación de prueba local ya ejecutada. | No repetir checkout/invoice ni presentar pruebas manuales como webhook orgánico. |

## 4. Ruta de trabajo, en orden

### Fase 1 — Corregir calendario y operaciones de agenda de voz

**Estado:** EN CURSO · [PR #857](https://github.com/Nicricht/helvoca/pull/857) DRAFT  
**HEAD actual al corte:** `8834023525e9e3c6a327e696f429bd32f725f294`

Problema reproducido: Gemini puede asociar una fecha con un día de semana incorrecto. El backend todavía no contrasta el día expresado por el cliente con la fecha local del negocio.

**Trabajo:**
1. Mantener las pruebas de regresión en `RealtimeToolServiceTest` y conseguir un RED específico, no provocado por fixtures incompletos.
2. Validar `requestedWeekday` en backend usando la zona horaria del negocio antes de listar disponibilidad, comprobar, crear o reprogramar.
3. Rechazar días inválidos con `INVALID_REQUESTED_WEEKDAY` y discrepancias con `WEEKDAY_DATE_MISMATCH`; si el cliente no dijo el día, conservar compatibilidad y no inventarlo.
4. Entregar al modelo `localDate`, `dayOfWeek` y `timezone` desde `get_business_information`; ajustar los esquemas de herramientas y las instrucciones para que Gemini envíe el día solo cuando el cliente lo haya mencionado.
5. Mantener la zona horaria local de Santiago como fuente de verdad, incluso cuando el instante ISO cruce medianoche UTC.
6. Conservar el flujo de reserva de dos fases y la regla de cancelación explícita; no marcar una cancelación como exitosa si la herramienta no la ejecutó.

**RED ya observado:** [Phase 8 run #42029853646](https://github.com/Nicricht/helvoca/actions/runs/42029853646), sobre el HEAD indicado: 96 tests, 6 fallos de regresión esperados porque el guard aún no existe y 1 error porque `localDate` todavía no se expone. La prueba de frontera UTC/local pasó. Esto confirma que la brecha está reproducida; no es una certificación aprobada.

**Aceptación para cerrar Fase 1:**
- Las regresiones pasan en GREEN.
- Casos de día correcto, día incorrecto, valor inválido, campo omitido y conversión UTC/Chile cubiertos.
- Disponibilidad, creación, reprogramación y cancelación no producen efectos si la fecha no es válida.
- Phase 8 offline, Fast Gate, Full Gate, Golden Journey y cobertura diferencial exigida pasan sobre el mismo HEAD final.
- PR #857 sigue DRAFT hasta revisar todos los checks exactos.

**Siguiente acción única:** implementar el guard mínimo en backend y completar la información local del negocio; después ejecutar los gates sobre el nuevo HEAD.

### Fase 2 — Aceptación de voz en Aurora DEMO

**Estado:** PENDIENTE DE ACEPTACIÓN DIRIGIDA · no requiere repetir la llamada histórica de 478 s.

**Trabajo:**
1. Con Fase 1 en GREEN, comprobar offline el Golden Journey: saludo → servicios → disponibilidad → propuesta → confirmación → reserva creada → consulta de reserva → reprogramación → cancelación explícita → cierre.
2. Verificar que la confirmación de reserva se ejecuta en la segunda fase y que el asistente no anuncia éxito antes de recibir `success=true` y el `bookingId`.
3. Verificar que cancelación y reprogramación usan el `bookingId` de la reserva del mismo cliente/tenant.
4. Confirmar límites y observabilidad de coste/tiempo de Twilio y Gemini antes de cualquier prueba externa. No asumir que el saldo disponible es un tope de gasto.
5. Solo si queda una brecha que no se pueda validar offline, solicitar una llamada real nueva, con destino, duración, presupuesto y consentimiento expresos. No reutilizar números ni consumir intentos por diagnóstico.

**Aceptación:** acciones correctas y persistidas para Aurora DEMO, tenant aislado, trazas legibles, coste atribuible y llamada de aceptación solo si fue autorizada. El resultado de una llamada no sustituye los gates automáticos.

### Fase 3 — Completar WhatsApp coexistence de Aurora

**Estado:** BLOQUEADO POR META + QA · [PR #855](https://github.com/Nicricht/helvoca/pull/855) DRAFT, apilado sobre [PR #853](https://github.com/Nicricht/helvoca/pull/853).

**Decisión invariable:** conectar el número existente de Aurora terminado en **0447**, conservando WhatsApp Business App. Sin número temporal, sin nuevo proveedor y sin gasto adicional no aprobado.

**Estado externo comprobado:**
- Dominio `recepvoz.cl` verificado en Meta.
- Tech Provider aceptado por el titular, pero la verificación legal de RECEPVOZ SpA sigue pendiente; Meta for Developers Access Verification también aparece como Not verified.
- La sociedad fue constituida el 2026-11-05; el Inicio de Actividades ante SII sigue pendiente. No afirmar que está realizado ni cargar documentación falsa.
- La solicitud de verificación de negocio ya existe: continuar esa solicitud, seleccionar la forma jurídica real y aportar solo documentos/datos auténticos. Repetir la verificación del dominio dentro de esa solicitud si Meta lo solicita; no crear una segunda solicitud.
- Falta una configuración Embedded Signup **v4** válida, sus identificadores y credenciales de sistema/webhook en `demo-isolated`. El número …0447 no está vinculado ni su elegibilidad de coexistencia confirmada.

**QA de código:**
- HEAD revisado de PR #855: `2f144a2b0fc41319a9984188434b6a175e5429ab`.
- Backend: 2.545 tests reportados, 0 failures/errors.
- JaCoCo diferencial no aprobado: 559/673 líneas (83,1%), 331/422 ramas (78,4%) y 151/158 métodos (95,6%). Requisito: 100% diferencial.
- Revisar también los hallazgos adversariales y de idempotencia del historial de #848 antes de declarar seguro el staging.

**Secuencia obligatoria:**
1. Completar la verificación legal existente en Meta.
2. Completar Access Verification, revisión/permisos de app y configuración Embedded Signup v4.
3. Corregir PR #855 y PR #853, resolver hallazgos de seguridad y alcanzar cobertura diferencial requerida en exact-HEAD.
4. Confirmar por Graph el WABA, Phone Number ID, `is_on_biz_app`, `platform_type`, número y tenant correctos.
5. Cargar secretos únicamente en `demo-isolated` por canal seguro; validar firma y suscripción de webhooks.
6. Solicitar aprobación separada para desplegar únicamente `demo-isolated`.
7. El titular completa el emparejamiento de coexistencia desde el teléfono. Cancelar ante cualquier flujo que ofrezca migración estándar, eliminación o pérdida de la app.
8. Prueba controlada: mensaje entrante → tenant Aurora → IA → respuesta entregada; verificar eco, duplicados e historial, y confirmar que WhatsApp Business App continúa funcionando.

**Aceptación:** inbound y outbound reales con recibo de entrega, tenant/phone binding verificado, sin duplicados, y Business App preservada. No habilitar flags ni tráfico antes de todos los gates y autorizaciones.

### Fase 4 — Cerrar dominio y presencia pública

**Estado:** PARCIALMENTE COMPLETADO.

1. Confirmar con una solicitud HTTP fresca que `https://recepvoz.cl` responde después de la recuperación de Railway; el readback del navegador anterior quedó pendiente.
2. Terminar la verificación separada de `www.recepvoz.cl`: CNAME `www → h8rnmpcx.up.railway.app`, proxy DNS only si Railway lo requiere, TXT `_railway-verify.www` con el valor exacto que entregue Railway, reintento de verificación y certificado SSL.
3. No modificar el apex `recepvoz.cl`, registros de correo ni otros servicios al corregir `www`.
4. Confirmar HTTPS y redirección/canonicalización de ambos dominios antes de usarlos como evidencia de presencia pública.

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
