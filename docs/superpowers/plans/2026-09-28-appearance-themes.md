# Appearance Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add tenant-persisted curated accent variants to the canonical RecepVoz dark frontend.

**Architecture:** Persist one validated preset on `business`, expose a dedicated admin mutation endpoint, apply it through one early `appearance.js` runtime, and make `frontend-foundation.css` consume brand tokens rather than hard-coded accent colors.

**Tech Stack:** Spring Boot, JPA/Flyway, vanilla JavaScript/CSS, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-28-appearance-themes-design.md`

## Global constraints

- Base is exact certified dark-foundation HEAD `f9e2585a5715095269a7ad2bc7cfeace0d24ec0b`.
- Presets: `cyan`, `blue`, `emerald`, `violet`, `amber`.
- Default: `cyan`.
- Database is authoritative; sessionStorage is cache only.
- BUSINESS_ADMIN may mutate; BUSINESS_ADMIN and OPERATOR may read through existing business response.
- Semantic status colors never vary by appearance preset.
- No merge, deploy, calls, WhatsApp sends, payments, provisioning or destructive effects.

## Review focus

- Tenant theme update must resolve the business from the authenticated tenant, never a client-provided id.
- Existing onboarding/business updates must not accidentally reset `appearance_theme`.
- Theme cache must be cleared on logout/session expiry.
- Operators must not see an enabled persistence action.
- Each preset must preserve >=4.5 contrast on primary action text/focus-relevant surfaces.

### Task 1: RED backend contract

- [ ] Extend `BusinessServiceTest` with default/current-theme, valid update/audit, and unsupported-theme cases.
- [ ] Add controller/security test or extend an existing MVC authorization contract for BUSINESS_ADMIN-only PUT.
- [ ] Run targeted tests and observe RED for missing appearance behavior.

### Task 2: GREEN persistence/API

- [ ] Add migration `V78__business_appearance_theme.sql`.
- [ ] Add `appearanceTheme` to `Business` and `BusinessResponse`.
- [ ] Add validated `BusinessAppearanceRequest` / response and `PUT /api/v1/business/appearance`.
- [ ] Implement tenant-scoped update + audit.
- [ ] Run targeted backend tests GREEN.

### Task 3: RED frontend contract

- [ ] Add `e2e/appearance-themes.spec.js`.
- [ ] Assert default cyan, five Settings presets, instant preview, persistence payload, operator read-only behavior, cache clear, and semantic colors unchanged.
- [ ] Run targeted Playwright and observe RED for missing UI/runtime.

### Task 4: GREEN frontend runtime/UI

- [ ] Add public `appearance.js` with normalize/apply/sync/cache/clear contract.
- [ ] Load it early on authenticated customer surfaces.
- [ ] Convert `frontend-foundation.css` to brand tokens and five data-attribute preset maps.
- [ ] Add Settings appearance markup and wire it into `ux-simplification.js`.
- [ ] Reconcile theme from `GET /api/v1/business` and persist via dedicated PUT.
- [ ] Clear cache on logout/session expiry.
- [ ] Sync Inventory/Simulator business responses into the same applicator.
- [ ] Run targeted Playwright GREEN.

### Task 5: Adversarial review and certification

- [ ] Run Fast Gate against foundation HEAD.
- [ ] Run backend targeted tests, Settings/appearance/frontend foundation E2E.
- [ ] Run Full Gate for final HEAD.
- [ ] Review tenant isolation, unsupported values, failed-save rollback, stale cache, semantic colors, contrast, mobile overflow and duplicated CSS.
- [ ] Update Draft PR checkpoint with exact HEAD and CI.
