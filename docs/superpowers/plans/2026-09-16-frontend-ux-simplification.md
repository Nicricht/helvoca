# Helvoca Frontend UX Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dense setup-first customer console with a concise operations-first dashboard and progressively disclosed configuration without changing backend contracts or safety gates.

**Architecture:** Keep the existing static Spring Boot frontend and API endpoints. Restructure `index.html` and `styles.css`, adapt `app.js` to render operational copy and disclosure summaries, compact `commercial-status.js`, and simplify `phone-provisioning.js`. Preserve all existing backend-confirmed state and explicit confirmation steps.

**Tech Stack:** Spring Boot static resources, vanilla HTML/CSS/JavaScript, Playwright E2E, Maven backend tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-frontend-ux-simplification-design.md`

## Global Constraints

- No backend schema changes or Flyway migrations.
- Do not change certification flags.
- Do not trigger real calls, WhatsApp messages, provider purchases or payments.
- Billing checkout remains explicit and provider-verified.
- Phone provisioning remains explicit and confirmed before purchase.
- No industry-specific `if restaurant`, `if clinic`, or equivalent logic.
- Preserve existing API endpoint contracts and authorization behavior.

---

### Task 1: Add failing UX regression coverage

**Files:**
- Create: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: existing `/api/v1/auth/me`, `/api/v1/business`, `/api/v1/onboarding/status`, `/api/v1/services`, `/api/v1/business/hours`, `/api/v1/knowledge`, `/api/v1/phone-numbers`, `/api/v1/ai-agent`, `/api/v1/billing/status`, `/api/v1/subscription`, `/api/v1/public/pricing`.
- Produces: browser assertions for operational headline, progressive disclosure, compact billing and phone choice tabs.

- [ ] **Step 1: Write the failing test**

Create a Playwright spec that mocks a ready tenant and asserts:

```js
await expect(page.getByRole('heading', { level: 1 })).toHaveText('Helvoca está operativa');
await expect(page.getByText('Prepara tu negocio en minutos')).toHaveCount(0);
await expect(page.locator('#readyBanner')).toBeHidden();
await expect(page.locator('#advancedPanel')).toBeVisible();
await expect(page.locator('#configBusinessPanel')).toBeHidden();
await page.getByRole('button', { name: 'Negocio y agente' }).click();
await expect(page.locator('#configBusinessPanel')).toBeVisible();
await expect(page.locator('#commercialPlans')).toBeHidden();
await page.getByRole('button', { name: 'Gestionar plan' }).click();
await expect(page.locator('#commercialPlans')).toBeVisible();
await page.getByRole('button', { name: 'Teléfono' }).click();
await expect(page.getByRole('button', { name: 'Conectar mi número' })).toBeVisible();
await expect(page.getByRole('button', { name: 'Buscar un número nuevo' })).toBeVisible();
```

- [ ] **Step 2: Run CI and verify RED**

Open a PR and wait for GitHub Actions. Expected: browser E2E fails because the operational headline, disclosure panels and compact billing/phone controls do not yet exist.

- [ ] **Step 3: Commit**

Commit message: `test: define simplified customer console UX`.

---

### Task 2: Simplify auth and dashboard hierarchy

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`

**Interfaces:**
- Consumes: existing onboarding status payload.
- Produces: `#dashboardTitle`, `#dashboardSummary`, concise `#statusGrid`, single `#nextStepBanner`, compact AI analyzer.

- [ ] **Step 1: Implement minimal markup and styles**

Change the authenticated header to contain a dynamic operational title and one concise summary. Keep the four status cards but remove duplicate readiness copy from the default flow. Reduce auth hero copy to one heading and one sentence.

- [ ] **Step 2: Implement status-driven copy**

Add `renderDashboardState(status)` in `app.js`:

```js
function renderDashboardState(status) {
    const ready = Boolean(status.readyForCalls);
    $('#dashboardTitle').textContent = ready ? 'Helvoca está operativa' : 'Termina de preparar Helvoca';
    $('#dashboardSummary').textContent = ready
        ? 'Tu negocio está listo para atender clientes.'
        : (nextStepText[status.nextStep] || 'Completa el siguiente paso para activar Helvoca.');
    $('#readyBanner').classList.add('hidden');
    $('#nextStepBanner').classList.toggle('hidden', ready);
}
```

Call it from `applyStatus`.

- [ ] **Step 3: Run JavaScript syntax checks and E2E**

Expected: operational headline assertions pass while disclosure/billing/phone assertions may still fail.

- [ ] **Step 4: Commit**

Commit message: `feat: simplify dashboard hierarchy`.

---

