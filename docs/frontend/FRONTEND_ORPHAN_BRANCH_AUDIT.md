# Frontend orphan branch audit

Authoritative working ledger for `feat/frontend-orphan-branch-rescue`.

Audit base:
- `main@8dc165d102a4157fdc6e3cd38b51b76b70b388d1`
- `feat/frontend-finish-integration@3d73320848c7d81956701af6eb60f516f9eb1c80`
- integration is **260 commits ahead / 0 behind** `main`.

## Rules

- The certified integration branch is the canonical frontend reference.
- Historical branches are never merged wholesale.
- Rescue only behavior that still belongs in the current product.
- Production behavior requires RED → GREEN evidence before adoption.
- Schema/API/security changes are HIGH risk.
- No merge to `main`, deploy, real calls, WhatsApp, payments, orders, deliveries or provisioning during this audit.

## Final classification

| Branch / PR | Evidence | Decision |
| --- | --- | --- |
| `feat/frontend-foundation-dark` / #625 | source ancestry is fully contained by integration | **integrated** |
| `feat/frontend-dashboard-finish` / #620 | source ancestry is fully contained by integration | **integrated** |
| `feat/frontend-onboarding-settings-finish` / #629 | source behavior is represented and certified in integration | **integrated** |
| `feat/frontend-commerce-operations-finish` / #623 | certified source HEAD is contained by integration | **integrated** |
| `feat/frontend-conversations-calls-finish` / #621 | certified source HEAD is contained by integration | **integrated** |
| `feat/frontend-billing-account-finish` / #634 | certified source HEAD is contained by integration | **integrated** |
| `feat/frontend-final-visual-polish` | zero commits ahead of the integrated line when reviewed; later integration contains it | **integrated / superseded** |
| `test/frontend-release-candidate-v1` | branch resolved to the same integration HEAD at audit start | **test mirror, no missing product UI** |
| `test/release-candidate-first-business-hardening` / #641 | five safe RC scenarios, no production source; selectively copied into canonical integration at `4af59d475c9e00ccb6bd6c5851d8f5a5d8571b00` | **consumed into integration; PR #641 closed unmerged** |
| `feat/frontend-appearance-themes` / #637 | unique tenant theme API/schema/runtime/UI work; PR explicitly says **POST-RC ONLY** and forbids reopening current RC visual scope | **valid future feature, deliberately deferred; do not rescue into current RC** |
| `feat/landing-hero-dark-v1` / #612 | unique richer public hero, two hero CTAs, conversation card and trust strip; current integration already has the dark canonical hero, Sofía voice preview and certified auth/landing behavior | **superseded for current RC; preserve as future marketing reference only** |
| `feat/frontend-finalization-batch-1` / #614 | extends #612 and adds `recepvoz-ui.css` + brand asset; also contains a historical “light commercial design” contract that conflicts with the current dark Foundation; onboarding-next-step intent is covered by newer integration tests | **superseded for current RC; do not reintroduce parallel visual system** |
| `feat/frontend-commercial-redesign-v3` / #610 | PR #610 is merged; current `main` is the merge result | **already merged historically, not orphaned** |
| `codex/frontend-ux-simplification` / #115 | PR #115 is merged | **already merged historically, not orphaned** |
| `feat/frontend-operational-redesign` / #135 | PR is closed unmerged and thousands of commits behind; its stated outcomes (operational home, settings, reservation filters, conversation traceability, internal-only operations) are implemented by newer Dashboard / Conversations / Settings integration | **superseded by newer product work; no rescue** |

## Important findings

### 1. The main visibility problem is not an orphan branch

The largest reason the current product can look older is simple:

`feat/frontend-finish-integration@4af59d475c9e00ccb6bd6c5851d8f5a5d8571b00` is **260 commits ahead of `main`** and remains unmerged.

That integration line contains the certified Foundation, Dashboard, Onboarding/Settings, Commerce, Conversations/Calls, Billing/Account, shared navigation and release-candidate contracts.

