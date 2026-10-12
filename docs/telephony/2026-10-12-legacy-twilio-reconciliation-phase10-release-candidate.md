# Helvoca / RecepVoz: Phase 10/10 release candidate and controlled production checklist

**Prepared:** 2026-10-12. **Classification: PRODUCTION / COMMERCIAL: NO-GO.**

**Purpose:** A *code-review candidate* for a safe, explicitly authorized future pilot. Passing CI does **not** authorize carrier API access, Gemini connections, billable calls, Twilio number operations, provider configuration changes, Railway deployment or migration, merging, or commercial activation. Phase 10 itself performs none of these actions.

## 1. Baseline: what has been proven

- Repository: `Nicricht/helvoca`; PR #791, branch `feat/twilio-legacy-reconciliation-phase1-20261011`, open / draft / unmerged at baseline HEAD `4400af431a92e9681b4bc33692f74fa13eb08f66`.
- Phase 9 full run: [GitHub Actions 38225150823](https://github.com/Nicricht/helvoca/actions/runs/38225150823), exact baseline HEAD, **7/7** required jobs SUCCESS. Java 2,147 passed; Playwright 615 passed; differential executable lines 822/822, branches 666/666, methods 204/204 (100% each).
- Phase 1–9 local code: inspect / reconcile attested imported numbers with immutable RLS-protected approvals; accept Twilio callbacks and stream frames only with an authenticated and correctly bound carrier/tenant; reject cross-stream SIDs; fail closed for inactive, legacy and `VERIFIED_EXISTING` provenance in commercial call routing.
- Gemini Live `gemini-3.8-live` is a **code default**, not independently proven to be the deployed Railway model, licensed in the specific Google project, or successfully connected.

### Blocking ancestry finding

The authenticated GitHub compare `main...4400af431a92e9681b4bc33692f74fa13eb08f66` returned `status=diverged`, `behind_by=108`, `ahead_by=127`, merge base `60869bb6a6763cf6dc3de8863d7c8d77fe4447de`, main at `ca58693e167752bd6eab8ff4e9be770391fe4293`. **DIVERGED_FROM_MAIN** is a merge blocker; counts and SHAs must be rechecked just before any merge. PR #790 (`994170526d98ab10178d0667a7af80ac38c8ea67`) is also open/draft/unmerged, and PR #791 contains stacked history. **Never merge PR #791 independently of its parent or assume green CI on the old base covers a resolved main.**

## 2. Release decision matrix

| Gate | Current evidence | Decision |
|---|---|---|
| Local Java/Postgres/Playwright security and changed-code coverage | Phase 9 exact HEAD 7/7 green | Technical PASS on *that* SHA |
| Phase 10 final exact HEAD | Required 7/7 + 100% differential lines / branches / methods | Pending Phase 10 CI |
| Branch ancestry / PR #790 dependency | PR #791 108 behind main, parent unmerged at audit | **BLOCKED** |
| Production Twilio carrier ownership, phone SID, owner AC/subaccount, voice/status webhooks | Mocked checks only; no fresh carrier read | **PENDING** |
| Actual Railway Postgres pilot row and tenant binding | Not re-attested in this phase | **PENDING** |
| Gemini credentials, actual production model, quota, audio handshake | Offline only | **PENDING** |
| `VERIFIED_EXISTING` live ingress enablement | `TwilioCallBindingGuard` deliberately accepts only `MANAGED_PURCHASE` | **BLOCKED** |
| Authorized human, consent, budget, window, pilot and rollback | No explicit production approval granted | **BLOCKED** |

The code candidate cannot be marketed or described as a production-ready `VERIFIED_EXISTING` calling integration. Reconciliation writes provenance truthfully; it **does not** activate voice routing or satisfy real-call certification.

## 3. Safe sequence before any real operation

1. **Source ancestry:** get PR #790 reviewed and resolve its stacked relationship to #791 in the authorized merge plan. Bring the candidate up-to-date with current `main` through a reviewed non-production integration. Re-run seven mandatory CI jobs on the **new exact HEAD**. Do not rebase or merge as part of this release-candidate audit without a separate instruction.
2. **Read-only carrier attestation (new explicit approval):** request a narrowly scoped Twilio read-only carrier lookup; compare the trusted pilot E.164, PN SID, owning AC SID/subaccount, capabilities, exact voice URL, status URL and method. Compare to a separately authorized **read-only carrier** and Railway DB query. Neither screenshots nor `external_id` alone prove ownership. Never use a carrier purchase/phone mutation to repair disagreement.
3. **Pilot scope:** before relying on the kill switch, confirm the intended pilot tenant has a `pilot_launch_control` row. `ControlledPilotExternalEffectGuard` returns `NOT_PILOT_MANAGED` and allows effects for unregistered tenants even when `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false`. Verify its state starts PAUSED, record the rollback owner, and confirm nonpilot tenants cannot be accidentally routed by their independent carrier/provenance guards. **Do not rely on the global pilot flag alone.**
4. **Isolated and reversible enrollment (separate approval):** confirm human requester and a *different* platform approver, fresh Twilio proof, original row snapshot, duplicate/tenant conflict check, PENDING→APPROVED→APPLIED audit and no implicit `active=true`. Treat `VERIFIED_EXISTING` as non-routable after enrollment. Keep `app.telephony.twilio.existing-enrollment-admin-enabled` OFF unless specifically authorized.
5. **Model identity (new explicit approval):** inspect only safe non-secret configuration metadata first. `GEMINI_LIVE_PROBE_ON_STARTUP=false` by default; an authenticated Gemini WebSocket probe can consume quota and must have its own authorization/budget. A configured `GEMINI_API_KEY` does not prove model, billing or call usage.
6. **Live traffic (new explicit approval, NOT part of this Phase 10 audit):** only after independent technical signoff and a specifically reviewed route for `VERIFIED_EXISTING`. Use a consenting controlled tester, one specified pilot number/tenant, an exact time window, upper bound on attempts, maximum per-call seconds, **maximum authorized cost**, cost/usage alerts, monitored logs, and a kill-switch operator. Never enable startup-call routines to 'see if it works'. A voice success must include verified carrier account/callbacks, correct tenant, Gemini setup confirmation, audible bidirectional media, clean hangup and no unintended booking/payment.
7. **Rollback:** stop new calls and pause the tenant; set controlled pilot effects OFF where applicable, disable certification/provisioning/probe routines, restore the previously reviewed application revision and **only** restore webhook configuration if a deliberate authorized change occurred. Preserve request/approval/audit append-only history and provider evidence. Do not delete immutable audit rows, overwrite PN/AC provenance, or revert an applied migration by hand.

## 4. Explicit-approval ledger (all NOT AUTHORIZED)

Each proposed action requires its own written **explicit approval**, naming the exact tenant, pilot E.164 (when applicable), scope, human owner and requested window. A broad 'continúa' to develop this phase is **not** approval for production.

| Action | Current authorization |
|---|---|
| Read-only Twilio/DB pilot ownership inspection | NOT AUTHORIZED |
| Change Railway config, production DB, Twilio callbacks or reconcile a real row | NOT AUTHORIZED |
| Rebase/merge PR #790 or #791, deploy/restart production | NOT AUTHORIZED |
| Run a real Gemini model/credential WebSocket probe | NOT AUTHORIZED |
| Place an inbound/outbound paid carrier call or enable commercial traffic | NOT AUTHORIZED |
| Purchase/release/transfer a phone number | NOT AUTHORIZED |

Authorization record to fill before **any** separately approved operation: approver and operator identities; tenant identifier; exact E.164; affected provider account/subaccount; scope (read-only vs mutating vs billable); maximum attempts; per-call duration; maximum authorized cost and currency; consent evidence; scheduled window and timezone; monitoring owner; stop condition; rollback procedure; ticket/change ID; decision and timestamps. Never place credentials or bearer tokens in this ledger.

## 5. Maintained fail-closed defaults and ongoing protections

- `TWILIO_PROVISIONING_ENABLED=false`, `TWILIO_CERTIFICATION_INGRESS_ENABLED=false`, `TWILIO_CERTIFICATION_CALL_ON_STARTUP=false`, `GEMINI_LIVE_ENABLED=false`, and `GEMINI_LIVE_PROBE_ON_STARTUP=false` are code defaults, not verified values on deployed Railway instances.
- Admin enrollment controller is guarded by an explicit opt-in and `PLATFORM_ADMIN` authorization; two distinct trusted platform admins must approve/execute.
- `MANAGED_PURCHASE` alone satisfies Twilio call guard and local phone readiness; a `VERIFIED_EXISTING` row, even active, with correct PN/AC evidence, remains call-blocked until a separately implemented and reviewed release.
- `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED` defaults false; it **does not** protect `NOT_PILOT_MANAGED` tenants by itself. The release review must include this caveat and define a reliable check.
- Phone history, RLS, timestamps, carrier status, callbacks and media stream names cannot by themselves authorize commercial calling.

## 6. Exit criterion for Phase 10

**Code release candidate** exists when this runbook and its local release-invariant tests are committed, the exact final HEAD has all seven required CI jobs SUCCESS and 100% applicable *differential* line/branch/method coverage, and the PR remains draft/unmerged. That is **not production GO**.

**PRODUCTION / COMMERCIAL: NO-GO** remains binding until ancestry, ownership, model access, real route enablement, user consent, budget, and separately granted operational authorizations have been satisfied. An operator must issue a new explicit instruction for each external operation; this document does not authorize any.
