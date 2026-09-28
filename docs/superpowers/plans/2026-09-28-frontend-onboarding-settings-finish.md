# RecepVoz Onboarding / Settings Finish — Implementation Plan

**Spec:** `docs/superpowers/specs/2026-09-28-frontend-onboarding-settings-finish-design.md`

**Branch:** `feat/frontend-onboarding-settings-finish`  
**Base:** `feat/frontend-foundation-dark`

## Task 1 — RED: guided onboarding progression

Files:
- modify `e2e/first-user-ux-v2.spec.js`

Add regressions that prove:
- exactly one incomplete step is marked `.next`;
- the primary onboarding CTA names the real next step;
- CTA points to `/settings.html?section=<business|services|hours|receptionist>`;
- progress text and `aria-valuenow` match persisted backend status;
- at 390 px the onboarding card has no body-level horizontal overflow.

Observe RED in GitHub Actions before product implementation.

## Task 2 — GREEN: onboarding next action

Files:
- modify `src/main/resources/static/index.html`
- modify `src/main/resources/static/first-user-ux-v2.js`
- modify `src/main/resources/static/first-user-ux-v2.css` only for composition if required.

Implement:
- stable `#firstUserNextAction`;
- next-step label mapping;
- section query mapping;
- current-step semantics using text/classes, not color alone;
- keep the existing four-step model and existing readiness logic.

Verify targeted first-user E2E.

## Task 3 — RED: human settings information architecture

Files:
- modify `e2e/settings.spec.js`

Add regressions that prove:
- settings navigation exposes the human categories required by the spec;
- core setup categories are Negocio, Servicios, Horarios, Recepcionista;
- Respuestas/Canales remain available as advanced content rather than competing top-level setup steps;
- query parameter `?section=services|hours|receptionist|business` opens the correct panel;
- navigation uses semantic selected/current state;
- existing values are still visible in their corresponding panels.

Observe RED before implementation.

## Task 4 — GREEN: settings structure and deep-linking

Files:
- modify `src/main/resources/static/ux-simplification.js`
- modify `src/main/resources/static/settings-page.js` only if necessary
- modify `src/main/resources/static/settings.html` only where stable semantic markup improves the flow.

Implement:
- primary setup nav: Negocio, Servicios, Horarios, Recepcionista;
- secondary “Más configuración” disclosure that preserves Respuestas and Canales;
- deep-link resolver for `section`;
- `aria-selected`, `aria-controls`, role/tab semantics;
- update URL with `history.replaceState` when switching settings sections;
- no new palette or generic controls; reuse foundation.

Do not remove knowledge, phone/channel, team, billing or other existing capabilities.

## Task 5 — RED/GREEN: validation and save feedback

Files:
- modify `e2e/settings.spec.js`
- modify `src/main/resources/static/app.js`
- modify `src/main/resources/static/ux-simplification.js` if section-aware feedback needs presentation support.

Cover:
- missing service -> Services section is selected and human error is visible;
- missing hours -> Horarios section is selected;
- missing greeting -> Recepcionista section is selected;
- successful save shows scoped success feedback and preserves loaded values.

Keep existing persistence endpoints and coordinated save behavior.

## Task 6 — responsive/accessibility regression

Files:
- modify `e2e/settings.spec.js`
- modify feature composition CSS/JS only as needed.

Verify at 390/768/1440:
- no body overflow;
- category nav is usable;
- touch targets/focus visible;
- one primary save/continue action remains reachable.

## Task 7 — exact-HEAD certification

Before completion:
1. Compare current `feat/frontend-foundation-dark` HEAD to this branch’s base.
2. Synchronize any newer foundation changes into this branch without dropping feature work.
3. Invalidate earlier evidence after synchronization.
4. Run via CI companion PR against `main` because repository PR workflow only targets `main`.
5. Require Fast Gate + Golden Journey + full `test` job green on exact final HEAD.
6. Keep authoritative PR Draft, no merge, no deploy, no real external effects.
7. Update Resume checkpoint with exact HEAD, CI run IDs, completed blocks and next step.
