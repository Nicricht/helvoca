# RecepVoz Appearance Themes Design

**Date:** 2026-09-28
**Base:** `feat/frontend-foundation-dark` @ `f9e2585a5715095269a7ad2bc7cfeace0d24ec0b`
**Branch:** `feat/frontend-appearance-themes`
**Risk:** MEDIUM

## Goal

Allow each tenant to choose one of five curated accent variants while preserving the canonical dark foundation, accessibility, semantic colors, and tenant isolation.

## Presets

- `cyan` — default: #22C7D6
- `blue` — #4F8CFF
- `emerald` — #35D399
- `violet` — #806BFF
- `amber` — #F59E42

Each preset also owns a hover, soft-background and border token. Success/warning/danger/info colors do not change with the selected preset.

## Architecture

1. Persist the selected preset on the tenant root `business.appearance_theme`.
2. `GET /api/v1/business` returns `appearanceTheme`.
3. `PUT /api/v1/business/appearance` is BUSINESS_ADMIN-only and accepts only the five supported values.
4. `appearance.js` is the single frontend applicator. It sets `data-rv-accent-theme` on `<html>`.
5. `sessionStorage` is only a visual cache so navigation/reloads do not flash the default theme. The database remains authoritative.
6. `frontend-foundation.css` exposes brand tokens and aliases existing accent tokens to them so feature CSS consumes the selected theme without parallel stylesheets.
7. Settings gains a first-class **Apariencia** section with five preview cards. Admins can persist a selection; operators can view the current choice but cannot mutate it.

## Data and tenant safety

The preference belongs to the authenticated tenant and never accepts a business id from the browser. The service resolves the business through `TenantProvider.requireBusinessId()`. The existing forced RLS on `business` remains unchanged.

Migration `V78__business_appearance_theme.sql` adds a non-null `appearance_theme` with default `cyan` and a database CHECK constraint for the five supported presets.

## Rendering behavior

The canonical default becomes Cyan Voice. On authenticated console pages:

- the tiny early script applies the cached session preset before the rest of the application renders;
- when `/api/v1/business` returns, the authoritative value replaces the cache;
- logout/session expiry clears the cache.

A first visit on a new device uses the safe default cyan until the tenant response arrives. No server-side rendering or framework migration is introduced.

## Scope

Theme changes affect:
- primary buttons/actions;
- active navigation;
- focus rings;
- links/highlights;
- voice/accent decorative elements;
- charts/components that already consume accent variables.

Theme changes must not alter:
- success;
- warning;
- danger/error;
- info states;
- WhatsApp/provider brand colors;
- business behavior.

## Acceptance criteria

- Legacy tenants with no explicit selection resolve to `cyan`.
- Only the five curated presets can be persisted.
- The theme is tenant-scoped and BUSINESS_ADMIN-only for mutation.
- Operators can read but cannot save the preference.
- Settings shows five accessible preview choices.
- Choosing a preset previews it immediately and persists only through the dedicated endpoint.
- A failed save restores the authoritative theme and shows an actionable error.
- The selected theme applies across Home, Settings, Inventory and Simulator on the same session.
- Logout/session expiry removes the cached tenant appearance.
- Semantic success/warning/danger/info colors remain unchanged between presets.
- All affected backend/unit/E2E tests and repository gates are green for the exact final HEAD.
- PR remains Draft; no merge or deploy.

## Failure cases

- unsupported theme string;
- blank/null theme;
- operator attempts mutation;
- tenant A selection leaking into tenant B;
- network failure while saving;
- stale session cache disagrees with backend;
- direct page navigation before business data loads;
- contrast regression on one preset;
- preset changes semantic status colors;
- duplicated stylesheet/theme implementation.
