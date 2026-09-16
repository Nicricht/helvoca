# V42 Plans, Entitlements & Billing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace enum-bound commercial rules with a PostgreSQL-backed plan/entitlement engine that consumes V41 usage and preserves existing public pricing/subscription behavior.

**Architecture:** Add global `commercial_plan` and `commercial_plan_entitlement` reference tables, map subscription plan codes as strings, evaluate period usage exclusively from `usage_meter_event`, and adapt billing providers to immutable provider-neutral plan data. Existing controllers remain stable while their values become database-backed.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL, Flyway, Spring JDBC/JPA, JUnit 5, Mockito, GitHub Actions, Railway.

**Spec:** `docs/superpowers/specs/2026-09-16-v42-plan-entitlements-billing-design.md`

## Global Constraints

- V41 `usage_meter_event` is the only source of period usage truth.
- No industry-specific branches.
- No caller-provided `business_id` in public APIs.
- Missing subscription/plan/required hard entitlement fails closed.
- Preserve public plan codes `EMPRENDE`, `NEGOCIO`, `PRO`, `ENTERPRISE`.
- Preserve existing subscription status/grace semantics.
- Do not activate real providers or modify certification flags.
- Merge only an exact PR HEAD with green CI, then require green main CI and Railway production verification.

---

### Task 1: Persist the commercial plan catalog

**Files:**
- Create: `src/main/resources/db/migration/V42__plans_entitlements_billing.sql`
- Create: `src/main/java/cl/helvoca/billing/CommercialPlanCatalogService.java`
- Create: `src/test/java/cl/helvoca/billing/CommercialPlanCatalogServiceTest.java`

**Interfaces:**
- Produces: `CommercialPlanCatalogService.Plan`, `EntitlementRule`, `findActiveByPublicCode(String)`, `requireByCode(String)`, `activePlans()`.

- [ ] **Step 1: Write failing catalog tests**

Cover active plan ordering, public-code resolution, inactive-plan rejection, and entitlement loading. The tests must reference the new service before it exists.

- [ ] **Step 2: Verify RED**

Run: `mvn -Dtest=CommercialPlanCatalogServiceTest test`
Expected: test compilation failure because `CommercialPlanCatalogService` does not exist.

- [ ] **Step 3: Add Flyway V42 migration**

Create `commercial_plan` and `commercial_plan_entitlement`, seed the four current commercial plans/rules, drop finite subscription plan CHECK constraints, add FKs from current/pending plan codes, and explicitly keep runtime access read-only for the global catalog tables.

Seed technical/public mappings exactly:

```text
BASIC      -> EMPRENDE    -> Emprende    -> 24990 CLP
PRO        -> NEGOCIO     -> Negocio     -> 39990 CLP
BUSINESS   -> PRO         -> Pro         -> 69990 CLP
ENTERPRISE -> ENTERPRISE  -> Enterprise  -> 119990 CLP, custom pricing
```

Seed `VOICE_SECONDS` as soft USAGE entitlements and `CONCURRENT_CALLS` as hard CAPACITY entitlements according to the spec.

- [ ] **Step 4: Implement `CommercialPlanCatalogService`**

Use `NamedParameterJdbcTemplate`. Return immutable records only. Normalize technical/public codes with `trim().toUpperCase(Locale.ROOT)`. Reject null/blank codes and missing/inactive checkout plans.

Required record shapes:

```java
public record Plan(String code, String publicCode, String displayName,
                   Integer monthlyPriceClp, String currency,
                   boolean customPricing, boolean recommended,
                   boolean active, int sortOrder,
                   List<EntitlementRule> entitlements) {}

public record EntitlementRule(String key, String kind, String meterKey,
                              BigDecimal limitValue, String unit,
                              boolean hardLimit, BigDecimal overageUnitSize,
                              Integer overagePriceClp) {}
```

- [ ] **Step 5: Verify GREEN**

Run: `mvn -Dtest=CommercialPlanCatalogServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add V42 database commercial catalog`

---

### Task 2: Evaluate generic entitlements from V41 usage

