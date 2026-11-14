# Conciliación del prepago de Gemini API: RecepVoz / Aurora Sur (9 nov 2026)

**Estado:** auditoría de solo lectura, conciliación *parcial*, no atribución de todos los cargos. **Cuenta:** `012EDB-BE9B10-047F71`, proyecto `RecepVoz-Gemini-Pilot`. No registrar claves, nombres completos de pacientes ni datos de tarjetas.

## Conciliación financiera con el libro de Google (capturas autenticadas del 9 nov 2026)

**Octubre 2026: adquisición del prepago; NO asumir consumo de llamadas en octubre**

| Movimiento 29 oct, Google Transactions | CLP |
| --- | ---: |
| Pago manual a Google recibido por Visa (comprobante validado en Gmail) | +5.000 |
| «Prepago para Google Cloud» | -4.202 |
| IVA de la compra | -798 |
| Saldo contable final mostrado para octubre | **0** |

El cierre contable en cero **no significa** que se agotaran los créditos de uso en octubre. El prepago fue adquirido en ese mes y aparece como crédito disponible en el periodo siguiente. Tampoco debe registrarse el traslado del crédito como *una segunda recarga*. El documento de octubre incluye factura n.º 5721287314 creada el 29 oct (el identificador sirve para cotejar, no inferir nuevos cargos).

**Noviembre 2026: aplicación del prepago a las transacciones**

| Movimiento Google Transactions, agrupado como «1–30 de noviembre» | CLP |
| --- | ---: |
| «Importe restante disponible para usarlo después» (crédito trasladado) | +5.000 |
| Cargo «Google Cloud» antes de IVA | -6.990 |
| IVA de noviembre (aprox. 19 % de CLP 6.990) | -1.328 |
| **Saldo final mostrado al 9 nov** | **-3.318** |

**Cierre exacto:** `+5.000 - 6.990 - 1.328 = -3.318 CLP`. Los **CLP 8.318** de cargos de noviembre, IVA incluido, explican el saldo negativo **contablemente** sin necesidad de imputar CLP 5.148 a octubre. El periodo «1–30 de noviembre» es la agrupación mensual del panel; la captura se tomó **el 9 nov**, por lo que no son transacciones del futuro.

**Diferencia todavía no atribuida a un modelo/API:**

| Vista | CLP |
| --- | ---: |
| AI Studio → «Costo de la cuenta de facturación para la API de Gemini», 1–9 nov, todos los modelos | ~3.510 |
| Transactions → cargo «Google Cloud» 1–9 nov, antes del IVA | 6.990 |
| **Diferencia pre-IVA entre vistas** | **~3.480** |

**No confundir conciliación de saldo con atribución de SKU.** El gráfico de Gemini es aproximado (etiqueta `$3.51K`) y expresa costes de la API seleccionada; la línea «Google Cloud» representa un cargo agregado. La diferencia de ~CLP 3.480 puede deberse a otras partidas, alcance de los filtros o desfases del reporte. **No hay pruebas para adjudicársela a Gemini 3.8 Live ni a un segundo servicio concreto.** El mismo panel señala que los costos pueden tardar más de 24 horas en aparecer. También debe verificarse en las facturas el tratamiento del IVA registrado en ambos periodos; no concluir doble imposición ni cobro indebido basándose solo en estos movimientos.

**Evidencias:** captura compartida por el titular del gráfico Gemini API nov 1–9 (`$3.51K`); captura de Google Transactions 1–9 nov con Google Cloud -6.990, IVA -1.328, crédito disponible +5.000 y saldo -3.318; captura Transactions 29 oct con +5.000 del pago y cargos de compra de prepago -4.202 / IVA -798; correo de Google «Recibimos el pago» validado en Gmail. No publicar capturas con datos de tarjeta, claves o datos personales completos en GitHub.

**Pendiente para atribución completa:** exportación autenticada de Google Cloud Billing por **servicio, SKU, proyecto, modelo, fecha e impuestos**, con corte y moneda homogéneos, para explicar los CLP 6.990 antes de IVA y conciliar la gráfica CLP ~3.510 con los cargos agregados. Las llamadas registradas en Aurora no equivalen por sí mismas a la totalidad del consumo de la cuenta.

