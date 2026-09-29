# RecepVoz Visual Product Source of Truth

**Status:** Canonical visual product specification  
**Source:** User-approved reference images supplied 2026-09-29  
**Applies to:** Marketing/authentication + authenticated customer product  
**Implementation baseline:** `feat/frontend-finish-integration@3d73320848c7d81956701af6eb60f516f9eb1c80`  
**Risk:** LOW for documentation; future implementation against this spec is MEDIUM

## 1. Authority

The approved reference images are the primary visual authority for the RecepVoz frontend.

They are not moodboards and not optional inspiration. They define the target product language that implementation must reproduce in structure, hierarchy, density, contrast, navigation, spacing, component treatment and responsive behavior.

Because chat attachments are not a durable repository dependency, this document translates the visible reference language into explicit product rules. Future work should cite this specification instead of relying on a conversation screenshot.

Authority order when visual requirements conflict:

1. this visual product specification and its screen blueprints;
2. explicit product behavior/security invariants;
3. current canonical design tokens in `frontend-foundation.css`;
4. older frontend design documents;
5. legacy CSS and historical production appearance.

The currently deployed `main` frontend is **not** a visual reference. It is historical implementation state.

## 2. Product intent

RecepVoz should visually communicate:

- an operational SaaS used every day by a real business;
- a modern AI receptionist, not a generic admin template;
- confidence, clarity and speed rather than decoration;
- high information density without visual clutter;
- a single coherent product from marketing page to daily operations;
- mobile usability without collapsing into an unrelated design.

A customer should understand within seconds:

- what RecepVoz does;
- whether the receptionist is operating;
- what happened today;
- what requires attention;
- where to manage conversations, agenda, customers, inventory, configuration and billing.

## 3. Core visual language

### 3.1 Dark navy foundation

The application uses a deep navy/blue-black canvas, not neutral black and not gray charcoal.

Canonical current token family:

```css
--rv-bg-canvas: #06111c;
--rv-bg-shell: #081825;
--rv-surface-1: #0b1b29;
--rv-surface-2: #102434;
--rv-surface-3: #153047;
--rv-surface-hover: #183b52;

--rv-border-subtle: #163549;
--rv-border-default: #1f465d;
--rv-border-strong: #2b617c;

--rv-text-primary: #f4fbff;
--rv-text-secondary: #a7bdcc;
--rv-text-tertiary: #6f8b9e;
```

These values may be tuned only if screenshot comparison shows a measurable mismatch. Pages must not invent independent background palettes.

### 3.2 Accent roles

Product/action accent:

```css
--rv-accent: #16d9f5;
--rv-accent-hover: #42e3f7;
```

AI-specific accent:

```css
--rv-ai-accent: #8b5cf6;
--rv-ai-accent-hover: #a678ff;
```

Rule:

- cyan = primary product action, active product navigation, data emphasis, product glow;
- violet = AI identity, receptionist-specific accent or intentionally secondary branded emphasis;
- green = success/positive operational state;
- red = destructive/error;
- amber = warning/attention.

Do not recolor semantic statuses with the brand accent.

The two approved reference variants demonstrate the same system with different accent emphasis. The canonical RecepVoz product should use **cyan as primary product accent and violet as the AI accent**, allowing both references to inform one coherent system rather than becoming two competing themes.

### 3.3 Contrast

Ordinary text must remain clearly readable against dark surfaces.

Forbidden:

- dark heading text on dark navy;
- gray metadata that disappears at normal brightness;
- buttons whose label contrast drops below readable AA levels;
- decorative overlays that reduce text legibility.

The current deployed screenshot where “No pierdas otra llamada” nearly disappears is explicitly a failed visual state.

## 4. Brand treatment

The brand consists of:

- waveform/voice mark;
- “RecepVoz” wordmark;
- compact high-contrast placement.

Marketing header:

- full mark + wordmark;
- white wordmark;
- cyan or violet waveform according to brand context;
- top-left, visually balanced with navigation and CTAs.

Authenticated shell:

- compact mark + wordmark at top of sidebar;
- not a large marketing logo;
- business/account identity appears separately in the top utility bar.

Branding must never overlap or intercept navigation hit areas.

## 5. Layout architecture

### Desktop authenticated shell

Target at >= 981 px:

- persistent left rail around **220 px**;
- utility top bar around **56–64 px**;
- main content begins to the right of the rail;
- page heading aligned to content grid;
- maximum useful content width should feel intentional, not stretched edge-to-edge;
- cards align on a repeatable grid.

Sidebar target navigation:

**Inicio · Conversaciones · Agenda · Clientes · Inventario · Configuración · Facturación**

Internal/technical routes must not appear in ordinary customer navigation.

