# HORIZON — AGENT BOOTSTRAP CONTRACT

Read these first for every material task:

1. `AGENTS.md`
2. `docs/HORIZON_MASTER_ROADMAP.md`
3. `ENGINEERING_STATUS.md`
4. `docs/VERIFICATION_MATRIX.md`
5. Relevant source/tests for the current gate

Never use chat history as code truth.

## Reasoning

When the host exposes configurable reasoning effort:
- initialize with `reasoning_effort=high`;
- keep high reasoning for the entire task/session;
- never voluntarily downgrade;
- never ask the user to repeat the setting;
- if unavailable, use the strongest available mode and continue.

## Tool / Plugin Autoload

At the beginning of every material engineering task, inspect available tool/plugin capabilities once and automatically select the relevant ones.

The user must not need to remind the agent.

Use as applicable:

- Superpowers: planning, systematic debugging, TDD, execution, verification-before-completion, code review, branch finishing.
- Codex Dev Workflows: feature development, bug investigation, verification, QA, orchestration, release review, interrupted-task recovery, handoff.
- Codex Engineering Guardrails: scoped implementation and independent verification.
- GitHub: repository/history/reference inspection.
- Context7: current library/API documentation.
- Exa: broad/current technical research.
- SciSpace: academic/scientific research.
- Security review capabilities when the changed surface materially requires them.
- Remote Desktop Commander only for actual machine/device evidence.

Do not invoke irrelevant plugins just to satisfy a checklist.

## Execution

Default:

`INSPECT → RECONCILE → PLAN → IMPLEMENT → TEST → BUILD → VERIFY → DOCUMENT → NEXT GATE`

After a gate passes, continue automatically to the next unblocked gate.

Do not stop merely because a report was produced.
Do not ask the user what to do next when normal continuation is already authorized.

## Speed

Prefer local execution over remote.
Batch independent reads/searches/checks.
Run independent checks in parallel when safe.
Reuse long-lived build/test processes.
Use one batched remote verification pass for physical-device evidence.
Never use remote to inspect source already available locally.

## Current HORIZON priority

Current panel = UNSTABLE.

Immediate target:

`B0 — Active-Work Reconciliation + Panel Recovery Firewall`

Before touching UI source:
1. inspect actual working tree;
2. identify uncommitted/unpushed work;
3. identify active ownership/processes when exposed;
4. preserve local work;
5. checkpoint before overlapping edits.

Do not overwrite local UI from GitHub state alone.

Do not add new UI features until B0 passes.

Then proceed through the master roadmap:
- B1–B6 UI recovery
- scientific GNSS propagation/solver gates
- Emergency Navigation / GNSS Interference Resilience
- external location delivery
- long-run/device/release verification

## Evidence

Use:
- VERIFIED
- OBSERVED
- MEASURED
- UNVERIFIED
- BLOCKED
- FAILED
- INFERRED

Rule:

**NO RECEIPT → NO EPISTEMIC UPGRADE**

Never invent measurements, device capabilities, test results, API behavior, or completion status.

## Safety

- Minimal safe change.
- Preserve verified behavior.
- Never overwrite user/local work.
- Never force-push or rewrite unrelated history.
- No speculative Gradle/Room/schema/dependency changes.
- Keep observed/predicted/derived/dead-reckoned states separate.
- Update the master roadmap when material state changes.

## Completion

Complete means relevant tests/build/device verification and durable evidence.

If a required external capability is unavailable, mark BLOCKED once, preserve evidence, and continue independent workstreams.
