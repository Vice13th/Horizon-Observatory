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

## K FAST-TRACK EXECUTION OVERRIDE

Horizon is time-critical. Once B0 is VERIFIED, Emergency Navigation / GNSS Interference Resilience becomes a **PARALLEL P0/P1 engineering track** and MUST NOT wait for blocked orientation work.

Rules:

- Do NOT wait for B4/B5 orientation hardware limitations before implementing K.
- Do NOT wait for all UI polish before implementing K.
- B1–B3 UI work and K software work may proceed in parallel when file ownership is independent.
- K0–K7 software architecture, state machine, evidence model, replay/simulation, tests, persistence, emergency mode, reception-optimization profile, and explicit external-location API work should be completed locally first.
- K8 physical/device validation is the only late hardware gate; prepare its exact verification batch while local K work continues.
- Use golden/synthetic/replay datasets for K development when physical interference testing is unavailable, but label those results separately from real-device evidence.
- Every K component must preserve raw evidence and explicit uncertainty/provenance.
- After each K gate passes, immediately continue to the next K gate without waiting for user prompts.
- Do not declare K COMPLETE until K8 and all required release evidence are genuinely verified.
- If a hardware-only subgate is BLOCKED, park only that subgate and continue every independent K workstream.

Preferred K execution order:

K0 capability contract
→ K1 interference evidence
→ K2 survivor/trust ranking
→ K3 resilience state machine
→ K4 last-trusted PVT + uncertainty bridge
→ K5 Emergency Navigation mode
→ K6 Reception Optimization
→ K7 Horizon Resilient Location API/bridge
→ K8 real-device/long-run validation

Parallelize K0/K1/K2/K3/K4/K5/K6/K7 wherever dependencies and file ownership permit.

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

Then use the fast-track order:
- B1–B3 UI evidence/polish as independent work
- K0–K7 Emergency Navigation / GNSS Interference Resilience in parallel
- scientific propagation/solver gates in parallel where dependencies permit
- K8 physical/long-run validation when hardware evidence is available
- external location delivery validation
- final integration and release verification

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
