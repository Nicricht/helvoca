# RecepVoz Frontend Screen Blueprints

**Status:** Canonical screen-level product contract  
**Parent authority:** `VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`  
**Reference:** User-approved visual compositions supplied 2026-09-29

This document translates the approved reference images into concrete screen requirements. It defines what each screen must communicate, how its information should be arranged, and what constitutes a visual/product mismatch.

## 1. Shared authenticated shell

### Purpose

Make every operational screen feel like one continuous product.

### Desktop structure

Left rail, approximately 220 px:

- RecepVoz compact brand at top;
- Inicio;
- Conversaciones;
- Agenda;
- Clientes;
- Inventario;
- Configuración;
- Facturación.

Top utility bar:

- search or contextual utility area when useful;
- signed-in business avatar/name;
- compact account/menu affordance;
- no duplicate global navigation.

Main content:

- starts right of sidebar;
- page heading aligned to main grid;
- 24–32 px horizontal desktop content padding;
- visually stable across routes.

### Acceptance

A user moving between Inicio, Conversaciones, Agenda, Inventario, Configuración and Facturación should perceive a page-content change, not a shell redesign.

## 2. Public landing + authentication

### User question

“What is RecepVoz, why should I care, and how do I start?”

### Target composition

Desktop hero is two major columns:

**Left:**
- compact value badge, e.g. “Tu recepcionista de IA, 24/7”;
- large headline, maximum 3–4 visual lines;
- short supporting paragraph;
- primary CTA;
- secondary CTA;
- compact value/trust row.

**Right:**
- visual demonstration of the AI receptionist;
- phone/call treatment or equivalent product visualization;
- customer/assistant bubbles;
- voice/waveform cue;
- explicit visual connection to calls, bookings and WhatsApp-style follow-up.

Top navigation:

- Producto;
- Funciones;
- Precios;
- FAQ;
- Iniciar sesión;
- Comenzar.

### Authentication

Login/register may be:

- a dedicated route;
- a modal/drawer;
- or a secondary panel that does not visually overpower the hero.

It must not reduce the landing experience to “large empty card + login box”.

### Visual hierarchy

1. headline;
2. product visualization;
3. primary CTA;
4. value proposition;
5. secondary CTA/navigation;
6. authentication utility.

### Failed state examples

- headline too dark to read;
- login panel visually stronger than the product promise;
- no top product navigation;
- hero illustration absent leaving empty space;
- small logo floating without compositional relationship;
- huge blank areas.

## 3. Inicio / Dashboard

### User question

“What is happening in my business right now?”

### Header

Title: **Inicio**

Supporting text: short operational summary, e.g. “Resumen de tu negocio en tiempo real”.

Top-right context:

- date/window selector, e.g. Últimos 7 días;
- no unnecessary settings clutter.

### First row: KPI cards

Reference structure uses four equally legible KPI cards:

- Llamadas recibidas;
- Citas agendadas;
- Clientes nuevos;
- Tasa de conversión.

Each card contains:

- label;
- dominant metric;
- trend/delta where meaningful;
- one restrained semantic icon.

Metrics are visually dominant. Icons support the metric; they do not become decoration.

### Second row

Left, larger analytical panel:

**Llamadas y citas**
- simple chart;
- two series;
- restrained grid;
- readable labels;
- enough height to scan weekly behavior.

Right:

**Últimas conversaciones**
- 4–6 recent items;
- channel/avatar cue;
- phone/name;
- short preview;
- relative time;
- “Ver todas”.

### Empty/error states

If there is no data, preserve the layout but replace values with useful empty guidance. Do not collapse the entire screen into one giant empty card.

## 4. Conversaciones

### User question

“What did the customer ask, what did RecepVoz do, and do I need to act?”

### Desktop composition

Three conceptual regions inside the standard shell:

1. conversation list controls;
2. conversation list;
3. selected conversation detail.

List header:

- page title;
- subtitle;
- channel tabs/filters;
- search;
- optional filter control.

Conversation row:

- channel/avatar;
- customer identifier;
- last-message preview;
- timestamp;
- outcome/status pill;
- clear selected state.

Selected detail:

- customer identity;
- last activity;
- contextual actions;
- message/transcript body;
- composer or next-action area only when supported.

### Message style

Customer and receptionist messages have clear visual distinction without excessive gradients.

AI message accent may use violet; product/action controls remain cyan.

### Mobile

At 390 px:

- list and detail cannot sit side by side;
- use list-first then detail/back pattern;
- no body overflow;
- search/filters remain reachable.

## 5. Agenda

### User question

“What is booked, when, and what are the appointment details?”

### Desktop composition

Header:

- title Agenda;
- subtitle “Gestiona tus citas y reservas”;
- view selector;
- primary action “Nueva cita” where supported.

Main workspace:

- day/week schedule grid;
- time rail;
- appointment blocks positioned by time;
- current/selected appointment visually distinct.

Right detail rail:

- compact month calendar;
- selected date;
- appointment detail card:
  - customer;
  - phone;
  - date;
  - time;
  - party size if relevant;
  - notes/location where supported;
  - edit/cancel actions only if real.

### Appointment colors

Use a restrained categorical palette. Do not make each appointment a random bright color.

Green may indicate confirmed/successful states; cyan/blue can represent ordinary scheduled states.

### Mobile

Agenda may switch to:

