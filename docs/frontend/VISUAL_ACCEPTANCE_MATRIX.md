# RecepVoz Visual Acceptance Matrix

**Status:** Required visual QA contract  
**Authority:** `VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`, `FRAME_CONTRACT.md`, `SCREEN_BLUEPRINTS.md`, `COMPONENT_CONTRACTS.md`  
**Purpose:** Make visual completion observable, repeatable and reviewable.

## 1. Why this exists

Functional green tests do not prove visual fidelity.

A screen can:

- have correct routes;
- load correct data;
- avoid overflow;
- pass keyboard tests;

and still fail the product visually through weak contrast, wrong hierarchy, incorrect proportions, poor density, inconsistent shell treatment or missing reference composition.

Visual acceptance is therefore a separate gate.

## 2. Required viewports

Every principal customer surface must be reviewed at minimum at:

| Target | Width | Height | Purpose |
| --- | ---: | ---: | --- |
| Wide desktop | 1536 | 950 | Large-shell containment |
| Desktop | 1440 | 900 | Primary reference comparison |
| Common laptop | 1366 | 768 | Real-world desktop density |
| Compact desktop | 1280 | 720 | Breakpoint/collision protection |
| Tablet | 768 | 1024 | Layout transition and density |
| Mobile | 390 | 844 | Compact/touch contract |

These are the canonical frame viewports from `FRAME_CONTRACT.json`. Bug-specific widths may be added when real production evidence exposes a gap. Marketing may additionally be reviewed at 1920 × 1080 when hero composition materially changes.

## 3. Required routes

Visual certification covers:

| Surface | Route / state |
| --- | --- |
| Public landing | `/` unauthenticated |
| Login | login state |
| Registration | registration state |
| Onboarding | incomplete first-user state |
| Inicio | `/` ready authenticated owner |
| Conversaciones | `/conversations.html` |
| Agenda | Home Agenda workspace / canonical agenda route if later separated |
| Clientes | Home Clientes workspace / canonical customers route if later separated |
| Inventario | `/inventory.html` |
| Configuración | `/app/settings` |
| Facturación | `/account.html` |
| Simulador | `/simulator.html` |

If routing changes, update this table before certification.

## 4. Screenshot evidence

For each required route:

1. load deterministic mocked or seeded state;
2. wait for fonts/layout/data to settle;
3. capture full viewport screenshot;
4. capture critical component detail only when necessary;
5. name evidence with route + viewport + state + HEAD.

Example:

`visual-rc/<HEAD>/home-1440-ready.png`

Screenshots used for certification must belong to the exact implementation HEAD being reviewed.

The Full Gate stores deterministic implementation screenshots under `test-results/visual-evidence/<HEAD>/` and uploads them as the `frontend-visual-evidence` artifact even when the suite passes. These images prove the exact application HEAD rendered the canonical seeded states; they do **not** replace the separate post-deploy production screenshot check in section 22.

## 5. Comparison dimensions

Each screen receives PASS/FAIL for all applicable dimensions.

| Dimension | Pass definition |
| --- | --- |
| Shell | Shared brand, nav, topbar and content frame match canonical product structure |
| Macro layout | Major regions are positioned and proportioned like the approved blueprint |
| Hierarchy | User can identify page title, primary task and important metrics immediately |
| Density | Screen feels operational, not sparse marketing UI or cluttered admin UI |
| Typography | Size, weight, line length and contrast reflect documented roles |
| Color | Navy/cyan/violet/semantic roles follow the canonical system |
| Contrast | Important text/actions are clearly readable |
| Spacing | Repeated gaps/padding align to shared scale |
| Geometry | Radii, control heights, card proportions are coherent |
| Navigation | Current/hover/focus states are clear and navigation remains usable |
| Components | Shared controls match component contracts |
| States | Loading/empty/error/disabled states are intentional |
| Responsive | Layout meaning survives tablet/mobile |
| Overflow | No accidental body-level horizontal overflow |
| Accessibility | Focus, touch targets and state meaning remain usable |

