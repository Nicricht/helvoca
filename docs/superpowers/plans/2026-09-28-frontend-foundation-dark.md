# Frontend Foundation Dark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish one dark-first visual system across the existing RecepVoz static frontend without changing business behavior or deeply redesigning feature areas.

**Architecture:** Keep the existing Spring Boot static HTML/CSS/JavaScript stack. Add one canonical `frontend-foundation.css` layer loaded after legacy product styles, convert the current light-console browser contract into a dark-foundation contract, and adapt only the minimum shared shell/components needed across representative pages.

**Tech Stack:** Spring Boot static resources, HTML, CSS, vanilla JavaScript, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-09-28-frontend-foundation-dark-design.md`

## Global Constraints

- Preserve the current Spring Boot static HTML/CSS/JavaScript architecture.
- Preserve backend APIs, authorization, tenant resolution, confirmation gates, billing/payment semantics, inventory semantics, phone provisioning safeguards, simulator isolation, and provider behavior.
- No real calls, WhatsApp sends, payments, deployments, destructive actions, or other external side effects.
- Keep PR Draft and base branch `chore/first-pass-engineering-system`.
- Finish only the shared visual foundation. Do not deeply redesign Dashboard, Inventory, Conversations, Settings, Billing, or other feature areas.
- No normal authenticated application panel may use pure or near-white as its primary background.
- Shared visual primitives consume semantic CSS custom properties.
- Target WCAG 2.2 AA for ordinary text and interactive contrast where applicable.
- Supported shared breakpoints: >1200px wide desktop, 981–1200px standard desktop/tablet landscape, <=980px navigation transition, <=620px compact mobile.
- Future feature CSS may adjust composition but must not become a new source of truth for generic colors, buttons, controls, cards, tables, dialogs, focus, navigation, or breakpoints.

## Review Focus

- Legacy `commercial-ui-v3.css` specificity leaves isolated white cards/dialogs after the new layer loads; representative tests must inspect multiple primitive types, not body alone.
- Bootstrap styles override dark form/button states on `index.html` or `settings.html`; tests must inspect computed styles of controls and focus treatment.
- Generic table rules create body-level horizontal overflow on compact widths; tests must distinguish contained table scrolling from page overflow.
- Simulator visual migration accidentally alters safe-mode interaction or action availability; existing simulator behavior suite must remain green.
- Reduced-motion/focus/disabled styles become visually indistinguishable in dark mode; browser assertions must pin focus visibility and disabled opacity/cursor behavior.

---

### Task 1: Replace the light-console test contract with RED dark-foundation tests

**Files:**
- Create: `e2e/frontend-foundation.spec.js`
- Modify: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: existing `mockReadyTenant(page)` behavior/patterns from `e2e/frontend-ux.spec.js`.
- Produces: executable visual contract for stylesheet ordering, dark canvas/surfaces, shared controls, focus, disabled state, and overflow.

- [ ] **Step 1: Write the failing foundation tests**

Create tests that:
- verify `/frontend-foundation.css` exists in representative pages and loads after legacy product styles;
- verify authenticated Home background is not `rgb(247, 248, 252)` and representative panel is not `rgb(255, 255, 255)`;
- verify Settings input/select and Inventory table/dialog primitives use dark surfaces;
- verify a focused interactive control exposes a visible outline or box-shadow distinct from its resting state;
- verify disabled button state is visually distinct;
- verify representative desktop/tablet/mobile routes do not create body-level horizontal overflow;
- verify Simulator body/panel surfaces are dark while safe-mode copy remains present.

Replace the old test named `ready customer console uses the light commercial design system` with a test that asserts the canonical dark foundation contract instead of the light palette.

- [ ] **Step 2: Run targeted tests to verify RED**

Run: `npx playwright test e2e/frontend-foundation.spec.js e2e/frontend-ux.spec.js --grep "dark|foundation|commercial visual"`

Expected: FAIL because `frontend-foundation.css` is not loaded and the authenticated console still resolves to the light palette.

- [ ] **Step 3: Commit the RED contract**

Commit message: `test: define dark frontend foundation contract`

---

### Task 2: Add the canonical visual foundation and wire representative product pages

**Files:**
- Create: `src/main/resources/static/frontend-foundation.css`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/settings.html`
- Modify: `src/main/resources/static/inventory.html`
- Modify: `src/main/resources/static/simulator.html`
- Modify: `src/main/resources/static/operations.html`

**Interfaces:**
- Consumes: semantic roles and token values from the spec; RED browser contract from Task 1.
- Produces: global CSS variables and shared styling for body/canvas, surfaces, typography, buttons, inputs/selects/textarea, badges, cards, messages, tables, dialogs, navigation, focus, disabled, reduced motion, and responsive spacing.

- [ ] **Step 1: Implement the semantic token layer**

Define the exact spec tokens:
`--rv-bg-canvas`, `--rv-bg-shell`, `--rv-surface-1`, `--rv-surface-2`, `--rv-surface-3`, `--rv-surface-hover`, border tokens, text tokens, accent tokens, and semantic success/warning/danger/info colors.

Map legacy variables such as `--bg`, `--surface`, `--surface-2`, `--border`, `--text`, `--muted`, `--accent`, `--accent-2`, `--success`, `--warning`, and `--danger` onto the canonical values for compatibility.

- [ ] **Step 2: Implement global primitive rules**

