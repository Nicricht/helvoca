# Legacy root asset cleanup

**Status:** Audited / RED contract
**Branch:** `chore/legacy-root-cleanup`
**Base:** `main@4817c44428d377635899f59909c0c295baec8676`
**Risk:** MEDIUM
**FRAME CHANGE:** NO

## Why this cleanup exists

The public entry and authentication flow is now React at `/app/auth`.
The root `/` and `/index.html` are compatibility-only.

A runtime audit of every top-level HTML document under
`src/main/resources/static` shows that **none of the current HTML surfaces load any local legacy JavaScript bundle**.

The old root implementation files therefore no longer own product behavior. A number of them remain only because historical tests, CI syntax checks, documentation, or the Spring Security public-asset allowlist still mention them.

This cleanup removes dead runtime assets and updates those historical contracts without changing backend behavior, database schema, authentication semantics, provider configuration or canonical React routes.

## Runtime audit

Current HTML documents audited:

- `account.html`
- `business-import.html`
- `conversations.html`
- `data-deletion.html`
- `index.html`
- `inventory.html`
- `invite.html`
- `operations.html`
- `phone-numbers.html`
- `platform.html`
- `pricing.html`
- `privacy.html`
- `sales.html`
- `settings.html`
- `simulator.html`
- `terms.html`

Result:

- local legacy JavaScript loaded by those documents: **0 files**
- `styles.css` is still used by legal pages
- `frontend-foundation.css` is still used by legal/compatibility pages and the React shell
- PWA manifest/icons/service worker remain active
- `recepvoz-phone-hero.svg` remains active in React Auth

## Dead runtime assets approved for retirement

### JavaScript

- `app.js`
- `commercial-status.js`
- `business-activation-guide.js`
- `ux-simplification.js`
- `first-user-ux-v2.js`
- `phone-provisioning.js`
- `voice-selector.js`

Evidence:
- no current HTML loads these files;
- direct code-search references are limited to historical docs, tests, CI, the Spring Security allowlist, or another dead asset;
- `voice-selector.js` is referenced at runtime only by the already-dead `phone-provisioning.js`.

### CSS

- `auth-visual-refresh.css`
- `landing-motion.css`
- `dashboard-motion.css`
- `dashboard-finish.css`
- `first-user-ux-v2.css`
- `commercial-ui-v3.css`

Evidence:
- no current HTML document loads these stylesheets;
- remaining code references are test fixtures, historical docs, or the public-asset allowlist;
- active React surfaces own their styling through the React frontend and CSS modules.

### Unreferenced legacy visual assets

- `recepvoz-auth-hero.svg`
- `recepvoz-icon.svg`

Evidence:
- `recepvoz-auth-hero.svg` has no runtime consumer after Auth moved to `recepvoz-phone-hero.svg`;
- `recepvoz-icon.svg` has no repository consumer;
- PWA icons `recepvoz-icon-192.png` and `recepvoz-icon-512.png` remain active and are explicitly out of scope for deletion.

## Assets explicitly preserved

Do **not** delete in this cleanup:

- `styles.css`
- `frontend-foundation.css`
- `recepvoz-phone-hero.svg`
- `recepvoz-icon-192.png`
- `recepvoz-icon-512.png`
- `manifest.webmanifest`
- `service-worker.js`
- legal HTML pages
- compatibility redirect HTML pages

## Historical contracts that must migrate

The cleanup is not complete by simply deleting files.

Known historical references include:

- `.github/workflows/ci.yml` syntax-checking dead JS files;
- `SecurityConfig.PUBLIC_CONSOLE_ASSETS` exposing dead JS/CSS/assets;
- `e2e/auth-visual-refresh.spec.js` explicitly requesting retired CSS;
- `e2e/frontend-final-visual.spec.js` mounting dead CSS fixture layers;
- `e2e/frontend-foundation.spec.js` historical CSS fixture assumptions;
- `e2e/frontend-finish-integration.spec.js` reading `commercial-status.js`;
- `e2e/react-platform-migration.spec.js` reading legacy `app.js`;
- specialized settings/voice/phone/commercial tests that still exercise the retired implementation.

These tests must be converted to canonical React behavioral contracts or removed only when an equivalent canonical contract already exists.

Historical documentation may continue mentioning old filenames when documenting past migrations. Documentation is not a runtime dependency and does not by itself block deletion.

## Security boundary

After cleanup, `PUBLIC_CONSOLE_ASSETS` must expose only files that still exist and are intentionally public.

Deleting dead allowlist entries is security-surface reduction, not an authentication behavior change.

## CI boundary

CI must no longer run `node --check` against deleted files or execute specialized suites whose only subject is the retired root implementation.

Canonical React auth, settings, platform, PWA, navigation and full E2E suites must remain green.

## RED contract

Before implementation, the cleanup contract must fail because:

- all approved dead assets still exist;
- Spring Security still lists dead assets as public;
- CI still syntax-checks retired JS files;
- historical tests still directly request/read legacy assets.

## GREEN acceptance

GREEN requires:

1. every approved dead runtime asset is physically absent;
2. preserved compatibility/legal/PWA assets remain present;
3. no current HTML document loads a retired JS/CSS asset;
4. Spring Security no longer allowlists removed assets;
5. CI no longer syntax-checks removed JS files;
6. canonical React Auth behavior remains covered;
7. canonical React Settings/Platform behavior remains covered;
8. PWA smoke remains green;
9. full browser E2E remains green;
10. backend tests and integration E2E remain green;
11. no backend business rule or database schema change;
12. exact-head Fast Gate and Full Gate pass before merge;
13. exact merged `main` SHA deploys through Railway after CI with `checkSuites=true`.
