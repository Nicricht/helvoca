# PR #768 · Inventory premium: affected frontend QA traceability

**Scope:** only `frontend/src/pages/Inventory/*`, `frontend/src/features/inventory/api.ts` and inventory Playwright E2E.
**Risk:** HIGH where inventory stock/variants, authorization and retries cross server boundaries; MEDIUM for UI geometry and filtering.
**Frame change:** NO. All fixtures are intercepted browser requests, not real DB/provider requests.
**Release gate:** **100% applicable affected executable statements/lines, branches and functions when instrumented, plus 100% meaningful affected interactions/state transitions.** A passing CI test count alone is not a measured percentage.

## Evidence inventory (coverage obligations, not percentages)

| Affected user action, state or invariant | Automated behavioral evidence | Proof / current limit |
| --- | --- | --- |
| Inventory initial load, official art, dense operational lead, accurate product counts and stock | `e2e/react-inventory.spec.js` (ready, skeleton, primary failure, authoritative stock), `e2e/frontend-release-candidate.spec.js` | Assertions and exact-head PNGs |
| Search, LOW/TRACKED/OUT/RESTOCKED/UNCONFIGURED filters, sorting (attention/name/available), empty state | `e2e/react-inventory.spec.js` ("keeps search", "all stock status filters and sort choices") | Filter transitions and row order asserted |
| Pagination, table media read budget, page reset after search | `e2e/react-inventory.spec.js` ("local pagination bounds media reads") | Next page and search reset asserted; **previous page not explicitly asserted** |
| Inspector open/close, focus trap, Escape, restored trigger focus, client viewport bounds | `e2e/react-inventory.spec.js` ("inspector contains keyboard tab focus", "captures exact-head inspector") | Real navigation and geometry; 6 canonical viewports |
| Catalog price/media permission, missing price, missing catalog | `e2e/react-inventory.spec.js` ("uses real catalog price", "absent catalog price", "stays useful when catalog data fails") | Assertions prevent fabricated data |
| Create catalog product, guided stock setup, partial success retry, failing create | `e2e/react-inventory-mutations.spec.js` | Network request payload and duplicate prevention |
| Edit catalog product, invalid currency, no duplicate POST | `e2e/react-inventory-mutations.spec.js` ("editing an existing catalog product") | Added in coverage-audit increment, requires exact-head CI |
| Configure base stock and apply explicit delta | `e2e/react-inventory-mutations.spec.js`, `e2e/react-inventory.spec.js` | Payload asserted and UI refreshed |
| Alert acknowledge (success/error) and queues refetch | `e2e/react-inventory-automation.spec.js` | New deterministic fixture, requires exact-head CI |
| Restock waiting cancellation (success/error) without external notification | `e2e/react-inventory-automation.spec.js` | New deterministic fixture, requires exact-head CI |
| Base restock via LOW_STOCK alert, explicit delta, no automatic mutation on open | `e2e/react-inventory-automation.spec.js` | New deterministic fixture, requires exact-head CI |
| Variant-specific restock via linked alert, base stock not changed | `e2e/react-inventory-automation.spec.js` | New deterministic fixture, requires exact-head CI |
| Operator can read automation data but cannot acknowledge/cancel/restock | `e2e/react-inventory-automation.spec.js` | New negative-role fixture, requires exact-head CI |
| Backend unavailable in one or all secondary data sources; no invented zeros | `e2e/react-inventory.spec.js` | 503 states asserted; six-viewport exact-head PNGs |
| Variant create, edit, delta, history, deactivate, role boundary | `e2e/react-inventory-variants.spec.js` | Request payload + UI outcomes asserted |
| Variant empty/duplicate options and legacy nested fields preserved | `e2e/react-inventory-variants.spec.js` | No unauthorized writes + serialized options asserted |
| Modal create/stock/variant/error responsive geometry | `e2e/inventory-visual-evidence-helper.js`, three Inventory E2E specs | 36 exact-head screenshots; top and bottom bounds asserted |
| Inventory Java / SQL authoritative domain invariants | Existing backend/Testcontainers quality suites and `docs/engineering/invariants.md` | No inventory server/schema change in this PR; frontend mocks **do not** certify DB semantics |
| Global commercial release journeys | GitHub Golden Journey and system integration workflows | Exact final SHA required |

## Missing certification evidence: explicit HOLD items

1. **Measured frontend executable coverage:** there is currently no JavaScript/TSX source instrumentation or report in the frontend `package.json` and relevant CI evidence for this PR; therefore cannot claim 100% statements/lines/branches/functions. Implement and enforce scoped source coverage or justify inapplicability with a behavior inventory according to `docs/engineering/QA_POLICY.md`.
2. **Complete affected-control inventory:** some actions still need dedicated assertions for their **failure/edge** outcomes, especially pagination backward navigation, product edit server 409, stock input boundary cases, variants server failures, variant JSON invalid/non-object legacy input, history failure and some close/cancel combinations.
3. **Permission matrix depth:** roles BUSINESS_OWNER/BUSINESS_ADMIN/OPERATOR have partial browser proof; server authorization and mixed granular claims need scope-specific verification before a 100% coverage claim.
4. **Real PostgreSQL and providers:** not changed by this PR; no new real-data mutations or paid calls should be introduced to achieve a coverage number. Cross-layer invariants must be linked to existing Testcontainers/contract tests, not inferred from mocked E2E.

**Release state:** HOLD / DRAFT until missing applicable coverage obligations are either implemented and certified on exact HEAD or shown inapplicable with documented, testable rationale. Do not lower 100% thresholds.
