# First-Pass Engineering Risk Model

Quality effort is **proportional** to the risk introduced by the change.

## LOW

Typical scope:
- copy, documentation, isolated visual polish;
- non-behavioral configuration with no runtime effect.

Minimum evidence:
- syntax/lint/type validation when applicable;
- targeted tests only when behavior is affected;
- targeted UI/E2E only when interaction is affected.

## MEDIUM

Typical scope:
- normal feature work;
- CRUD/API changes;
- business rules;
- stateful frontend behavior;
- data transformations.

Required evidence:
- acceptance criteria and impact map;
- TDD for new behavior and reproducible bugs when applicable;
- unit and integration tests at affected boundaries;
- changed-code coverage;
- E2E for user-visible flows;
- project regression gate.

## HIGH

Typical scope:
- authentication/authorization;
- payments/billing;
- tenant or ownership boundaries;
- inventory consistency;
- destructive operations;
- migrations;
- external messaging/telephony;
- sensitive data or production infrastructure.

Required evidence includes MEDIUM plus applicable:
- threat/abuse review;
- isolation and authorization tests;
- idempotency and concurrency tests;
- retry/timeout/provider-failure tests;
- adversarial tests;
- rollback/recovery evidence;
- full regression gate.

Choose the lowest level that fully covers the actual blast radius. Do not use HIGH ceremony for a LOW-risk copy edit, and do not downgrade a HIGH-risk change to save time.
