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
