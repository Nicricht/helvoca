# RecepVoz Dark Frontend Foundation — Implementation Plan

> **Execution rule:** follow First-Pass Engineering and `superpowers:test-driven-development`. Every production change must be preceded by a browser contract that is observed RED for the intended reason.

**Goal:** Turn the approved dark-foundation specification into the canonical shared visual layer consumed by the existing static RecepVoz frontend, without changing backend/business behavior or deeply redesigning feature areas.

**Architecture:** Keep Spring Boot static HTML/CSS/JavaScript. Add one final shared stylesheet, `frontend-foundation.css`, loaded after legacy/page CSS on representative product routes. It owns semantic tokens and generic visual primitives. Existing feature CSS keeps layout/composition ownership. Use load order and scoped selectors deliberately rather than building another unbounded override pile.

**Spec:** `docs/superpowers/specs/2026-09-28-frontend-foundation-dark-design.md`

**Base branch:** `chore/first-pass-engineering-system`

**Working branch:** `feat/frontend-foundation-dark`

**Risk:** MEDIUM.

## Global constraints

- No merge and no deploy.
- No real calls, WhatsApp sends, payments, provisioning, destructive production actions, or provider effects.
- Preserve all API, auth, tenant, inventory, billing, simulator-safety, and business contracts.
- Do not migrate to React/Vue/Tailwind or introduce a new frontend framework.
- Do not deeply redesign Dashboard, Onboarding/Settings, Commerce, Conversations, or Billing here.
- Keep `frontend-foundation.css` the last shared visual authority on adopted pages.
- Avoid pure/near-white primary authenticated surfaces.
- Target viewports: 390 px, 768 px, 1440 px.
- Existing functional E2E remains authoritative and must not be weakened to make the theme pass.

## Review focus

1. Legacy CSS specificity accidentally wins over the canonical foundation.
2. Public auth/marketing composition is unintentionally restyled while authenticated surfaces become dark.
3. Focus/disabled/error states become unreadable against dark surfaces.
4. Tables/dialogs create body-level horizontal overflow.
5. Simulator safety semantics or behavior changes while styling is migrated.
6. Generic selectors are so broad that later feature branches require more `!important`.
7. `frontend-foundation.css` is not publicly served by Spring Security in the real app even though static Playwright can see it.

---

### Task 1: Establish the RED browser contract

**Files**
- Create: `e2e/frontend-foundation.spec.js`
- Do not change production files in this task.

**Interfaces**
- Consumes the current HTML/CSS DOM contracts from `index.html`, `settings.html`, `inventory.html`, `simulator.html`, and `operations.html`.
- Produces an executable contract for stylesheet ownership, dark authenticated surfaces, shared controls, focus visibility, and responsive overflow.

**Step 1: Add a minimal authenticated-tenant route helper**

In the new spec, create a local helper that:
- puts `helvoca_access_token=e2e-token` in `sessionStorage`;
- stubs only the GET endpoints required for the authenticated home/settings shell;
- returns deterministic business/onboarding/agent/phone/billing values;
- never permits a real provider request.

Do not copy unrelated commercial journey setup.

**Step 2: Add failing test `canonical foundation stylesheet owns representative product routes`**

For:
- `/` with authenticated ready tenant,
- `/settings.html`,
- `/inventory.html`,
- `/simulator.html`,
- `/operations.html`,

assert:
- a stylesheet whose pathname is `/frontend-foundation.css` exists;
- it appears after local legacy/page styles;
- no page has accidental horizontal overflow at 1440, 768, or 390 px.

**Step 3: Add failing test `authenticated console uses the canonical dark surface hierarchy`**

On authenticated `/` assert computed values:
- body/canvas is not `rgb(247, 248, 252)` or white;
- representative visible card/panel is not `rgb(255, 255, 255)`;
- `--rv-accent` resolves to the foundation accent;
- ordinary text remains high contrast.

On `/settings.html` and `/inventory.html`, assert representative panels and form controls have dark computed backgrounds.

**Step 4: Add failing test `foundation exposes visible focus and disabled states`**

Using keyboard focus on representative:
- link,
- primary button,
- input/select,

