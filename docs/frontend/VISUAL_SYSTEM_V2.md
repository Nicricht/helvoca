# RecepVoz Visual System v2

**Status:** implementation source of truth for PR #734  
**Frame seal:** `FRAME CHANGE: YES`  
**Risk:** MEDIUM

## Product direction

RecepVoz must feel alive, premium and operational without becoming visually noisy. Motion is part of the information architecture, not decoration.

The approved design language is:

- dark navy operational cockpit;
- cyan as the principal live/action signal;
- violet for AI/automation depth;
- emerald for healthy/confirmed states;
- amber/red reserved for attention and danger;
- luminous but controlled depth, glass-like surfaces and soft internal highlights;
- large readable hierarchy instead of miniature dashboard density.

## Motion hierarchy

Three motion layers are required:

1. **Ambient motion** — slow glows, waves, orbits and breathing light. Typical duration: 6–20 seconds.
2. **Interaction motion** — hover lift, press compression, active navigation and drawers. Typical duration: 120–320 ms.
3. **Data motion** — progress growth, chart reveal, status pulse, staggered rows and state changes. Typical duration: 300–800 ms.

Motion must prefer `transform` and `opacity`, remain subtle, and respect `prefers-reduced-motion`.

## Canonical screen asset map

### Inicio
- `/app/assets/home/hero-bot.webp` — main reception hero.
- `/app/assets/home/agenda.webp` — quick action: Nueva cita.
- `/app/assets/home/orders.webp` — quick action: Ver pedidos.
- `/app/assets/home/inventory.webp` — quick action: Gestionar stock.
- `/app/assets/home/automation.webp` — quick action: Configurar IA.

### Agenda
- `/app/assets/recepvoz/v2/agenda/hero-calendar-robot.webp` — hero.
- `/app/assets/recepvoz/v2/agenda/appointments-calendar.webp` — floating appointment/calendar element.

### Operaciones
- `/app/assets/recepvoz/v2/operations/hero-order-robot.webp` — hero.
- `/app/assets/recepvoz/v2/operations/order-package.webp` — order state element.
- `/app/assets/recepvoz/v2/operations/delivery-truck.webp` — delivery state element.

### Inventario
- `/app/assets/recepvoz/v2/inventory/hero-stock-robot.webp` — hero.
- `/app/assets/recepvoz/v2/inventory/stock-confirmed.webp` — healthy stock state.
- `/app/assets/recepvoz/v2/inventory/stock-warning.webp` — low/out-of-stock state.

### Configuración
- `/app/assets/recepvoz/v2/settings/hero-ai-settings.webp` — hero.
- `/app/assets/recepvoz/v2/settings/hero-document-organizer.webp` — knowledge/document setup.
- `/app/assets/recepvoz/v2/settings/business-storefront.webp` — business profile.
- `/app/assets/recepvoz/v2/settings/import-sources.webp` — import sources.

### Plan y consumo
- `/app/assets/recepvoz/v2/plan/hero-usage-robot.webp` — usage/plan hero.

## Shell contract

All authenticated screens share:

- the same RecepVoz brand block;
- live system status;
- primary navigation with sliding active treatment;
- plan/usage card driven by live subscription data when available;
- account/logout access;
- ambient background field;
- consistent responsive behavior.

The shell must never hard-code customer plan usage.

## Responsive evidence

Before merge, exact-head browser evidence is required at:

- 1536×950
- 1440×900
- 1366×768
- 1280×720
- 768×1024
- 390×844

The body must not horizontally overflow at any canonical viewport.

## Acceptance rule

Green CI is necessary but not sufficient. The PR is not visually complete until cross-screen screenshots demonstrate the intended hierarchy, asset placement, motion-safe structure and responsive composition.
