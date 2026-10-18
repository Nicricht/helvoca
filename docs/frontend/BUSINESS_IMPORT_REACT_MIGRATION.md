# Business Import → React migration audit

## Decision

`business-import.html` is a unique onboarding/configuration capability and must not be deleted or collapsed into Inventory.

Target React route: `/app/settings/import`.

Reasons:
- backend namespace is `/api/v1/onboarding/import/**`;
- the flow spans catalog products, service catalog and optional inventory initialization;
- it is restricted to `BUSINESS_ADMIN` at the controller boundary;
- Settings already advertises this capability as business setup/import;
- Inventory may keep a contextual shortcut, but Inventory is not the owner of service imports or general business onboarding;
- the route should remain a secondary configuration surface, not a new primary navigation item.

Legacy `/business-import.html` becomes compatibility-only after React parity is proven.

## Current capabilities

### Authentication and authorization
- document requires an existing app session;
- `GET /api/v1/auth/me` determines roles;
- backend preview/apply endpoints require `ROLE_BUSINESS_ADMIN`;
- non-admin users must not be able to execute preview/apply.

### Business context
- `GET /api/v1/business` provides the business name used by the importer.

### Preview phase
- `POST /api/v1/onboarding/import/preview`;
- multipart request with `businessName` and up to 12 files;
- accepted UX formats: CSV, TSV, XLS/XLSX, PDF, JPEG, PNG, WEBP;
- maximum 10 MB per file;
- spreadsheet parsing is preferred when possible;
- semantic/AI analysis is reserved for supported unstructured sources;
- preview may classify products, services, historic sales, receipts, customers, mixed data or unknown data;
- preview is non-authoritative and must not mutate catalog, services, stock, orders, customers or payments.

### Review phase
- user can choose which detected items to apply;
- user may edit kind, name, description, category, duration, SKU, price and stock;
- SERVICE requires positive integer duration;
- SERVICE must not send SKU or stock;
- PRODUCT may carry SKU and stock;
- blank price/stock remain unknown instead of fabricating values.

### Apply phase
- `POST /api/v1/onboarding/import/apply`;
- requires explicit user action after preview/review;
- max 500 items;
- duplicate names/SKUs fail closed;
- existing products are matched by SKU/name and updated instead of blindly duplicated;
- existing product inventory is not overwritten when stock was not supplied;
- provided product stock may configure authoritative inventory;
- services create/update the service catalog and never configure inventory;
- historic sales/receipts are not converted into real orders or payments.

## Safety / invariants

Risk: HIGH because apply mutates catalog/service truth and may initialize inventory.

Preserve:
- tenant isolation;
- inventory integrity;
- explicit preview → review → apply separation;
- no automatic apply after upload or preview;
- no fabricated stock, price, orders, payments or customers;
- SERVICE cannot mutate inventory;
- existing inventory remains unchanged when imported stock is absent;
- backend remains authoritative for validation/conflicts;
- browser QA must mock import endpoints and must not trigger real external effects.

## Frontend ownership

FRAME CHANGE: NO.

Canonical owner: Settings / business onboarding.

Route:
- canonical: `/app/settings/import`;
- legacy compatibility: `/business-import.html` → canonical route;
- Settings CTA points to canonical route;
- Inventory contextual CTA points to canonical route;
- no new top-level sidebar item.

## Legacy assets after parity
Retire:
- `business-import.js`;
- `business-import.css`.

Keep `business-import.html` only as a minimal compatibility redirect.

## RED-first acceptance contract

1. Authenticated BUSINESS_ADMIN can open `/app/settings/import`.
2. Upload + Analyze triggers preview only.
3. Preview results remain editable before apply.
4. SERVICE keeps duration and never sends SKU/stock.
5. Product with blank stock sends `onHand: null`.
6. Apply endpoint is called only after explicit `Importar al negocio` action.
7. Result counts are rendered from backend truth.
8. Non-admin users cannot use the importer.
9. `/business-import.html` redirects to React and does not load retired JS/CSS.
10. Settings and Inventory links point directly to the canonical React route.
11. Direct route build/controller support works for both slash variants.
12. Normal primary navigation remains unchanged.


## Approved-file profile and weekly-hours proposals · 2026-10-18

- New `businessFacts` is a **preview-only** optional payload from the already quota-guarded PDF/image semantic provider. Only directly documented public phone, email, URL, address, city/commune/region, public description and complete ISO-week opening/closing intervals may be suggested. Unknown fields remain null. Existing CSV/XLSX local import and its cost-free catalog behavior remain unchanged.
- A scanned document never mutates business profile, schedule or catalog until the authenticated `BUSINESS_ADMIN` independently clicks **Confirmar y guardar perfil**, **Confirmar y guardar horarios**, and/or the existing **Importar al negocio**. All are separate controlled writes through existing endpoints. General profile writes first read the current profile, preserve omitted fields and stop on failed read; hours cannot overwrite a previously configured weekly calendar. No phone purchase, schedule by specialist, provider/live connection, medical record or patient data is inferred.
- Service durations absent from the documents remain **unknown** and must be supplied by the owner before service apply. A phrase such as “sábado hasta las 14:00” never justifies inventing Saturday opening time. The model must treat attachments as untrusted data and never obey embedded instructions. A provider API key alone does not grant permission to spend: quota, provider usage evidence, plan entitlement and global AI import opt-in stay fail-closed by default.
- Acceptance fixture: Centro Médico Aurora Sur synthetic example in the project conversation: four service names; Santiago/Providencia; weekdays 08:00–20:00; Saturday closing 14:00 with unconfirmed opening; prices, physician schedules, phone and durations unknown. This text is not verified customer identity, real clinic connection or real appointment. Test harnesses must mock provider; live PDF analysis requires a separately approved isolated tenant and spending quota.
- QA on final commit required: existing importer Java & browser gates + added parse/sanitization and user approval/overwrite regressions, exact SHA Full Gate, no production/real provider operation. Any unverified coverage is BLOCKED and may not be reported as certified.

- A document containing only valid public profile fields or complete opening hours, without any recognizable catalog service, still yields a useful `businessFacts` preview. This is independent of catalog import; it must not save automatically or require inventing an item to proceed. Browser and mocked-provider tests cover that case.
