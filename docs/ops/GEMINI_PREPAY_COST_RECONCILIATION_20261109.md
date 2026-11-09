# Conciliación del prepago de Gemini API: RecepVoz / Aurora Sur (9 nov 2026)

**Estado:** auditoría de solo lectura, conciliación *parcial*, no atribución de todos los cargos. **Cuenta:** `012EDB-BE9B10-047F71`, proyecto `RecepVoz-Gemini-Pilot`. No registrar claves, nombres completos de pacientes ni datos de tarjetas.

## Evidencia financiera del proveedor (corte 9 nov 2026)

| Concepto | CLP | Grado |
| --- | ---: | --- |
| Prepago 29 oct 2026, correo de Google verificado | +5.000 | Pago confirmado |
| Saldo disponible mostrado en AI Studio | -3.318 | Saldo observado (captura) |
| Consumo neto implícito si no hubo ajustes/otros abonos | 8.318 | **Inferencia**, no sumatoria de facturas |
| Gasto nov 1–9 en gráfico de la cuenta | ~3.170 | Vista de facturación, cifras redondeadas |
| Diferencia por conciliar | ~5.148 | **No atribuirla** a un modelo/llamada sin detalle diario/SKU |

Fuentes privadas: Gmail de Google *«Recibimos el pago de 012EDB-BE9B10-047F71»* 29-oct; captura AI Studio compartida por titular; vista autenticada de Billing de la misma cuenta. Comprobante de Google (solo identificador público de cuenta); no exportar datos personales de pago.

El aviso de Google indica saldo requerido > USD 0. Los costes del gráfico pueden tener retrasos: https://docs.cloud.google.com/billing/docs/how-to/export-data-bigquery-tables/standard-usage y https://docs.cloud.google.com/billing/docs/how-to/view-history.

## Datos registrados en Aurora (NO facturas)

- Railway `demo-isolated`, proyecto `helvoca-aurora-demo-20261018`. Entre 1–9 nov: **10** registros `TWILIO_CERTIFICATION_COMMAND CREATED`; **8** `Twilio media stream started`; **7** `Gemini Live setup complete`, usando `gemini-3.8-live`.
- Dos conectaron/terminaron en condiciones excepcionales; la llamada 9 nov 20:34Z `CA81e4b28e766c52123e1b3f48053b6840` abrió socket pero falló antes de setup, `NO_CREDITS`.
- Se observaron 7 `RECEPVOZ_CALL_CERTIFICATION FAILED` y 0 PASS en logs consultados (algunos registros antiguos/otros proveedores no necesariamente incluidos).
- La suma de duración de medios anteriormente derivada de logs fue ~29min47s. **No es metraje facturado** ni lista completa de sesiones de Google, y no vincula los importes de octubre.
- Twilio/Railway son proveedores aparte; sus facturas no se descuentan del prepago de Gemini.

## Brechas verificadas del código, rama main en `f699cb2f`

1. `GeminiLiveVoiceSession.handleInTenant` procesa audio, herramientas, transcripciones y errores, pero omite el campo documentado `BidiGenerateContentServerMessage.usageMetadata`. No quedan en el log tokens de entrada, salida, audio, texto, pensamientos ni caché por llamada.
2. `CallLifecycleService.updateEstimatedCost` usa tasas por minuto configurables; `application.yml` define tasas por defecto 0 y en Aurora no se observaron nombres de variables de tarifa Gemini. **Un estimado de 0 no demuestra factura 0.**
3. Falta un desglose autenticado Google por fecha, modelo/SKU, tipo de operación, deducciones y ajustes de octubre. Sin esto no se puede reconciliar cada CLP.

## Corrección incremental autorizada

PR DRAFT independiente para capturar **cada evento** `usageMetadata` con `callId`, `businessId`, `model`, números de tokens por modalidad y número de secuencia. **No sumar automáticamente eventos** hasta verificar si Google reporta acumulados o incrementales en esa versión; no registrar datos de audio, transcripciones, claves ni payloads brutos. Al cerrar emitir número de snapshots recibidos y aviso `PROVIDER_METADATA_NOT_BILLING_INVOICE`. Esta telemetría se activa solo si se integra y despliega el código; no convierte llamadas previas en medibles retroactivamente. Ver especificación oficial https://ai.google.dev/api/live.

La reconciliación Google histórica sigue bloqueada hasta consultar/exportar **Transactions** y **Spend** de 29 oct–9 nov por modelo/SKU. No compras, recargas automáticas, llamadas ni cambios de producción.
