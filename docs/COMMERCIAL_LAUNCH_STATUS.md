# Helvoca — Commercial Launch Status

## Decision

Helvoca / RecepVoz puede venderse como **piloto asistido de alcance controlado**. El siguiente hito comercial es conseguir y operar correctamente el primer cliente, no añadir funcionalidades especulativas.

Runbook principal: `docs/FIRST_CUSTOMER_OPERATION.md`.

## Repository and production review — 2026-09-29

La base funcional recertificada para esta revisión es:

```text
main@8ec4d007c29a5e938140828da8bd6f34c152bbaf
```

Evidencia observada para ese SHA antes de abrir la recertificación comercial:

- `helvoca/full-verification`: SUCCESS;
- Railway production deployment: SUCCESS para el mismo SHA;
- muestra reciente de 301 solicitudes HTTP: 154 x 200, 124 x 401, 20 x 403, 3 x 404 y **0 x 5xx**;
- 0 Pull Requests abiertas al cerrar el hardening de frontend anterior.

La certificación comercial actual se ejecuta de nuevo sobre una rama dedicada `cert/commercial-readiness-*` y debe demostrar que no está detrás del `main` vigente. Esta evidencia técnica **no activa proveedores ni autoriza por sí sola un cliente real**.

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
