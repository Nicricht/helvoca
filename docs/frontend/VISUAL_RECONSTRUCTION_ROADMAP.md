# RecepVoz Visual Reconstruction Roadmap

**Status:** Product/engineering execution plan  
**Input authority:** approved visual references + canonical frontend documentation  
**Baseline:** frozen frontend RC `3d73320848c7d81956701af6eb60f516f9eb1c80`  
**Rule:** Do not modify the frozen RC directly.

## 1. Objective

Close the gap between the functionally certified frontend and the approved visual product references without changing business rules or inventing unsupported backend behavior.

The work is a **visual reconstruction**, not another theme layer.

Success means the principal screens reproduce the reference family in:

- composition;
- shell;
- hierarchy;
- density;
- typography;
- accent roles;
- component language;
- responsive behavior;
- visual polish.

## 2. Branch strategy

Create a dedicated implementation branch from the frozen RC, for example:

`feat/frontend-reference-reconstruction`

Draft PR only during implementation.

Do not:

- work on `main`;
- mutate the frozen RC branch;
- merge/deploy merely to inspect appearance;
- pull PR #637 Appearance Themes into this work.

## 3. Work order

The order is deliberate because later screens depend on earlier shared decisions.

### Phase 0 — Visual evidence harness

Before production CSS changes:

- define deterministic mocked states for each principal route;
- add screenshot capture at 1440 × 900, 768 × 1024 and 390 × 844;
- produce a contact sheet for desktop screens;
- store evidence as CI artifacts rather than committing generated screenshots unless explicitly desired;
- add stable measurements for shell geometry, overflow and clickability.

Deliverable: reproducible current-state baseline.

### Phase 1 — Canonical shell

Files likely involved:

- `frontend-foundation.css`;
- shared header/nav HTML on product routes;
- page-specific shell overrides.

Target:

- 220 px desktop sidebar;
- compact product brand;
- stable utility topbar;
- unified seven-area navigation;
- consistent page content origin;
- cyan active state;
- no brand/nav overlap;
- responsive transition at <= 980 px.

Gate:

- Home, Conversations, Inventory, Settings, Account and Simulator display the same shell;
- desktop/tablet/mobile screenshots pass shell criteria.

### Phase 2 — Public landing/auth

Likely files:

- `index.html`;
- `styles.css`;
- marketing/auth-specific shared CSS;
- only minimal JS if behavior requires it.

Target:

- hero matching approved composition;
- readable large headline;
- product navigation;
- primary/secondary CTA hierarchy;
- voice/phone product visualization;
- trust/value row;
- login/register secondary to product promise.

Do not invent unsupported claims.

Gate:

- desktop hero composition visually comparable to reference;
- mobile hero remains coherent;
- auth functions unchanged.

### Phase 3 — Inicio

Likely files:

- `index.html`;
- `home-business.css`;
- `dashboard-finish.css`;
- `home-business.js` only for rendering/layout hooks, not business-rule changes.

Target:

- four KPI cards;
- calls/citas chart;
- recent conversations;
- clean title/date hierarchy;
- empty/error/loading states maintain composition.

Gate:

- screenshot comparison + existing dashboard E2E.

### Phase 4 — Conversaciones

Likely files:

- `conversations.html`;
- `conversations.css`;
- `conversations.js` only where markup/class structure needs visual support.

Target:

- master/detail;
- compact list;
- channel tabs/search/filter;
- selected row;
- readable chat/transcript;
- detail header actions.

Gate:

- desktop list/detail proportions;
- mobile list-to-detail behavior;
- conversation functional E2E unchanged.

### Phase 5 — Agenda

First decide whether Agenda remains a Home workspace or becomes its own route. Do not visually implement both.

Target:

- day/week scheduling workspace;
- time rail;
- appointment blocks;
- month/calendar context;
- selected appointment detail;
- intentional mobile day/list mode.

If existing backend/UI cannot express the full reference calendar, document the delta rather than create fake appointment interactions.

### Phase 6 — Clientes

Target:

- operational list/table;
- customer search;
- meaningful last-interaction context;
- detail treatment consistent with Conversations/Inventory.

Avoid dashboard-style card mosaics.

### Phase 7 — Inventario

Likely files:

