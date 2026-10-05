# Home Premium Petrol Motion V4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace accidental serif typography with a professional sans product stack and make Home's petroleum surfaces feel continuously alive through restrained layered ambient motion.

**Architecture:** Shared typography belongs in the canonical frontend foundation because it affects the product frame. All new atmospheric visuals remain local to React Home and are implemented as non-interactive, aria-hidden layers beneath existing content. Existing data contracts, shell geometry, robot assets and operational interactions remain unchanged.

**Tech Stack:** React 18, TypeScript, CSS Modules, Framer Motion, Playwright.

**Spec:** `docs/frontend/HOME_VISUAL_ATMOSPHERE_V4.md`

## Global Constraints

- FRAME CHANGE: YES only for the shared UI font family.
- Keep protected `--rv-frame-*` geometry tokens unchanged.
- Keep sidebar, topbar, gutters, content max-width and canonical breakpoints unchanged.
- Preserve cyan/violet/emerald glows and the Home robot.
- New ambient motion loops must be 8–20 seconds and must stop for reduced-motion users.
- No operational API contract changes.
- No new runtime font CDN or third-party asset dependency.

## Review Focus

- Windows/Chrome fallback must render a sans family even when Inter is not locally installed; Task 1 tests the declared stack and browser-computed result.
- Layered background motion must never capture pointer input or sit above readable content; Task 2 tests aria-hidden ownership and z-index/pointer behavior.
- Infinite animation must stop under reduced motion; Task 2 tests computed animation state with reduced motion.
- Atmosphere must not create horizontal overflow at 1536, 768 or 390 widths; Task 3 runs viewport containment checks.
- Global typography must not move protected frame geometry across principal screens; Task 3 checks Home, Agenda, Inventory and Settings at canonical widths.

---

### Task 1: Shared professional sans typography

**Files:**
- Modify: `src/main/resources/static/frontend-foundation.css`
- Test: `e2e/frontend-visual-system-v2.spec.js`

**Interfaces:**
- Consumes: existing canonical foundation and frame tokens.
- Produces: `--rv-font-ui` and body inheritance for the shared UI.

- [ ] **Step 1: Write the failing typography contract test**
- [ ] **Step 2: Run the focused visual-system test and verify RED**
- [ ] **Step 3: Add `--rv-font-ui` and apply it to body without changing geometry**
- [ ] **Step 4: Run the focused test and verify GREEN**
- [ ] **Step 5: Commit**

### Task 2: Layered petroleum atmosphere on Home

**Files:**
- Modify: `frontend/src/pages/Home/HomePage.tsx`
- Modify: `frontend/src/pages/Home/HomePage.module.css`
- Test: `e2e/frontend-visual-system-v2.spec.js`
- Test: `e2e/dashboard-motion.spec.js`

**Interfaces:**
- Consumes: Home `.page` isolation context and existing `MotionConfig reducedMotion="user"`.
- Produces: aria-hidden `flow`, `grid`, `particles`, and `halo` visual layers.

- [ ] **Step 1: Write failing atmosphere and reduced-motion tests**
- [ ] **Step 2: Run focused tests and verify RED**
- [ ] **Step 3: Add the four decorative layers and local CSS keyframes**
- [ ] **Step 4: Preserve existing robot/glow animations and keep text stationary**
- [ ] **Step 5: Run focused tests and verify GREEN**
- [ ] **Step 6: Commit**

### Task 3: Responsive and exact-HEAD certification

**Files:**
- Modify if required by regressions only: Home/foundation files above.
- Test: existing React Home and visual E2E suites.

**Interfaces:**
- Consumes: Tasks 1–2 exact HEAD.
- Produces: merge-ready, responsive, reduced-motion-safe branch.

- [ ] **Step 1: Run targeted Home/visual/motion E2E**
- [ ] **Step 2: Verify 1536×950, 768×1024 and 390×844 containment**
- [ ] **Step 3: Verify unrelated principal screens preserve frame geometry**
- [ ] **Step 4: Run Fast Gate**
- [ ] **Step 5: Run Full Gate and inspect exact-HEAD visual evidence**
- [ ] **Step 6: Synchronize current main, re-certify if main moved, merge, then verify exact-main CI and Railway deployment**
