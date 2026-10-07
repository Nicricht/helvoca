# Helvoca / RecepVoz — V1 Finish Audit

**Date:** 2026-10-07  
**Repository:** `Nicricht/helvoca`  
**Audited main:** `bbc30cad7c75c53f858e71a72edd3bd56b653474`  
**Goal:** stop treating historical branches as the product backlog and define the shortest safe path to a finished V1.

## Executive ruling

**No software P0 blocker was found in the current `main` audit.**

The product core is already broad and production-shaped:

- canonical React application;
- Spring Boot backend;
- PostgreSQL/Flyway;
- tenant isolation/security;
- bookings/agenda;
- orders/operations;
- inventory;
- voice AI;
- WhatsApp stack;
- SaaS billing state machine;
- onboarding/import;
- pilot launch cage;
- observability/readiness;
- exact-main CI and production deployment.

The shortest path to V1 is therefore **not more speculative feature development** and **not a blind review of 60 historical refs**.

The finish path is:

```text
current main
  -> close only concrete P1 launch gates
  -> rescue historical branch material only when it closes one of those gates
  -> freeze everything else
  -> final release candidate
  -> controlled first-customer activation
```

## Current certified baseline

- exact-main CI: **37595789186 — SUCCESS**
- Railway deployment: `ea122483-82f2-44d4-8b15-387197f78b05` — **SUCCESS**
- API: **Online 1/1**
- PostgreSQL: **Online 1/1**
- Railway warnings/criticals/recent failures: **0**
- Railway pending work: **0**
- open PRs at audit start: **0**
- branch count at audit start: **61**

Repository scale on this exact main:

- files: **1559**
- React/frontend files: **95**
- Java production files: **665**
- Java test files: **424**
- browser E2E specs: **46**
- Flyway migrations: **93**

## V1 capability matrix

| Capability | Status | Priority | Ruling |
| --- | --- | --- | --- |
| `AUTH_AND_SESSION` | **READY** | P0 | React AuthPage + AuthBoundary; backend AuthController/AuthService/RegistrationService; auth/role/permission tests exist. |
| `TENANT_ISOLATION_SECURITY` | **READY** | P0 | TenantProvider/TenantAwareDataSource/RLS integration coverage, JWT/permission/rate-limit tests; APIs derive tenant server-side. |
| `ONBOARDING_IMPORT_READINESS` | **READY** | P1 | Onboarding, auto-onboarding, business import preview/apply, activation guide and readiness services/controllers are present with tests. |
| `BUSINESS_CONFIG_SERVICES_HOURS_KNOWLEDGE` | **READY** | P1 | Business/service/knowledge APIs plus business hours and schedule-exception services/tests exist; Settings React surface exists. |
| `CUSTOMERS` | **READY** | P1 | Customer API exists; current product architecture embeds customer work in Agenda/Operations instead of standalone Customers page. |
| `BOOKINGS_AGENDA` | **READY** | P0 | Booking CRUD/availability/concurrency/confirmation lifecycle plus React Agenda and dedicated E2E/integration tests. |
| `ORDERS_OPERATIONS` | **READY** | P1 | Order workflow, React Orders/Operations and E2E coverage present. |
| `INVENTORY` | **READY** | P1 | Stock, variants, reservations, alerts, restock flows, concurrency tests and multiple React inventory E2E suites exist. |
| `HUMAN_HANDOFF` | **READY** | P1 | HumanHandoffService/Controller, realtime transfer tool coverage and integration tests exist. |
| `VOICE_SOFTWARE_STACK` | **READY_WITH_EXTERNAL_GATE** | P0 | Multi-provider voice edge, Twilio transport, Gemini Live/OpenAI Live, tools, fail-closed routing and extensive tests exist. Real tenant-specific carrier matrix remains an external activation gate. |
| `WHATSAPP_SOFTWARE_STACK` | **READY_WITH_EXTERNAL_GATE** | P1 | Twilio/Meta WhatsApp, tenant routing, embedded signup/config/readiness, booking/tool flows and extensive tests exist. Real tenant pilot delivery certification remains external. |
| `SAAS_BILLING_SOFTWARE` | **READY_WITH_EXTERNAL_GATE** | P1 | Plans, entitlements, usage, Mercado Pago recurring checkout/webhook reconciliation and hardening tests exist. Real Mercado Pago TEST provider round-trip remains pending. |
| `REACT_PRODUCT_SURFACES` | **READY** | P1 | Canonical React routes exist for Auth, Home, Plan, Inventory, Agenda, Orders, Settings, Import, Simulator, Internal Operations, Platform, Invite, Sales and Pricing. |
| `PILOT_SAFETY_OBSERVABILITY` | **READY** | P0 | Controlled pilot guard, GO/NO-GO, launch control/readiness, kill switches and certification/reconciliation surfaces exist with tests. |
| `DATABASE_MIGRATIONS` | **READY** | P0 | PostgreSQL/Flyway is authoritative; current tree contains 93 migrations and exact-main CI passed integration/system tests. |
| `CI_RELEASE_PIPELINE` | **READY** | P0 | Exact main CI 37595789186 completed SUCCESS; Railway exact-SHA deployment is online and healthy. |
| `VOICE_REAL_TENANT_CERTIFICATION` | **PENDING_EXTERNAL** | P1 | Controlled human call evidence exists, but the full tenant-specific carrier/interruption matrix must be completed only in an authorized launch window when voice is in scope. |
| `WHATSAPP_REAL_TENANT_CERTIFICATION` | **PENDING_EXTERNAL** | P1 | Meta pilot runbook explicitly keeps real traffic disabled until tenant/provider prerequisites and an authorized real pilot certification are completed. |
| `MERCADOPAGO_TEST_ROUNDTRIP` | **PENDING_EXTERNAL** | P1 | Local/state-machine certification is green, but real TEST recurring checkout + signed webhook/provider round-trip is not yet certified. |
| `FIRST_CUSTOMER_ACTIVATION_EVIDENCE` | **PENDING_OPERATIONAL** | P1 | Requires completed onboarding form, owners, exact sold scope, success metric, channel certification, rollback owner, payment method and tenant Launch Cage GO. |
| `MERCHANT_PAYMENT_LIVE` | **OUT_OF_V1** | DEFER | Current commercial launch status explicitly says merchant payment LIVE is not commercially available. Do not block booking-first assisted V1 on it. |
| `THIRD_VOICE_PROVIDER` | **OUT_OF_V1** | DEFER | README lists this as a future commercial milestone; Gemini Live + OpenAI Live already provide the current multi-provider architecture. |
| `FULL_SELF_SERVICE_EXTERNAL_ACTIVATION` | **OUT_OF_V1** | DEFER | Assisted controlled-pilot launch is the current sellable stage; fully self-service provider onboarding should follow real pilot evidence. |