## Datos registrados en Aurora (NO facturas)

- Railway `demo-isolated`, proyecto `helvoca-aurora-demo-20261018`. Entre 1–9 nov: **10** registros `TWILIO_CERTIFICATION_COMMAND CREATED`; **8** `Twilio media stream started`; **7** `Gemini Live setup complete`, usando `gemini-3.8-live`.
- Dos conectaron/terminaron en condiciones excepcionales; la llamada 9 nov 20:34Z `CA81e4b28e766c52123e1b3f48053b6840` abrió socket pero falló antes de setup, `NO_CREDITS`.
- Se observaron 7 `RECEPVOZ_CALL_CERTIFICATION FAILED` y 0 PASS en logs consultados (algunos registros antiguos/otros proveedores no necesariamente incluidos).
- La suma de duración de medios anteriormente derivada de logs fue ~29min47s. **No es metraje facturado** ni lista completa de sesiones de Google, y no vincula los importes de octubre.
- Twilio/Railway son proveedores aparte; sus facturas no se descuentan del prepago de Gemini.

## Brechas verificadas del código, rama main en `f699cb2f`

1. `GeminiLiveVoiceSession.handleInTenant` procesa audio, herramientas, transcripciones y errores, pero omite el campo documentado `BidiGenerateContentServerMessage.usageMetadata`. No quedan en el log tokens de entrada, salida, audio, texto, pensamientos ni caché por llamada.
2. `CallLifecycleService.updateEstimatedCost` usa tasas por minuto configurables; `application.yml` define tasas por defecto 0 y en Aurora no se observaron nombres de variables de tarifa Gemini. **Un estimado de 0 no demuestra factura 0.**
3. Las transacciones del proveedor ya concilian exactamente el saldo de -3.318 CLP; falta desglose Google por servicio/SKU, modelo y fecha para atribuir CLP 6.990 de Google Cloud a las operaciones de API y explicar la diferencia de ~CLP 3.480 con el gráfico de Gemini.

## Corrección incremental autorizada

PR DRAFT independiente para capturar **cada evento** `usageMetadata` con `callId`, `businessId`, `model`, números de tokens por modalidad y número de secuencia. **No sumar automáticamente eventos** hasta verificar si Google reporta acumulados o incrementales en esa versión; no registrar datos de audio, transcripciones, claves ni payloads brutos. Al cerrar emitir número de snapshots recibidos y aviso `PROVIDER_METADATA_NOT_BILLING_INVOICE`. Esta telemetría se activa solo si se integra y despliega el código; no convierte llamadas previas en medibles retroactivamente. Ver especificación oficial https://ai.google.dev/api/live.

La reconciliación del **saldo** de Google ya está completa. La **atribución del costo por modelo/SKU** sigue bloqueada hasta disponer del desglose exportado de `Spend` y Google Cloud Billing por proyecto, servicio y SKU. No compras, recargas automáticas, llamadas ni cambios de producción.

## Instrumentación propuesta en PR #871 (no desplegada)

La capa `GeminiLiveUsageMeter` captura **cada** mensaje `usageMetadata` de la API Live, sin almacenar el JSON original. Cada snapshot emite `GEMINI_LIVE_USAGE_SNAPSHOT` con identificadores internos de llamada y negocio, modelo, secuencia, tokens prompt/respuesta/pensamientos/caché/total, modalidades AUDIO y TEXT, y un indicador de presencia del campo total. Al terminar una sesión emite `GEMINI_LIVE_USAGE_SESSION` con `provider_usage_events` y `usage_reported`. Ante errores `NO_CREDITS`, la ausencia de metadata es `UNKNOWN`, **no factura 0**. La consola y los datos de Google siguen siendo la autoridad del cobro.

