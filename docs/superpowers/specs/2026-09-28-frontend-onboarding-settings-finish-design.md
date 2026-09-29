# RecepVoz onboarding and settings finish

**Date:** 2026-09-28  
**Branch:** `feat/frontend-onboarding-settings-finish`  
**Base:** `feat/frontend-foundation-dark`  
**Risk:** MEDIUM

## Goal

Make initial business setup self-explanatory without inventing a second onboarding system. Preserve the existing four-step model and existing backend capabilities, but reorganize presentation so a new user always knows what is complete, what is next, what is optional, and what will happen when they save.

The returning-user settings experience must remain powerful without presenting technical/provider controls as the primary product surface.

## Existing contracts to preserve

The frontend already consumes and must continue to consume the existing business contracts for:

- onboarding status;
- business identity and business profile;
- services;
- business hours;
- knowledge;
- AI-agent configuration;
- phone/channel state;
- team/invitations, commercial status and other settings capabilities already present.

No new backend onboarding model or duplicate persistence API is introduced.

## Product model

The visible first-use progression remains:

1. **Negocio** — identity and essential public business information.
2. **Servicios** — what the business offers and what can be reserved.
3. **Horarios** — when customers can be attended/reserved.
4. **Recepcionista** — greeting, voice/behavior essentials and safe channel readiness.

The step state is derived from real onboarding status. Exactly one incomplete step is visually identified as next.

## Onboarding experience

The first-use surface must:

- show `Paso N de 4` / equivalent real progress;
- visually distinguish complete, current and future steps;
- expose one primary CTA whose label names the next action;
- hide operational clutter until setup is sufficiently complete;
- preserve progress across reloads because progress comes from persisted backend state;
- finish with a clear successful result and safe simulator action, never an automatic real call.

The flow may navigate into settings, but must take the user directly to the relevant section rather than dumping them at an undifferentiated form.

## Settings information architecture

Use human categories that map onto existing capability groups:

- **Negocio**
- **Servicios**
- **Horarios**
- **Recepcionista**
- **Equipo**
- **Integraciones**
- **Facturación**

Feature-specific technical controls may stay within these categories or behind explicit advanced disclosure. Existing capabilities are re-grouped, not removed.

For the mission scope, the first four categories receive the strongest UX treatment. Team/integrations/billing must remain reachable and functional but are not deeply redesigned beyond hierarchy/coherence.

## Save and validation behavior

Keep existing persistence endpoints. UI behavior should make scope clear:

- errors appear in or next to the section that owns the invalid state;
- first invalid control receives or can receive focus;
- success feedback identifies what was saved;
- failed partial requests must not erase valid loaded values;
- primary action is visually dominant;
- secondary/advanced actions do not compete with the current onboarding step.

Where the existing implementation still persists multiple contracts in one submit, preserve that backend behavior unless a concrete defect requires change. Visual grouping does not imply new APIs.

## Responsive and accessibility

Required widths: 390, 768 and 1440 px.

- no accidental horizontal page overflow;
- settings category navigation remains usable;
- forms become one column where appropriate;
- primary CTA remains reachable;
- labels remain associated with controls;
- visible focus is inherited from the canonical foundation;
- step/category state is not communicated by color alone;
- semantic tab/button state uses `aria-selected`, `aria-controls`, `aria-current` or equivalent where appropriate.

## Design-system rule

Consume `frontend-foundation.css` from the base branch. This branch may add onboarding/settings composition classes but may not introduce a new global palette, generic button skin, form skin, card skin, dialog skin, navigation visual language, focus system or breakpoint vocabulary.

## Testing strategy

Use Playwright regressions before implementation.

Required browser contracts:

- four-step progress reflects real backend status;
- exactly one next step and one dominant next-action CTA;
- next-action navigates to the matching settings category;
- settings exposes the human category structure;
- business/services/hours/receptionist values still load and persist through existing endpoints;
- validation/error and save-success states remain visible and human-readable;
- advanced controls stay available via disclosure;
- 390/768/1440 have no accidental body overflow;
- keyboard focus/semantic category navigation works;
- no test triggers real phone/WhatsApp/payment/provider side effects.

## Completion

The branch is complete only when the final feature HEAD is synchronized against the then-current `feat/frontend-foundation-dark` HEAD and fresh verification for that exact commit is green. PR remains Draft. No merge or deploy.
