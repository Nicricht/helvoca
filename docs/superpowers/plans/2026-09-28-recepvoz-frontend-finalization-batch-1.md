# RecepVoz Frontend Finalization — Batch 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finalizar las primeras tres pantallas del frontend comercial de RecepVoz: Landing, Login/Registro y Onboarding, usando un único sistema visual oscuro y conservando todos los contratos funcionales actuales.

**Architecture:** Mantener el frontend HTML/CSS/JavaScript existente. Introducir una hoja canónica `recepvoz-ui.css` que posea los tokens y componentes visuales de las superficies migradas, dejando las hojas legacy activas solo para pantallas aún no migradas. Portar a este bloque los cambios ya certificados de la PR #612 en vez de reescribirlos desde cero, y retirar únicamente los overrides legacy que compitan con Landing/Auth/Onboarding.

**Tech Stack:** Spring Boot static resources, HTML5, CSS3, JavaScript vanilla, Bootstrap 5 existente, Playwright 1.55, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-28-recepvoz-frontend-finalization-v1-design.md`

## Global Constraints

- No desarrollar directamente en `main`.
- No introducir React, Vue, Tailwind ni otro framework.
- Fondo principal: `#0F1115`.
- Superficie principal: `#151922`.
- Superficie elevada: `#1B2230`.
- Texto principal: `#F5F7FA`.
- Texto secundario: `#A7B0BF`.
- Marca primaria: `#6D5DF6`.
- Marca secundaria: `#8B7CFF`.
- Éxito: `#22C55E`; advertencia: `#F59E0B`; error: `#EF4444`.
- No usar blanco puro como fondo dominante.
- No usar fondos galaxia, glassmorphism excesivo ni waveforms decorativos.
- Preservar IDs, payloads y endpoints existentes salvo necesidad demostrada.
- No realizar llamadas, mensajes, pagos ni provisioning reales.
- Validar 390 px, 768 px y 1440 px.
- Ejecutar Fast Gate durante iteración y Full Gate al cierre del bloque.
- Al terminar estas tres pantallas, detenerse y mostrar al usuario los cambios antes de continuar con Inicio/Reservas/Clientes.

## Review Focus

1. **CSS legacy cargado después o con mayor especificidad:** las tres superficies migradas deben mantener paleta y jerarquía canónicas incluso con `styles.css`, `first-user-ux-v2.css` y scripts existentes activos.
2. **JavaScript que reescribe copy/DOM después de cargar:** el Hero/Auth/Onboarding no debe volver a contenido o clases antiguas tras `DOMContentLoaded` o carga de estado.
3. **Viewport de 390 px:** logo, tabs, formularios, CTA y pasos de onboarding deben caber sin overflow horizontal.
4. **Errores de autenticación:** una respuesta 401 o validación HTML no debe ocultar formulario ni perder el mensaje humano.
5. **Estado de onboarding parcialmente configurado:** el paso siguiente debe ser único, la navegación operativa avanzada debe permanecer oculta y el progreso debe reflejar exactamente el estado real.

---

### Task 1: Canonical visual foundation for the first three screens

**Files:**
- Create: `src/main/resources/static/recepvoz-ui.css`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Create: `src/main/resources/static/recepvoz-brand-header.webp`
- Test: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: existing Bootstrap + `styles.css` layout contracts and DOM IDs in `index.html`.
- Produces: canonical CSS variables and reusable classes `.rv-shell`, `.rv-brand-link`, `.rv-brand-logo`, `.rv-surface`, `.rv-button-primary`, `.rv-button-secondary`, `.rv-field`, `.rv-status`.

- [ ] **Step 1: Write failing E2E contract for canonical theme and brand asset**

Add test `public entry uses the canonical dark design system and brand asset` asserting:
- `/recepvoz-ui.css` loads after all legacy styles on `/`;
- body background is `rgb(15, 17, 21)`;
- public primary surface is `rgb(21, 25, 34)`;
- header contains `.rv-brand-logo[src="/recepvoz-brand-header.webp"]`;
- legacy `.brand` text node is absent;
- no horizontal overflow at 390/768/1440.

- [ ] **Step 2: Run the targeted test and verify RED**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "canonical dark design system"`

Expected: FAIL because `recepvoz-ui.css` and the standalone brand asset do not yet exist.

- [ ] **Step 3: Add the canonical stylesheet and optimized logo**

Create `recepvoz-ui.css` with the exact tokens from Global Constraints and only shared primitives needed by Landing/Auth/Onboarding.

Convert the user-provided logo to `recepvoz-brand-header.webp` without stylistic alteration. Keep transparent background and constrain header rendering to approximately 52 px desktop / 44 px mobile.

Add `/recepvoz-ui.css` and `/recepvoz-brand-header.webp` to `PUBLIC_CONSOLE_ASSETS`.

Load `recepvoz-ui.css` as the final stylesheet in `index.html`.

- [ ] **Step 4: Run the targeted test and verify GREEN**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "canonical dark design system"`

Expected: PASS.

- [ ] **Step 5: Run Fast Gate**

