# Operations and Conversations UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Operations and Conversations into a consistent customer-facing Helvoca experience while preserving APIs and technical diagnostics.

**Architecture:** Keep the existing static HTML/CSS/JS pages. Reuse `operations.css` as the shared dark base already loaded by both Operations and Conversations, move technical panels into native collapsed disclosure, and translate backend event codes only at render time.

**Tech Stack:** Spring Boot static resources, vanilla HTML/CSS/JavaScript, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-09-17-operations-conversations-ux-design.md`

## Global Constraints

- Do not change backend endpoints or payloads.
- Do not activate real calls, WhatsApp sends, payments or provider services.
- Keep technical event codes unchanged outside the presentation layer.
- Preserve tenant authentication behavior.

---

### Task 1: Customer-facing Operations

**Files:**
- Modify: `src/main/resources/static/operations.html`
- Modify: `src/main/resources/static/operations.css`
- Modify: `src/main/resources/static/operations.js`
- Test: `e2e/operations.spec.js`

**Interfaces:**
- Consumes: `/api/v1/operations/dashboard`, `/api/v1/operations/readiness`, `/api/v1/operations/certification`, `/api/v1/calls/{id}`, request and learning endpoints.
- Produces: existing DOM IDs plus `#technicalDiagnostics` and `#diagnosticsSummary`.

- [ ] **Step 1: Write the failing E2E expectations**
  Assert `Hoy en tu negocio`, closed `#technicalDiagnostics`, hidden provider details before expansion, and human action labels.
- [ ] **Step 2: Verify RED**
  Current main has no `#technicalDiagnostics` and renders raw action types, so these expectations fail against the pre-change page.
- [ ] **Step 3: Implement the new Operations hierarchy**
  Put metrics and business work first, move readiness/certification into `<details>`, and preserve current IDs.
- [ ] **Step 4: Translate presentation labels**
  Map call statuses, request states/priorities and call actions to human Spanish in `operations.js`.
- [ ] **Step 5: Verify JavaScript syntax and E2E contract**
  Run `node --check src/main/resources/static/operations.js` and the focused Playwright spec when CI/runtime is available.

### Task 2: Consistent Conversations

**Files:**
- Modify: `src/main/resources/static/conversations.css`
- Modify: `src/main/resources/static/conversations.js`
- Test: `e2e/conversations.spec.js`

**Interfaces:**
- Consumes: existing Operations dashboard, messaging conversations and call detail endpoints.
- Produces: same inbox DOM contract with human presentation labels and automatic initial selection.

- [ ] **Step 1: Write the failing E2E expectations**
  Assert the newest conversation auto-opens and `CREATE_BOOKING` is rendered as `Reserva creada`.
- [ ] **Step 2: Verify RED**
  Current main leaves the detail empty initially and exposes raw action types.
- [ ] **Step 3: Implement human labels and initial selection**
  Add presentation-only event mapping and open the newest available conversation after successful loading.
- [ ] **Step 4: Unify the visual language**
  Replace light hardcoded colors in `conversations.css` with the shared dark variables from `operations.css`.
- [ ] **Step 5: Verify JavaScript syntax and focused E2E contract**
  Run `node --check src/main/resources/static/conversations.js` and the focused Playwright spec when CI/runtime is available.