### Task 3: Add progressive disclosure for configuration

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`
- Verify: `src/main/resources/static/voice-selector.js`

**Interfaces:**
- Consumes: existing setup form fields and save behavior.
- Produces: disclosure buttons `Negocio y agente`, `Permisos del agente`, `Servicios`, `Horarios`, `Preguntas frecuentes`, `Teléfono`; panels including `#configBusinessPanel`.

- [ ] **Step 1: Restructure the advanced form without changing field names**

Wrap existing field groups in collapsible sections. Keep all current `name`, `id`, template and API-facing attributes so form collection remains compatible.

- [ ] **Step 2: Add disclosure behavior**

Use buttons with `aria-expanded` and `aria-controls`. Only one section needs to be open at a time. `showAdvanced()` opens the configuration hub but not every section.

- [ ] **Step 3: Add summary text**

Populate short summaries from already loaded values, such as `4 servicios`, `5 intervalos`, `1 respuesta`, and active phone count.

- [ ] **Step 4: Verify voice selector still binds to the agent voice field**

Do not rename the underlying `agentVoice` field expected by `voice-selector.js`.

- [ ] **Step 5: Run E2E**

Expected: configuration disclosure assertions pass.

- [ ] **Step 6: Commit**

Commit message: `feat: add progressive configuration disclosure`.

---

### Task 4: Compact the subscription experience

**Files:**
- Modify: `src/main/resources/static/commercial-status.js`
- Modify: `e2e/commercial-status.spec.js`

**Interfaces:**
- Consumes: current billing and subscription payloads.
- Produces: compact `#commercialStatusCard` summary and `Gestionar plan` expander; preserves `#commercialPlans`, checkout buttons and pending state.

- [ ] **Step 1: Update tests first**

Change the existing commercial E2E so plan cards are expected hidden on load and visible only after clicking `Gestionar plan`. Keep assertions that no POST occurs on load and that checkout requires confirmation.

- [ ] **Step 2: Verify RED**

Expected: existing UI shows plans immediately, so the updated test fails.

- [ ] **Step 3: Implement compact summary**

Render plan, service state and minute usage in a single compact row. Place pending checkout and plan cards inside a hidden details container toggled by `Gestionar plan`.

- [ ] **Step 4: Run E2E**

Expected: commercial tests pass and billing safety behavior is unchanged.

- [ ] **Step 5: Commit**

Commit message: `feat: compact subscription management`.

---

### Task 5: Simplify phone setup into two explicit paths

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/phone-provisioning.js`
- Modify: `src/main/resources/static/styles.css`
- Modify: `e2e/phone-provisioning.spec.js`

**Interfaces:**
- Consumes: existing manual phone form and provisioning APIs.
- Produces: `Conectar mi número` and `Buscar un número nuevo` controls with mutually exclusive panels.

- [ ] **Step 1: Update phone E2E first**

Assert that opening `Teléfono` shows both path buttons, manual connection is the default panel, provisioning search stays hidden until `Buscar un número nuevo` is clicked, and no provisioning POST occurs before explicit confirmation.

- [ ] **Step 2: Verify RED**

Expected: current phone UI displays manual and provisioning flows simultaneously.

- [ ] **Step 3: Implement path switcher**

Keep current forms and endpoint calls, but place them in mutually exclusive panels. Shorten customer-facing copy and keep provider details inside the provisioning path.

- [ ] **Step 4: Run E2E**

Expected: phone UX and confirmation-gate tests pass.

- [ ] **Step 5: Commit**

Commit message: `feat: simplify phone setup flow`.

---

### Task 6: Full verification and release

**Files:**
- No new production files required.

**Interfaces:**
- Produces: a mergeable SHA with full CI green and verified Railway deployment.

- [ ] **Step 1: Run full CI**

Expected workflow commands:

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/phone-provisioning.js
node --check src/main/resources/static/commercial-status.js
node --check src/main/resources/static/voice-selector.js
mvn --batch-mode --no-transfer-progress test
npm install --no-audit --no-fund
npx playwright install --with-deps chromium
npm run test:e2e
```

- [ ] **Step 2: Review PR diff**

Confirm no backend contract, certification flag, billing verification or provisioning confirmation was weakened.

- [ ] **Step 3: Merge only the exact green head SHA**

Use the PR head SHA as `expected_head_sha` when merging.

- [ ] **Step 4: Verify main CI**

Confirm the merge commit workflow succeeds.

- [ ] **Step 5: Verify Railway**

Confirm Railway deploys the merged main revision successfully and `/actuator/health` remains healthy. No Flyway migration check is required because no migration is introduced.
