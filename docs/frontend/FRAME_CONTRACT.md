# RecepVoz Frontend Frame Contract

**Version:** 1.0.0  
**Status:** Canonical protected frontend shell contract  
**Parent authority:** `VISUAL_PRODUCT_SOURCE_OF_TRUTH.md`  
**Machine-readable seal:** `FRAME_CONTRACT.json`

## 1. Purpose

This contract is the permanent visual boundary between the **RecepVoz frame** and each screen's editable content.

Its goal is simple: a local request such as “change the Inventory table”, “make this chart larger” or “improve the Agenda card” must not silently redesign the entire product.

The frame is the stable container. Screens fill named slots inside it.

## 2. The seal

Every frontend Pull Request must explicitly state one of these values:

`FRAME CHANGE: NO`

Use this for ordinary screen work. Only the declared editable slots may change. Protected frame geometry, tokens and shared primitives stay unchanged.

`FRAME CHANGE: YES`

Use this only when the request intentionally changes the global product shell or shared visual system. A frame change requires cross-screen evidence because its blast radius is global.

A local request must default to **FRAME CHANGE: NO**.

## 3. Zonas protegidas

The following are **protected frame territory**:

- product canvas and shell background roles;
- canonical palette and semantic color roles;
- global spacing scale;
- canonical radii;
- topbar geometry;
- desktop sidebar target width;
- content maximum width and page gutters;
- desktop / compact / mobile breakpoint vocabulary;
- page heading structure;
- generic card, button, input, focus and status language;
- shared navigation treatment;
- `--rv-frame-*` tokens;
- `.rv-frame-*` and `.rv-page-*` frame primitives.

These may not be redefined inside a page-specific stylesheet.

## 4. Zonas editables

A normal screen task may change only the slot or component explicitly named by the task.

Examples of editable territory:

- KPI values/content;
- charts and their local arrangement;
- table columns and row content;
- conversation list/detail content;
- appointment/calendar content;
- screen-specific filters;
- local forms;
- empty/loading/error states;
- screen-specific imagery;
- local panel composition.

Feature CSS may own composition **inside an editable slot**. It must not create a second global palette, shell, button system, form system, card system or breakpoint vocabulary.

## 5. Canonical geometry

The machine-readable values live in `FRAME_CONTRACT.json` and the CSS authority is `frontend-foundation.css`.

| Contract | Canonical value |
| --- | ---: |
| Desktop sidebar target | 220 px |
| Topbar minimum height | 64 px |
| Content max width | 1420 px |
| Desktop page gutter | 24 px |
| Compact page gutter | 20 px |
| Mobile page gutter | 12 px |
| Desktop begins | 981 px |
| Compact max | 980 px |
| Mobile max | 620 px |

These are frame values, not page-specific suggestions.

## 6. Reserved frame primitives

Only `frontend-foundation.css` owns these selectors:

- `.rv-frame-shell`
- `.rv-frame-topbar`
- `.rv-page-frame`
- `.rv-page-header`
- `.rv-page-grid`
- `.rv-frame-slot`

Only `frontend-foundation.css` may define `--rv-frame-*` custom properties.

A feature stylesheet may **consume** these primitives and variables, but must not redefine them.

## 7. Screen map

### Dashboard

Protected frame:
- shell;
- navigation;
- topbar;
- page container;
- page heading geometry.

Editable slots:
- `kpi-grid`;
- `analytics`;
- `recent-activity`;
- `quick-actions`.

### Conversaciones

Protected frame:
- shell;
- navigation;
- topbar;
- page container.

Editable slots:
- `conversation-list`;
- `conversation-detail`;
- `customer-inspector`;
- `conversation-actions`.

### Agenda

Editable slots:
- `agenda-toolbar`;
- `calendar`;
- `appointment-detail`;
- `agenda-actions`.

### Clientes

Editable slots:
- `customer-search`;
- `customer-list`;
- `customer-detail`;
- `customer-actions`.

### Inventario

Editable slots:
- `inventory-summary`;
- `inventory-toolbar`;
- `product-table`;
- `product-inspector`;
- `inventory-actions`.

### Configuración

Editable slots:
- `settings-navigation`;
- `settings-form`;
- `integration-panel`;
- `settings-actions`.

### Facturación

Editable slots:
- `plan-summary`;
- `usage-summary`;
- `invoice-list`;
- `billing-actions`.

The JSON manifest is the authoritative machine-readable list. If a new principal screen is introduced, update the manifest and this document in the same PR.

## 8. Local change protocol

For a normal screen request:

1. identify the screen;
2. identify the editable slot;
3. set `FRAME CHANGE: NO`;
4. change only that slot and its local component styles;
5. run the screen's affected tests;
6. verify frame containment at relevant canonical widths;
7. verify an unrelated principal screen did not change when the change could share CSS.

Example:

```text
FRAME CHANGE: NO
Screen: Inventory
Editable slot(s): product-table
Global frame unchanged: YES
```

Changing Inventory does not authorize edits to Dashboard, Conversations, Agenda, the global shell or shared tokens.

## 9. Global frame change protocol

A deliberate global change requires:

```text
FRAME CHANGE: YES
Reason: <why the shared shell must change>
Affected protected area: <token / topbar / sidebar / grid / shared component>
Cross-screen evidence: <routes + viewports checked>
```

A FRAME CHANGE: YES PR must review at least:

- public/auth surface when relevant;
- Dashboard;
- one dense operational screen such as Conversations or Inventory;
- Configuración;
- desktop, compact/tablet and mobile widths.

## 10. Canonical viewports

Frame regression coverage should use these sizes whenever applicable:

- 1536 × 950
- 1440 × 900
- 1366 × 768
- 1280 × 720
- 768 × 1024
- 390 × 844

Additional bug-specific widths are added when real production evidence exposes a gap.

## 11. Forbidden local behavior

With `FRAME CHANGE: NO`, a page-specific change must not:

- change `--rv-frame-*` values;
- redefine reserved frame primitives;
- alter global body/shell/topbar behavior to solve a local layout issue;
- introduce a new global color palette;
- change the canonical breakpoint vocabulary;
- restyle generic buttons, cards, forms or navigation globally;
- change another principal screen merely to make the edited screen fit.

If a local screen genuinely cannot be improved without one of these changes, stop treating it as local and explicitly promote the PR to `FRAME CHANGE: YES`.

## 12. Relationship to legacy CSS

RecepVoz still contains historical selectors and compatibility layers. This contract does not require an unsafe big-bang rewrite.

New work must move toward the protected primitives and must not expand legacy global ownership.

Existing legacy selectors may be migrated gradually. A migration that changes shared geometry is a FRAME CHANGE: YES.

## 13. Completion rule

A screen change is not visually complete merely because its own screenshot looks better.

For `FRAME CHANGE: NO`, completion also means:

- protected tokens unchanged;
- reserved frame primitives unchanged;
- no body-level horizontal overflow at relevant widths;
- screen content stays inside the frame;
- shared navigation/header geometry remains stable;
- the PR names exactly what was allowed to change.

This is the “sello del frame”: local work stays local unless the user explicitly asks to change the frame itself.
