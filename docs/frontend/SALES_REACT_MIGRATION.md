# Public Sales Landing → React migration audit

## Decision

`sales.html` is a public marketing surface with no authenticated or tenant-owned behavior. It should migrate to a dedicated public React route:

- canonical route: `/app/sales`
- legacy compatibility: `/sales.html` redirects to `/app/sales`
- route remains outside `AuthBoundary`
- root `/` remains the existing login / account bootstrap surface
- `pricing.html` remains unchanged in this PR

FRAME CHANGE: NO.

This is a public-surface redesign, not a tenant application frame change. Protected business AppShell tokens and geometry stay untouched.

## Risk

MEDIUM.

The work is primarily visual, but it changes a production public route, direct-route packaging, commercial links and CI smoke expectations.

## Product goals

The current sales page is structurally correct but visually static. The React version should communicate that RecepVoz is actively working for a business rather than presenting a flat brochure.

The canonical experience should feel:
- premium;
- dark petroleum;
- cyan + violet AI accents;
- calm rather than noisy;
- visibly alive through restrained motion;
- image-rich using existing RecepVoz visual assets;
- responsive and keyboard accessible;
- safe under prefers-reduced-motion.

## Content truth that must remain

Do not invent conversion rates, customer counts, ROI percentages, uptime promises or provider capabilities.

Preserve these product truths:
- voice and WhatsApp are the core channels described commercially;
- business information is configured before activation;
- RecepVoz can support reservations, orders, quotations, leads, delivery and requests when those capabilities are enabled;
- the demo/pilot flow is controlled;
- WhatsApp availability depends on business configuration;
- public starting price remains linked to the pricing surface;
- CTA into the existing root registration/login surface remains available.

## Visual composition

### Hero
- retain the commercial promise: “Que una llamada o un WhatsApp sin responder no se convierta en un cliente perdido.”
- show an animated product vignette instead of a static two-row card;
- use the existing `/app/assets/home/hero-bot.webp` asset;
- show live-looking but clearly illustrative call/message activity;
- CTA: “Probar con mi negocio” → `/`;
- pricing CTA → `/pricing.html`.

### Product work in motion
Use existing first-party visual assets:
- agenda → `/app/assets/home/agenda.webp`;
- orders → `/app/assets/home/orders.webp`;
- inventory → `/app/assets/home/inventory.webp`;
- automation → `/app/assets/home/automation.webp`.

These cards explain concrete outcomes, not fictional metrics.

### How it works
Keep the safe progression:
1. configure business;
2. choose allowed actions;
3. test;
4. activate pilot.

### Business fit
Keep agenda-oriented and sales/request-oriented examples.

### Final CTA
Preserve the launch message and starting-price path without implying guaranteed results.

## Accessibility / motion

- semantic header/nav/main/footer;
- one H1;
- visible focus treatment;
- meaningful image alt text;
- decorative layers aria-hidden;
- no content that exists only through animation;
- `prefers-reduced-motion` disables continuous motion and reduces entrance movement;
- mobile navigation remains usable without horizontal overflow.

## Routing / compatibility

- add public React route `/sales` before protected routes in `App.tsx`;
- add direct-route build copy for `/app/sales/`;
- Spring forwards `/app/sales` and trailing slash;
- `sales.html` becomes compatibility redirect;
- retire `sales.css` after parity;
- keep `/sales.html` in Spring public allowlist;
- add `/app/sales` to interaction audit route inventory.

## CI / smoke

Existing production sales smoke currently greps rendered marketing text directly from legacy HTML. After React migration it must instead verify:
- legacy compatibility document points to `/app/sales`;
- canonical `/app/sales` returns the React shell;
- the bundled source contains the canonical commercial marker used by the page build.

Browser E2E remains the authoritative rendered-journey evidence.

## RED-first acceptance contract

1. `/app/sales` renders without an access token;
2. no authenticated AppShell/sidebar appears;
3. one H1 contains the existing core commercial promise;
4. primary CTA points to `/`;
5. pricing CTA points to `/pricing.html`;
6. visual product cards use existing agenda/orders/inventory/automation assets;
7. page visit produces zero non-GET `/api/v1/**` requests;
8. legacy `/sales.html` redirects to `/app/sales`;
9. legacy document no longer loads `sales.css`;
10. page remains contained at desktop, compact and mobile widths;
11. reduced-motion users do not depend on animation for content;
12. title and description are set for the canonical React route;
13. authenticated business routes remain inside `AuthBoundary`;
14. no pricing/backend/external-provider mutation is introduced.