## What is actually blocking “finished V1”

There are **four P1 launch gates**, not dozens of undefined product gaps:

1. **Voice real-tenant certification**, only if real voice is part of the sold scope.
   - software path exists;
   - controlled human-call evidence exists;
   - tenant-specific carrier/interruption matrix is still an external launch gate.

2. **WhatsApp real-tenant certification**, only if WhatsApp is part of the sold scope.
   - software stack, tenant config, readiness and safety gates exist;
   - real provider delivery must remain closed until an explicitly authorized pilot.

3. **Mercado Pago TEST round-trip**, only if automatic SaaS billing is required for V1 launch.
   - local billing logic is hardened;
   - actual TEST recurring provider checkout + signed webhook round-trip remains external and unproven.
   - assisted/manual commercial collection remains the documented bridge.

4. **First-customer activation evidence.**
   - this is operational, not a missing platform subsystem;
   - requires exact customer scope, owners, onboarding, success metric, payment method, enabled-channel certifications and Launch Cage GO.

## What must NOT block V1

Freeze these out of V1 unless a real first-customer contract explicitly requires them:

- live merchant payment collection for the business's own customers;
- a third voice provider;
- fully self-service activation of every external provider;
- speculative new dashboards/features;
- blanket resurrection of old branches;
- cosmetic refactors with no P0/P1 effect.

## Important documentation observation

Some older documents, especially README milestone lists and dated certification files, describe features as future work even though newer `main` contains their implementations. For finish decisions, **current source + current tests + current production state outrank stale milestone prose**.

This is a documentation-drift issue, not a V1 software blocker.

## Part 2 contract

Part 2 must inspect the remaining branches **only against this matrix**.

Priority order:

1. `fix/real-call-certification-regressions-v1` for unique regression coverage relevant to voice certification.
2. `cert/saas-billing-sandbox-provider-run-1` only for material that closes the Mercado Pago TEST gate.
3. `docs/recepvoz-engineering-operating-system` only if it adds a release-critical guard; otherwise it cannot delay V1.
4. The remaining 53 runtime branches are queried by capability/gap, not reviewed wholesale.
5. The 3 ARCHIVE_FIRST branches remain archival unless they contain evidence required by a concrete P1.

## Definition of “finished V1”

Helvoca V1 is finished when:

- no software P0 exists;
- all P1 items in the exact sold scope are closed or explicitly excluded;
- final backend/integration/browser/security gates are green on one RC;
- exact RC/main deployment is healthy;
- one controlled tenant has complete activation evidence;
- real external effects are enabled only for the explicitly certified channels.

That is the finish line. Branch count is no longer the finish metric.