- `inventory.html`;
- `inventory.css`;
- `frontend-foundation.css` only for shared component corrections.

Target:

- compact toolbar;
- table-first layout;
- strong product column;
- price/stock/status scanning;
- clear Add Product action;
- responsive containment.

Functional inventory invariants remain untouched.

### Phase 8 — Configuración

Likely files:

- `settings.html`;
- `ux-simplification.js`;
- settings-specific CSS / foundation;
- `app.js` only where existing validation navigation requires it.

Target:

- compact internal tabs/navigation;
- human section hierarchy;
- clean two-column form groups where useful;
- Receptionist AI configuration aligned with reference;
- advanced configuration secondary.

Gate:

- deep links;
- validation routing;
- save feedback;
- keyboard;
- 390/768/1440.

### Phase 9 — Facturación

Likely files:

- `account.html`;
- `account.css`;
- `account.js` only for existing read-only data.

Target:

- plan card;
- active state;
- price;
- entitlements;
- next invoice;
- usage/payment summary.

No real checkout or mutation introduced unless separately approved.

### Phase 10 — Simulator and onboarding

Simulator:

- preserve explicit safe-test messaging;
- bring transcript/result layout into same product family;
- AI accent may be stronger.

Onboarding:

- preserve four-step product flow;
- visually align progress, step cards and next action with reconstructed system;
- do not clutter with normal operational navigation.

### Phase 11 — Cross-product visual hardening

Review all screenshots simultaneously.

Remove:

- legacy CSS rules no longer needed;
- duplicated button/nav/card skins;
- obsolete gradients;
- accidental purple product-action states;
- inconsistent radii/spacing;
- route-specific shell inventions.

Do not perform unrelated JS/backend cleanup merely because files are open.

## 4. Design implementation rules

### One source of shared truth

Shared palette/geometry/interaction remains in `frontend-foundation.css`.

Page CSS is for composition.

If the same visual rule is copied to three screens, stop and promote it to the canonical layer.

### Cyan and violet

- product action/navigation = cyan;
- AI identity/accent = violet;
- statuses = semantic colors.

Never use violet simply because an older rule already exists.

### No “AI generated UI” symptoms

Avoid:

- too many equal cards;
- giant rounded panels around every section;
- random gradients;
- excessive glow;
- repeated decorative badges;
- emoji as core nav icons;
- empty whitespace caused by generic templates;
- every section using a different component pattern.

## 5. Product-data rule

References may show sample data to demonstrate composition.

Implementation must distinguish:

- visual sample in tests/mocks;
- real production data.

Never hardcode restaurant/customer/phone/plan data into production UI to make screenshots look correct.

## 6. Visual RED/GREEN process

For each phase:

**RED**
- capture current exact-HEAD screenshot;
- document mismatch against blueprint;
- add measurable regression where stable.

**GREEN**
- implement smallest coherent CSS/markup change;
- capture same deterministic state;
- pass targeted functional E2E.

**REFACTOR**
- remove duplicate/obsolete styling;
- verify sibling screens did not drift.

## 7. Required evidence per phase

Each phase checkpoint should record:

- branch;
- exact HEAD;
- routes changed;
- current/reference screenshots reviewed;
- visual failures closed;
- targeted tests;
- remaining known gaps;
- next phase.

Do not use chat memory as the ledger.

## 8. Final visual certification

Before calling the reconstruction complete:

- capture every required route at 1440/768/390;
- build desktop contact sheet;
- run visual acceptance matrix;
- run complete functional Full Gate;
- record exact HEAD;
- ensure no Critical or unaccepted Important visual defects;
- preserve Draft/no-deploy state until an explicit release decision.

## 9. Production is a separate gate

The current public site may continue to look old until the approved reconstruction is actually merged and deployed.

Do not use production appearance as proof that branch implementation failed before checking the deployed commit hash.

After an explicitly authorized deployment:

- verify Railway commit;
- verify public static assets;
- hard-refresh/cache/service-worker state;
- capture production screenshots;
- compare them with certified branch screenshots.

## 10. Product ruling

The next frontend implementation should be judged by **visual acceptance against the approved references**, not by “tests green” alone.

Functional tests are mandatory. They are not a substitute for visual product review.