### Tablet

At <= 980 px:

- fixed left rail releases;
- product navigation becomes a contained horizontal navigation row;
- no body-level horizontal overflow;
- data surfaces may scroll internally where appropriate.

### Mobile

At <= 620 px:

- one-column content hierarchy;
- minimum touch targets 42–44 px;
- no tiny desktop sidebar squeezed into the viewport;
- no inaccessible off-screen actions;
- tables either scroll in contained regions or transform to readable rows/cards;
- dialogs fit within viewport width.

## 6. Geometry and spacing

Canonical spacing scale:

`4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

Target geometry:

- compact controls: 8–10 px radius;
- standard cards/inputs: 10–14 px;
- large panels: 14–16 px;
- hero/marketing surfaces: up to 20–24 px;
- excessive pill shapes reserved for badges/toggles, not general cards.

Cards should not look like nested boxes inside nested boxes. Use spacing, typography and separators before adding another border.

## 7. Typography hierarchy

The references use strong, legible sans-serif hierarchy.

Desktop targets:

- marketing hero: 52–72 px depending on viewport;
- authenticated page title: 30–40 px;
- section title: 18–24 px;
- card metric: 28–34 px;
- component title: 14–17 px;
- body: 14–16 px;
- labels: 12–14 px;
- metadata: 11–13 px.

Requirements:

- page title is immediately dominant;
- secondary descriptions remain readable;
- metrics use strong numeric emphasis;
- no important action copy below 12 px;
- uppercase and letter-spacing are reserved for eyebrow/brand metadata.

## 8. Surface hierarchy

Use four levels:

1. canvas;
2. shell/sidebar/topbar;
3. standard card/control surface;
4. selected/elevated/detail/dialog surface.

Hierarchy comes primarily from:

- luminance;
- border;
- spacing;
- content grouping.

Shadows should be subtle and rare in authenticated product UI. Marketing may use glow/light effects where they support the voice/AI concept.

## 9. Marketing/authentication direction

The public entry screen must read as a premium AI SaaS hero, not as “login form + empty dark page”.

Required desktop composition:

- top product navigation;
- left hero copy;
- dominant headline;
- one primary CTA and one secondary CTA;
- product/voice illustration occupying meaningful visual weight;
- trust/value bullets along lower hero edge;
- login available without becoming the main visual subject.

Reference headline structure:

**Nunca más pierdas una llamada de tu negocio**

Supporting proposition should communicate:

- answers customers;
- understands requests;
- schedules;
- follows up;
- reduces missed opportunities.

The actual product may use approved copy variations, but hierarchy and confidence must match the reference.

## 10. Authenticated product direction

The authenticated product is a dense operational workspace.

Each principal screen must have:

- sidebar navigation;
- utility topbar;
- clear page title + one-line purpose;
- one obvious primary action where applicable;
- content organized around the user’s task, not backend entities;
- predictable empty/loading/error states.

Avoid:

- giant empty cards;
- repeated headings;
- decorative panels with no action;
- exposing raw implementation terminology.

## 11. Interaction states

Every interactive component must define:

- default;
- hover;
- focus-visible;
- active/current;
- disabled;
- loading where applicable;
- error where applicable.

Navigation selection must be unmistakable from hover.

Focus must use the product accent and remain visible on dark surfaces.

## 12. Accessibility

Target WCAG 2.2 AA where applicable.

Required:

- keyboard navigation through primary product areas;
- readable contrast;
- visible focus;
- semantic buttons/links;
- status not conveyed only by color;
- touch-friendly mobile targets;
- motion kept nonessential;
- responsive zoom does not hide critical actions.

## 13. What “matching the reference” means

A screen does not pass because it uses the same colors.

Visual acceptance requires matching all of these dimensions:

- information architecture;
- macro layout;
- proportions;
- hierarchy;
- density;
- typography;
- spacing;
- navigation;
- component vocabulary;
- accent roles;
- contrast;
- responsive behavior;
- interaction states.

A page with correct tokens but wrong composition is a visual failure.

## 14. Non-goals

The reference images do not authorize:

- inventing backend features that do not exist;
- fabricating data in production;
- adding real payment/call/WhatsApp effects;
- changing tenant/security rules;
- hiding missing functionality behind fake controls;
- copying decorative elements that reduce usability.

Where a reference depicts a feature the backend does not yet support, preserve the visual slot only when product has explicitly accepted that feature. Otherwise document it as future scope.

## 15. Product freeze rule

The current Release Candidate remains frozen.

Implementation work to close visual gaps must happen in a new dedicated branch/PR and must be justified by this specification plus visual evidence.

No direct edits to the frozen RC merely to “try something”.