Any applicable FAIL blocks visual completion.

## 6. Severity model

### Critical visual defect

Blocks release candidate.

Examples:

- important text unreadable;
- navigation/action physically unclickable or obscured;
- primary content missing;
- body overflow hides required controls;
- destructive/action state visually misleading;
- auth/onboarding product hierarchy unusable.

### Important visual defect

Must be fixed before visual freeze.

Examples:

- screen composition substantially differs from approved blueprint;
- wrong sidebar/header architecture;
- inconsistent primary action;
- major density/proportion mismatch;
- mobile structure clearly degrades;
- duplicate or contradictory visual systems.

### Minor visual defect

May be tracked post-RC if explicitly accepted.

Examples:

- small spacing variance;
- non-critical icon alignment;
- subtle border/radius discrepancy;
- tertiary metadata wrapping without task impact.

“Minor” cannot be used to dismiss repeated inconsistencies that collectively make a screen look unfinished.

## 7. Landing acceptance

### Desktop

Required:

- product navigation visible;
- brand readable;
- headline dominates;
- headline contrast passes;
- primary CTA visually dominant;
- secondary CTA clearly secondary;
- product/voice visual occupies meaningful right-side weight;
- no large unexplained blank region;
- login access does not overpower hero;
- lower trust/value row visible.

### Mobile

Required:

- headline remains first-order content;
- hero visual may simplify but must not disappear into unexplained empty space;
- CTA remains above the fold or immediately reachable;
- no desktop two-column squeeze;
- login/register path obvious.

## 8. Inicio acceptance

The current owner home answers one question first: **“¿Qué necesita mi atención ahora?”**

Required:

- the page title **Inicio** and owner summary **Qué está pasando hoy** are immediately visible;
- the operational summary surfaces calls and current business activity without turning the page into a KPI mosaic;
- **Necesita tu atención** is visually prominent whenever there are unresolved items;
- **Actividad reciente** remains scannable and translates technical events into human language;
- **Accesos rápidos** provides direct navigation to Agenda, Clientes, Conversaciones, Inventario, Configuración and Facturación;
- ready, empty and degraded states preserve the same hierarchy instead of replacing the page with a giant blank card;
- no giant empty whitespace or duplicate global navigation.

### Ventas y rendimiento

The Ventas workspace is part of the authenticated home workspace but is visually secondary to the owner’s daily Inicio summary.

Required:

- **Ventas y rendimiento** exposes confirmed collections, paid orders, units sold and average ticket for the selected 7/30/90-day period;
- **Ventas en el tiempo** receives enough space to make trend direction readable;
- product ranking distinguishes “Más pedidos” from “Mayor facturación”;
- channel mix and strongest demand window are readable without decorative chart noise;
- **Gestionado por voz y WhatsApp** is explicitly described as recorded order origin, not causal ROI;
- useful measured insights remain concise and evidence-based;
- mixed currencies are never visually combined into a false aggregate.

Tablet/mobile:

- owner summary, attention, recent activity and quick access reflow in task order;
- sales metrics and trend remain readable without page-level horizontal overflow;
- tables/charts may stack or scroll inside their own containers but must not widen the page.

## 9. Conversaciones acceptance

Required:

- selected conversation visually obvious;
- list and detail have intentional relative widths;
- filters/search compact and aligned;
- messages readable;
- sender roles distinguishable;
- status/outcome pills restrained;
- customer identity/action header clear.

At 390 px:

- list/detail do not coexist in a cramped desktop split;
- user can navigate into and out of detail;
- composer/actions remain usable.

## 10. Agenda acceptance

Required:

- time axis readable;
- appointment blocks align to schedule;
- selected/current state clear;
- monthly calendar/detail rail secondary to schedule;
- “Nueva cita” or equivalent primary action clear if real.

Mobile:

- convert intentionally to day/list/stacked layout;
- do not preserve unusable desktop geometry.

## 11. Clientes acceptance

Required:

- search prominent enough to find a customer;
- table/list density consistent with Inventory;
- customer identity stronger than metadata;
- detail/attention states clear;
- no arbitrary dashboard-card mosaic.

## 12. Inventario acceptance

Required:

- table is primary visual structure;
- search and filters align in one toolbar;
- product, category, price, stock and status scan easily;
- low stock visible but not visually explosive;
- primary creation action visible when authorized;
- row action does not obscure data.

## 13. Configuración acceptance

Required:

- internal section navigation compact;
- current section clear;
- form labels/controls align;
- advanced sections secondary;
- save action unmistakable;
- errors open/focus owning section;
- no equal-weight navigation-card sprawl.

## 14. Facturación acceptance

Required:

- current plan and status are first-order;
- price/cadence readable;
- entitlements compact;
- next invoice and usage secondary but clear;
- no fake provider success;
- operator/read-only states intentionally styled.

## 15. Simulador acceptance

Required:

- safe-test identity is visually obvious;
- “no real calls / WhatsApp / commercial effects” remains visible;
- transcript and outcome hierarchy readable;
- AI accent supports, not overwhelms, the workspace;
- start/reset actions clear.

## 16. Onboarding acceptance

Required:

- four steps visible or clearly represented;
- progress understandable;
- exactly one next action visually dominant;
- completed/current/upcoming states distinct;
- no unrelated operational clutter;
- responsive flow does not hide CTA.

## 17. Cross-screen coherence review

Before freeze, view all desktop screenshots together.

Fail if:

- one route looks like a different product;
- sidebar width/treatment shifts unexpectedly;
- title hierarchy changes arbitrarily;
- buttons use different skins for same role;
- one screen returns to white/light surfaces;
- cyan/violet roles swap without reason;
- typography density changes dramatically;
- page margins drift route by route.

This “contact sheet” review is mandatory because many coherence defects are invisible when pages are reviewed alone.

## 18. Automated checks

Automated tests should enforce measurable invariants where stable:

- expected design token values;
- stylesheet authority/load order;
- sidebar width and fixed/compact transition;
- navigation current state;
- no body overflow at required widths;
- minimum target sizes;
- key contrast ratios;
- absence of forbidden large light surfaces;
- absence of decorative global gradients where not allowed;
- critical interactions remain clickable;
- deterministic screenshot snapshots for stable routes when feasible.

Pixel snapshot tests are evidence, not the only source of truth. Small antialiasing differences must not cause meaningless churn.

## 19. Human/product checks

A product reviewer must answer for each screen:

- What is this screen for?
- What should the user notice first?
- What is the primary action?
- What requires attention?
- Does the page look like the approved reference family?
- Is anything visually present that has no product purpose?
- Is anything functionally important visually buried?

If these answers are unclear, the screen fails even if CSS values technically match.

## 20. Implementation loop for visual gaps

For each failed screen:

1. capture current screenshot;
2. identify mismatch against blueprint;
3. classify Critical / Important / Minor;
4. add a stable regression contract where measurable;
5. change the smallest coherent visual layer;
6. capture same viewport/state again;
7. run affected functional E2E;
8. re-run cross-screen coherence review before freeze.

Do not rewrite tests simply because a new screenshot “looks nicer”.

## 21. Release gate

Frontend visual freeze requires all of the following:

- functional Full Gate green;
- visual acceptance matrix PASS for all principal screens;
- desktop contact sheet reviewed;
- tablet/mobile required routes reviewed;
- no Critical defects;
- no unaccepted Important defects;
- exact-HEAD evidence recorded in Draft PR;
- no production deploy claimed until the exact approved SHA is actually deployed and verified.

## 22. Production verification after an authorized deploy

Only after explicit authorization to merge/deploy:

1. verify deployed Railway commit hash;
2. load public domain;
3. capture production screenshots at required viewports;
4. compare production to certified branch screenshots;
5. hard refresh/service-worker cache if necessary;
6. verify static asset versions correspond to deployed SHA;
7. only then call production visually updated.

A green branch is not proof that production changed.