Run:
`bash scripts/ci/fast-gate.sh 8dc165d102a4157fdc6e3cd38b51b76b70b388d1`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit:
`feat(ui): add canonical dark frontend foundation`

---

### Task 2: Finish Screen 1 — Landing

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/recepvoz-ui.css`
- Modify: `src/main/resources/static/ux-simplification.js`
- Modify: `src/main/resources/static/commercial-ui-v3.css`
- Test: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: canonical tokens and components from Task 1.
- Produces: public sections `#publicTrustStrip`, `#howItWorks`, `#publicFeatures`, `#publicPreview`, `#publicFinalCta`, plus final branded header/Hero.

- [ ] **Step 1: Port the already-certified PR #612 contract into failing tests on the implementation branch**

Assert:
- branded header;
- eyebrow `Recepcionista con IA`;
- H1 `No pierdas otra llamada.`;
- body `RecepVoz atiende a tus clientes, responde preguntas y agenda reservas mientras tú trabajas.`;
- CTA `Probar RecepVoz` → `#registerForm`;
- CTA `Ver cómo funciona` → `#howItWorks`;
- Sofía demo contains customer request, response and `Reserva creada`;
- trust strip has exactly four items.

Add new landing completion assertions:
- `#howItWorks` has exactly three steps: `Tu cliente llama`, `Sofía resuelve`, `Tu negocio continúa`;
- `#publicFeatures` exposes only the core capabilities, not provider/technical vocabulary;
- `#publicPreview` contains real product UI labels, not invented statistics;
- `#publicFinalCta` links to registration and pricing;
- public page does not contain `Twilio`, `webhook`, `tenant`, `entitlements`, `sandbox`.

- [ ] **Step 2: Run targeted Landing tests and verify RED**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "public|landing|hero|trust"`

Expected: FAIL on missing canonical sections / old public-company block.

- [ ] **Step 3: Implement the complete Landing**

In `index.html`:
- replace the old `.public-company-card` with the three-step `#howItWorks` section;
- add a compact core-feature section;
- add one operational preview;
- add one final CTA + legal footer;
- retain auth forms below/within the public flow without duplicating them.

In `ux-simplification.js`:
- remove or update only public-page DOM rewrites that conflict with the new fixed copy.

In `commercial-ui-v3.css`:
- remove only public Landing overrides now owned by `recepvoz-ui.css`; do not change dashboard/settings/inventory rules yet.

- [ ] **Step 4: Verify Landing at 390/768/1440**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "public|landing|hero|trust"`

Expected: PASS with no overflow.

- [ ] **Step 5: Run Fast Gate**

Run:
`bash scripts/ci/fast-gate.sh 8dc165d102a4157fdc6e3cd38b51b76b70b388d1`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit:
`feat(ui): finish public landing experience`

---

### Task 3: Finish Screen 2 — Login / Registration

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/recepvoz-ui.css`
- Modify: `src/main/resources/static/app.js` only if a display-state bug requires it
- Test: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: existing form IDs `#registerForm`, `#loginForm`, `#registerTab`, `#loginTab`, `#authMessage`.
- Produces: dark auth surface with unchanged registration/login payload behavior.

- [ ] **Step 1: Add failing Auth visual/behavior tests**

Extend `auth tabs and simplified registration controls are usable` and auth tests to assert:
- auth panel background uses canonical surface;
- register has exactly businessName/email/password;
- login has exactly email/password;
- one submit CTA visible per active tab;
- active tab is programmatically indicated;
- `#authMessage` remains visible after 401;
- invalid password prevents network request;
- forms do not overflow at 390 px;
- no placeholder or label uses contrast below the canonical muted text class.

- [ ] **Step 2: Run targeted Auth tests and verify RED**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "auth|registration|login"`

Expected: FAIL on visual contract before migration.

- [ ] **Step 3: Migrate Auth markup/classes to the canonical components**

Keep names, IDs, `required`, `minlength`, autocomplete attributes and JS event bindings unchanged.

Remove decorative or duplicate copy around auth. Keep:
- title/tab;
- fields;
- one CTA;
- automatic language/timezone hint;
- inline human error area.

Do not move registration to a new route in this batch.

- [ ] **Step 4: Verify payload and error regression tests**

Run:
`npx playwright test e2e/frontend-ux.spec.js -g "auth|registration|login"`

Expected: PASS, including existing payload assertions.

- [ ] **Step 5: Run Fast Gate**

Run:
`bash scripts/ci/fast-gate.sh 8dc165d102a4157fdc6e3cd38b51b76b70b388d1`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit:
`feat(ui): simplify dark authentication experience`

---

### Task 4: Finish Screen 3 — Onboarding

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/first-user-ux-v2.js`
- Modify: `src/main/resources/static/first-user-ux-v2.css`
- Modify: `src/main/resources/static/recepvoz-ui.css`
- Test: `e2e/first-user-ux-v2.spec.js`

**Interfaces:**
- Consumes: onboarding status keys `businessProfileConfigured`, `servicesConfigured`, `scheduleConfigured`, `phoneConfigured`.
- Produces: four-step guided onboarding with a single next action and no operational clutter.

- [ ] **Step 1: Add failing Onboarding contract tests**

