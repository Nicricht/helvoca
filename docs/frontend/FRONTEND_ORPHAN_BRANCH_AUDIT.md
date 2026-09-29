# Frontend orphan branch audit

Authoritative working ledger for `feat/frontend-orphan-branch-rescue`.

Base integration at audit start:
`feat/frontend-finish-integration@3d73320848c7d81956701af6eb60f516f9eb1c80`.

## Rules

- The certified integration branch is the canonical frontend reference.
- Historical branches are never merged wholesale.
- Rescue only behavior that is still valid for the current product.
- Production behavior requires RED → GREEN evidence before adoption.
- Any schema/API/security change is treated as HIGH risk.
- No merge to `main`, deploy, real calls, WhatsApp, payments, orders, deliveries or provisioning during this audit.

## Initial classification

| Branch | Initial finding | Current classification |
| --- | --- | --- |
| `feat/frontend-foundation-dark` | contained by integration | integrated |
| `feat/frontend-dashboard-finish` | contained by integration | integrated |
| `feat/frontend-onboarding-settings-finish` | contained by integration | integrated |
| `feat/frontend-commerce-operations-finish` | contained by integration | integrated |
| `feat/frontend-conversations-calls-finish` | contained by integration | integrated |
| `feat/frontend-billing-account-finish` | contained by integration | integrated |
| `feat/frontend-final-visual-polish` | zero commits ahead of integration | integrated/superseded |
| `test/frontend-release-candidate-v1` | identical to integration at audit start | test mirror |
| `test/release-candidate-first-business-hardening` | two commits ahead, workflow + E2E only | test-only, review separately |
| `feat/frontend-appearance-themes` | divergent; unique UI/API/schema/auth work | **candidate for selective rescue** |
| `feat/frontend-finalization-batch-1` | divergent historical UI/assets | pending supersession review |
| `feat/landing-hero-dark-v1` | divergent historical landing UI | pending supersession review |
| `feat/frontend-commercial-redesign-v3` | historical branch related to redesign already represented in main/integration | pending delta review |
| `feat/frontend-operational-redesign` | old operational/backend/UI branch, far behind current line | pending supersession review |
| `codex/frontend-ux-simplification` | old UX/security branch, far behind current line | pending supersession review |

## Required output before closure

For every divergent branch, record:
1. unique behavior/files;
2. whether current integration already has an equivalent;
3. rescue decision and rationale;
4. RED/GREEN evidence for rescued behavior;
5. exact final HEAD and CI evidence.

## Resume checkpoint

- Branch: `feat/frontend-orphan-branch-rescue`
- Base at creation: `feat/frontend-finish-integration@3d73320848c7d81956701af6eb60f516f9eb1c80`
- Risk: HIGH
- Completed: canonical/contained branches identified; divergent candidates enumerated.
- Next: deep review `feat/frontend-appearance-themes`, define desired current behavior and establish RED contract on this branch before implementation.
