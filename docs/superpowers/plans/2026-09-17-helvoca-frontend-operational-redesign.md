# Helvoca Frontend Operational Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert Helvoca into a coherent operational product where Inicio shows the daily business workspace, Configuración is a dedicated page, reservations are filterable and traceable to their originating conversation, and Operaciones contains advanced history/diagnostics instead of duplicating the home workspace.

**Architecture:** Keep the existing static HTML/JS frontend and Spring Boot APIs. Reuse current domain endpoints and add one tenant-safe read endpoint for reservation traceability so the UI never guesses by phone or timestamp. Move presentation responsibilities without duplicating operational data.

**Tech Stack:** Spring Boot, Java 21, static HTML/CSS/JavaScript, Playwright E2E, GitHub Actions, Railway.

**Spec:** `docs/superpowers/specs/2026-09-17-helvoca-frontend-operational-redesign-design.md`

## Global Constraints

- Universal multi-tenant UX; no industry-specific conditionals.
- Voice and WhatsApp share domain semantics.
- Never invent operational or payment state.
- Technical provider details stay out of the main customer flow.
- Desktop uses compact tables; mobile uses cards.
- Each independent data module fails gracefully without hiding healthy modules.
- Preserve existing authentication and authorization behavior.

---

### Task 1: Move operational workspace to Inicio

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/commercial-status.js`
- Modify: `src/main/resources/static/operations-business.js`
- Modify: `src/main/resources/static/operations-business.css`
- Modify: `src/main/resources/static/operations.html`
- Test: `e2e/home-operational.spec.js`
- Test: `e2e/operations.spec.js`

**Interfaces:**
- Consumes: `/api/v1/operations/dashboard`, `/api/v1/bookings`, `/api/v1/customers`, `/api/v1/services`, `/api/v1/commercial/orders`
- Produces: reusable `window.HelvocaBusinessWorkspace.mount(root, options)`

- [ ] Write failing E2E assertions that Inicio contains tabs Reservas/Pedidos/Solicitudes/Clientes and Operaciones does not duplicate that workspace.
- [ ] Verify the assertions fail on the current UI.
- [ ] Refactor `operations-business.js` into a reusable mountable workspace and mount it on Inicio.
- [ ] Remove the duplicate workspace markup from Operaciones.
- [ ] Run targeted frontend tests and syntax checks.
- [ ] Commit: `feat: move business workspace to home`

### Task 2: Create dedicated Configuración page

**Files:**
- Create: `src/main/resources/static/settings.html`
- Create: `src/main/resources/static/settings.css`
- Create: `src/main/resources/static/settings.js`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Test: `e2e/settings.spec.js`
- Test: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes the same existing business/services/hours/knowledge/agent/phone/billing APIs.
- Produces route `/settings.html` and consistent nav target.

- [ ] Write failing E2E for dedicated Configuración route and absence of embedded config on ready Inicio.
- [ ] Verify RED.
- [ ] Create settings shell and load the existing configuration UI inside the new page without duplicating backend APIs.
- [ ] Update navigation targets to `/settings.html`.
- [ ] Expose new static assets in SecurityConfig.
- [ ] Verify login/register and ready home still work.
- [ ] Commit: `feat: move configuration to dedicated page`

### Task 3: Add reservation filters

**Files:**
- Modify: `src/main/resources/static/operations-business.js`
- Modify: `src/main/resources/static/operations-business.css`
- Test: `e2e/home-operational.spec.js`

**Interfaces:**
- Produces client-side filters for search/date/service/status/source without new backend APIs.

- [ ] Write failing E2E for service, status and search filters.
- [ ] Verify RED.
- [ ] Add compact filter bar, dynamic service options, date/status/source filtering and result count.
- [ ] Add mobile filter layout.
- [ ] Verify filtered table rows and clear filters.
- [ ] Commit: `feat: filter reservations workspace`

### Task 4: Add tenant-safe reservation trace API

**Files:**
- Modify: `src/main/java/cl/helvoca/call/CallActionRepository.java`
- Modify: `src/main/java/cl/helvoca/operations/BusinessOperationEventRepository.java`
- Create: `src/main/java/cl/helvoca/booking/BookingTraceService.java`
- Create: `src/main/java/cl/helvoca/booking/BookingTraceController.java`
- Test: `src/test/java/cl/helvoca/booking/BookingTraceServiceTest.java`

**Interfaces:**
- Produces: `GET /api/v1/bookings/{bookingId}/trace`
- Response contains booking origin metadata plus either `callId` or `conversationId` when a real source exists.

- [ ] Write service tests proving tenant isolation and VOICE/WHATSAPP/MANUAL source resolution.
- [ ] Verify RED.
- [ ] Add repository queries by business/entity/operation source reference.
- [ ] Implement trace service/controller.
- [ ] Run targeted Java tests.
- [ ] Commit: `feat: expose reservation conversation trace`

### Task 5: Show full reservation context

**Files:**
- Modify: `src/main/resources/static/operations-business.js`
- Modify: `src/main/resources/static/operations-business.css`
- Modify: `src/main/resources/static/conversations.js`
- Test: `e2e/home-operational.spec.js`
- Test: `e2e/conversations.spec.js`

**Interfaces:**
- Consumes `GET /api/v1/bookings/{id}/trace`, existing `GET /api/v1/calls/{id}`, and existing messaging conversation detail endpoint.
- Produces deep links `/conversations.html?call=<id>` and `?whatsapp=<id>`.

- [ ] Write failing E2E showing reservation drawer summary/transcript/actions and deep link.
- [ ] Verify RED.
- [ ] Load trace when opening reservation.
- [ ] For voice, render call summary/transcript/actions.
- [ ] For WhatsApp, render message history.
- [ ] For manual, render explicit “Creada manualmente”.
- [ ] Add conversation deep-link selection.
- [ ] Verify partial failures leave basic reservation detail visible.
- [ ] Commit: `feat: show reservation conversation history`

### Task 6: Finalize Operaciones advanced view and global navigation

**Files:**
- Modify: `src/main/resources/static/operations.html`
- Modify: `src/main/resources/static/operations.js`
- Modify: `src/main/resources/static/operations.css`
- Modify: `src/main/resources/static/conversations.html`
- Modify: `src/main/resources/static/index.html`
- Test: `e2e/operations.spec.js`
- Test: `e2e/conversations.spec.js`

**Interfaces:**
- Operaciones retains advanced activity/calls/diagnostics; Inicio owns daily workspace.

- [ ] Write failing assertions for non-duplicated Operaciones and consistent navigation.
- [ ] Verify RED.
- [ ] Remove customer-facing duplication from Operaciones and keep advanced history/diagnostics.
- [ ] Ensure all navigation points to Inicio/Conversaciones/Operaciones/Configuración consistently.
- [ ] Verify responsive behavior and no technical codes in primary customer surfaces.
- [ ] Run targeted E2E + CI.
- [ ] Commit: `refactor: finalize operational navigation`

## Final Verification

- [ ] Run JS syntax checks for all modified static JS.
- [ ] Run targeted Playwright specs: home, settings, operations, conversations, frontend UX.
- [ ] Run targeted Java tests for BookingTraceService.
- [ ] Verify GitHub Actions on final SHA.
- [ ] Verify Railway deployment once.
