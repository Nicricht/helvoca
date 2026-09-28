# RecepVoz dark visual foundation

**Date:** 2026-09-28  
**Branch:** `feat/frontend-foundation-dark`  
**Base:** `chore/first-pass-engineering-system`  
**Risk:** MEDIUM

## 1. Goal

Create the definitive visual foundation for the existing RecepVoz frontend without rebuilding the product, changing backend behavior, or deeply redesigning individual product areas.

The result must make the authenticated product feel like one serious commercial SaaS. Future frontend branches must be able to build Dashboard, Onboarding/Settings, Commerce/Inventory, Conversations/Calls, and Billing/Account on top of this foundation without inventing new colors, spacing systems, button styles, form treatments, navigation patterns, cards, tables, modals, or responsive conventions.

The visual direction is **dark-first, operational, calm, high-contrast, and compact enough for real business work**. Large white application surfaces are explicitly out of scope for the authenticated console.

## 2. Constraints and non-goals

This work MUST:

- preserve the current Spring Boot static HTML/CSS/JavaScript architecture;
- preserve existing backend APIs, authorization, tenant resolution, confirmation gates, billing/payment semantics, inventory semantics, phone provisioning safeguards, simulator isolation, and provider behavior;
- avoid real calls, WhatsApp sends, payments, deployments, destructive actions, or other external side effects;
- keep the PR in Draft state;
- remain based on `chore/first-pass-engineering-system`;
- finish only the shared visual foundation.

This work MUST NOT:

- migrate the frontend to React or another framework;
- redesign Dashboard, Inventory, Conversations, Settings, Billing, or other feature areas in depth;
- change business rules merely to simplify styling;
- remove existing capabilities;
- merge or deploy;
- use a page-specific visual invention where a shared foundation rule can solve the problem.

## 3. Current-state findings

Repository inspection identified 13 static HTML pages:

- `index.html`
- `settings.html`
- `inventory.html`
- `simulator.html`
- `operations.html`
- `sales.html`
- `pricing.html`
- `platform.html`
- `invite.html`
- `phone-numbers.html`
- `privacy.html`
- `terms.html`
- `data-deletion.html`

The frontend already contains useful dark primitives in `styles.css`, but the effective design system is fragmented across overlapping files including:

- `styles.css`
- `home-business.css`
- `first-user-ux-v2.css`
- `commercial-ui-v3.css`
- `inventory.css`
- `simulator.css`
- `sales.css`
- `pricing.css`
- inline `<style>` blocks on several pages.

The strongest contradiction is `commercial-ui-v3.css`, which defines a light console contract with `#f7f8fc` page backgrounds and `#ffffff` surfaces for authenticated Dashboard, Settings, and Inventory. `e2e/frontend-ux.spec.js` currently enforces that light palette. Earlier repository design specifications explicitly call for keeping Helvoca's dark visual language.

This branch therefore treats the problem as a **design-contract consolidation**, not a collection of isolated restyles.

## 4. Engineering perspectives

### Product

Protect the operational product hierarchy already established by the repository. The foundation must improve comprehension without deciding feature-specific information architecture that belongs to later branches.

### Architecture

Establish one final visual authority for shared primitives while keeping legacy feature CSS temporarily compatible. The foundation must lower future styling entropy rather than create another competing theme.

### UX/UI

Define hierarchy, density, typography, interaction states, responsive behavior, accessible contrast, and consistent component language.

### Engineering

Integrate with existing static pages with the smallest coherent set of changes. Avoid framework migration and unrelated refactors.

### QA

Turn the visual foundation into executable browser contracts: stylesheet ordering, dark surfaces, focus behavior, representative components, and overflow/responsive checks.

### Continuity

Keep the Draft PR Resume checkpoint current with exact HEAD, completed blocks, valid evidence, rulings, blockers, and next step.

Security and Operations are reviewed as non-regression boundaries because this branch does not intentionally change auth, data, integrations, deployment, or production behavior.

## 5. Risk and impact map

Risk is **MEDIUM** because shared CSS and navigation/component primitives affect many user-visible routes and responsive states, but the branch does not intentionally change privileged business behavior.

