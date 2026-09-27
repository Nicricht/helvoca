# Performance & Cost Engine V5

## Objetivo

V5 convierte las señales operacionales que Helvoca ya persiste en un snapshot tenant-scoped de rendimiento, costo y regresiones.

No reemplaza V39 Observability ni V41 Usage & Cost Metering. Los reutiliza:

- `call_session` sigue siendo la fuente de duración y costo estimado de voz;
- `call_action` entrega volumen y fallos de tools;
- `business_operation_retry_attempt` entrega retries del motor seguro;
- `persistent_job` entrega retries y latencia de jobs durables;
- `outbound_message` y `messaging_message` entregan interacciones persistidas con providers;
- `payment_webhook_event` entrega latencia de procesamiento de webhooks;
- `usage_meter_event` entrega costos adicionales, costos reales cuando existen y tokens cuando algún provider los persiste.

V5 no crea otra tabla de métricas y no duplica el ledger V41.

## Endpoint

```http
GET /api/v1/observability/performance-cost?from=<instant>&to=<instant>
```

Acceso:

- solo `BUSINESS_ADMIN`;
- tenant derivado del JWT;
- el cliente no puede elegir `businessId`;
- la ventana máxima por defecto es 31 días.

Las llamadas de certificación y el provider `simulator` se excluyen de los presupuestos de producción.

## Métricas del período

El snapshot devuelve el período pedido y el período anterior de igual duración.

Incluye:

- cantidad de llamadas;
- duración promedio de llamada;
- p95 de duración de llamada;
- tool calls;
- tool calls fallidas;
- retries del Safe Operation Retry Engine;
- retries observados de jobs;
- cantidad de jobs;
- interacciones persistidas con providers;
- journeys observados por canal;
- tokens AI de entrada/salida/total cuando existen en V41;
- costo estimado de telefonía;
- costo estimado de IA de voz;
- otros costos estimados del ledger;
- costo estimado total;
- costo real persistido cuando existe;
- tool calls por journey;
- provider interactions por journey;
- retries por journey;
- costo estimado por journey.

### Provider interactions

V5 usa el término `providerInteractionsObserved` deliberadamente.

Cuenta evidencia durable de:

- llamada telefónica real;
- outbound message que llegó a intento de provider;
- mensaje conversacional con provider message id o fallo de provider;
- webhook de pago recibido.

No afirma que esto sea el número exacto de requests HTTP de todos los SDKs porque el `main` actual no persiste cada request de provider de manera genérica.

## Latencia por etapa

V5 calcula promedio, p95 y máximo en milisegundos para etapas que ya tienen timestamps durables:

- `CALL_ANSWER`: inicio de llamada -> respuesta;
- `AI_SETUP`: inicio de llamada -> setup AI listo;
- `JOB_COMPLETION`: creación de job -> terminal;
- `OUTBOUND_ACCEPTANCE`: creación outbound -> envío aceptado;
- `PAYMENT_WEBHOOK`: recepción -> procesamiento.

No se inventa latencia de tool o SQL si no existe una marca temporal de inicio y fin.

## Regression engine

Cada snapshot compara automáticamente con el período inmediatamente anterior de igual duración.

Por defecto una métrica se marca `REGRESSION` si empeora más de 25% respecto de una baseline positiva.

Estados:

- `PASS`;
- `REGRESSION`;
- `NO_BASELINE`;
- `NO_DATA`.

El porcentaje es configurable:

```properties
app.performance-cost.regression-percent=25
```

También existen budgets absolutos opcionales. Un valor `0` los deja deshabilitados para no imponer umbrales arbitrarios sin baseline:

```properties
app.performance-cost.max-p95-ai-setup-ms=0
app.performance-cost.max-p95-call-answer-ms=0
app.performance-cost.max-retries-per-journey=0
app.performance-cost.max-tool-calls-per-journey=0
app.performance-cost.max-provider-interactions-per-journey=0
app.performance-cost.max-estimated-cost-usd-per-journey=0
```

## Cobertura y verdad de los datos

La respuesta incluye un bloque `coverage`.

V5 marca explícitamente cuando una señal todavía no está persistida:

- tokens AI: disponibles solo si existen eventos `AI_INPUT_TOKENS`, `AI_OUTPUT_TOKENS` o `AI_TOKENS` en V41;
- actual provider cost: disponible solo cuando `actual_cost_usd` está poblado;
- critical query latency: el `main` actual no la persiste de forma genérica, por lo tanto V5 la marca como no disponible en vez de fabricar una latencia.

Esto permite distinguir:

`0 medido`

de:

`sin telemetría suficiente`.

## Seguridad

La salida contiene agregados y timings. No expone:

- teléfono;
- nombre de cliente;
- transcript;
- contenido de mensaje;
- job payload;
- dirección;
- checkout URL;
- webhook body;
- tokens secretos;
- provider credentials.

## Certificación

V5 incluye:

- tests de budgets relativos;
- tests de budgets absolutos configurables;
- test de ausencia de baseline;
- contrato de autorización BUSINESS_ADMIN;
- PostgreSQL/Testcontainers para agregación real;
- prueba de aislamiento entre tenants;
- prueba de exclusión de llamadas de certificación/simulador.

La PR debe permanecer DRAFT y no se hace merge sin autorización explícita.
