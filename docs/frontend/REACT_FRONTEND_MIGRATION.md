# React frontend migration foundation

**Status:** Proposed / implementation branch  
**Branch:** `feat/react-frontend-foundation`  
**Risk:** HIGH  
**FRAME CHANGE:** YES

## Decision

Migrate the authenticated RecepVoz application progressively from page-scoped HTML/CSS/JavaScript to React + TypeScript + Vite while keeping Spring Boot 4 / Java 21 as the backend and preserving existing REST/WebSocket contracts.

This is a strangler migration, not a big-bang rewrite.

The public/static surfaces may remain static HTML when React does not provide a concrete product or maintenance benefit.

## Why now

The authenticated frontend has outgrown the page-script model. Current examples include:

- `index.html` carrying public/authenticated responsibilities and substantial inline UI composition;
- `home-business.js` at roughly 2,800 lines;
- `settings-page.js` at roughly 1,100 lines;
- `inventory.js` at roughly 1,000 lines;
- repeated page-local handling for session token, Bearer authorization, fetch/JSON, 401 handling and logout;
- a canonical visual contract already exists, but software components are still duplicated across pages.

The goal is to preserve the product behavior while reducing duplicated UI infrastructure and making future screens easier to test and evolve.

## Target stack

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- React Hook Form + Zod where forms require schema validation
- Framer Motion for restrained micro-interactions
- Lucide React for UI iconography
- CSS variables + CSS Modules initially
- Radix primitives only where they materially improve accessible dialogs, menus, tabs or similar controls
- Playwright remains the E2E authority

No Redux by default. Add a global client-state library only if a concrete requirement cannot be handled cleanly by server state, URL state, component state or a small context.

## Architecture principles

1. Spring Boot remains the source of business truth.
2. Existing REST/WebSocket contracts are reused unless a missing contract is proven.
3. Authentication behavior is preserved during migration.
4. Server state belongs in TanStack Query rather than duplicated global state.
5. A single typed API client owns:
   - auth header;
   - JSON parsing;
   - normalized errors;
   - 401/session-expiry handling.
6. Shared UI primitives must consume the existing visual authority instead of creating another design system.
7. Feature code is organized by business area, not by giant page scripts.
8. Legacy routes remain available until the replacement passes equivalence and regression gates.
9. A migrated legacy route is retired only after exact-HEAD certification.

## Initial directory target

```text
frontend/
  src/
    app/
      router/
      providers/
      auth/
    pages/
    features/
      billing/
      bookings/
      customers/
      conversations/
      inventory/
    components/
    api/
    design-system/
    assets/
```

The exact shape may be adjusted after implementation evidence, but large all-purpose page components are explicitly out of scope.

## Deployment direction

Prefer one deployment initially:

```text
Vite build -> static bundle -> Spring Boot -> Railway
```

The implementation must inspect and preserve the current Docker/Spring static-resource/security model before changing build output. A separate frontend service is not introduced without a demonstrated need.

## Visual authority

The migration must consume, not discard:

- `docs/frontend/VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`
- `docs/frontend/FRAME_CONTRACT.md`
- `docs/frontend/COMPONENT_CONTRACTS.md`
- `src/main/resources/static/frontend-foundation.css`

The long-term goal is a true software component system backed by the existing product contracts.

Product action/navigation remains cyan, AI identity may use violet, and semantic states retain semantic colors.

## Motion rules

Motion must support comprehension, not decorate every element.

Allowed examples:
- page enter/exit;
- restrained card entrance;
- counters/progress interpolation;
- drawer/dialog transitions;
- subtle ambient AI/voice illustration movement.

Requirements:
- respect `prefers-reduced-motion`;
- avoid continuous high-cost animation;
- no movement that blocks reading or interaction.

## Migration order

Pilot:
1. Plan y consumo / Facturación

Preferred subsequent sequence after the pilot is certified:
2. Conversaciones / Recepcionista IA
3. Inventario
4. Configuración
5. Clientes / Agenda
6. Inicio last

`home-business.js` is intentionally not the pilot because it is currently the largest and most operationally coupled frontend surface.

## Pilot acceptance criteria: Plan y consumo

The React pilot must:

- use the existing authenticated backend;
- read real account/plan/usage data already supported by the product;
- preserve admin/operator behavior currently covered by tests;
- support loading, ready and error states;
- handle expired/invalid session consistently;
- be usable at 1440, 768 and 390 px;
- create no real payment or checkout side effect;
- preserve tenant boundaries;
- retain a legacy fallback until replacement certification;
- pass targeted Playwright plus repository gates.

Visual composition should remain intentionally small:

1. plan summary;
2. period usage;
3. projection where derivable from real data;
4. billing state.

Do not invent invoices, payment cards, charges or provider state.

## Impact map

### UI
New React application foundation, routing, providers, API client, design-system bridge and first billing/account route.

### API/business logic
No intended business-rule changes. Existing endpoints should be consumed as-is.

### Data
No schema migration planned for foundation/pilot.

### Security
HIGH sensitivity because the new client will handle authenticated requests. Token handling and 401 behavior must remain equivalent or safer.

### Integrations
No production calls, WhatsApp sends, payment attempts, checkout or provider provisioning are required.

### Operations
Build/Docker/Spring static serving/Railway packaging may be affected. One deployment remains the preferred default.

### Continuity
Draft PR is the durable resume checkpoint. Exact HEAD and CI evidence must be updated after each meaningful block.

## Failure cases to cover

- no token / expired token;
- account API 401;
- account API 5xx;
- partial usage data;
- missing optional billing fields;
- operator role with restricted actions;
- mobile overflow;
- route refresh / direct navigation;
- build bundle not served by Spring;
- legacy path accidentally broken before replacement certification;
- duplicated requests on rerender;
- motion disabled by user preference.

## First implementation block

Before migrating Plan y consumo:

1. establish the minimal Vite + React + TypeScript project;
2. add Router and QueryClient;
3. add the central API/auth bridge;
4. add a minimal AppShell that consumes the canonical frame contract;
5. integrate build output with the current Spring Boot packaging without creating a second Railway service;
6. add a smoke E2E proving the React route can load under the authenticated application boundary.

Only after that foundation is green should `account.html/account.js/account.css` begin migration.