Impact map:

| Surface | Expected impact |
| --- | --- |
| UI | High within presentation layer: tokens, surfaces, typography, controls, navigation, tables, dialogs, cards, responsive behavior |
| API | None |
| Business logic | None |
| Data | None |
| Security | No intended behavior change; preserve auth/tenant boundaries |
| Integrations | None; no provider actions |
| Operations | No deploy; CI only |
| Continuity | Draft PR checkpoint required |

Relevant engineering invariants to preserve:

- tenant isolation;
- inventory integrity;
- confirmation and duplicate-order safety;
- payment truth;
- simulator/test external-side-effect safety;
- final-state truth;
- verification freshness.

## 6. Visual architecture

### 6.1 Canonical layer

Create `src/main/resources/static/frontend-foundation.css`.

It becomes the **last shared stylesheet loaded by product pages** and owns shared visual primitives. Existing page-specific styles may continue to own feature layout and specialized composition, but they must not be the source of truth for global colors, typography, buttons, form controls, generic cards, tables, dialogs, alerts, focus states, or navigation treatment.

The branch does not attempt a risky one-shot deletion of all historical CSS. Instead:

1. establish the canonical layer;
2. load it last on representative and shared product pages;
3. neutralize conflicting light-theme contract rules;
4. migrate only the minimum page-specific declarations needed to demonstrate the foundation;
5. leave feature-depth cleanup to the later focused branches.

This avoids both extremes: adding ad-hoc overrides forever or performing a high-blast-radius CSS rewrite in this foundation branch.

### 6.2 Token model

Use semantic CSS custom properties instead of page-specific raw colors.

Proposed core palette:

```css
--rv-bg-canvas: #070a10;
--rv-bg-shell: #0a0f17;
--rv-surface-1: #0d131d;
--rv-surface-2: #121a26;
--rv-surface-3: #182232;
--rv-surface-hover: #1d293a;

--rv-border-subtle: #202b3a;
--rv-border-default: #2a3749;
--rv-border-strong: #3a4a60;

--rv-text-primary: #f4f7fb;
--rv-text-secondary: #a7b3c5;
--rv-text-tertiary: #76859a;
--rv-text-disabled: #59677a;

--rv-accent: #806bff;
--rv-accent-hover: #927fff;
--rv-accent-soft: rgba(128, 107, 255, .14);
--rv-accent-border: rgba(128, 107, 255, .38);

--rv-success: #45d89a;
--rv-warning: #f2b864;
--rv-danger: #ff7187;
--rv-info: #65a7ff;
```

These are design targets, not permission for arbitrary duplication. Shared components consume semantic variables.

No normal authenticated application panel may use pure or near-white as its primary background.

### 6.3 Surface hierarchy

Use a maximum of four meaningful elevation/surface levels in ordinary product UI:

1. canvas;
2. shell/base surface;
3. card/control surface;
4. elevated overlay/dialog.

Hierarchy should primarily come from luminance, spacing, type, and borders. Shadows are reserved for overlays and intentionally elevated elements rather than every card.

Nested-card patterns should be reduced. A section inside a card should normally use spacing or a subtle separator before introducing another bordered container.

### 6.4 Typography

Retain the system/Inter-compatible stack already used by the product.

Define semantic type roles:

- page title;
- section title;
- component title;
- body;
- secondary body;
- label;
- metadata/caption;
- eyebrow/overline.

Use weight and size deliberately. Avoid excessive all-caps or letter spacing except compact brand/eyebrow use.

Recommended scale:

- page title: 30–36px desktop, 26–30px mobile;
- section title: 20–24px;
- component title: 15–17px;
- body: 14–16px;
- label: 13–14px;
- metadata: 12–13px.

Line-height must remain readable and should not be tightened merely to fit dense cards.

### 6.5 Spacing and geometry

Use a shared spacing scale based on:

`4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

Use a small radius vocabulary rather than per-component invention:

- compact control: 8px;
- standard control/card: 10–12px;
- large panel: 14–16px;
- major marketing/hero surfaces only: up to 20px.

Controls in the same context should share height. Primary form controls should generally target 40–44px desktop and at least 44px touch height where mobile interaction requires it.

## 7. Shared component contract

### Buttons

Provide shared variants:

- primary;
- secondary;
- ghost;
- danger/destructive;
- small/compact.

All variants define default, hover, active, focus-visible, and disabled states. Accent color is reserved for meaningful action and selection, not decoration.

### Inputs, textarea, and select

Use dark control surfaces with clearly visible boundaries and text. Define:

- placeholder;
- hover;
- focus-visible;
- invalid/error;
- disabled/read-only;
- help text.

Focus must be visible without relying on browser-default behavior that disappears against the dark theme.

### Badges and status pills

Use semantic status treatments for neutral, success, warning, danger, and info. Status must remain understandable from text, not color alone.

### Cards and panels

Cards use the surface hierarchy and consistent padding. Generic card styling is global. Feature CSS may adjust internal layout, not invent new card skins.

### Tables

Tables should support operational density:

- quiet header;
- clear row separation;
- predictable hover/focus;
- numeric alignment when appropriate;
- scroll container rather than viewport overflow;
- mobile transformation remains feature-specific where data requires cards.

### Dialogs/modals

Dialogs use one overlay/backdrop system, one elevated surface treatment, shared close button behavior, clear heading/actions hierarchy, and viewport-safe mobile sizing.

### Alerts/messages

Normalize informational, success, warning, and error messages. Avoid giant colored surfaces. Message state must not rely only on border/color.

### Empty/loading/error states

Define shared visual language for:

- loading;
- no data;
- recoverable error;
- authentication required;
- disabled/unavailable actions.

Feature copy remains owned by each screen.

## 8. Application shell and navigation

Authenticated pages should feel part of one application.

Desktop:

- stable left-rail/sidebar behavior where the existing shell already supports it;
- consistent active, hover, and focus states;
- brand and session/account information visually secondary to primary work;
- avoid rebuilding navigation independently per page.

Tablet/mobile:

- retain a compact navigation pattern compatible with existing markup;
- avoid horizontal viewport overflow;
- maintain accessible tap targets;
- allow wrapping or horizontal nav scrolling only when deliberate and visually contained.

This branch may normalize existing `.app-nav`, `.inventory-nav`, `.topbar`, and equivalent shared selectors. It must not redesign each information architecture.

## 9. Responsive contract

Shared target ranges:

- wide desktop: > 1200px;
- standard desktop/tablet landscape: 981–1200px;
- tablet/mobile navigation transition: <= 980px;
- compact mobile: <= 620px.

The exact component behavior may differ by feature, but global spacing, controls, typography, dialogs, and shell rules should use a coherent breakpoint strategy.

Required invariant: no representative application route creates accidental horizontal page overflow at supported test viewports.

## 10. Accessibility contract

Target WCAG 2.2 AA for ordinary text and interactive contrast where applicable.

Required:

- visible `:focus-visible` state on links, buttons, inputs, selects, and textareas;
- keyboard-usable navigation and dialogs using existing semantic controls;
- disabled state visually distinct from hover/default;
- semantic state labels not dependent on color alone;
- sufficient touch targets on compact viewports;
- reduced-motion respect for nonessential transitions;
- no low-contrast gray-on-gray metadata that becomes unreadable on dark surfaces.

This branch does not introduce an accessibility framework dependency.

## 11. Page adaptation scope

### `index.html`

Load the canonical foundation last. Preserve auth/dashboard behavior. Demonstrate the dark foundation in auth and authenticated shell without redesigning Dashboard content.

### `settings.html`

Load the same foundation and normalize shell, controls, cards, tabs/navigation, and form states. Do not restructure the settings workflow in this branch.

### `inventory.html`

Load the foundation and normalize shell, table, toolbar, inputs, buttons, badges, and dialogs. Do not redesign inventory information architecture or business behavior.

### `simulator.html`

Move its currently light visual language onto the dark foundation while preserving safe-simulator messaging and behavior. No real telephony may be triggered.

### `operations.html`

Adopt global surfaces/controls/navigation only. Do not redesign operational diagnostics or feature ordering.

### Utility/legal/admin pages

Bring obviously divergent shared primitives into the same dark family where low-risk, especially `phone-numbers.html`, `platform.html`, `invite.html`, and legal pages. Public marketing pages (`sales.html`, `pricing.html`) may retain their purpose-specific composition but should share foundation tokens where practical.

## 12. Legacy CSS policy

`commercial-ui-v3.css` currently contains valuable interaction/layout polish mixed with the obsolete light-theme contract.

Do not blindly delete it.

The implementation should either:

- remove/replace only the light palette sections that conflict with the foundation; or
- leave it loaded before `frontend-foundation.css` while the canonical layer neutralizes its global visual contract.

The chosen implementation must be explicit and tested. The foundation layer must not become an undocumented pile of `!important` overrides. Use specificity and load order deliberately; `!important` is reserved for compatibility cases that cannot be safely changed in the legacy selector during this branch.

## 13. Test strategy

Use Playwright as the browser contract already present in the repository.

### RED contract changes

Update/add tests before implementation so the existing light console fails the new contract.

At minimum verify:

1. `frontend-foundation.css` loads after legacy product styles on representative routes;
2. authenticated Dashboard body/canvas is dark, not `rgb(247, 248, 252)`;
3. representative card/panel surface is dark, not `rgb(255, 255, 255)`;
4. the primary accent remains deliberate and consistent;
5. representative input/button/table/dialog surfaces follow the foundation;
6. keyboard focus is visibly styled;
7. desktop/tablet/mobile representative routes avoid accidental horizontal overflow;
8. Simulator adopts dark surfaces without changing safe-mode behavior.

Existing functional E2E remains authoritative for behavior and must continue passing.

### Fast Gate

Run:

`bash scripts/ci/fast-gate.sh <base-sha>`

CSS-only changes may not produce extensive Fast Gate output, so targeted Playwright is required for this branch.

### Full Gate

The Draft PR must finish with repository CI green for the exact final HEAD. No prior-commit CI may be used as final evidence after implementation changes.

## 14. Adversarial review focus

Before final certification inspect deliberately for:

- unreadable muted text on dark surfaces;
- accidental white/light containers left by legacy CSS;
- active navigation indistinguishable from hover;
- focus rings clipped or invisible;
- disabled controls looking actionable;
- nested borders creating visual noise;
- dialogs exceeding small viewports;
- tables causing body-level horizontal overflow;
- Bootstrap selectors defeating the canonical layer;
- CSS specificity that makes future feature branches need more `!important`;
- light-theme tests weakened instead of intentionally rewritten;
- simulator/provider controls accidentally changing behavior;
- feature-specific layout regressions caused by generic selectors.

## 15. Acceptance criteria

The branch is acceptable when:

- authenticated RecepVoz is consistently dark-first across representative screens;
- no major white application surfaces remain in the authenticated console;
- shared buttons, inputs, selects, cards, tables, badges, dialogs, alerts, focus and disabled states visibly belong to one system;
- navigation and shell treatment are coherent across Dashboard, Settings, Inventory, Simulator, and Operations without deep feature redesign;
- typography, spacing, borders, radii and shadows follow documented shared scales;
- responsive checks pass at repository target viewports;
- existing product behavior remains intact;
- no real external effects occurred;
- targeted browser tests and the repository Full Gate are green for the exact final HEAD;
- the PR remains Draft;
- the PR Resume checkpoint identifies branch, PR, HEAD, completed blocks, valid evidence, blockers/rulings, and next step.

## 16. Future-branch rule

After this foundation lands into the frontend integration chain, future frontend work must treat this visual system as the default contract.

A feature branch may add feature-specific composition, but it should not introduce a new:

- brand/accent palette;
- canvas/surface palette;
- spacing scale;
- generic button skin;
- generic form-control skin;
- generic card/table/dialog skin;
- focus treatment;
- navigation visual language;
- breakpoint system;

unless a concrete product requirement demonstrates that the shared foundation cannot express the need and the deviation is documented.

That rule is the primary defense against RecepVoz returning to a collection of independently generated screens.
