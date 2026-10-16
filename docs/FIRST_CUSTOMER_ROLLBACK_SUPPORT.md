# First Customer Rollback & Support Runbook

## Purpose

This runbook is the operational safety net for the first controlled RecepVoz customer. It does not authorize provider activation, deployment, real calls, WhatsApp delivery, or charging by itself.

Use it together with:

- `docs/CONTROLLED_REAL_BUSINESS_PILOT_V1.md`
- `docs/FIRST_CUSTOMER_OPERATION.md`
- `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`

## Before activation

The pilot must have all of the following written down before any external effect is enabled:

- business/tenant id;
- RecepVoz pilot owner;
- customer-side owner;
- start and planned review/end time;
- approved channel(s);
- approved capabilities;
- explicit exclusions;
- success metric;
- human escalation destination;
- rollback decision owner;
- support contact path.

Do not invent missing names or contact details. A blank owner/contact is a NO-GO.

## Immediate stop controls

**HIGH-RISK scope warning (verified in code, 2026-10-15): these are pilot-scoped controls, NOT universal emergency stops.** `ControlledPilotExternalEffectGuard.evaluate(...)` permits a tenant with no `pilot_launch_control` row and returns `allowed=true / pilotManaged=false / NOT_PILOT_MANAGED` even when `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false`. This behavior is explicitly asserted in `ControlledPilotExternalEffectGuardTest.nonPilotTenantPreservesExistingBehaviorEvenWhenGlobalPilotSwitchIsOff`. Never infer that switching off this flag blocks all tenants, all provider routes, existing sessions, queued jobs or payments already sent.

1. **Pilot-managed tenant:** verify the exact tenant has a `pilot_launch_control` record and put it in `PAUSED` using the authorized admin flow. Re-read its actual state and the guarded path's denial outcome; a missing row is **NO-GO**, not evidence of PAUSED.
2. **Pilot-managed routes, multiple tenants:** set `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false` through the approved production change procedure if the incident owner authorizes it. Recheck the effective deployed value and guarded denial for each relevant managed tenant. This flag alone is **not** a global provider shutdown.
3. **Unmanaged tenants / other routes / in-flight work:** escalate immediately to the incident owner and use the independently supported channel/provider/work-queue stop procedure for each affected route, with authorization and evidence of terminal cessation. If no safe scoped stop exists, keep `P0 / NO-GO`, contain ingress with an incident-approved platform mechanism and do not claim that effects have stopped. Do not improvise ad-hoc SQL, revoke shared credentials or stop all production blindly.

**Twilio voice WebSocket admission boundary (C2-B, 2026-10-16):** Twilio voice ingress checks the pilot before returning TwiML. A pause can occur between that response and the authenticated media WebSocket `start` event. The production-wired `TwilioCallBindingGuard.allowsMedia` now verifies the certified carrier AccountSid/phone/existing nonterminal call binding **and re-evaluates `Effect.VOICE` for the business ID taken from the verified phone**, not from untrusted `customParameters`. `DRAFT`, `READY`, `PAUSED`, `COMPLETED`, an enrolled RUNNING tenant with pilot switch OFF, or a failed pilot-control read deny creation of the *new* AI session: the WebSocket receives a policy-violation close before provider/session creation. Unmanaged tenants retain their preexisting behavior. This protects only **new media-session admission**. It does **not** terminate AI streams already active when PAUSE occurs, hang up carrier calls independently, or recall external provider effects. In-flight STOP remains an explicit operator NO-GO until certified independently.

**Twilio WhatsApp outbound boundary (C2-B, 2026-10-16):** unlike Meta WhatsApp, `TwilioWhatsAppMessagingProvider.send` previously reached `HttpClient.send` without a controlled-pilot guard. It now applies `Effect.WHATSAPP` immediately before the Twilio HTTP request. An enrolled tenant in `PAUSED`, `DRAFT`, `READY` or `COMPLETED` state, or an enrolled RUNNING tenant with the global pilot switch off, cannot emit a new Twilio WhatsApp message through this provider; an unavailable control repository fails before the HTTP call. An authorized RUNNING tenant and `NOT_PILOT_MANAGED` tenant keep their established behavior. This is not proof of cessation of previously submitted messages, other routes, live provider-side processing, or tenants without pilot enrollment. The separate `TWILIO_WHATSAPP_ENABLED` feature flag must not be toggled merely to manufacture a release green gate.

**Payment-order recovery boundary (C2-B, 2026-10-16):** `PaymentWorkflowService.refreshFromProvider` may encounter a missing external order and recover it by calling `PaymentProviderAdapter.create` while answering payment status or reconciling. This is a **new outbound payment effect**, not a read-only status check. The recovery path now re-evaluates `ControlledPilotExternalEffectGuard.Effect.PAYMENT` immediately before calling `create`. A stopped **enrolled** pilot does not recreate a provider order; its last known payment remains for later reconciliation. An authorized RUNNING pilot and an unmanaged tenant preserve existing recovery behavior. A provider/status/guard error must not become an unapproved replacement order. This only protects the guarded recovery path, **not** merchant subscription billing, an already-sent request, provider-side processing, other non-pilot tenants, or an in-flight request that has passed the check. Never infer universal STOP from this code or from a zero backlog.

**Verify absence of new external effects and monitor in-flight work before debugging or reactivating.** If the control's scope is unknown, treat STOP as **unverified** and record the gap.

## P0 incidents

Treat as P0 and stop external effects immediately when any of these occurs:

