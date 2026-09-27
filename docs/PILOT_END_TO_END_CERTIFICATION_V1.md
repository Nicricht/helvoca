# Pilot End-to-End Certification V1

This certification is the exit gate between feature-complete engineering and a controlled real-business pilot.

It does **not** place real calls, send real WhatsApp messages, charge real payments, deploy production changes, or mutate production tenant data.

## Certified customer journey

The gate exercises the existing deterministic sandbox path:

Voice context -> product/media selection -> WhatsApp handoff -> exact product/variant selection -> quote -> order confirmation -> inventory reservation -> sandbox payment creation -> verified payment webhook -> inventory consumption -> payment confirmation preparation -> shared Voice/WhatsApp commercial context -> journey trace / observability -> reconciliation safety.

## Adversarial scenarios

The same gate also verifies the failure surfaces that matter before a pilot:

1. repeated questions / duplicated assistant behavior are detected;
2. replay fixtures remain deterministic and anonymized;
3. transient provider/backend failures have bounded retries;
4. payment verification failure -> retry -> success remains exactly-once;
5. duplicate payment webhook does not apply payment twice;
6. durable jobs preserve lease/retry/dead-letter behavior;
7. inventory reservation expiry and paid recovery remain safe;
8. V6 reconciliation detects inconsistent commercial states;
9. only allowlisted reconciliation repairs mutate state;
10. journey trace remains available for post-incident diagnosis.

## Command

bash scripts/ci/pilot-e2e-certification.sh

## Acceptance criteria

The pilot gate is green only when all targeted suites pass together.

The normal repository Full Gate remains authoritative after this targeted certification:

- Java compilation;
- complete backend suite;
- PostgreSQL/Flyway/Testcontainers;
- differential JaCoCo thresholds;
- Playwright browser E2E.

## Safety boundary

This certification deliberately uses local/Testcontainers/sandbox evidence. Provider delivery remains disabled where the existing tests require it. A green pilot certification means the software path is ready for a controlled pilot decision; it is not evidence that external production credentials or provider networks are healthy.
