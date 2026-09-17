# Helvoca Frontend UX Simplification Design

## Goal

Transform the current customer console from a dense configuration surface into a simple operations-first SaaS experience that a non-technical business owner can understand at a glance.

## Product principles

1. The default dashboard answers three questions within five seconds: is Helvoca operating, what is configured, and what needs attention.
2. Onboarding language appears only while setup is incomplete. Once the tenant is ready, the primary headline becomes operational rather than instructional.
3. Advanced configuration remains available but is progressively disclosed. The customer sees summaries first and opens only the section they need.
4. Technical provider details such as Twilio, E.164 and provisioning internals are secondary. They remain accessible where required, but are not the first thing a customer sees.
5. Billing details do not dominate the dashboard. The current plan and remaining minutes are summarized compactly, while plan comparison and checkout live behind an explicit action.
6. Existing backend authorization, confirmation gates, billing verification, phone provisioning confirmation and certification flags remain unchanged.
7. No industry-specific frontend logic is introduced. The UI continues to reflect tenant configuration and capabilities rather than hardcoded verticals.

## Auth experience

Reduce the marketing copy and visual noise on the first screen. Keep a short value statement, one supporting sentence, and the registration/login forms. Preserve the existing registration payload and automatic timezone/language detection.

## Dashboard experience

When the business is ready for calls, show an operational headline such as `Helvoca está operativa` and a concise supporting line. When setup is incomplete, show `Termina de preparar Helvoca` with a single next-action message.

Keep the four readiness cards for business, services, hours and phone, but shorten their labels and status text. Remove duplicate readiness banners. Only one next-step surface should be visible.

The AI onboarding analyzer remains available, but becomes a compact `Actualizar negocio con IA` card instead of a large hero section once the user is inside the dashboard.

## Configuration experience

Replace the permanently expanded advanced form with progressive disclosure:

- Negocio y agente
- Permisos del agente
- Servicios
- Horarios
- Preguntas frecuentes
- Teléfono

Each section is collapsed by default and shows a concise summary. Opening a section reveals the existing fields and controls. The save action remains explicit and uses the current backend endpoints.

The detailed capability checkboxes remain available under `Permisos del agente`, but the explanatory warning paragraph is removed from the default view.

## Telefonía

Present two clear choices inside the phone section:

- `Conectar mi número`
- `Buscar un número nuevo`

The manual form and self-service provisioning search are not displayed simultaneously. Provider-specific details stay within the selected flow. Provisioning still requires the existing explicit confirmation before any charged action.

## Subscription

Replace the large commercial card with a compact summary showing current plan, service state and minute usage. Add an explicit `Gestionar plan` control that expands the existing pending-state and plan-selection UI. Checkout behavior and provider verification remain unchanged.

## Visual system

Keep the existing dark theme and accent palette. Reduce nested card borders, increase spacing between primary groups, shorten labels, and reserve strong color for state and actions. Use the existing responsive breakpoints and make the new disclosure controls keyboard-accessible.

## Testing

Add browser coverage that proves:

- a ready tenant sees an operational headline rather than onboarding copy;
- duplicate readiness copy is absent;
- advanced configuration sections are collapsed by default and open on demand;
- subscription plan cards are hidden until `Gestionar plan` is activated;
- phone setup shows one path at a time;
- all existing confirmation gates for billing and provisioning still work.

No backend schema or Flyway migration is required for this redesign.
