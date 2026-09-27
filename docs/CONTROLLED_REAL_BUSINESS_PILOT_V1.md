# Controlled Real Business Pilot V1

This branch prepares RecepVoz for one tightly controlled real-business pilot.

## Safety model

No provider is activated by this branch and no real call, WhatsApp message, payment, deployment, or production inventory mutation is performed while building or certifying it.

A tenant that is not enrolled in the pilot keeps its existing behavior.

Once a tenant has a `pilot_launch_control` record, real external effects are fail-closed and require both:

1. `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=true`;
2. the tenant pilot status to be `RUNNING`.

The environment flag is the global safety switch. The existing tenant-scoped `PAUSED` state is the per-business kill switch.

## External-effect boundaries

The controlled-pilot guard is applied immediately before:

- routing pilot calls to an AI voice provider;
- sending a real WhatsApp message through Meta;
- creating a real external payment intention.

Pausing a pilot must stop these effects without weakening the existing journey, idempotency, reconciliation, retry, or certification gates.

## Launch sequence

1. Keep the global switch disabled.
2. Configure exactly one pilot tenant.
3. Complete technical readiness and activation checklist.
4. Exercise the complete journey with provider delivery still blocked.
5. Start the tenant pilot.
6. Enable the global switch only during an explicitly authorized controlled pilot window.
7. If an anomaly appears, pause the tenant immediately or disable the global switch.
8. Observe Journey Trace, Conversation Quality, jobs, payments, inventory, and V6 reconciliation.
9. Do not expand to another tenant until the first pilot has been reviewed.

## Certification

The branch must keep the existing Pilot End-to-End Certification V1, Full Gate, PostgreSQL/Flyway/Testcontainers, JaCoCo, and Playwright green. No coverage exception and no `continue-on-error` is allowed.


## GO / NO-GO preflight

The tenant-scoped endpoint `GET /api/v1/operations/pilot-preflight` composes the existing readiness, activation, lifecycle, inventory, metrics, and V6 reconciliation surfaces into one launch decision.

A `GO` requires:
- all technical readiness checks to pass;
- all required activation checklist steps to be complete;
- pilot responsibility, objective, contact, and planned end to be configured;
- the tenant to be enrolled behind the external-effect guard;
- the global switch to remain safe for the current pilot lifecycle;
- tracked sellable inventory to be available;
- no open out-of-stock blocker;
- no V6 reconciliation anomaly.

The response also reports the current traffic mode:
- `BLOCKED_GLOBAL`
- `BLOCKED_TENANT`
- `LIVE_ALLOWED`
- `NOT_ENROLLED`

The dashboard renders this as the **Launch cage** card alongside orders, payment state, inventory availability, stock alerts, and reconciliation anomalies.

A `GO` is readiness evidence only. It never enables providers, changes the global switch, starts a pilot, or deploys code.

## First-customer operations and rollback

Before enrolling a real business, also complete:

- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`
- `docs/FIRST_CUSTOMER_OPERATION.md`
- `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md`
- `docs/COMMERCIAL_EXTERNAL_GATES_V1.md`

The launch cage is a technical safety boundary. It does not replace customer-specific approval, human voice certification, provider sandbox proof, repository protection, or an assigned incident owner.