**Consulta de diagnóstico:** filtrar en los logs del servicio por `GEMINI_LIVE_USAGE_SNAPSHOT`, agrupar por `call_id`; observar cada `sequence` sin sumarla automáticamente. La telemetría no debe registrar secretos ni texto de pacientes. En la vista de Google se debe confirmar intervalo y SKU, impuestos, ajustes y otros modelos. No hubo compra, llamada ni despliegue para esta función.


## Guardas de gasto en llamadas (PR #871, pendientes de revisión y despliegue)

El circuito `VoiceProviderHealthRegistry` bloquea durante **24 horas** por defecto tras un `NO_CREDITS` confirmado, en vez de reintentar a los 15 minutos. Se trata de memoria de proceso: **un reinicio la borra**. No sustituye saldo real, autorización de operaciones ni el límite de inversión de Google. Otros motivos de interrupción mantienen sus duraciones anteriores.

La propiedad Spring `app.commercial.calls.max-calls-per-business-per-utc-day` (variable de entorno `HELVOCA_MAX_CALLS_PER_BUSINESS_PER_UTC_DAY`) limita las **NUEVAS** llamadas admitidas por cada negocio, usando consulta transaccional de PostgreSQL + bloqueo de capacidad existente. Cuenta todas las llamadas iniciadas (incluidas fallidas y demostraciones), **sin** contar dos veces un mismo CallSid reintentado. Al alcanzar el máximo rechaza llamadas nuevas antes de establecer Gemini, conservando las llamadas ya conectadas hasta que termine el cliente. `0` (valor por defecto) deja la admisión por día sin límite. Una cifra `8` es un ejemplo operativo para Aurora, **no un límite en pesos ni el costo máximo mensual**.

Mantener independientes los controles externos: recarga automática de Google OFF; configurar límite de inversión mensual y alertas en AI Studio/Google Cloud Billing de forma autorizada; observar que el límite experimental de Google tiene retrasos y puede excederse. **No introducir** un corte fijo de duración de conversación, pues ya se retiró a petición del titular. Monitorear repeticiones y llamadas fallidas para investigar abuso y regeneración de sesiones. Ningún control de cantidad por día puede garantizar gasto en CLP cuando la duración es libre y falta un cargo monetario certificado por API; registrarlo como riesgo residual.


## Contexto por turno y compresión opcional · 10-nov-2026 (PR #871)

La documentación oficial Live API afirma que el contexto acumulado se re-procesa y factura por turno; la compresión mediante `contextWindowCompression.slidingWindow` limita el historial antiguo que debe recontabilizarse. Fuente: https://ai.google.dev/gemini-api/docs/live-api/best-practices y https://ai.google.dev/gemini-api/docs/live-api/session-management .

La implementación opt-in incorpora las variables `GEMINI_LIVE_CONTEXT_COMPRESSION_ENABLED=false` (por defecto OFF), `GEMINI_LIVE_CONTEXT_COMPRESSION_TRIGGER_TOKENS=25000` y `GEMINI_LIVE_CONTEXT_COMPRESSION_TARGET_TOKENS=12000`, con límites técnicos de 16k–64k para trigger y 4k–(trigger-4k) para target. El contrato envía enteros serializados como string según el protocolo y conserva las instrucciones de sistema, las herramientas, las transcripciones, el modo de interrupción y la voz Sulafat; no introduce límite fijo por minutos. La compresión descarta turnos antiguos y puede perder detalles si no están en el estado persistido, por lo que NO debe habilitarse para clientes ni certificarse como ahorro hasta validar reservas, cambios, cancelaciones, escucha y resultados de Google en Aurora aislada.

Ahorro monetario por llamada: **NOT_VERIFIED**, pues los 32 snapshots previos no son una factura. La caché explícita/implícita de Live API tampoco debe suponerse disponible. No se efectuaron llamadas, pagos ni recargas para implementar esta función. Se conserva PR DRAFT y main intacto. El CI previo #42750631253 falló por ramas 78/81 (96,3%) del controlador de fecha; se añadió prueba fail-closed para las rutas omitidas sin modificar producción, y el SHA posterior exige su propia certificación Fast/Full.


## Ahorro por setup repetido: contrato compacto de herramientas (10-nov-2026, PR #871)

