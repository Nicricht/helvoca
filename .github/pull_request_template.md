## What changes?

<!-- One concise description of the complete functional block. -->

## Risk level

- [ ] LOW
- [ ] MEDIUM
- [ ] HIGH

<!-- Why is this the correct risk level? -->

## Acceptance criteria

- [ ] Expected happy path is covered.
- [ ] Important failure/edge cases are covered.
- [ ] Existing resolved state is not asked for or mutated unnecessarily.
- [ ] Partial success cannot be mistaken for terminal success.

## Impact and affected invariants

<!-- Name affected UI/API/data/security/integration surfaces and the relevant entries from docs/engineering/invariants.md. -->

- [ ] Affected invariants are identified.
- [ ] Tenant/security/financial/inventory boundaries were reviewed when applicable.

## Frontend frame contract

<!-- Required for frontend/UI changes. Use N/A only when no frontend surface is touched. See docs/frontend/FRAME_CONTRACT.md. -->

- FRAME CHANGE: NO / YES / N/A
- Screen:
- Editable slot(s):
- Global frame unchanged: YES / NO / N/A
- Cross-screen evidence:

- [ ] A local UI request changes only its declared editable slot(s).
- [ ] Protected `--rv-frame-*` tokens and reserved frame primitives are unchanged when FRAME CHANGE: NO.
- [ ] Cross-screen evidence is recorded when FRAME CHANGE: YES.

## Regression protection

- [ ] Every reproducible bug fixed has a regression test.
- [ ] RED was observed before the fix/new behavior when TDD applies.
- [ ] Fast Gate passed.
- [ ] Changed executable Java lines meet differential JaCoCo coverage.
- [ ] Full backend suite passed.
- [ ] Browser E2E passed when applicable.
- [ ] Security/adversarial/concurrency/idempotency checks passed when risk requires them.

## Test evidence

<!-- Commands/runs and what they prove. -->

## Resume checkpoint

<!-- Required for MEDIUM/HIGH or multi-step work. Keep this current so another conversation can continue without rebuilding context. -->

- Branch:
- Pull Request:
- HEAD:
- Completed blocks:
- CI / evidence valid for this HEAD:
- Blockers / rulings:
- Next step:

## Final commit evidence

- [ ] Cited verification belongs to the exact final commit.
- [ ] No implementation or engineering-contract change occurred after the cited verification.

## Production safety

- [ ] No secrets are included.
- [ ] No unauthorized real call/message/payment/destructive action is triggered.
- [ ] Existing stronger repository gates were not removed or weakened.
- [ ] Deployment/runtime verification is defined if this changes production behavior.

## Release / rollback

<!-- For production-impacting work, describe release, migration, rollback, and runtime verification. Otherwise write N/A. -->
