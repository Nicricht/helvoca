# V31 Safe Operation Retry Engine

V31 ejecuta de forma real las decisiones `retryPolicy` y `maxAutoRetries` introducidas por V30 sin convertir los reintentos en duplicaciones de negocio.

## Principio

Helvoca reintenta automáticamente solo fallos transitorios. Un error de negocio no se repite a ciegas: se devuelve una acción de fallback para que el agente replantee la conversación. La intervención humana sigue reservada para casos `UNRESOLVABLE` cuando la política efectiva lo permite.

## Ejecución segura

Cada intento de una operación universal se ejecuta en una transacción independiente `REQUIRES_NEW` con aislamiento `SERIALIZABLE`. Si el resultado se clasifica como transitorio, esa transacción se marca para rollback antes del siguiente intento. De esta forma un intento que alcanzó a producir cambios parciales no se confirma antes de reintentarse.

Los defaults de V30 producen un máximo de tres intentos: el intento original más dos retries automáticos. `retryPolicy=NONE` ejecuta una sola vez.

El backoff es corto y acotado: 100, 200, 400, 800 y máximo 1000 ms. V30 limita `maxAutoRetries` a cinco, por lo que V31 nunca supera seis intentos totales.

## Clasificación

- `TRANSIENT`: timeout, error temporal de backend/provider o conflicto declarado retryable. Se revierte el intento y se reintenta dentro del límite de política.
- `RESOLVABLE_WITH_FALLBACK`: indisponibilidad de horario, cambio de total, falta de contexto, cobertura de delivery u otro error de negocio que puede resolverse conversacionalmente. No se reintenta a ciegas.
- `UNRESOLVABLE`: configuración/provider ausente u otra condición que el sistema no puede resolver automáticamente. Solo aquí puede aparecer `HUMAN_HANDOFF`, de acuerdo con `escalationPolicy`.

Los resultados fallidos incluyen `automation.failureClass`, `automation.fallbackAction`, `automation.humanEscalation` y `automation.retryCount`.

## Canales

El motor se aplica desde la misma capa de operaciones en Voice y WhatsApp a las capacidades universales mapeadas por el Policy Engine: ORDER, DELIVERY, BOOKING, QUOTE, LEAD, REQUEST y PAYMENT.

Las confirmaciones del cliente introducidas antes de V31 siguen siendo obligatorias donde corresponde. V31 no convierte una confirmación en aprobación humana ni elimina pisos de seguridad de ORDER, DELIVERY, BOOKING o PAYMENT.

## Pagos

V31 no interpreta un checkout como pago exitoso. PAYMENT conserva su idempotency key estable y el provider sigue siendo autoridad sobre el estado. Solo la verificación del provider puede producir `SUCCEEDED`.

## Auditoría

La migración V31 crea `business_operation_retry_attempt`, un historial append-only con tenant, tipo de operación, source reference, operation id cuando existe, tool, número de intento, máximo de intentos, outcome, clase de fallo, código de error sanitizado y delay aplicado.

No se almacenan argumentos arbitrarios, teléfonos, direcciones, confirmation tokens, checkout URLs ni secretos. UPDATE y DELETE se bloquean mediante trigger PostgreSQL.

## Garantías probadas

Los tests de integración PostgreSQL/Testcontainers verifican:

- retries transitorios con backoff;
- rollback real de side effects entre intentos;
- éxito posterior al retry;
- fallback sin retry para fallos de negocio;
- `retryPolicy=NONE`;
- historial append-only;
- migraciones Flyway con validación Hibernate;
- compatibilidad con el CI backend y E2E del proyecto.