**Evidencia que motiva el cambio:** una llamada real de 153 s (CallSid `CAd4d2aa5c33d5d5358fdca5a66201dbbf`) con compresión activa produjo 17 snapshots y un pico de 132.223 tokens de entrada en un evento. La llamada anterior de 303 s tuvo 32 snapshots y un máximo de 51.420. Son metadatos de proveedor, NO factura ni comparación A/B equivalente. El usuario canceló Psicología pero mantuvo Dermatología hasta el cierre: la certificación exige cancelar **ambas** reservas creadas; el cleanup posterior canceló la restante. Esa divergencia de prueba NO demuestra que `cancel_booking` fallara ni justifica relajar `CallCertificationBookingEvidence` o cancelar citas no solicitadas. Tienen evidencia y detalles en Issue #823.

**Delta implementado, solo opt-in:** `GEMINI_LIVE_COMPACT_TOOL_DESCRIPTIONS_ENABLED=false` por defecto. Cuando se habilita, Gemini Live recibe descripciones concisas para herramientas de catálogo, identificación, disponibilidad y reservas, sin alterar nombres, `parameters`, `required`, tipos de campo, tokens de confirmación, `end_call`, voces, barge-in, enrutamiento, API común, persistencia ni aislamiento. Las descripciones preservan explícitamente las fases de `create_booking`, fechas `NEXT_WEEK`/`THIS_WEEK`, `requestedWeekday`, motivos desconocidos de no disponibilidad, datos literales de las herramientas y cancelación solo cuando el cliente la pide. La capa compartida `RealtimeToolDefinitions` se conserva intacta para otras integraciones. Se añadió prueba determinista de inmutabilidad de esquema y reducción real de caracteres de setup con la opción activada/desactivada.

**Límite:** acortar el setup no equivale a ahorro facturado. Todavía NO habilitado en Aurora ni verificado con proveedor. Sin llamadas, Twilio, Google API, WhatsApp, pagos, cambios de `main` o producción comercial para este commit. Conservar PR DRAFT, validar CI exact-HEAD y medir tamaño de setup y registro de tokens sin datos privados antes de considerar la opción para una prueba DEMO posterior autorizada.


## Telemetría segura del tamaño del setup Gemini Live (10-nov-2026, PR #871)

