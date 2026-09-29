# RecepVoz Frontend Component Contracts

**Status:** Canonical reusable UI contract  
**Parent authority:** `VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`

This document prevents each screen from inventing its own visual system. Feature CSS may control composition, but shared component behavior and visual roles must remain consistent.

## 1. Application shell

### Desktop

- sidebar width target: 220 px;
- sidebar fixed/persistent at >= 981 px;
- top utility bar target: 56–64 px;
- main content offset must guarantee no overlap with sidebar or brand;
- brand block must never intercept navigation pointer events;
- sidebar and topbar use shell surfaces, not card surfaces.

### Compact

At <= 980 px:

- sidebar becomes horizontal navigation;
- horizontal navigation is contained and intentionally scrollable if needed;
- navigation does not cause body overflow;
- topbar may wrap but identity/action controls stay usable.

## 2. Navigation item

An item contains:

- optional icon;
- text label;
- active/current state.

Target states:

**Default**
- transparent/subtle background;
- secondary text.

**Hover**
- subtle surface lift;
- primary text.

**Current**
- accent-soft background;
- accent border or left indicator;
- high-contrast text.

**Focus**
- visible cyan focus ring independent from current state.

Minimum compact hit target: 42 px.

## 3. Page heading

Every principal authenticated screen uses one heading block.

Contains:

- H1;
- one concise supporting sentence;
- optional eyebrow only where useful;
- optional page-level action group.

Do not repeat the same page title in a second card immediately below.

Desktop H1 target: 30–40 px.

## 4. Primary action

Use for the single highest-priority action in the local context.

Visual role:

- cyan background;
- dark/high-contrast label where contrast supports it;
- clear hover/focus;
- 40–44 px standard height;
- optional icon.

Examples:

- Comenzar;
- Nueva cita;
- Agregar producto;
- Guardar cambios;
- Nueva prueba.

Do not put multiple visually identical primary actions in the same local region.

## 5. Secondary action

Use for useful but non-dominant actions.

- dark surface;
- visible border;
- primary text;
- accent border on hover/focus.

Examples:

- Ver cómo funciona;
- Ver facturas;
- Cancelar non-destructive navigation.

## 6. Destructive action

Use semantic danger color.

Examples:

- cancelar cita when destructive;
- delete/remove.

Must not share the product cyan/violet accent.

## 7. KPI card

Required anatomy:

- label;
- dominant metric;
- optional trend;
- optional supporting icon.

Target:

- compact;
- aligned height across row;
- metric 28–34 px;
- labels 12–14 px;
- icons restrained;
- trend uses semantic color and text/symbol.

Do not turn KPI cards into large illustration tiles.

## 8. Operational card/panel

Standard card:

- surface-1 or surface-2;
- 1 px subtle/default border;
- 12–16 px radius;
- 16–24 px padding depending on density;
- no decorative gradient;
- subtle or no shadow.

Panel header:

- title left;
- contextual action right;
- separator only when useful.

## 9. Table

Table-first operational surfaces use:

- quiet header row;
- strong first column;
- consistent column alignment;
- subtle row separators;
- hover state;
- focusable/actionable rows where interaction exists.

Numeric rules:

- prices aligned consistently;
- quantities easy to scan;
- avoid mixing currency formatting conventions within one table.

Mobile:

- contained horizontal scroll is acceptable;
- page-level horizontal overflow is not.

## 10. Search field

Search fields should be visually compact and immediately identifiable.

- leading search icon optional;
- placeholder describes entity, e.g. “Buscar conversaciones…”;
- 40–44 px height;
- dark control surface;
- accent focus;
- clear reset when product requires it.

## 11. Filter/tab group

For small sets of modes:

- compact segmented/tabs;
- one selected state;
- not giant cards;
- visible focus.

Examples:

- Todas / Llamadas / WhatsApp;
- General / Recepcionista IA / Horarios / Integraciones;
- view selector for Agenda.

## 12. Status pill

Use for concise state, not sentences.

Examples:

- Activo;
- Cita agendada;
- Consulta;
- Cliente nuevo;
- Bajo stock.

Rules:

- textual meaning required;
- semantic color;
- small but readable;
- no excessive saturation;
- do not use pill styling for normal buttons.

## 13. Conversation row

Anatomy:

- selection control only if batch behavior exists;
- avatar/channel icon;
- primary customer identifier;
- short preview;
- time;
- status/outcome.

Selected row:

- elevated/selected surface;
- accent cue;
- remains readable.

## 14. Chat bubble

Two clear roles:

**Customer**
- neutral dark surface;
- primary/secondary text.

**RecepVoz**
- cyan or AI-violet tinted surface according to context;
- never low-contrast neon text;
- timestamp metadata quiet.

Do not overuse speech-bubble tails or decoration.

## 15. Calendar appointment

Contains:

- customer;
- secondary detail such as party size/service;
- positioned according to time.

Rules:

- selected appointment visibly distinct;
- text must fit or truncate predictably;
- categorical color use constrained;
- status color retains semantic meaning.

## 16. Form field

Anatomy:

- label;
- control;
- optional help;
- inline error.

Control:

- dark background;
- visible border;
- primary text;
- placeholder tertiary;
- cyan focus ring;
- disabled state unmistakable.

Validation:

- error appears near field;
- settings navigation opens owning section when hidden;
- focus moves to actionable invalid control when appropriate.

## 17. Settings internal navigation

Target visual:

- compact tab bar or narrow section navigation;
- current section marked with accent;
- advanced settings visually secondary;
- no equal-weight six-card chooser.

At compact widths, navigation may scroll horizontally.

## 18. Billing plan card

Anatomy:

- plan name;
- state badge;
- price;
- billing cadence;
- concise entitlement list;
- one plan action if real.

It should feel commercially important without becoming a marketing landing card inside the application.

## 19. Empty state

Contains:

- concise title;
- explanation;
- next action if one exists.

Avoid illustration-heavy empty states in dense operational screens unless they materially improve comprehension.

## 20. Error state

Contains:

- human readable problem;
- recovery guidance;
- retry when meaningful.

Never expose:

- Java exception names;
- provider enum values;
- stack traces;
- tenant/security implementation details.

## 21. Dialog/drawer

Use for local creation/edit/detail tasks.

Dialog:

- max viewport-safe width;
- clear title;
- body;
- aligned action row;
- visible close path.

Drawer:

- appropriate for detail on desktop;
- converts to full/stacked view on mobile if necessary.

## 22. Icons

Icons support meaning but never replace labels for primary navigation in the standard product.

Rules:

- one consistent icon family;
- visually consistent stroke/weight;
- 16–20 px typical UI;
- avoid emoji in final customer product navigation;
- semantic status icons may use color.

## 23. Data visualization

Charts should resemble the references:

- dark panel;
- subtle grid;
- high-contrast axis labels;
- cyan + blue/violet series;
- no rainbow palette;
- tooltip readable;
- legends concise.

Charts do not replace exact numbers when the user needs operational truth.

## 24. Marketing hero illustration

May use:

- phone device;
- waveform;
- voice/call glow;
- conversation bubbles;
- cyan/violet lighting.

Must not:

- reduce headline legibility;
- become generic stock imagery;
- imply unsupported provider behavior;
- rely on an external image if CSS/HTML composition provides better responsive control without quality loss.

## 25. Loading

Preferred:

- skeletons or quiet progress;
- preserve component size;
- loading shimmer only as functional motion;
- respect reduced motion.

## 26. Component ownership rule

`frontend-foundation.css` owns:

- palette;
- surfaces;
- shared typography roles;
- generic buttons;
- generic form controls;
- focus;
- generic cards;
- generic table language;
- shell/nav visual treatment;
- status primitives.

Feature CSS owns:

- screen-specific grids;
- conversation master/detail sizing;
- calendar geometry;
- inventory column behavior;
- dashboard chart/card arrangement;
- billing composition.

A feature stylesheet must not introduce a second global design system.