Assert for incomplete tenant:
- `#firstUserOnboarding` visible;
- exactly four steps;
- completed steps use ✓;
- exactly one step has `.next`;
- progress text matches actual completed count;
- only `Inicio` and required setup navigation remains visually available;
- Inventory remains hidden;
- operational workspace and advanced surfaces are hidden;
- one dominant `Continuar configuración` action exists;
- no horizontal overflow at 390/768/1440.

Add partial states 0/4, 1/4, 3/4 and ready 4/4.

- [ ] **Step 2: Run targeted Onboarding tests and verify RED**

Run:
`npx playwright test e2e/first-user-ux-v2.spec.js`

Expected: at least one new visual/navigation assertion FAILS before migration.

- [ ] **Step 3: Simplify onboarding rendering and styles**

Preserve the four existing status keys. Keep `renderSteps()` as the single source for progress.

Make the UI:
- one focused surface;
- no gradients;
- dark canonical colors;
- next step visually dominant;
- completed steps quiet;
- one CTA;
- mobile steps stacked.

Remove onboarding-specific duplicated theme tokens from `first-user-ux-v2.css` where `recepvoz-ui.css` now owns them.

- [ ] **Step 4: Run the onboarding suite and verify GREEN**

Run:
`npx playwright test e2e/first-user-ux-v2.spec.js`

Expected: PASS.

- [ ] **Step 5: Run Fast Gate**

Run:
`bash scripts/ci/fast-gate.sh 8dc165d102a4157fdc6e3cd38b51b76b70b388d1`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit:
`feat(ui): focus first-user onboarding`

---

### Task 5: Remove legacy conflicts for migrated surfaces

**Files:**
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/commercial-ui-v3.css`
- Modify: `src/main/resources/static/first-user-ux-v2.css`
- Modify: `src/main/resources/static/ux-simplification.js`
- Test: `e2e/frontend-ux.spec.js`
- Test: `e2e/first-user-ux-v2.spec.js`

**Interfaces:**
- Consumes: canonical ownership established in Tasks 1–4.
- Produces: no competing theme definitions for Landing/Auth/Onboarding.

- [ ] **Step 1: Add regression test for late-style stability**

Test `migrated entry screens keep canonical colors after client scripts settle`:
- wait for the same initialization point used by the app;
- compare computed colors before and after scripts/state settle;
- H1 remains `#F5F7FA`;
- public/auth/onboarding surfaces remain canonical;
- no dynamically inserted stylesheet redefines migrated selectors.

- [ ] **Step 2: Run the regression test and verify RED if conflicts remain**

Run:
`npx playwright test e2e/frontend-ux.spec.js e2e/first-user-ux-v2.spec.js -g "canonical|late-style|onboarding"`

Expected: FAIL if any legacy selector still owns migrated visual properties; otherwise document that the test already protects the contract and proceed only with proven dead rules.

- [ ] **Step 3: Remove only proven-obsolete rules**

Delete migrated-surface color/background/typography overrides from legacy files.

Do not refactor dashboard, settings, inventory, operations, sales or pricing styles in this batch.

- [ ] **Step 4: Run both targeted suites**

Run:
`npx playwright test e2e/frontend-ux.spec.js e2e/first-user-ux-v2.spec.js`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit:
`refactor(ui): remove entry-screen style conflicts`

---

### Task 6: Certify and show Batch 1

**Files:**
- No product file changes expected.
- Evidence: PR checks, Playwright output, and visual captures if available from the execution environment.

**Interfaces:**
- Consumes: completed Tasks 1–5.
- Produces: certified three-screen checkpoint for user review.

- [ ] **Step 1: Rebase/refresh comparison against current `main`**

Verify branch is 0 commits behind before final certification. If `main` moved, update without force push and rerun affected gates.

- [ ] **Step 2: Run Fast Gate on exact final HEAD**

Run:
`bash scripts/ci/fast-gate.sh <current-main-sha>`

Expected: PASS.

- [ ] **Step 3: Perform adversarial visual review**

Check:
- 390/768/1440;
- long business name/email;
- auth 401;
- 0/4, 1/4, 3/4, 4/4 onboarding;
- delayed JS initialization;
- no provider/technical vocabulary in public flow;
- no unintended light surface.

- [ ] **Step 4: Run Full Gate**

Use the repository PR workflow on the exact final HEAD.

Expected:
- JavaScript validation PASS;
- backend + JaCoCo PASS;
- differential coverage PASS;
- browser E2E PASS.

- [ ] **Step 5: Show the user the completed three-screen batch**

Present three sections only:
1. Landing: before/after structural changes and current visual result.
2. Login/Registro: changed hierarchy, forms and error behavior.
3. Onboarding: four-step focused flow and responsive result.

Include actual screenshots when the environment can capture them; otherwise include exact DOM/CSS evidence and test results. State PR/HEAD/CI and whether any limitation prevents a visual capture.

Then STOP. Do not begin Inicio/Reservas/Clientes until the user says to continue.

- [ ] **Step 6: Commit any evidence-only documentation if needed**

Do not commit generated screenshots unless explicitly useful to the repository. Do not merge or deploy.
