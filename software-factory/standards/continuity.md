# Continuity and Resumable Engineering

Long-running engineering work must survive chat loss, browser closure, session expiry, model handoff, or a new conversation.

## Core rule

The chat is never the only source of truth for durable engineering progress.

For MEDIUM/HIGH-risk work, or any multi-step task likely to outlive one interaction, maintain a **durable checkpoint** outside the chat.

The preferred durable source is the repository plus its Draft Pull Request. If a Pull Request is not available, use another repository-backed artifact such as an issue or tracked progress document.

## Resume checkpoint

A resume checkpoint records enough state for a fresh agent to continue without asking the user to reconstruct the work:

- repository and branch;
- Pull Request or other durable work item;
- exact HEAD commit;
- completed blocks;
- current CI and verification evidence tied to that HEAD;
- known blockers or rulings;
- the **next step**.

Update the checkpoint after each meaningful completed block, before a long external wait, and whenever the next action changes materially.

## Resume protocol

Before continuing interrupted work, reconstruct state in this order:

1. repository;
2. branch;
3. Pull Request or durable work item;
4. exact HEAD;
5. CI and verification attached to that HEAD;
6. completed blocks/checkpoint;
7. first unfinished next step.

Do not trust chat memory over repository state.

## Idempotent resume

Resumption must be **idempotent**:

- do not repeat a completed step whose evidence is still valid for the same HEAD;
- do not duplicate external side effects;
- do not reuse stale evidence after the implementation or engineering contract changes;
- rerun only the verification invalidated by the newer state, plus any repository-mandated final gates.

## Long-running operations

Prefer external durable execution for long-running CI, builds, browser suites, and certification when the project supports it. The conversation may disconnect while GitHub Actions or another CI system continues to preserve the result.

The agent should communicate the current block and durable checkpoint before or during a long wait when practical.

## Completion

A long-running task is not ready to hand off if a fresh conversation would be unable to determine where it stopped.
