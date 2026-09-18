# Helvoca Frontend Operational Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert Helvoca into a customer-facing operational workspace where Inicio shows real business activity, Configuración is a dedicated screen, reservations are filterable, and every reservation can show the conversation/transcript that created it.

**Architecture:** Keep the current static HTML/JS frontend and existing REST backend. Reuse existing APIs wherever possible, add one tenant-safe read endpoint for booking context, and avoid framework rewrites. Separate daily operations from advanced diagnostics while preserving current backend behavior.

**Tech Stack:** Spring Boot 4.1, Java 21, PostgreSQL/JPA, static HTML/CSS/JavaScript, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-09-17-helvoca-frontend-operational-redesign-design.md`

## Global Constraints

- Inicio answers “qué está pasando ahora”.
- Conversaciones answers “qué dijeron los clientes”.
- Operaciones answers “qué hizo Helvoca y cuál es el historial operativo”.
- Configuración answers “cómo quiero que trabaje Helvoca”.
- No hardcode industries.
- Never invent operational data.
- Voice and WhatsApp remain tenant-scoped and read-only for this redesign.
- Do not activate providers, certification flags, calls, WhatsApp sends, or payments.
- Desktop uses compact tables; mobile uses cards.
- Each module fails independently where practical.

---

### Task 1: Make Inicio the daily operational workspace

**Files:**
- Modify: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/home-business.js`
- Create: `src/main/resources/static/home-business.css`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Modify: `e2e/home-operational.spec.js`

**Interfaces:**
- Consumes: `GET /api/v1/bookings`, `/api/v1/customers`, `/api/v1/services`, `/api/v1/commercial/orders`, existing global `api()`.
- Produces: home tabs `bookings|orders|requests|customers`, tables/cards, detail drawer.

- [ ] Write E2E assertions that Inicio renders the operational tabs and booking/order/customer data.
- [ ] Verify the test fails because the workspace is absent.
- [ ] Add `home-business.css/js` and static workspace containers to Inicio.
- [ ] Load each data source independently with `Promise.allSettled`.
- [ ] Verify syntax and targeted E2E behavior.
- [ ] Commit.

### Task 2: Create dedicated Configuración screen and remove config clutter from Inicio

**Files:**
- Create: `src/main/resources/static/settings.html`
- Create: `src/main/resources/static/settings-page.js`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/ux-simplification.js`
- Modify: `src/main/java/cl/helvoca/security/SecurityConfig.java`
- Modify: `e2e/frontend-ux.spec.js`

**Interfaces:**
- Consumes: existing `app.js`, `phone-provisioning.js`, `commercial-status.js`, `voice-selector.js`, `ux-simplification.js`.
- Produces: `/settings.html` with the existing forms and APIs; Inicio nav points there.

- [ ] Add failing E2E assertions for `/settings.html` and absence of manual configuration on ready Inicio.
- [ ] Create settings page from the existing authenticated console markup.
- [ ] Add settings-specific script that forces the configuration shell open and hides operational home-only content.
- [ ] Point all Configuración navigation to `/settings.html`.
- [ ] Verify existing forms still use current IDs and backend APIs.
- [ ] Commit.

### Task 3: Add reservation filters

**Files:**
- Modify: `src/main/resources/static/home-business.js`
- Modify: `src/main/resources/static/home-business.css`
- Modify: `e2e/home-operational.spec.js`

**Interfaces:**
- Produces filter state: `query`, `date`, `serviceId`, `status`, `source`.
- Filters only client-side data already loaded from backend.

- [ ] Add failing E2E test for service/status/search filtering.
- [ ] Render compact filter controls above reservations.
- [ ] Populate service options from real service catalog.
- [ ] Implement date buckets, service/status/source filters, search and “Mostrando X de Y”.
- [ ] Add mobile filter layout.
- [ ] Commit.

### Task 4: Add tenant-safe booking context API

**Files:**
- Modify: `src/main/java/cl/helvoca/call/CallActionRepository.java`
- Create: `src/main/java/cl/helvoca/booking/BookingContextService.java`
- Create: `src/main/java/cl/helvoca/booking/BookingContextResponse.java`
- Modify: `src/main/java/cl/helvoca/booking/BookingController.java`
- Test: `src/test/java/cl/helvoca/booking/BookingContextServiceTest.java`

**Interfaces:**
- New endpoint: `GET /api/v1/bookings/{id}/context`
- Response:
  - `channel`: `VOICE|WHATSAPP|MANUAL|null`
  - `sourceReferenceId`: call/conversation UUID when available
  - `call`: existing `CallDetailResponse` when voice
  - `whatsapp`: existing `ConversationDetail` when WhatsApp
  - `events`: operation event history for the booking operation

- [ ] Write failing unit tests for VOICE, WHATSAPP and MANUAL booking contexts.
- [ ] Add repository query scoped by businessId/entityType/entityId.
- [ ] Implement context resolution without guessing by phone/time.
- [ ] Add controller endpoint.
- [ ] Verify tenant isolation through existing query services and booking lookup.
- [ ] Commit.

### Task 5: Show full reservation dossier in Inicio

**Files:**
- Modify: `src/main/resources/static/home-business.js`
- Modify: `src/main/resources/static/home-business.css`
- Modify: `e2e/home-operational.spec.js`

**Interfaces:**
- Consumes: `GET /api/v1/bookings/{id}/context`.
- Produces drawer sections: booking facts, summary, transcript/messages, actions, history, deep link to Conversations.

- [ ] Add failing E2E test that clicking a booking shows transcript and action history.
- [ ] Load booking context only when drawer opens.
- [ ] Render VOICE transcript using existing human labels.
- [ ] Render WHATSAPP messages when channel is WhatsApp.
- [ ] Render operation history and “Ver conversación completa”.
- [ ] Render explicit manual-origin fallback without fabricated conversation.
- [ ] Commit.

### Task 6: Make Operaciones advanced-only and finish responsive consistency

**Files:**
- Modify: `src/main/resources/static/operations.html`
- Modify: `src/main/resources/static/operations-business.js`
- Modify: `src/main/resources/static/operations-business.css`
- Modify: `src/main/resources/static/conversations.html`
- Modify: `e2e/operations.spec.js`
- Modify: `e2e/conversations.spec.js`

**Interfaces:**
- Inicio owns daily bookings/orders/requests/customers.
- Operaciones retains recent calls, operational history, advanced metrics and diagnostics.

- [ ] Add failing E2E assertion that Operaciones no longer duplicates daily workspace.
- [ ] Remove the duplicated daily workspace from Operaciones.
- [ ] Keep advanced activity, call detail and diagnostics.
- [ ] Verify mobile table/card and drawer behavior through structural E2E assertions.
- [ ] Verify Conversaciones deep-link query parameters remain accepted.
- [ ] Commit.

### Task 7: Final verification

**Files:** no production changes unless a failing verification reveals a scoped regression.

- [ ] Run focused Java tests for booking context.
- [ ] Run targeted Playwright E2E for Inicio, Operaciones, Conversaciones, Configuración.
- [ ] Check JavaScript syntax for changed static scripts.
- [ ] Verify GitHub CI on final SHA.
- [ ] Check Railway production status once.
