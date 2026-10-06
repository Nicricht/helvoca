# Pricing React migration

**Status:** Implementation candidate / GREEN pending CI
**Branch:** `feat/react-pricing-migration`
**Base:** `main@6b4e15855c5b756c1ec897f74cf326adde01f40c`
**Risk:** MEDIUM
**FRAME CHANGE:** NO

## Decision

Migrate the public pricing surface from legacy `pricing.html + pricing.js + pricing.css` to React + TypeScript inside the existing Vite application.

Canonical route:

- `/app/pricing`

Compatibility route:

- `/pricing.html` redirects to `/app/pricing`

Pricing remains public and therefore must live outside `AuthBoundary`.

This migration does not introduce checkout, payment mutations, subscriptions or provider activation.

## Current source of truth

The current legacy page performs one read-only request:

- `GET /api/v1/public/pricing`

Backend authority already exists in:

- `PublicPricingController`
- `CommercialPlanCatalogService`
- PostgreSQL commercial plan catalog and entitlements

The public API returns:

- `code`
- `name`
- `monthlyPriceClp`
- `includedMinutes`
- `maxConcurrentCalls`
- `overagePerMinuteClp`
- `customPricing`
- `recommended`

No frontend hardcoded plan catalog may replace this endpoint.

## Current launch positioning

Repository launch notes currently define the public catalog as:

- Emprende: $24.990 CLP/month, 100 voice minutes, $149 CLP/min overage.
- Negocio: $39.990 CLP/month, 250 voice minutes, $129 CLP/min overage; recommended.
- Pro: $69.990 CLP/month, 500 voice minutes, $109 CLP/min overage.
- Enterprise: custom quote from $119.990 CLP/month.

Those values remain backend data, not React constants.

WhatsApp must remain described as a controlled pilot capability, not unlimited usage.

## Product behavior to preserve

The React replacement must:

1. be reachable directly at `/app/pricing`;
2. remain usable without an access token;
3. fetch only `GET /api/v1/public/pricing` on initial render;
4. render loading, success and failure states;
5. distinguish recommended and custom-pricing plans;
6. format CLP with Chilean formatting;
7. show included voice minutes and concurrent-call capacity;
8. show overage price when supplied;
9. preserve a path back to the public Sales experience;
10. preserve a path to configure/start a trial at `/`;
11. introduce no payment, checkout or subscription side effect;
12. remain contained at desktop, tablet and mobile widths;
13. respect `prefers-reduced-motion`;
14. keep authenticated application routes inside `AuthBoundary`.

## Visual direction

Pricing joins the current premium public product family:

- dark petroleum canvas;
- cyan product/action emphasis;
- violet reserved for AI identity;
- semantic state colors;
- restrained Framer Motion;
- clear recommended-plan hierarchy;
- responsive card/grid composition;
- no decorative gradients that fight the current product language;
- no fake customer or provider state.

The pricing page may use first-party RecepVoz visual assets only when they support comprehension.

## Routing and packaging impact

Implementation is expected to update:

- `frontend/src/app/App.tsx`
- `frontend/src/pages/Pricing/PricingPage.tsx`
- `frontend/src/pages/Pricing/PricingPage.module.css`
- `frontend/vite.config.ts`
- `src/main/java/cl/helvoca/frontend/ReactFrontendController.java`
- `src/main/resources/static/pricing.html`
- security public-asset allowlist only if legacy asset retirement makes entries obsolete
- E2E contracts and production smoke where needed

Expected retirement after GREEN:

- `pricing.js`
- `pricing.css`

The legacy HTML becomes compatibility-only.

## Security and side-effect boundary

Pricing is intentionally public.

The page may perform read-only public pricing retrieval. It must not:

- create checkout sessions;
- create payment intents;
- mutate subscriptions;
- activate Mercado Pago;
- send WhatsApp;
- place calls;
- create bookings or orders.

## RED contract

Before implementation, a dedicated Playwright contract must fail against the current branch because:

- `PricingPage` does not exist;
- `/app/pricing` is not registered in React;
- Spring does not forward `/app/pricing`;
- Vite does not emit a direct-route copy for `/app/pricing/`;
- `pricing.html` still renders the legacy page and loads `pricing.js` / `pricing.css`.

## GREEN acceptance

GREEN requires:

- focused Pricing migration tests pass;
- existing Sales/public contracts remain compatible;
- backend and database contracts remain unchanged unless a proven defect is found;
- complete repository Fast Gate and Full Gate pass on exact HEAD;
- production deployment uses the exact merged `main` SHA;
- Railway health and logs are clean;
- `Wait for CI` remains enabled.


## Part 2 implementation checkpoint

The React implementation now reads the existing public pricing catalog, adds the canonical public route, keeps legacy compatibility, retires the legacy Pricing JS/CSS, updates Sales links and production smoke expectations, and adds direct-route coverage. No backend business rule or database migration was introduced.
