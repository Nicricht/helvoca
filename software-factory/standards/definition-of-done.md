# Universal Definition of Done

A task is not complete because code exists or because one targeted test passes.

Completion requires evidence appropriate to its risk:

- acceptance criteria are satisfied;
- required tests exist and pass;
- every reproducible bug fix has a regression test that would fail if the defect returned;
- affected unit, integration, E2E, security, and invariant checks pass as applicable;
- build and changed-code coverage gates pass when the project defines them;
- required CI gates are green;
- evidence belongs to the **final commit** being presented;
- any code or engineering-contract change after certification makes earlier certification **invalid** and requires fresh verification;
- production-impacting work has an explicit release and rollback path;
- tests did not trigger unauthorized real external effects.

No completion claim may rely on stale runs, partial verification, or “it should work”.