Add canonical rules for:
- page canvas and shell;
- generic card/panel surfaces;
- headings/body/labels/metadata;
- button variants and states;
- input/select/textarea states;
- badges/status pills;
- messages/alerts;
- table shell/header/rows;
- dialog/backdrop/close controls;
- shared topbar/navigation active/hover/focus;
- `:focus-visible`;
- `:disabled` and `[aria-disabled="true"]`;
- reduced-motion preference;
- compact responsive spacing at <=980px and <=620px.

Use deliberate load-order/specificity against `commercial-ui-v3.css` and Bootstrap. Avoid blanket `!important`; use it only where a legacy rule cannot be safely neutralized otherwise.

- [ ] **Step 3: Load the foundation last on representative routes**

Add `<link rel="stylesheet" href="/frontend-foundation.css?v=20260928-1">` after existing local/legacy styles in Home, Settings, Inventory, Simulator, and Operations.

- [ ] **Step 4: Run the foundation test suite**

Run: `npx playwright test e2e/frontend-foundation.spec.js e2e/frontend-ux.spec.js --grep "dark|foundation|commercial visual"`

Expected: PASS.

- [ ] **Step 5: Run existing representative behavior suites**

Run: `npx playwright test e2e/settings.spec.js e2e/simulator.spec.js`

Expected: PASS with no business-behavior changes.

- [ ] **Step 6: Commit**

Commit message: `feat: add canonical dark frontend foundation`

---

### Task 3: Bring divergent utility/admin surfaces into the same foundation without feature redesign

**Files:**
- Modify: `src/main/resources/static/platform.html`
- Modify: `src/main/resources/static/invite.html`
- Modify: `src/main/resources/static/phone-numbers.html`
- Modify: `src/main/resources/static/privacy.html`
- Modify: `src/main/resources/static/terms.html`
- Modify: `src/main/resources/static/data-deletion.html`
- Modify: `src/main/resources/static/sales.html`
- Modify: `src/main/resources/static/pricing.html`
- Modify: `e2e/frontend-foundation.spec.js`

**Interfaces:**
- Consumes: `frontend-foundation.css` from Task 2.
- Produces: consistent foundation loading and low-risk shared primitive adoption across remaining static pages, while allowing public marketing composition to remain purpose-specific.

- [ ] **Step 1: Extend tests for utility/legal/admin coverage**

Add assertions that:
- platform/invite/legal/phone-number pages load the foundation and do not use a white page canvas;
- public sales/pricing keep their existing content/navigation but load the foundation after their purpose-specific stylesheet where compatible;
- no page in the static page inventory causes horizontal page overflow at compact mobile width.

- [ ] **Step 2: Run the new assertions to verify RED**

Run: `npx playwright test e2e/frontend-foundation.spec.js`

Expected: FAIL on pages not yet loading the foundation.

- [ ] **Step 3: Wire remaining pages**

Add the canonical foundation stylesheet last to the remaining static pages. Remove only inline/page-local declarations that directly force obsolete light canvas/surface colors or duplicate canonical primitives; preserve feature/page composition.

- [ ] **Step 4: Run the foundation suite**

Run: `npx playwright test e2e/frontend-foundation.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `style: unify remaining frontend surfaces`

---

### Task 4: Adversarial QA, regression gates, and final checkpoint

**Files:**
- Modify only if a verified regression/finding requires a targeted fix and corresponding RED test.
- Update PR body Resume checkpoint after each meaningful block and before final CI wait.

**Interfaces:**
- Consumes: complete branch implementation from Tasks 1–3.
- Produces: exact-HEAD verification evidence and durable continuation state.

- [ ] **Step 1: Run Fast Gate**

Determine the base SHA from `chore/first-pass-engineering-system`.

Run: `bash scripts/ci/fast-gate.sh <base-sha>`

Expected: exit 0.

- [ ] **Step 2: Run targeted visual and responsive E2E**

Run: `npx playwright test e2e/frontend-foundation.spec.js e2e/frontend-ux.spec.js e2e/settings.spec.js e2e/simulator.spec.js`

Expected: all tests pass.

- [ ] **Step 3: Run the complete interaction/browser suite**

Run: `npm run test:e2e`

Expected: all tests pass.

- [ ] **Step 4: Perform adversarial review**

Inspect computed styles and branch diff specifically for:
- surviving near-white authenticated panels;
- low contrast muted text;
- focus clipping/invisibility;
- disabled controls that look active;
- nested border noise;
- dialog overflow;
- page-level horizontal overflow;
- Bootstrap/legacy specificity conflicts;
- excessive `!important`;
- simulator behavior changes;
- feature-specific regressions caused by generic selectors.

For any Critical/Important finding, add a regression test, observe RED, make the smallest fix, and rerun the affected suite before continuing.

- [ ] **Step 5: Push final implementation state and verify PR CI**

Wait for GitHub Actions attached to the exact final HEAD. Inspect failing jobs/logs if any. Use systematic debugging before changes for unexpected failures.

Expected: required Full Gate jobs green for exact final HEAD.

- [ ] **Step 6: Update the Draft PR checkpoint**

Record:
- branch;
- PR #625;
- exact HEAD;
- completed blocks;
- Fast Gate/targeted E2E/Full Gate evidence tied to that HEAD;
- blockers/rulings;
- next step = ready for integration review, with no merge/deploy performed.

- [ ] **Step 7: Final branch review**

Review the whole branch against the spec and this plan. No merge. No deploy. Keep PR Draft.
