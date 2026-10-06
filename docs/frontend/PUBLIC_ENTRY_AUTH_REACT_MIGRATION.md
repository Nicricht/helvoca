# Public Entry / Authentication React migration

**Status:** Implementation candidate / GREEN pending CI
**Branch:** `feat/react-public-entry-migration`
**Base:** `main@32acedbaf39b69fdf269710841f1e3725d96e396`
**Risk:** HIGH
**FRAME CHANGE:** NO

## Goal

Retire the remaining legacy root console and move the public authentication entry to React + TypeScript without changing authentication semantics, registration rules, database schema, provider configuration or tenant authorization.

This is the final large customer-facing legacy shell.

## Current state

The public root `/` is still served by `src/main/resources/static/index.html`.

Current legacy surface:
- `index.html`: 709 lines / ~46 KB;
- `app.js`: 824 lines / ~35 KB;
- registration and login live in the same document as retired dashboard/configuration markup;
- the root loads six legacy JavaScript bundles:
  - `app.js`
  - `commercial-status.js`
  - `business-activation-guide.js`
  - `ux-simplification.js`
  - `first-user-ux-v2.js`
  - `phone-provisioning.js`
- it also loads multiple legacy presentation layers, including auth/dashboard-specific CSS.

Authenticated business Home is already React at `/app`.
Settings/onboarding capabilities are already React under `/app/settings`.
Agenda, Operations, Inventory, Plan, Simulator, Sales and Pricing are already canonical React surfaces.

The legacy root already redirects an authenticated session to `/app`, so keeping the authenticated dashboard inside `index.html` no longer provides product value.

## Existing backend authority

No auth backend rewrite is required.

Public endpoints remain:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

Authenticated identity check remains:
- `GET /api/v1/auth/me`

Registration authority remains:
- `RegistrationService`
- `RegisterBusinessRequest`

Login/JWT authority remains:
- `AuthService`
- `LoginRequest`

Token storage key remains:
- `helvoca_access_token` in `sessionStorage`

## Target routing

Canonical public authentication route:
- `/app/auth`

Compatibility public entry:
- `/` -> `/app/auth`
- `/index.html` -> `/app/auth`

Authenticated customer destination:
- `/app`

Platform admin destination:
- `/app/platform`

`/app/auth` must live outside `AuthBoundary`.

`AuthBoundary` and authenticated API 401 handling must send expired/unauthenticated sessions directly to `/app/auth`, not through the legacy root.

## Authentication behavior to preserve

### Registration

Fields remain intentionally short:
- business name;
- email;
- password.

Client sends:
- `adminName = businessName`
- `businessName`
- `email`
- `password`
- detected IANA timezone;
- detected browser language;
- `humanTransferPhone: null`

Password constraint remains minimum 10 / maximum 72 characters.

On success:
1. store `accessToken` in `sessionStorage` under `helvoca_access_token`;
2. navigate to `/app`.

No checkout, call, WhatsApp send, booking or order side effect belongs in public auth.

### Login

Fields:
- email;
- password.

On normal business success:
1. store access token;
2. navigate to `/app`.

On `PLATFORM_ADMIN` success:
1. store access token;
2. navigate to `/app/platform`.

Authentication errors stay visible on the form and must not destroy the current tab/inputs.

### Existing session

When `/app/auth` opens with an existing token:
- validate it with `GET /api/v1/auth/me`;
- valid business session -> `/app`;
- valid platform admin -> `/app/platform`;
- expired/invalid session -> clear token and show login.

## Public product presentation

The React auth page should retain the useful product story from the current root:
- RecepVoz brand;
- “No pierdas otra llamada.” positioning;
- phone/AI visual;
- calls, agenda and controlled WhatsApp capabilities;
- clear links to Sales and Pricing;
- premium dark petroleum visual system;
- cyan product/action emphasis;
- violet AI identity;
- motion that remains nonessential;
- responsive desktop/tablet/mobile behavior;
- `prefers-reduced-motion` support.

The page must not claim unlimited WhatsApp or guaranteed commercial results.

## Legacy retirement boundary

After GREEN, `index.html` becomes compatibility-only.

Expected root-only retirement candidates:
- `app.js`
- `auth-visual-refresh.css`
- `landing-motion.css`
- `dashboard-motion.css`

The following legacy scripts are currently loaded only by the root and should be removed only after their behaviors are proven already covered by canonical React surfaces:
- `commercial-status.js`
- `business-activation-guide.js`
- `ux-simplification.js`
- `first-user-ux-v2.js`
- `phone-provisioning.js`

Shared assets/styles used by other compatibility documents are not removed merely because the root migrates.

Legacy Java/JUnit and Playwright tests that assert the old `index.html + app.js` implementation must be migrated to behavioral React contracts rather than deleted without replacement.

## Implementation impact expected

Likely:
- `frontend/src/pages/Auth/AuthPage.tsx`
- `frontend/src/pages/Auth/AuthPage.module.css`
- `frontend/src/app/App.tsx`
- `frontend/src/app/AuthBoundary.tsx`
- `frontend/src/api/client.ts`
- `frontend/vite.config.ts`
- `src/main/java/cl/helvoca/frontend/ReactFrontendController.java`
- `src/main/resources/static/index.html`
- security public-asset allowlist cleanup
- root/auth E2E contracts
- legacy console tests that currently inspect `index.html/app.js`
- CI JavaScript validation if `app.js` is retired

No database migration is expected.

## RED contract

Before implementation, a dedicated E2E/source contract must fail because:
- `AuthPage` does not exist;
- `/app/auth` is not registered;
- `AuthBoundary` still redirects to `/`;
- API 401 handling still redirects to `/`;
- Vite does not package a direct auth route;
- Spring does not forward `/app/auth`;
- root still contains the full legacy console;
- root still loads `app.js` and other root-only scripts.

## GREEN acceptance

GREEN requires:
- public auth React contract passes;
- registration payload semantics are unchanged;
- login and platform-admin routing are preserved;
- existing-token handling is preserved;
- auth errors are usable;
- root/index compatibility redirect works;
- legacy root console scripts are no longer requested;
- desktop/tablet/mobile containment passes;
- reduced-motion passes;
- existing React Home/Settings flows remain green;
- Fast Gate and Full Gate pass on exact HEAD;
- no backend auth rule or database schema change unless a proven defect requires it;
- merged exact `main` SHA deploys through Railway only after CI;
- `checkSuites=true` remains enabled.


## Part 2 implementation checkpoint

The public entry is now implemented in React:
- `AuthPage.tsx` and `AuthPage.module.css`;
- canonical public `/app/auth` route outside `AuthBoundary`;
- root and `/index.html` reduced to compatibility-only redirect;
- existing token validation through `GET /api/v1/auth/me`;
- business login/registration -> `/app`;
- platform admin -> `/app/platform`;
- API 401 and `AuthBoundary` redirect directly to `/app/auth`;
- direct-route Vite packaging and Spring forwarding;
- legacy root scripts are no longer requested by the entry surface;
- legacy Java/root-text contracts were migrated to compatibility behavior.

The historical root-only script files remain in the repository for a later dead-asset cleanup pass so specialized historical CI references can be retired deliberately. They are no longer executed by the public root.
