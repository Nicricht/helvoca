# First Real Business Pilot Readiness V1

This is the operational preflight for the first real RecepVoz business pilot.

It does not activate a provider, deploy code, create a payment, send WhatsApp, place a call, or mutate production inventory.

## Stacked safety prerequisite

This branch is intentionally stacked on `pilot/controlled-real-business-v1`.

The underlying safety PR must remain the source of truth for:

- the global external-effects kill switch;
- tenant-scoped `RUNNING / PAUSED` control;
- Voice, WhatsApp and payment provider boundaries.

## Read-only preflight endpoint

`GET /api/v1/operations/pilot-preflight`

The endpoint reports one of these states:

- `NOT_ENROLLED`: the tenant does not have persisted pilot control yet.
- `NOT_READY`: pilot exists but readiness/configuration/checklist still has blockers.
- `READY_SAFE`: all launch checks pass and every external effect is still blocked.
- `RUNNING_BLOCKED`: pilot has started, but the global switch is still preventing real effects.
- `LIVE`: pilot is RUNNING and Voice, WhatsApp and Payment effects are allowed.
- `PAUSED_SAFE`: the tenant kill switch is active and all external effects are blocked.
- `COMPLETED`: pilot is complete and effects remain blocked.
- `UNSAFE`: guard decisions are inconsistent with the lifecycle and must not be used for launch.

## Exact launch sequence

1. Configure exactly one pilot tenant.
2. Complete business data, agent instructions, prices, FAQ and policies.
3. Complete technical readiness for voice, WhatsApp, catalog, commercial flow and payments.
4. Complete the activation checklist, including conversation, mutation and handoff tests when required.
5. Confirm preflight returns `READY_SAFE`.
6. Start the tenant pilot while the global external-effects switch is still OFF.
7. Confirm preflight returns `RUNNING_BLOCKED` and `safeToEnableGlobalSwitch=true`.
8. Only during an explicitly authorized launch window, enable `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=true`.
9. Confirm preflight returns `LIVE`.
10. Watch Journey Trace, Conversation Quality, jobs, inventory, payment verification and reconciliation.
11. On any anomaly, pause the tenant first. Confirm `PAUSED_SAFE`.
12. If tenant pause is insufficient or state is unclear, disable the global switch.

## Abort conditions

Do not enable the global switch if:

- preflight is not `RUNNING_BLOCKED`;
- any launch blocker exists;
- any effect is already allowed before the launch window;
- pilot status is not `RUNNING`;
- provider configuration is ambiguous;
- payment verification or reconciliation is degraded;
- Journey Trace cannot diagnose the test interaction.

The first real-business pilot must remain single-tenant until its review is complete.