Fresh exact-HEAD Full CI: `36524973203` — SUCCESS, backend **1330/1330**, browser E2E **118/118**.

Therefore a runtime built from `main` cannot show the complete integrated frontend.

### 2. Appearance Themes is real, but intentionally outside the RC

PR #637 is not accidental lost work. Its own durable ruling says:
- POST-RC ONLY;
- do not merge into the frozen frontend RC;
- resume only as a deliberate later release feature with fresh gates against the then-current integration base.

Unique future scope includes:
- five curated tenant accent presets;
- persisted `business.appearance_theme`;
- admin-only appearance mutation;
- shared `appearance.js` runtime;
- Settings appearance UI;
- tenant/cache/expiry hardening.

This work remains preserved in PR #637 and must not be silently mixed into the current release candidate.

### 3. Landing / Batch 1 preserve useful ideas, not current canonical code

The older landing branches contain:
- a richer Sofía conversation card;
- hero CTAs “Probar RecepVoz” and “Ver cómo funciona”;
- compact trust strip;
- a dedicated brand asset.

Those are valid future marketing/design references.

However their implementation is not suitable for wholesale rescue because:
- they predate the final integration;
- Batch 1 creates a second shared design layer (`recepvoz-ui.css`);
- parts of its contract explicitly expect the older light commercial system;
- current Foundation is the canonical dark visual authority;
- current RC already has certified dark landing/auth behavior and a Sofía preview;
- current RC hardening explicitly says not to add more cosmetics without a demonstrated defect.

Decision: preserve the ideas, not the old implementation.

### 4. Old operational work is functionally superseded

PR #135 was never merged, but its product objectives are now covered by later certified work:
- operational Dashboard;
- customer Conversations/Calls;
- Settings;
- reservation/order traceability;
- internal-only Operations boundary.

Reintroducing the old branch would revive stale backend/UI code from a branch far behind the current product line.

## Rescue result

**No production source from the audited orphan branches qualifies for rescue into the current RC.**

This is intentional, not a no-op:
- valid current work is already integrated;
- Appearance Themes is deliberately post-RC;
- RC hardening is test-only and has now been preserved in the canonical integration;
- old visual branches conflict with or are superseded by the canonical dark Foundation;
- old operational work is superseded by later integrated behavior.

The audit therefore prevents two opposite failure modes:
1. losing genuinely valuable future work;
2. blindly merging stale branches and regressing the certified frontend.

## Preserved future candidates

Do not lose these when planning post-RC work:
1. **Appearance Themes** — resume PR #637 deliberately after RC.
2. **Public marketing composition** — if a later product review wants a stronger acquisition landing, use #612/#614 only as reference for richer Sofía conversation storytelling, trust strip, CTAs and brand asset. Reimplement on the current Foundation, never by merging those branches.
3. **RC hardening tests** — preserved in the canonical integration at `4af59d475c9e00ccb6bd6c5851d8f5a5d8571b00`; PR #641 is closed unmerged.

## Resume checkpoint

- Repository: `Nicricht/helvoca`
- Branch: `feat/frontend-orphan-branch-rescue`
- PR: #643 — DRAFT
- Base: `feat/frontend-finish-integration@3d73320848c7d81956701af6eb60f516f9eb1c80`
- Scope: audit / classification only; no production code rescued.
- Production-code changes in this PR: **none**.
- Main finding: current integration is 260 commits ahead / 0 behind `main`.
- Current unique deferred feature: `feat/frontend-appearance-themes@2112abcd03350516e830c23aaa2327d90f4ab22d` / PR #637 — POST-RC.
- RC hardening: consumed into canonical integration at `4af59d475c9e00ccb6bd6c5851d8f5a5d8571b00`; PR #641 closed unmerged.
- Next step: human/release decision on when the certified integration line may advance toward `main`; post-RC appearance work remains separate.
- Safety: no merge, deploy or external business effect performed.