**Files:**
- Create: `src/main/java/cl/helvoca/billing/CommercialEntitlementService.java`
- Create: `src/test/java/cl/helvoca/billing/CommercialEntitlementServiceTest.java`

**Interfaces:**
- Consumes: `BusinessSubscriptionRepository`, `CommercialPlanCatalogService`, V41 `usage_meter_event`.
- Produces: `snapshot(UUID businessId)`, `capacity(UUID businessId, String entitlementKey)`.

- [ ] **Step 1: Write failing entitlement tests**

Test these behaviors independently:

```text
1. Missing business subscription fails closed.
2. VOICE_SECONDS usage is summed from usage_meter_event only.
3. remaining = max(0, limit - used).
4. overage = max(0, used - limit).
5. soft VOICE_SECONDS overage does not disable an ACTIVE subscription.
6. missing CONCURRENT_CALLS capacity fails closed.
7. expired TRIALING subscription is not service-allowed.
```

- [ ] **Step 2: Verify RED**

Run: `mvn -Dtest=CommercialEntitlementServiceTest test`
Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement entitlement evaluation**

Query V41 with one aggregate per meter key over `[current_period_start, current_period_end)`:

```sql
SELECT COALESCE(SUM(quantity), 0)
FROM usage_meter_event
WHERE business_id = :businessId
  AND meter_key = :meterKey
  AND occurred_at >= :from
  AND occurred_at < :to
```

Return immutable views containing rule metadata plus `used`, `remaining`, `overage`, and `hardExceeded`. Capacity rules have no ledger usage.

- [ ] **Step 4: Verify GREEN**

Run: `mvn -Dtest=CommercialEntitlementServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: evaluate V42 entitlements from V41 usage`

---

### Task 3: Remove enum-bound subscription behavior

**Files:**
- Modify: `src/main/java/cl/helvoca/billing/BusinessSubscription.java`
- Modify: `src/main/java/cl/helvoca/billing/BusinessSubscriptionService.java`
- Modify: `src/main/java/cl/helvoca/telephony/CallLifecycleService.java`
- Modify: `src/test/java/cl/helvoca/billing/BusinessSubscriptionServiceTest.java`
- Modify existing call lifecycle tests that construct subscription views.
- Delete after all references are removed: `src/main/java/cl/helvoca/billing/PlanCode.java`

**Interfaces:**
- `BusinessSubscription.getPlanCode()/setPlanCode(...)` become `String`.
- `pendingPlanCode` becomes `String`.
- `BusinessSubscriptionService.SubscriptionView` keeps compatibility fields and adds `publicPlanCode`, `planName`, `entitlements`.

- [ ] **Step 1: Rewrite tests first**

Replace enum expectations with string technical codes. Add a regression asserting `BusinessSubscriptionService.view(...)` no longer returns a silent PRO fallback for a missing subscription.

- [ ] **Step 2: Verify RED**

Run: `mvn -Dtest=BusinessSubscriptionServiceTest test`
Expected: compile/test failure against the old enum/fallback implementation.

- [ ] **Step 3: Refactor entity and service**

`startBasicTrial` stores `"BASIC"`. `view` delegates plan/usage/capacity evaluation to `CommercialEntitlementService`; it must not query `CallSessionRepository` for usage and must not use `CallCommercialProperties` for plan capacity.

Compatibility voice fields are derived from `VOICE_SECONDS`:

```java
long usedMinutes = ceil(usedSeconds / 60);
int includedMinutes = exact included seconds / 60 for seeded plans;
long overageMinutes = Math.max(0, usedMinutes - includedMinutes);
```

- [ ] **Step 4: Fail closed in call admission**

When commercial configuration is absent/invalid, `CallLifecycleService.startInboundCall` must count the rejection as subscription-related and throw `CallCapacityExceededException`, not fall back to a global commercial capacity.

- [ ] **Step 5: Verify GREEN plus call tests**