El setup del modelo incorpora instrucciones de negocio, normas de voz, un catálogo de funciones y configuración de ventana. En la llamada real de 94 segundos (CallSid redactable por referencia en Issue #823, evidencia #6774265002) el primer `prompt_tokens=10693` coincidió numéricamente con el primer snapshot de la prueba anterior, pese al flag de descripciones compactas. Un evento `usageMetadata` no identifica por sí solo qué parte corresponde al setup ni demuestra que el flag se aplicó en la sesión real. Por ello se añadió una observación **solo numérica**, `GEMINI_LIVE_SETUP_SIZE`, al construir el setup, con `compact_tools`, `compression`, `system_chars`, `declarations_chars`, `tool_count` y `message_chars`. No registra instrucciones, payloads, audio, transcripciones, nombres ni credenciales. `kind=CHARACTERS_NOT_TOKENS` evita atribuir los números a consumo facturado.

El test offline mide el mismo objeto JSON serializado en modo normal y compacto, prueba que se conserva la cantidad de herramientas y que el tamaño de instrucciones permanentes es idéntico, mientras se reduce el tamaño de declaraciones y del mensaje completo. Esta medición **no reduce** de suyo el gasto. Permitirá determinar si conviene optimizar el contexto fijo o las respuestas repetidas, sin lanzar llamadas nuevas ni repetir gastos. El ahorro monetario y el cargo individual de Twilio/Google permanecen **NO_VERIFIED** hasta lectura de proveedores y A/B controlado con consentimiento específico. Nada de esto habilita `main`, pagos, recargas o llamadas automáticas.


## Nueva etapa opt-in: voz compacta y presupuesto preventivo por negocio · 10 nov 2026 · PR #871

**No duplicar:** ya existían compresión de ventana, catálogo de herramientas compacto, telemetría de uso, trazas de tamaño de setup, límite de cantidad de llamadas por tenant/día y estimaciones de minutos. Se añaden DOS diferencias estrictamente opt-in, ambas **OFF por defecto**:

1. `GEMINI_LIVE_LEAN_SYSTEM_INSTRUCTIONS_ENABLED=false`: sustituye solo la prosa de voz, redundante (~10k chars), por `GeminiLiveLeanSystemInstructions`. Mantiene **sin editar** `RealtimeToolService.buildInstructions(context)` (reglas de negocio/tenant, privacidad, zona horaria y calendario), esquema y cantidad de herramientas, voz seleccionada, actividad automática, interrupciones, cierre y modos bake-off. El prompt breve cubre una sola bienvenida, fechas `NEXT_WEEK`/`THIS_WEEK`, horas futuras, reserva en dos fases, cancelación bajo petición, nombre confirmado y despedida interrumpible. La opción no debe activarse a clientes sin evidencia comparativa de calidad conversacional en piloto aislado; no se hizo llamada para introducirla.
2. `HELVOCA_MAX_ESTIMATED_COST_PER_BUSINESS_PER_UTC_DAY_USD=0` y `HELVOCA_ESTIMATED_NEW_CALL_RESERVATION_USD=0`: con máximo positivo, requiere una reserva monetaria positiva por llamada nueva; si la reserva falta o supera el máximo, el backend **rechaza nuevas admisiones** (fail-closed). La suma transaccional por tenant/día usa `max(estimatedTotalCostUsd, reservation)` para CADA llamada iniciada, incluidos intentos fallidos, demostraciones y sesiones todavía abiertas (sus costos desconocidos no pueden contar como cero). La consulta y la admisión reutilizan el bloqueo empresarial PostgreSQL existente; callbacks de un `CallSid` ya persistido conservan idempotencia. La comparación permite gasto estimado exactamente igual al techo y rechaza el que lo excede. No desconecta una conversación ni introduce límite de minutos. Deshabilitar el máximo (0) conserva la política original de llamadas. La reserva requiere definición de una política comercial y no se activa automáticamente.
 
**Limitación material:** NO es un límite exacto de cargo, garantía de gasto Google, tarifa oficial ni crédito prepago; la sesión puede durar indefinidamente y Gemini Live factura por tokens y no por la mera duración estimada. El costo persistido depende de tasas por minuto configuradas, que actualmente pueden ser cero; la reserva amortigua esa incertidumbre pero puede ser insuficiente. El límite Google de CLP10.000 es experimental, puede excederse; el prepago y sus alertas deben seguir gestionados por Google. Cualquier protección dura debe imponerse desde el proveedor con las capacidades disponibles y una política comercial de suspensión de NUEVAS llamadas cuando se alcance el límite, sin interrumpir llamadas en curso.

**Pruebas sin gasto:** contrato de setup opt-in y rollback, voz/tenancy/booking invariants, umbrales de admisión, reserva no positiva o sobre techo, NULL de presupuesto, idempotencia, tenant aislamiento, estados fallidos, datos persistentes PostgreSQL. Los valores de gasto no acreditan una factura. No disparar telefonía, pagos ni Gemini solo para validar el código. Mantener PR DRAFT y main/producción intactos.


## 10-nov-2026 · Regresión de transferencia efectiva al crear sesión Gemini Live

La primera versión de la optimización había superado los tests que construían `GeminiLiveVoiceSession` directamente con un objeto de opciones configurado. Ese camino **no era representativo** de las llamadas reales: `GeminiLiveVoiceProvider.sessionProperties(context)` crea una copia de las opciones por sesión y omitía las tres optimizaciones del setup. Por tanto, los flags desplegados en Railway podían quedar **inactivos dentro de las sesiones reales**, pese a aparecer activos en configuración global. No se debe atribuir un ahorro de tokens real a esas sesiones sin los logs `GEMINI_LIVE_SETUP_SIZE` por llamada.

Se incorporó regresión por el camino verdadero del proveedor `GeminiLiveVoiceProviderBakeOffTest.realProviderSessionPropagatesLeanCompactAndCompressionWithIndependentSnapshots`. **RED verificado** en SHA `5918e052e1f6b085c209eda8e3fe07626c05fbbb`, Fast Gate https://github.com/Nicricht/helvoca/actions/runs/42912492436: 684 tests, 1 failure exactamente en la aserción de que el nuevo snapshot conservara `leanSystemInstructionsEnabled=true`; los otros 683 pasaron. Corrección mínima en `GeminiLiveVoiceProvider.java`: copiar los tres flags más `contextWindowCompressionTriggerTokens` y `contextWindowCompressionTargetTokens` al construir la sesión individual, sin tocar las rutas de pacientes, herramientas, agendas, voz, VAD o permisos.

El primer Fast Gate sobre el código corregido (SHA `2bb71d1c3714928548905a8d293c419a10e7df4f`) fue **bloqueado antes de ejecutar Java** por falta de un recibo de evidencia firmado para su parent exacto. No se eliminó ni debilitó el gate. Se añadió una nueva auditoría previa al siguiente commit (PR #871 comentario #6780799598). **Pendiente:** CI final GREEN exact HEAD, verificación de despliegue aislado y metadatos reales del próximo setup; no disparar llamadas ni recargas automáticas.


## 10-nov-2026 · Corrección mínima de redondeo Twilio y propuesta preventiva de presupuesto (NO ACTIVADA)

**Nueva evidencia auténtica, sin llamadas adicionales:** la llamada existente de 165 segundos
`CAd2a116d3701b4507143a48782ec12211` se liquidó en **USD 0,22380**.
La tarifa publicada de Twilio para un móvil chileno es **USD 0,0746 por minuto**;
Twilio factura cada minuto parcial como un minuto completo. Por tanto
`ceil(165/60) * 0.0746 = 3 * 0.0746 = USD 0.22380`.
En contraste, el estimador previo usaba `165/60 * 0.0746 = USD 0.205150`,
una **subestimación demostrada de USD 0.018650** para esa llamada.
Fuentes: https://www.twilio.com/en-us/voice/pricing/cl y
https://help.twilio.com/articles/223132307-How-do-you-round-minutes-for-billing- .
Para cargos de una cuenta concreta, manda siempre la factura o Pricing API autenticada.

**Delta del PR #871:** el estimador redondea hacia arriba los minutos de
conectividad cuando `telephonyProvider=twilio`, conservando los segundos
fraccionarios para otros proveedores y los estimados IA; no cambia el tiempo
de conversación, la voz, el calendario, el registro ni las reservas.
Prueba PostgreSQL con tarifa controlada USD 0,10/min: 165 s -> USD 0,30,
61 s -> USD 0,20, 0 s -> USD 0; proveedor distinto 30 s -> USD 0,05.
El presupuesto diario por tenant y la reserva transaccional ya existían:
no duplicarlos ni presentarlos como un cap de gasto auténtico.

**Gemini:** la tarifa pública `gemini-3.8-live` Standard incluye audio
entrante USD 0,005/min y saliente USD 0,018/min *equivalentes*, más texto
entrada USD 0,75/M tokens y salida USD 4,50/M tokens, incluido pensamiento.
La sesión real reutiliza contexto, por lo que un costo nominal por minuto
no representa el cargo de esa llamada. Los CLP 762 del 10 nov son consumo
**diario del proyecto**, no consumo atribuible al CallSid.
Fuente: https://ai.google.dev/gemini-api/docs/pricing .

**Política simulada para discusión, NO parámetro aprobado ni activo:**
un techo estimado ilustrativo de USD 5 por tenant/día y reserva de USD 1
por nueva llamada permitirían a lo sumo cinco admisiones por día con
estimados que no superen la reserva; si uno supera USD 1, permite menos.
La llamada observada costó USD 0,22380 **solo en Twilio**, y una estimación
nominal de audio Google de 3 minutos sería hasta USD 0,069 **sin texto ni
reprocesamiento**. No usar USD 0,29280 como costo final ni asumir que
USD 1 cubre conversaciones indefinidas. El límite controla SOLO
admisión de llamadas nuevas, no corta llamadas ni fija duración, no
proporciona garantía de factura; es un presupuesto común configurable
que se evalúa independientemente por tenant, no precios personalizados
por negocio. Sin tarifa efectiva de IA derivada de uso/SKU, sin umbral
aceptado por el dueño y con AI Studio mostrando CLP 8.991/10.000 del
límite experimental susceptible de excedentes: **NO-GO** para activar
`HELVOCA_MAX_ESTIMATED_COST_PER_BUSINESS_PER_UTC_DAY_USD` /
`HELVOCA_ESTIMATED_NEW_CALL_RESERVATION_USD`. Mantener Gemini, llamador
y cobros desactivados. Sin activar ni alterar variables, `main`,
producción comercial, pagos o llamadas. Ver Issue #823 para evidencia
y QA exact-HEAD.


## 11-nov-2026 · Fail-closed on zero-price voice rates when an estimated daily budget is enabled

**Observed gap in PR #871 HEAD `12d1485e`:** the per-business daily USD admission ceiling verified only the ceiling and positive reservation, but an effective carrier tariff or the AI fallback/model/provider rate could still be zero. For an already completed call, the estimated cost then risks falling back to the reservation rather than reflecting actual positive usage. The existing reservation and tenant lock remain mandatory and unchanged; this update is specifically a priced-admission *safety* improvement, not a new V1 milestone.

**New behavior:** with `HELVOCA_MAX_ESTIMATED_COST_PER_BUSINESS_PER_UTC_DAY_USD > 0`, a new call is denied **before the DB cost query or persistence** if its effective telephony provider rate is not positive, or if `HELVOCA_AI_COST_PER_MINUTE_USD` is not positive, or any configured AI provider/model rate override is nonpositive. This is conservative because the model is not always known at call admission. Operators must configure a positive generic AI fallback and positive explicit overrides for all configured routes; **a positive placeholder is not proof of a calibrated billing rate**. For the budget OFF (default = 0), prior behavior is preserved.

**QA contract:** no provider contact. Unit tests cover zero telephony, zero AI fallback, zero provider override, zero model override, positive configured rates and prior budget cases. Existing real PostgreSQL 16 Testcontainers tenant/idempotence and started-minute billing tests use an explicit fixture AI fallback. Require new HEAD Fast/Full Golden Journey, PostgreSQL 16/18, differential Java coverage 100% before treating this as certified. No deployment, provider LIVE action, merging to main, payments or variable changes without separate authorization.

**Commercial status remains NO-GO:** the admission guard is a *best-effort estimated budget*, not a hard cap because Gemini resubmits context, actual rates are not yet reconciled per call and calls deliberately have no duration limit. The owner has not approved a budget or per-call reservation. Do not set arbitrary USD/CLP values or claim that this prevents all provider overage.

### Exact-HEAD red-to-green correction: Spring placeholder alias in PostgreSQL fixture

- GitHub Fast Gate run https://github.com/Nicricht/helvoca/actions/runs/42941418549 failed only the **three real PostgreSQL budget tests** with the intended fail-closed error, because the test set `app.commercial.calls.ai-cost-per-minute-usd=0.02` while `application.yml` derives provider and model-specific rates from `HELVOCA_AI_COST_PER_MINUTE_USD:0`. The named aliases remained zero in Spring's effective binding.
- Minimal change confined to the **test fixture**: define `HELVOCA_AI_COST_PER_MINUTE_USD=0.02` via `@DynamicPropertySource` and assert the effective configuration prices both the AI fallback and all model/provider overrides. Real product runtime guard is **unchanged**; no production configuration, call, payment or database data modified.
- Require Fast and Full Gate exact HEAD, PostgreSQL compatibility/security, Golden Journey, and 100% applicable differential coverage before signing off. The original red result is a realistic binder-regression check, not evidence that unpriced LIVE calls should be allowed.