- list/day view;
- stacked date picker;
- selected appointment detail below.

Do not squeeze desktop calendar geometry into 390 px.

## 6. Clientes

### User question

“Who are my customers and what is their relationship with the business?”

### Required structure

Page title + short purpose.

Toolbar:

- search;
- meaningful filters where supported;
- primary action only if creating customers is a real product behavior.

Primary content:

- table/list with customer;
- contact information;
- last interaction;
- bookings/orders/conversation summary where data exists;
- customer state/attention cue if relevant.

Selecting a customer should open detail without abandoning product context.

### Reference alignment

The screenshots do not show a dedicated Clientes screen in detail, so this screen inherits the same visual grammar as Inventario and Conversaciones:

- operational table/list;
- dark compact rows;
- clear selected/hover states;
- restrained status pills;
- no dashboard-card collage.

## 7. Inventario

### User question

“What do I sell, what stock do I have, and what needs attention?”

### Header

- Inventario;
- subtitle “Gestiona tus productos y servicios”;
- primary action “Agregar producto” if supported.

### Toolbar

- search field;
- category/status filter;
- optional sort;
- no redundant filter controls.

### Table-first layout

Reference columns:

- Producto;
- Categoría;
- Precio;
- Stock;
- Estado;
- row action.

Requirements:

- product name strongest in row;
- category secondary;
- price aligned consistently;
- stock numerical and scannable;
- status pill concise;
- row actions remain reachable.

Low stock/out-of-stock receives semantic attention without coloring the entire row.

### Mobile

Use contained table scrolling or deliberate row cards. Never allow page-level horizontal overflow.

## 8. Configuración

### User question

“How does my receptionist and business behave?”

### Header

- Configuración;
- purpose copy: personalize/business/receptionist setup.

### Internal navigation

Reference shows compact tabs rather than equal-weight giant cards.

Target conceptual sections:

- General / Negocio;
- Recepcionista IA;
- Horarios;
- Integraciones.

Existing product requirements may also expose:

- Servicios;
- Respuestas/conocimiento;
- Canales;
- Equipo;
- other advanced settings.

Those should fit into the same hierarchy, with advanced areas clearly secondary.

### Receptionist panel

Fields shown by reference grammar:

- business/receptionist identity;
- business type where available;
- tone of conversation;
- greeting;
- instructions;
- voice;
- capabilities.

Form rules:

- labels above controls;
- help text quiet but readable;
- controls aligned on grid;
- one clear save action;
- visible success/error state;
- validation routes user to the owning section.

### Mobile

Internal tabs may scroll horizontally. Forms become one column.

## 9. Facturación

### User question

“What plan am I on, what do I get, and what is my billing status?”

### Page structure

Header:

- Facturación;
- “Gestiona tu plan y pagos”.

Primary card:

- plan name;
- status;
- monthly price;
- concise benefit/entitlement list;
- plan-change action only if a real supported flow exists.

Secondary card:

- next invoice;
- amount;
- billing date;
- “Ver facturas” or equivalent if real.

Payment method:

- masked method;
- expiry;
- edit action only if real and safely supported.

Usage:

- included vs used minutes;
- concurrency or limits where relevant;
- no raw provider terminology.

### Safety

Never fake a charge, checkout or payment success merely to match the mockup.

## 10. Simulador

### User question

“Can I safely test how my receptionist will behave?”

### Visual role

Simulator is operational but intentionally distinct from real calls.

Required prominent safety copy:

- no real phone call;
- no real WhatsApp;
- no real commercial data creation where applicable.

Workspace:

- start/new test action;
- conversation transcript;
- tool/action trace translated for humans;
- final resolution;
- reset/new test.

AI accent may be more visible here than elsewhere, but primary controls still follow product action rules.

## 11. Onboarding

### User question

“What must I do before RecepVoz is ready?”

Canonical four-step path:

1. Negocio;
2. Servicios;
3. Horarios;
4. Recepcionista.

Required:

- current progress;
- exactly one obvious next step;
- completed/current/upcoming distinction;
- contextual CTA;
- no exposure of advanced operational surfaces before setup makes sense;
- validation routes directly to the incomplete section.

On completion, transition to Inicio without leaving stale onboarding chrome.

## 12. Global empty/loading/error contract

Every principal screen must have intentional states.

### Loading

- skeleton/quiet progress;
- preserve page structure;
- no layout jump where practical.

### Empty

Explain:

- what is empty;
- whether that is normal;
- the next useful action.

### Error

Show:

- human explanation;
- retry/recovery action where meaningful;
- no raw stack/provider enum;
- do not hide unaffected parts of the workspace.

## 13. Responsive acceptance per screen

Each principal route must be reviewed at:

- 1440 × 900;
- 768 × 1024;
- 390 × 844.

At each size verify:

- no body-level horizontal overflow;
- title and primary action remain visible;
- navigation usable;
- controls not clipped;
- content priority remains sensible;
- dialogs contained;
- no text collision;
- no accidental desktop two-column layout on mobile.

## 14. Screen completion rule

A screen is not complete merely because its data is present.

It passes only when:

- the primary user question is answerable immediately;
- hierarchy matches this blueprint;
- shared shell matches the product;
- all important actions are visually obvious;
- responsive behavior is intentional;
- empty/loading/error states exist;
- visual acceptance evidence has been captured.