- action executed for the wrong tenant/customer;
- duplicate critical mutation that is not safely idempotent;
- unauthorized call, WhatsApp delivery, payment action, order, booking, cancellation or handoff;
- wrong price/plan/entitlement applied;
- call fails to terminate or emits audio after carrier hangup;
- credentials/secrets appear in trace, logs, UI or support artifacts;
- reconciliation reports a critical unresolved inconsistency;
- external provider behavior cannot be correlated to a known operation/call.

## P1 incidents

Pause the tenant unless the pilot owner explicitly determines the certified scope remains safe:

- repeated tool failures;
- incorrect FAQ/service answer caused by configured business data;
- severe latency that prevents completion of the contracted journey;
- handoff repeatedly unavailable;
- usage/entitlement counters materially inconsistent;
- dashboard result cannot be reconciled with persisted actions.

## Evidence to capture

Before changing or deleting anything, capture identifiers, not raw secrets:

- business id;
- call id;
- provider call id / stream id when applicable;
- operation id;
- booking/order/request id;
- job/correlation id;
- provider name and model;
- timestamp and timezone;
- error code;
- Journey Trace identifier;
- current pilot status and traffic mode.

Use Call Detail only when conversation content is required. Prefer anonymized Conversation Replay when a case must be shared.

Never paste access tokens, authorization headers, webhook secrets, payment payloads, or full customer transcripts into general support notes.

## Diagnostic order

1. Confirm tenant and pilot status.
2. Confirm traffic mode.
3. Open Journey Trace using the strongest known identifier.
4. Locate the first failing stage, not merely the final symptom.
5. Open Call Detail or domain entity only when needed.
6. Check retries, durable jobs, reconciliation and provider state.
7. Reproduce with simulator/certification harness before considering reactivation.

## Rollback rules

Rollback means returning the pilot to a known safe state, not blindly reversing database rows.

Preferred order:

1. PAUSE tenant.
2. Disable the **pilot-managed** external-effect flag via the approved operator procedure when needed; do **not** treat it as a universal kill switch. Verify the status of each affected tenant (including `NOT_PILOT_MANAGED`), route and pending job.
3. Stop pending outbound/durable work using supported domain controls.
4. Correct configuration through supported APIs/admin flows.
5. Reverse business mutations through their domain operation when reversal is supported.
6. Never repair production with ad-hoc SQL unless an explicitly reviewed incident procedure authorizes it.
7. Fix code only on a branch with tests reproducing the incident.
8. Require CI before any redeploy or reactivation.
9. Re-run the affected Golden Journey/certification pack.
10. Reactivate one tenant only after the incident owner records the reason for GO.

## Reactivation checklist

- [ ] root cause identified;
- [ ] all affected tenant/route/provider paths and previously queued/in-flight external effects have independently verified STOP or safe terminal outcomes, including `NOT_PILOT_MANAGED` where applicable;
- [ ] regression test added when the defect is reproducible in code;
- [ ] Fast Gate green;
- [ ] Full Gate green;
- [ ] relevant Golden Journey/certification green;
- [ ] reconciliation shows no unresolved critical anomaly;
- [ ] customer-facing data/config reviewed if it contributed;
- [ ] pilot owner approves reactivation;
- [ ] tenant is moved from PAUSED only after the checks above;
- [ ] global switch is enabled only for an explicitly authorized pilot window.

## C6-A: incidente de privacidad, solicitud de datos y consentimiento

Esta guía no sustituye la evaluación legal. Si existe acceso cruzado entre empresas, exposición de transcripciones o contactos, uso de datos sin fundamento verificado, o una comunicación/llamada no autorizada, tratarlo como **P0 por defecto** hasta que el responsable designado descarte riesgo. Bloquear y **comprobar** los efectos externos del tenant piloto gestionado; cuando el alcance sea incierto, usar el flag global del piloto solo para rutas gestionadas y confirmar además la contención independiente de rutas `NOT_PILOT_MANAGED`, proveedores y trabajos en vuelo. El flag no garantiza un STOP universal. Preservar solo metadatos mínimos y la evidencia privada necesaria, sin difundir contenido personal en GitHub.

Para una solicitud de acceso, rectificación o supresión, **no realizar una eliminación SQL manual** ni asumir que la política `docs/DATA_RETENTION_POLICY.md` es una purga desplegada. Canalizar al responsable de privacidad verificado, comprobar identidad y tenant por procedimiento autorizado, evaluar obligaciones de conservación/documentación y registrar decisión/fecha de cierre de forma segura. La ruta pública `/data-deletion.html` es informativa, no prueba que un ticket esté atendido.

Antes de reactivar el piloto después de un incidente de privacidad, requerir además aprobación registrada del responsable contractual/privacidad y, cuando corresponda, revisión jurídica, notificaciones exigibles y confirmación de que la causa ya no puede reproducirse. Los contactos, plazos de respuesta, escalaciones y avisos exigibles **deben acordarse y verificarse para el negocio y normativa aplicable**, no inventarse en este runbook.

## Support intake template

Record:

- Tenant:
- Reporter:
- Time observed:
- Expected behavior:
- Actual behavior:
- External effect involved:
- Call/operation/correlation id:
- Severity: P0 / P1 / P2
- Tenant paused: yes/no
- Pilot-managed switch disabled (verified effective): yes/no/not applicable
- Unmanaged tenant / independent provider STOP verified: yes/no/not applicable
- In-flight and queued effects terminal/contained: yes/no/not applicable
- Journey Trace checked: yes/no
- Root cause:
- Corrective action:
- Verification evidence:
- Reactivation decision:
- Owner:

## First-pilot operating cadence

For the first customer:

- review errors and unresolved requests daily;
- review usage/minutes and external-provider failures daily;
- review reconciliation anomalies before every expansion of scope;
- do not add a second real tenant while a P0 remains unresolved;
- after the pilot window, record continue / adjust / stop and the evidence behind the decision.