assert `outline-style` is not `none` or an equivalent visible focus treatment exists.

Assert a disabled button has a visually distinct state and is not pointer-clickable.

**Step 5: Add failing test `simulator is dark without losing safe-mode copy`**

Assert:
- simulator canvas and panel are dark;
- existing safe simulator wording remains present;
- no action is taken that calls a real provider.

**Step 6: Run RED**

Command:
`npx playwright test e2e/frontend-foundation.spec.js`

Expected:
- failures because `frontend-foundation.css` does not exist/load and current authenticated/simulator light surfaces violate the new contract;
- no failure should be caused by syntax, missing fixtures, or an unstubbed unrelated network request.

If the failure is not for those reasons, fix the test until RED is meaningful.

**Step 7: Commit RED evidence**

Commit message:
`test(ui): define dark frontend foundation contract`

Update PR #625 Resume checkpoint with:
- exact RED commit SHA;
- failing assertions and reason;
- next step = implement the canonical layer.

---

### Task 2: Add the canonical visual layer and serve it safely

**Files**
- Create: `src/main/resources/static/frontend-foundation.css`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/settings.html`
- Modify: `src/main/resources/static/inventory.html`
- Modify: `src/main/resources/static/simulator.html`
- Modify: `src/main/resources/static/operations.html`
- Test: `e2e/frontend-foundation.spec.js`
- Test: existing security contract tests that cover `PUBLIC_CONSOLE_ASSETS` when applicable.

**Interfaces**
- Produces semantic CSS variables:
  - `--rv-bg-canvas`
  - `--rv-bg-shell`
  - `--rv-surface-1`
  - `--rv-surface-2`
  - `--rv-surface-3`
  - `--rv-border-subtle`
  - `--rv-border-default`
  - `--rv-text-primary`
  - `--rv-text-secondary`
  - `--rv-text-tertiary`
  - `--rv-accent`
  - `--rv-accent-hover`
  - `--rv-success`
  - `--rv-warning`
  - `--rv-danger`
  - `--rv-info`.
- Consumed by later frontend feature branches. Do not rename these casually after this task.

**Step 1: Implement the smallest shared stylesheet that can satisfy the RED contract**

Use the approved palette from the spec. Define:
- canvas/surface hierarchy;
- text colors;
- border/radius/spacing variables;
- shared button variants;
- inputs/select/textarea;
- cards/panels;
- tables;
- badges/messages;
- dialog/backdrop;
- navigation hover/active/focus;
- disabled state;
- `:focus-visible`;
- `prefers-reduced-motion`.

Scope authenticated-home rules so the public auth/marketing surface is not accidentally flattened.

Prefer existing generic selectors already used by the product. Add narrowly scoped compatibility selectors only where legacy CSS wins.

**Step 2: Publicly serve the stylesheet**

Add `"/frontend-foundation.css"` to `SecurityConfig.PUBLIC_CONSOLE_ASSETS`.

Do not change authorization behavior beyond allowing this static asset.

**Step 3: Load it last on representative product pages**

Add:
`<link rel="stylesheet" href="/frontend-foundation.css?v=20260928-1">`

after existing local styles in:
- `index.html`;
- `settings.html`;
- `inventory.html`;
- `simulator.html`;
- `operations.html`.

Do not reorder JavaScript.

**Step 4: Run the targeted GREEN test**

Command:
`npx playwright test e2e/frontend-foundation.spec.js`

Expected: all foundation tests pass.

If a current feature stylesheet still wins, fix the smallest selector in `frontend-foundation.css` or, when clearly safer, remove the conflicting light-only declaration from the owning legacy CSS. Record that choice in the PR checkpoint.

**Step 5: Run affected existing browser tests**

Commands:
- `npx playwright test e2e/first-user-ux-v2.spec.js`
- `npx playwright test e2e/frontend-ux.spec.js`

Expected:
- functional tests stay green;
- any test that explicitly asserts the obsolete light palette must be intentionally updated to the new approved dark contract, not deleted or weakened.

**Step 6: Run Fast Gate**

Command:
`bash scripts/ci/fast-gate.sh 8483e21ae0c5aeb8d5af61e9a6caa292d73e1050`

Expected: PASS.

**Step 7: Commit**

Commit message:
`feat(ui): establish canonical dark frontend foundation`

Update the Resume checkpoint with exact HEAD and all evidence valid for it.

---

### Task 3: Normalize representative component states and responsive behavior

**Files**
- Modify: `src/main/resources/static/frontend-foundation.css`
- Modify only when necessary:
  - `src/main/resources/static/commercial-ui-v3.css`
  - `src/main/resources/static/simulator.css`
- Modify: `e2e/frontend-foundation.spec.js`

**Interfaces**
- Consumes the token/component contract from Task 2.
- Produces stable responsive and accessibility behavior for later feature branches.

**Step 1: Add RED regressions one behavior at a time**

Add separate tests for:
1. mobile navigation stays usable and page width does not overflow at 390 px;
2. a representative table scrolls within its container instead of expanding the page;
3. a representative dialog remains inside the mobile viewport;
4. muted text is readable and status is accompanied by text;
5. focus ring is not clipped on navigation and form controls;
6. simulator chat/composer is usable at 390 px.

Run each test before implementing its fix and confirm the expected RED.

**Step 2: Implement minimal responsive/accessibility fixes**

Keep breakpoints aligned with the spec:
- <= 980 px navigation transition;
- <= 620 px compact mobile.

Do not redesign feature information architecture.

**Step 3: Run targeted GREEN**

Command:
`npx playwright test e2e/frontend-foundation.spec.js`

Expected: PASS.

**Step 4: Run affected existing E2E**

Commands:
- `npx playwright test e2e/first-user-ux-v2.spec.js`
- `npx playwright test e2e/frontend-ux.spec.js`

Expected: PASS.

**Step 5: Commit**

Commit message:
`fix(ui): harden dark foundation responsive states`

Update Resume checkpoint with exact HEAD.

---

### Task 4: Adversarial review and foundation certification

**Files**
- No implementation change unless a defect is found.
- If a reproducible defect is found, add a failing regression first, fix it, rerun all affected evidence, and commit separately.

**Step 1: Adversarially inspect the final diff**

Check explicitly:
- legacy white surfaces remain;
- CSS specificity wars or unnecessary `!important`;
- public auth regression;
- hidden/disabled controls that still look active;
- low-contrast secondary text;
- table/dialog body overflow;
- focus visibility;
- simulator safe-mode behavior;
- static asset authorization;
- any unexpected Java/backend behavioral change.

**Step 2: Run Fast Gate on final implementation HEAD**

Command:
`bash scripts/ci/fast-gate.sh 8483e21ae0c5aeb8d5af61e9a6caa292d73e1050`

Expected: PASS.

**Step 3: Run targeted browser suite**

Commands:
- `npx playwright test e2e/frontend-foundation.spec.js`
- `npx playwright test e2e/first-user-ux-v2.spec.js e2e/frontend-ux.spec.js`

Expected: PASS.

**Step 4: Run Full Gate**

Use the repository CI/Full Gate for the exact final HEAD. Required final evidence:
- full backend suite green;
- differential coverage gate green if applicable;
- JavaScript validation green;
- complete Playwright suite green.

Do not reuse CI from an earlier commit.

**Step 5: Final PR #625 checkpoint**

Record:
- final HEAD;
- completed blocks;
- exact workflow run(s);
- Fast Gate result;
- targeted Playwright result;
- Full Gate result;
- blockers/rulings;
- next step: foundation is safe to consume from dependent frontend branches;
- explicit safety statement: no merge, no deploy, no real external effects.

Do not merge.

---

## Handoff to dependent frontend branches

Only after Task 4 is certified:

1. Resolve the exact final HEAD of `feat/frontend-foundation-dark`.
2. Create dependent branches from that exact ref, including `feat/frontend-onboarding-settings-finish`.
3. Their feature-specific CSS may add composition, but must consume this foundation and may not introduce a new generic palette, button/form/card/table/dialog/focus/navigation system.
4. If the foundation HEAD changes after a dependent branch starts, explicitly compare/rebase/merge as appropriate and invalidate stale dependent verification before claiming completion.
