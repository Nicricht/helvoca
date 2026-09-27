# Helvoca — Commercial Launch Status

## Decision

Helvoca / RecepVoz puede venderse como **piloto asistido de alcance controlado**. El siguiente hito comercial es conseguir y operar correctamente el primer cliente, no añadir funcionalidades especulativas.

Runbook principal: `docs/FIRST_CUSTOMER_OPERATION.md`.

## Repository review — 2026-09-27

La operación comercial de esta rama fue revisada contra `main` en:

```text
67ca6bb78d1664dee139aeced17b779ebfcc6284
```

Esto identifica la base de repositorio revisada, **no certifica por sí solo que ese SHA sea el desplegado en producción**. Antes de aceptar dinero o activar un piloto real, verificar el SHA desplegado, health/readiness y el estado de cada proveedor incluido en el alcance.

## Sellable core

Disponible para pilotos asistidos según configuración:

- business information / knowledge;
- catálogo de servicios/productos;
- bookings y disponibilidad;
- orders;
- quotes;
- leads;
- requests;
- delivery/pickup;
- customer confirmation flows;
- human handoff;
- tenant policies;
- operation history;
- usage metering;
- planes, límites y concurrent-call capacity;
- public pricing;
- onboarding/configuración asistida.

## Activation-dependent

No presentar como activo hasta probarlo para el tenant:

- real voice provider / telephony;
- WhatsApp delivery;
- outbound messaging;
- external calendar/meeting provider.

## Merchant payments

**Merchant payment LIVE no está disponible comercialmente.** El código actual permite SANDBOX y rechaza explícitamente LIVE. No incluir cobro real a clientes finales en el alcance de un piloto.

Esto es distinto del proceso mediante el cual un cliente paga a RecepVoz por su plan: ese medio de cobro/facturación debe definirse y autorizarse antes de cambiar un prospecto a `CUSTOMER`.

## Commercial guardrails

No prometer:

- WhatsApp ilimitado;
- SLA enterprise no contratado;
- disponibilidad 24/7 garantizada;
- cero errores;
- integraciones externas no certificadas;
- merchant payment LIVE;
- ROI garantizado.

## Pricing

Catálogo público/backend vigente:

- Emprende: $24.990 CLP/mes, 100 min, $149/min de excedente.
- Negocio: $39.990 CLP/mes, 250 min, $129/min de excedente.
- Pro: $69.990 CLP/mes, 500 min, $109/min de excedente.
- Enterprise: desde $119.990 CLP/mes, custom pricing.

Fuente operativa: `/api/v1/public/pricing`.

## Current acquisition pipeline

`docs/FIRST_PROSPECTS_TRACKER.csv` contiene prospectos reales. No inventar contacto, interés, calificación, demo, piloto o cliente.

Pipeline:

```text
NEW -> CONTACTED -> QUALIFIED -> DEMO -> PILOT -> CUSTOMER
                               \-> CLOSED
```

Definiciones y criterios: `docs/FIRST_CUSTOMER_OPERATION.md`.

## Launch assets

- `/sales.html` — public sales landing;
- `/pricing.html` — public pricing;
- `docs/FIRST_CUSTOMER_OPERATION.md` — operación completa del primer cliente;
- `docs/FIRST_SALE_TOMORROW.md` — field sales script;
- `docs/FIRST_SALES_SPRINT.md` — acquisition sprint;
- `docs/FIRST_PROSPECTS_TRACKER.csv` — tracker real;
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` — onboarding después de aceptar piloto.

## Before accepting money

Debe estar en verde el checklist de `docs/FIRST_CUSTOMER_OPERATION.md`: alcance, plan/precio, responsables, medio de pago a RecepVoz, datos de facturación/cobro, términos/privacidad disponibles, criterios de éxito y ausencia de bloqueo P0 conocido.

Si algo crítico falta, mantener el estado en `PILOT`, no `CUSTOMER`.

## Exit criteria for assisted-pilot stage

Pasar a adquisición más autoservicio solo después de tener evidencia real de:

- plan/entitlement enforcement estable con clientes;
- proceso de cobro/facturación probado y autorizado;
- onboarding sin intervención rutinaria de ingeniería;
- activación de canales externos con runbooks repetibles;
- datos reales suficientes de uso, objeciones, soporte y fallos.