Run: `mvn -Dtest=BusinessSubscriptionServiceTest,CallLifecycleServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `refactor: make subscriptions entitlement driven`

---

### Task 4: Make the payment gateway provider-neutral

**Files:**
- Modify: `src/main/java/cl/helvoca/billing/SubscriptionPaymentGateway.java`
- Modify: `src/main/java/cl/helvoca/billing/MercadoPagoSubscriptionGateway.java`
- Modify: `src/main/java/cl/helvoca/billing/BillingSubscriptionService.java`
- Modify: `src/test/java/cl/helvoca/billing/BillingSubscriptionServiceTest.java`
- Modify/add Mercado Pago gateway unit tests if present.

**Interfaces:**
- `SubscriptionPaymentGateway.createCheckout(UUID, String, PaymentPlan)`.

Required provider-neutral value:

```java
record PaymentPlan(String code, String displayName,
                   Integer monthlyPriceClp, boolean customPricing) {}
```

- [ ] **Step 1: Update billing tests first**

Tests must prove checkout resolves `NEGOCIO` through `CommercialPlanCatalogService`, persists pending technical code `PRO`, preserves current plan until verified payment, and rejects custom-pricing checkout before invoking the gateway.

- [ ] **Step 2: Verify RED**

Run: `mvn -Dtest=BillingSubscriptionServiceTest test`
Expected: compile/test failure against enum-based billing.

- [ ] **Step 3: Adapt gateway and billing service**

Mercado Pago uses the database plan's display name and fixed price. External reference stays `helvoca:<businessId>:<technicalCode>`. Reconciliation compares the technical code stored in the subscription; no provider response may redefine plan price or limits.

- [ ] **Step 4: Verify GREEN**

Run: `mvn -Dtest=BillingSubscriptionServiceTest,MercadoPagoWebhookControllerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor: decouple billing provider from plan rules`

---

### Task 5: Preserve public pricing and readiness compatibility

**Files:**
- Modify: `src/main/java/cl/helvoca/billing/PublicPricingController.java`
- Modify: `src/main/java/cl/helvoca/billing/SubscriptionController.java` if its plan view signature changes.
- Modify: `src/main/java/cl/helvoca/onboarding/SelfServiceReadinessService.java`
- Modify: relevant billing/onboarding controller/service tests.
- Update: `docs/PRODUCT_ARCHITECTURE.md` and/or README milestone text so V41/V42 are not described as future work.

- [ ] **Step 1: Add compatibility tests first**

Prove `GET /api/v1/public/pricing`-level mapping still exposes the legacy fields from the database catalog and readiness uses `publicPlanCode`/`planName` from the subscription snapshot without `PlanCode.valueOf(...)`.

- [ ] **Step 2: Verify RED**

Run focused pricing/readiness tests and confirm failure against enum-based implementation.

- [ ] **Step 3: Implement catalog-backed controllers/readiness**

For pricing, derive:

```text
includedMinutes          <- VOICE_SECONDS.limit / 60
maxConcurrentCalls       <- CONCURRENT_CALLS.limit
overagePerMinuteClp      <- VOICE_SECONDS.overage price where unit size = 60
```

Do not invent values when a generic plan omits a voice entitlement; use safe nullable/zero compatibility behavior only where the response type permits it, while generic entitlement detail remains authoritative.

- [ ] **Step 4: Verify focused tests**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: expose V42 catalog-backed commercial views`

---

### Task 6: Full verification and release

**Files:**
- All V42 changes.

- [ ] **Step 1: Run full backend suite**

Run: `mvn --batch-mode --no-transfer-progress test`
Expected: BUILD SUCCESS with zero failing tests.

- [ ] **Step 2: Run existing frontend syntax/E2E gates**

Use the repository CI workflow as the authoritative full gate.

- [ ] **Step 3: Review diff against V42 spec**

Confirm no real-provider enablement, no certification flag changes, no industry branches, and no direct period-usage calculation from channel tables.

- [ ] **Step 4: Open/update PR and require exact HEAD green**

Do not merge a superseded SHA.

- [ ] **Step 5: Merge exact green HEAD and verify main**

Require main CI success on the merge SHA.

- [ ] **Step 6: Verify Railway production**

Require Railway to deploy exactly the merge SHA, Flyway to migrate schema from v41 to v42, Spring/Tomcat to start, and the configured `/actuator/health` gate to pass before declaring V42 certified.